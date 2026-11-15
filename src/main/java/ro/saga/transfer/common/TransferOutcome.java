package ro.saga.transfer.common;

public enum TransferOutcome {
    COMPLETED,
    REJECTED_INSUFFICIENT_FUNDS,
    REJECTED_FRAUD,
    FAILED_TECHNICAL,
    COMPENSATED
}
