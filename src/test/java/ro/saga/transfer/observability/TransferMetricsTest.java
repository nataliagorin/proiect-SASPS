package ro.saga.transfer.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferService;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferMetricsTest {

    private static final TransferRequest REQUEST =
            new TransferRequest("key", Bank.ALPHA, 1, Bank.BETA, 2, BigDecimal.TEN);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TransferMetrics metrics = new TransferMetrics(registry);

    @Test
    void tagsVariantAndOutcome() {
        TransferService timed = metrics.timed("V1", request -> TransferResult.completed(1));

        timed.transfer(REQUEST);
        timed.transfer(REQUEST);

        assertThat(registry.get(TransferMetrics.TIMER).tag("variant", "V1").tag("outcome", "COMPLETED")
                .timer().count()).isEqualTo(2);
    }

    @Test
    void countsExceptions() {
        TransferService timed = metrics.timed("V1", request -> {
            throw new IllegalStateException("bug");
        });

        assertThatThrownBy(() -> timed.transfer(REQUEST)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.get(TransferMetrics.TIMER).tag("outcome", "EXCEPTION").timer().count()).isEqualTo(1);
    }
}
