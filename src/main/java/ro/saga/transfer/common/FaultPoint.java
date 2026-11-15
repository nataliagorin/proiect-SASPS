package ro.saga.transfer.common;

public enum FaultPoint {
    AFTER_DEBIT,
    FRAUD_TIMEOUT,
    AFTER_CREDIT,
    DURING_COMPENSATION,
    AUDIT_FAILURE
}
