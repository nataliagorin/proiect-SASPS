package ro.saga.transfer.bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferRecordsTest extends AbstractBankIntegrationTest {

    private static final BigDecimal FIFTY = new BigDecimal("50.00");

    @Autowired
    private Banks banks;
    @Autowired
    private LedgerService ledger;

    private BankOperations alpha;
    private BankOperations beta;

    @BeforeEach
    void resetData() {
        ledger.resetData();
        alpha = banks.of(Bank.ALPHA);
        beta = banks.of(Bank.BETA);
    }

    @Test
    void banksAreLookedUpByEnum() {
        assertThat(alpha.bank()).isEqualTo(Bank.ALPHA);
        assertThat(beta.bank()).isEqualTo(Bank.BETA);
    }

    @Test
    void debitCreatesAPendingRowAndMoneyInFlight() {
        long id = alpha.debitAndRecordPending(request("k-1", FIFTY));

        TransferRecord row = alpha.findTransfer(id).orElseThrow();
        assertThat(row.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(row.createdAt()).isNotNull();
        assertThat(row.finalizedAt()).isNull();
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("950.00");
        assertThat(ledger.getInFlightMoney()).isEqualByComparingTo(FIFTY);
    }

    @Test
    void insufficientFundsWritesNothing() {
        assertThatThrownBy(() -> alpha.debitAndRecordPending(request("k-1", new BigDecimal("1000.01"))))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(alpha.findTransfer("k-1")).isEmpty();
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
    }

    @Test
    void duplicateKeyDebitsOnlyOnce() {
        alpha.debitAndRecordPending(request("k-1", FIFTY));

        assertThatThrownBy(() -> alpha.debitAndRecordPending(request("k-1", FIFTY)))
                .isInstanceOf(DuplicateTransferException.class);
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("950.00");
    }

    @Test
    void creditIsIdempotent() {
        assertThat(beta.creditOnce(request("k-1", FIFTY))).isTrue();
        assertThat(beta.creditOnce(request("k-1", FIFTY))).isFalse();

        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1050.00");
        assertThat(beta.findTransfer("k-1").orElseThrow().status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void creditToUnknownAccountWritesNothing() {
        TransferRequest request = new TransferRequest("k-1", Bank.ALPHA, 1, Bank.BETA, 999, FIFTY);

        assertThatThrownBy(() -> beta.creditOnce(request)).isInstanceOf(AccountNotFoundException.class);
        assertThat(beta.findTransfer("k-1")).isEmpty();
    }

    @Test
    void completedTransferIsFinalizedAndCannotBeCompensated() {
        TransferRequest request = request("k-1", FIFTY);
        long id = alpha.debitAndRecordPending(request);
        beta.creditOnce(request);

        assertThat(alpha.markCompleted(id)).isTrue();
        assertThat(alpha.markCompleted(id)).isFalse();
        assertThat(alpha.compensate(id, "late")).isFalse();

        assertThat(alpha.findTransfer(id).orElseThrow().finalizedAt()).isNotNull();
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("950.00");
        assertThat(invariantsHold()).isTrue();
    }

    @Test
    void compensationRefundsOnce() {
        long id = alpha.debitAndRecordPending(request("k-1", FIFTY));

        assertThat(alpha.compensate(id, "Fraud check rejected")).isTrue();
        assertThat(alpha.compensate(id, "again")).isFalse();
        assertThat(alpha.markCompleted(id)).isFalse();

        TransferRecord row = alpha.findTransfer(id).orElseThrow();
        assertThat(row.status()).isEqualTo(TransferStatus.COMPENSATED);
        assertThat(row.failureReason()).isEqualTo("Fraud check rejected");
        assertThat(row.finalizedAt()).isNotNull();
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
        assertThat(invariantsHold()).isTrue();
    }

    @Test
    void attemptsAreCounted() {
        long id = alpha.debitAndRecordPending(request("k-1", FIFTY));

        assertThat(alpha.recordAttempt(id, "Beta Bank unavailable")).isEqualTo(1);
        assertThat(alpha.recordAttempt(id, "Beta Bank unavailable")).isEqualTo(2);
        assertThat(alpha.findTransfer(id).orElseThrow().failureReason()).isEqualTo("Beta Bank unavailable");
    }

    @Test
    void recoveryFindsOnlyOldPendingRows() throws InterruptedException {
        long old = alpha.debitAndRecordPending(request("k-1", FIFTY));
        long done = alpha.debitAndRecordPending(request("k-2", FIFTY));
        alpha.markCompleted(done);
        Thread.sleep(200);
        alpha.debitAndRecordPending(request("k-3", FIFTY));

        assertThat(alpha.findPendingOlderThan(Duration.ofMillis(100)))
                .extracting(TransferRecord::id)
                .containsExactly(old);
    }

    @Test
    void concurrentRequestsWithTheSameKeyDebitOnce() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    alpha.debitAndRecordPending(request("same", FIFTY));
                    return true;
                } catch (DuplicateTransferException duplicate) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            succeeded += result.get(30, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("950.00");
    }

    private boolean invariantsHold() {
        return ledger.getTotalMoney().add(ledger.getInFlightMoney()).compareTo(LedgerService.EXPECTED_TOTAL) == 0
                && ledger.countNegativeBalances() == 0;
    }

    private static TransferRequest request(String key, BigDecimal amount) {
        return new TransferRequest(key, Bank.ALPHA, 1, Bank.BETA, 2, amount);
    }
}
