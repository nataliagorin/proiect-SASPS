package ro.saga.transfer.fault;

import org.springframework.stereotype.Component;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;

import java.util.Random;

@Component
public class SimulatedFaultInjector implements FaultInjector {

    private record ThreadRandom(FaultProperties properties, Random random) {
    }

    private final ThreadLocal<ThreadRandom> randoms = new ThreadLocal<>();
    private volatile FaultProperties properties;

    public SimulatedFaultInjector(FaultProperties properties) {
        this.properties = properties;
    }

    public FaultProperties properties() {
        return properties;
    }

    public void reconfigure(FaultProperties properties) {
        this.properties = properties;
    }

    @Override
    public void maybeFail(FaultPoint point) {
        FaultProperties current = properties;
        if (current.rate() == 0 || !current.points().contains(point)) {
            return;
        }
        if (randomFor(current).nextDouble() < current.rate()) {
            throw new BankUnavailableException("Injected failure at " + point);
        }
    }

    private Random randomFor(FaultProperties current) {
        ThreadRandom threadRandom = randoms.get();
        if (threadRandom == null || threadRandom.properties() != current) {
            long seed = current.seed() * 31 + Thread.currentThread().getName().hashCode();
            threadRandom = new ThreadRandom(current, new Random(seed));
            randoms.set(threadRandom);
        }
        return threadRandom.random();
    }
}
