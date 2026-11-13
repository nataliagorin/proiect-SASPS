package ro.saga.transfer.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferService;

/** Times every variant the same way, so no variant measures itself. */
@Component
public class TransferMetrics {

    public static final String TIMER = "transfer.duration";

    private final MeterRegistry registry;

    public TransferMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Records {@value #TIMER} tagged with {@code variant} and {@code outcome} ({@code EXCEPTION} if it threw). */
    public TransferService timed(String variant, TransferService delegate) {
        return request -> {
            Timer.Sample sample = Timer.start(registry);
            String outcome = "EXCEPTION";
            try {
                TransferResult result = delegate.transfer(request);
                outcome = result.outcome().name();
                return result;
            } finally {
                sample.stop(registry.timer(TIMER, "variant", variant, "outcome", outcome));
            }
        };
    }
}
