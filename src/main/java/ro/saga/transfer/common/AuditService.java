package ro.saga.transfer.common;

public interface AuditService {

    void record(long transferId, TransferOutcome outcome);
}
