package ro.saga.transfer.bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankOperationsTest extends AbstractBankIntegrationTest {

    private static final int THREADS = 16;
    private static final int OPERATIONS_PER_THREAD = 1000;
    private static final int HOT_ACCOUNTS = 5;

    @Autowired
    private AlphaBankOperations alpha;
    @Autowired
    private BetaBankOperations beta;
    @Autowired
    private LedgerService ledger;
    @Autowired
    private InvariantChecker invariants;

    @BeforeEach
    void resetData() {
        ledger.resetData();
    }

    @Test
    void debitAndCreditChangeTheBalance() {
        alpha.debit(1, new BigDecimal("250.00"));
        beta.credit(1, new BigDecimal("250.00"));

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("750.00");
        assertThat(beta.balanceOf(1)).isEqualByComparingTo("1250.00");
    }

    @Test
    void debitOfTheWholeBalanceIsAllowed() {
        alpha.debit(1, new BigDecimal("1000.00"));

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("0");
    }

    @Test
    void debitBeyondTheBalanceIsRejectedAndChangesNothing() {
        assertThatThrownBy(() -> alpha.debit(1, new BigDecimal("1000.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
    }

    @Test
    void refundRestoresADebit() {
        alpha.debit(1, new BigDecimal("40.00"));
        alpha.refund(1, new BigDecimal("40.00"));

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> alpha.debit(1, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> beta.credit(1, new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownAccounts() {
        assertThatThrownBy(() -> alpha.debit(999, BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> beta.credit(999, BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void sixteenThreadsNeverLoseOrCreateMoney() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> workers = new ArrayList<>();

        for (int thread = 0; thread < THREADS; thread++) {
            workers.add(pool.submit(() -> {
                start.await();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                    long from = 1 + random.nextInt(HOT_ACCOUNTS);
                    long to = 1 + random.nextInt(HOT_ACCOUNTS);
                    try {
                        if (random.nextBoolean()) {
                            alpha.debit(from, BigDecimal.ONE);
                            beta.credit(to, BigDecimal.ONE);
                        } else {
                            beta.debit(from, BigDecimal.ONE);
                            alpha.credit(to, BigDecimal.ONE);
                        }
                    } catch (InsufficientFundsException ignored) {
                        // Nothing was debited, so nothing is credited.
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<Object> worker : workers) {
            worker.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        InvariantReport report = invariants.check();
        assertThat(report.moneyLost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.negativeBalances()).isZero();
    }
}
