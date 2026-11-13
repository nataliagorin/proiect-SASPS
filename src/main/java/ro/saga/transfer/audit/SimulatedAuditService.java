package ro.saga.transfer.audit;

import org.springframework.stereotype.Service;
import ro.saga.transfer.common.AuditService;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.TransferOutcome;

import java.util.concurrent.atomic.AtomicLong;

/** Fails at {@link FaultPoint#AUDIT_FAILURE}; otherwise only counts the records. */
@Service
public class SimulatedAuditService implements AuditService {

    private final FaultInjector faults;
    private final AtomicLong recorded = new AtomicLong();

    public SimulatedAuditService(FaultInjector faults) {
        this.faults = faults;
    }

    @Override
    public void record(long transferId, TransferOutcome outcome) {
        faults.maybeFail(FaultPoint.AUDIT_FAILURE);
        recorded.incrementAndGet();
    }

    public long recordedCount() {
        return recorded.get();
    }
}
