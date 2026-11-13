package ro.saga.transfer.audit;

import org.junit.jupiter.api.Test;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.TransferOutcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedAuditServiceTest {

    @Test
    void countsRecords() {
        SimulatedAuditService audit = new SimulatedAuditService(FaultInjector.NONE);

        audit.record(1, TransferOutcome.COMPLETED);
        audit.record(2, TransferOutcome.COMPENSATED);

        assertThat(audit.recordedCount()).isEqualTo(2);
    }

    @Test
    void failedRecordIsNotCounted() {
        SimulatedAuditService audit = new SimulatedAuditService(point -> {
            throw new BankUnavailableException("down");
        });

        assertThatThrownBy(() -> audit.record(1, TransferOutcome.COMPLETED))
                .isInstanceOf(BankUnavailableException.class);
        assertThat(audit.recordedCount()).isZero();
    }
}
