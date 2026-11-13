package ro.saga.transfer.common;

/** Places in a transfer where a failure can be injected. */
public enum FaultPoint {
    /** After the debit. */
    AFTER_DEBIT,
    /** In the fraud check. */
    FRAUD_TIMEOUT,
    /** After the credit, before COMPLETED. */
    AFTER_CREDIT,
    /** While refunding the debit. */
    DURING_COMPENSATION,
    /** In the audit step. */
    AUDIT_FAILURE
}
