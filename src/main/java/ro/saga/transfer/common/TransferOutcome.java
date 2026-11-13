package ro.saga.transfer.common;

/** What the caller learns. The benchmark counts each value separately. */
public enum TransferOutcome {
    COMPLETED,
    REJECTED_INSUFFICIENT_FUNDS,
    REJECTED_FRAUD,
    FAILED_TECHNICAL,
    COMPENSATED
}
