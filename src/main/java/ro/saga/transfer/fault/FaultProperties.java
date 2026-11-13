package ro.saga.transfer.fault;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ro.saga.transfer.common.FaultPoint;

import java.util.EnumSet;
import java.util.Set;

/** {@code app.fault.*}. An empty {@code points} means every {@link FaultPoint}. */
@ConfigurationProperties("app.fault")
public record FaultProperties(long seed, double rate, Set<FaultPoint> points) {

    public FaultProperties {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException("app.fault.rate must be between 0 and 1");
        }
        points = points == null || points.isEmpty() ? EnumSet.allOf(FaultPoint.class) : EnumSet.copyOf(points);
    }
}
