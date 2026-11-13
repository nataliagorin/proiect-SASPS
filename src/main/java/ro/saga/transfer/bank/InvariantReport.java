package ro.saga.transfer.bank;

import java.math.BigDecimal;

/** Acceptance rules 1-3 from docs/contracts.md, checked once the system is quiet. */
public record InvariantReport(
        BigDecimal totalMoney, BigDecimal expectedTotal, long negativeBalances, long pendingTransfers) {

    /** Positive when money disappeared, negative when money was created. */
    public BigDecimal moneyLost() {
        return expectedTotal.subtract(totalMoney);
    }

    public boolean isConsistent() {
        return moneyLost().signum() == 0 && negativeBalances == 0 && pendingTransfers == 0;
    }
}
