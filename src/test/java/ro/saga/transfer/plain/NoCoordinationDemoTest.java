package ro.saga.transfer.plain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;
import ro.saga.transfer.bank.BankOperations;
import ro.saga.transfer.bank.Banks;
import ro.saga.transfer.bank.InvariantChecker;
import ro.saga.transfer.bank.InvariantReport;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.common.Bank;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the hazard of uncoordinated distributed transfers without Saga or compensation.
 * Used for presentation slides to document the motivation of the project.
 * Not part of the benchmark suite.
 */
class NoCoordinationDemoTest extends AbstractBankIntegrationTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Autowired
    private Banks banks;

    @Autowired
    private LedgerService ledger;

    @Autowired
    private InvariantChecker invariants;

    @BeforeEach
    void resetData() {
        ledger.resetData();
    }

    @Test
    void uncoordinatedTransfer_whenTargetFailsAfterDebit_losesMoney() {
        BankOperations alpha = banks.of(Bank.ALPHA);

        // 1. Initial state: exactly 200,000.00 lei across both banks
        assertThat(ledger.getTotalMoney()).isEqualByComparingTo(LedgerService.EXPECTED_TOTAL);
        assertThat(invariants.check().isConsistent()).isTrue();

        // 2. Uncoordinated debit commits in Alpha Bank
        alpha.debit(1, AMOUNT);
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");

        // 3. Simulated crash / network partition: target bank never receives the credit,
        // and because there is no coordination, saga orchestrator or compensation job,
        // the money remains permanently lost "in flight".

        // 4. Verify system invariant failure: total money dropped below 200,000.00 lei
        BigDecimal totalMoneyAfterFailure = ledger.getTotalMoney();
        assertThat(totalMoneyAfterFailure).isEqualByComparingTo(new BigDecimal("199900.00"));

        InvariantReport report = invariants.check();
        assertThat(report.isConsistent()).isFalse();
        assertThat(report.moneyLost()).isEqualByComparingTo(AMOUNT);
    }
}
