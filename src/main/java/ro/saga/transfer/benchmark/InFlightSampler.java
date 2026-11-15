package ro.saga.transfer.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class InFlightSampler {

    public record Sample(long elapsedMs, BigDecimal totalMoney, BigDecimal inFlightMoney) {
    }

    private static final Logger log = LoggerFactory.getLogger(InFlightSampler.class);

    private final Supplier<BigDecimal> totalMoney;
    private final Supplier<BigDecimal> inFlightMoney;
    private final Duration interval;
    private final List<Sample> samples = Collections.synchronizedList(new ArrayList<>());
    private ScheduledExecutorService scheduler;
    private long startNanos;

    public InFlightSampler(Supplier<BigDecimal> totalMoney, Supplier<BigDecimal> inFlightMoney, Duration interval) {
        this.totalMoney = totalMoney;
        this.inFlightMoney = inFlightMoney;
        this.interval = interval;
    }

    public void start() {
        if (scheduler != null) {
            throw new IllegalStateException("Sampler already started");
        }
        samples.clear();
        startNanos = System.nanoTime();
        scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofPlatform().name("inflight-sampler").daemon().unstarted(runnable));
        scheduler.scheduleAtFixedRate(this::sample, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public List<Sample> stop() {
        if (scheduler == null) {
            throw new IllegalStateException("Sampler not started");
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
        scheduler = null;
        sample();
        synchronized (samples) {
            return List.copyOf(samples);
        }
    }

    public static BigDecimal maxInFlight(List<Sample> samples) {
        return samples.stream().map(Sample::inFlightMoney).reduce(BigDecimal.ZERO, BigDecimal::max);
    }

    private void sample() {
        try {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            samples.add(new Sample(elapsedMs, totalMoney.get(), inFlightMoney.get()));
        } catch (RuntimeException e) {
            log.warn("In-flight sample skipped: {}", e.getMessage());
        }
    }
}
