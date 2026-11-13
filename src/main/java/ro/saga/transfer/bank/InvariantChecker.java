package ro.saga.transfer.bank;

import org.springframework.stereotype.Component;

@Component
public class InvariantChecker {

    private final LedgerService ledger;

    public InvariantChecker(LedgerService ledger) {
        this.ledger = ledger;
    }

    public InvariantReport check() {
        return new InvariantReport(
                ledger.getTotalMoney(),
                LedgerService.EXPECTED_TOTAL,
                ledger.countNegativeBalances(),
                ledger.countPendingTransfers());
    }
}
