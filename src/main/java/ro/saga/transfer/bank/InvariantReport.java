package ro.saga.transfer.bank;

import java.math.BigDecimal;

public record InvariantReport(
        BigDecimal totalMoney, BigDecimal expectedTotal, long negativeBalances, long pendingTransfers) {

    public BigDecimal moneyLost() {
        return expectedTotal.subtract(totalMoney);
    }

    public boolean isConsistent() {
        return moneyLost().signum() == 0 && negativeBalances == 0 && pendingTransfers == 0;
    }
}
