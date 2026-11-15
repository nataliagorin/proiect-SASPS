package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InFlightSamplerTest {

    @Test
    void samplesAtTheIntervalUntilStopped() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        InFlightSampler sampler = new InFlightSampler(
                () -> new BigDecimal("200000.00"),
                () -> BigDecimal.valueOf(calls.incrementAndGet()),
                Duration.ofMillis(20));

        sampler.start();
        Thread.sleep(200);
        List<InFlightSampler.Sample> samples = sampler.stop();

        // ~10 scheduled readings plus the final one; generous bounds for slow CI machines.
        assertThat(samples).hasSizeBetween(5, 15);
        assertThat(samples).isSortedAccordingTo((a, b) -> Long.compare(a.elapsedMs(), b.elapsedMs()));
        assertThat(samples.getFirst().elapsedMs()).isLessThan(50);
        assertThat(samples.getLast().elapsedMs()).isGreaterThanOrEqualTo(200);
        assertThat(samples).allSatisfy(s -> assertThat(s.totalMoney()).isEqualByComparingTo("200000.00"));

        int callsAtStop = calls.get();
        Thread.sleep(60);
        assertThat(calls.get()).isEqualTo(callsAtStop);
    }

    @Test
    void maxInFlightIsTheLargestSample() {
        List<InFlightSampler.Sample> samples = List.of(
                new InFlightSampler.Sample(0, BigDecimal.TEN, new BigDecimal("12.50")),
                new InFlightSampler.Sample(100, BigDecimal.TEN, new BigDecimal("80.00")),
                new InFlightSampler.Sample(200, BigDecimal.TEN, BigDecimal.ZERO));

        assertThat(InFlightSampler.maxInFlight(samples)).isEqualByComparingTo("80.00");
        assertThat(InFlightSampler.maxInFlight(List.of())).isEqualByComparingTo("0");
    }

    @Test
    void aFailingReadingIsSkipped() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        InFlightSampler sampler = new InFlightSampler(() -> BigDecimal.ONE, () -> {
            if (calls.incrementAndGet() % 2 == 0) {
                throw new IllegalStateException("database busy");
            }
            return BigDecimal.ONE;
        }, Duration.ofMillis(10));

        sampler.start();
        Thread.sleep(100);
        List<InFlightSampler.Sample> samples = sampler.stop();

        assertThat(samples).isNotEmpty().hasSizeLessThan(calls.get());
    }

    @Test
    void cannotStopBeforeStarting() {
        InFlightSampler sampler = new InFlightSampler(() -> BigDecimal.ONE, () -> BigDecimal.ONE, Duration.ofMillis(10));

        assertThatThrownBy(sampler::stop).isInstanceOf(IllegalStateException.class);
    }
}
