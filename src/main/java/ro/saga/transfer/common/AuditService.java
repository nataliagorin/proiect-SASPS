package ro.saga.transfer.common;

/** Simulated audit step at the end of a transfer. A failure here is retried and never undoes the transfer. */
public interface AuditService {

    /** @throws BankUnavailableException if the audit system does not answer */
    void record(long transferId, TransferOutcome outcome);
}
