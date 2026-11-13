package ro.saga.transfer.common;

/** Stored in {@code transfer.status}. {@code PENDING} is the only non-terminal state. */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED,
    COMPENSATED
}
