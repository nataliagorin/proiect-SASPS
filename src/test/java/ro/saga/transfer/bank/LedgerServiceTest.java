package ro.saga.transfer.bank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.saga.transfer.bank.alpha.AlphaBankTransfer;
import ro.saga.transfer.bank.alpha.AlphaBankTransferRepository;
import ro.saga.transfer.bank.beta.BetaBankTransfer;
import ro.saga.transfer.bank.beta.BetaBankTransferRepository;
import ro.saga.transfer.common.TransferStatus;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;
import ro.saga.transfer.config.BetaBankDataSourceConfig;

import javax.sql.DataSource;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest extends AbstractBankIntegrationTest {

    private static final BigDecimal FIFTY = new BigDecimal("50.00");

    @Autowired
    private LedgerService ledger;
    @Autowired
    private InvariantChecker invariants;
    @Autowired
    private AlphaBankOperations alpha;
    @Autowired
    private AlphaBankTransferRepository alphaTransfers;
    @Autowired
    private BetaBankTransferRepository betaTransfers;
    @Autowired
    @Qualifier(AlphaBankDataSourceConfig.DATA_SOURCE)
    private DataSource alphaDataSource;
    @Autowired
    @Qualifier(BetaBankDataSourceConfig.DATA_SOURCE)
    private DataSource betaDataSource;

    @BeforeEach
    void resetData() {
        ledger.resetData();
    }

    @Test
    void seedHoldsTwoHundredThousandInHundredAccountsPerBank() {
        assertThat(ledger.getTotalMoney()).isEqualByComparingTo(LedgerService.EXPECTED_TOTAL);
        assertThat(countAccounts(alphaDataSource)).isEqualTo(100);
        assertThat(countAccounts(betaDataSource)).isEqualTo(100);
    }

    @Test
    void resetDataRestoresBalancesAndClearsTransfers() {
        alpha.debit(1, new BigDecimal("300.00"));
        alphaTransfers.save(pendingAlphaTransfer("reset-1"));

        ledger.resetData();

        assertThat(ledger.getTotalMoney()).isEqualByComparingTo(LedgerService.EXPECTED_TOTAL);
        assertThat(alphaTransfers.count()).isZero();
    }

    @Test
    void inFlightMoneyIsDebitedButNotYetCredited() {
        alpha.debit(1, FIFTY);
        alphaTransfers.save(pendingAlphaTransfer("flight-1"));

        assertThat(ledger.getInFlightMoney()).isEqualByComparingTo(FIFTY);
        assertThat(ledger.getTotalMoney().add(ledger.getInFlightMoney()))
                .isEqualByComparingTo(LedgerService.EXPECTED_TOTAL);

        betaTransfers.save(new BetaBankTransfer("flight-1", 1, 1, FIFTY, TransferStatus.COMPLETED));

        assertThat(ledger.getInFlightMoney()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void invariantCheckerFlagsPendingTransfers() {
        assertThat(invariants.check().isConsistent()).isTrue();

        alphaTransfers.save(pendingAlphaTransfer("pending-1"));

        InvariantReport report = invariants.check();
        assertThat(report.pendingTransfers()).isEqualTo(1);
        assertThat(report.isConsistent()).isFalse();
    }

    @Test
    void inconsistencyWindowRunsFromCreationToFinalization() {
        alphaTransfers.save(pendingAlphaTransfer("window-1"));
        JdbcClient.create(alphaDataSource)
                .sql("UPDATE transfer SET finalized_at = created_at + interval '250 milliseconds'")
                .update();

        assertThat(ledger.inconsistencyWindowsMillis()).containsExactly(250.0);
    }

    @Test
    void idempotencyKeyIsUniquePerBank() {
        alphaTransfers.saveAndFlush(pendingAlphaTransfer("dup-1"));

        assertThatThrownBy(() -> alphaTransfers.saveAndFlush(pendingAlphaTransfer("dup-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AlphaBankTransfer pendingAlphaTransfer(String key) {
        return new AlphaBankTransfer(key, 1, 1, FIFTY, TransferStatus.PENDING);
    }

    private static long countAccounts(DataSource dataSource) {
        return JdbcClient.create(dataSource).sql("SELECT count(*) FROM account").query(Long.class).single();
    }
}
