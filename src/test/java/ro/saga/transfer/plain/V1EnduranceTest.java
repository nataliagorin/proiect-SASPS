package ro.saga.transfer.plain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;
import ro.saga.transfer.bank.BankOperations;
import ro.saga.transfer.bank.Banks;
import ro.saga.transfer.bank.InvariantChecker;
import ro.saga.transfer.bank.InvariantReport;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.FraudDecision;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates criteria M1-P3-4 (concurrent idempotency) and M1-P3-6 ("V1 is perfect" endurance & stress).
 * Proves that PlainTransferService maintains all financial invariants (I1-I6) under high concurrency and fault injection.
 */
@TestPropertySource(properties = "app.recovery.enabled=false")
class V1EnduranceTest extends AbstractBankIntegrationTest {

    private static final int CONCURRENT_THREADS = 16;

    @Autowired
    private Banks banks;

    @Autowired
    private LedgerService ledger;

    @Autowired
    private InvariantChecker invariants;

    @Autowired
    private RecoveryJob recoveryJob;

    private BankOperations alpha;
    private BankOperations beta;

    @BeforeEach
    void resetData() {
        ledger.resetData();
        alpha = banks.of(Bank.ALPHA);
        beta = banks.of(Bank.BETA);
    }

    /**
     * M1-P3-4: 16 threads submitting the exact same idempotencyKey simultaneously.
     * Guarantees that only one transfer executes debit and credit, while all callers receive consistent results.
     */
    @Test
    void concurrentDuplicateTransfers_16Threads_movesMoneyExactlyOnce() throws Exception {
        PlainTransferService service = new PlainTransferService(
                banks, FaultInjector.NONE, req -> FraudDecision.APPROVED, (id, outcome) -> {}, 3, 10L);

        TransferRequest sharedRequest = new TransferRequest(
                "shared-key-16-threads", Bank.ALPHA, 1, Bank.BETA, 2, new BigDecimal("100.00"));

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TransferResult>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return service.transfer(sharedRequest);
            }));
        }

        // Release all 16 threads simultaneously
        startGate.countDown();

        List<TransferResult> results = new ArrayList<>();
        for (Future<TransferResult> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // All threads should receive a successful completed result
        assertThat(results).hasSize(CONCURRENT_THREADS);
        Long expectedTransferId = null;
        for (TransferResult result : results) {
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.outcome()).isEqualTo(TransferOutcome.COMPLETED);
            assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
            if (expectedTransferId == null) {
                expectedTransferId = result.transferId();
            } else {
                assertThat(result.transferId()).isEqualTo(expectedTransferId);
            }
        }

        // Balance moved exactly once: 100.00 debited from Alpha(1), 100.00 credited to Beta(2)
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1100.00");

        // Invariants must hold perfectly
        InvariantReport report = invariants.check();
        assertThat(report.isConsistent()).isTrue();
        assertThat(report.moneyLost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.negativeBalances()).isZero();
        assertThat(report.pendingTransfers()).isZero();
    }

    /**
     * M1-P3-6: Criterion "V1 is correct" under stress and fault injection.
     * Executes bidirectional concurrent transfers across 16 threads with injected failures,
     * reconciles pending transfers, and asserts zero money lost, zero negative balances, and zero pending rows.
     */
    @Test
    void v1IsCorrect_underHighLoadAndFaultInjection_preservesAllInvariants() throws Exception {
        int totalTransfers = Integer.getInteger("test.endurance.transfers", 1000);
        double faultRate = 0.15; // 15% technical faults

        FaultInjector probabilisticFaults = point -> {
            if (point == FaultPoint.AFTER_DEBIT || point == FaultPoint.DURING_COMPENSATION) {
                if (ThreadLocalRandom.current().nextDouble() < faultRate) {
                    throw new BankUnavailableException("Injected transient failure at " + point);
                }
            }
        };

        PlainTransferService service = new PlainTransferService(
                banks, probabilisticFaults, req -> FraudDecision.APPROVED, (id, outcome) -> {}, 3, 5L);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        AtomicInteger completedCount = new AtomicInteger();
        AtomicInteger compensatedCount = new AtomicInteger();

        int transfersPerThread = totalTransfers / CONCURRENT_THREADS;

        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            final int threadId = t;
            futures.add(pool.submit(() -> {
                startGate.await();
                ThreadLocalRandom rnd = ThreadLocalRandom.current();

                for (int i = 0; i < transfersPerThread; i++) {
                    boolean alphaToBeta = rnd.nextBoolean();
                    Bank fromBank = alphaToBeta ? Bank.ALPHA : Bank.BETA;
                    Bank toBank = alphaToBeta ? Bank.BETA : Bank.ALPHA;
                    long fromAccount = rnd.nextLong(1, 101);
                    long toAccount = rnd.nextLong(1, 101);
                    BigDecimal amount = new BigDecimal(rnd.nextInt(1, 20) + ".00");

                    String key = "stress-" + threadId + "-" + i;
                    TransferRequest req = new TransferRequest(key, fromBank, fromAccount, toBank, toAccount, amount);

                    TransferResult res = service.transfer(req);
                    if (res.outcome() == TransferOutcome.COMPLETED) {
                        completedCount.incrementAndGet();
                    } else if (res.outcome() == TransferOutcome.COMPENSATED) {
                        compensatedCount.incrementAndGet();
                    }
                }
                return null;
            }));
        }

        startGate.countDown();

        for (Future<Void> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Quiescence phase: run RecoveryJob until all pending transfers are reconciled
        recoveryJob.recoverPendingTransfers(Duration.ZERO);

        // Verify criteria I1 - I6:
        // I1: Total money is exactly 200,000.00
        assertThat(ledger.getTotalMoney()).isEqualByComparingTo(LedgerService.EXPECTED_TOTAL);

        // I2, I3: InvariantChecker verifies zero money lost, zero negative balances, zero PENDING left
        InvariantReport report = invariants.check();
        assertThat(report.isConsistent()).isTrue();
        assertThat(report.moneyLost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.negativeBalances()).isZero();
        assertThat(report.pendingTransfers()).isZero();

        assertThat(completedCount.get() + compensatedCount.get()).isGreaterThan(0);
    }
}
