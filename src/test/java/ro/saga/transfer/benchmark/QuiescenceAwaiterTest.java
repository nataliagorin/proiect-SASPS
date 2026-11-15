package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class QuiescenceAwaiterTest {

    @Test
    void returnsImmediatelyWhenNothingIsPending() {
        long start = System.nanoTime();

        long left = new QuiescenceAwaiter(() -> 0, Duration.ofSeconds(10), Duration.ofMillis(50)).await();

        assertThat(left).isZero();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void waitsUntilThePendingTransfersAreResolved() {
        AtomicLong pending = new AtomicLong(3);

        long left = new QuiescenceAwaiter(pending::decrementAndGet, Duration.ofSeconds(10), Duration.ofMillis(5))
                .await();

        assertThat(left).isZero();
    }

    @Test
    void givesUpAfterTheBoundAndReportsWhatIsLeft() {
        long start = System.nanoTime();

        long left = new QuiescenceAwaiter(() -> 2, Duration.ofMillis(150), Duration.ofMillis(20)).await();

        assertThat(left).isEqualTo(2);
        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isGreaterThanOrEqualTo(Duration.ofMillis(150))
                .isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void boundCoversTwoRecoveryPasses() {
        assertThat(QuiescenceAwaiter.recoveryBound(true, 5000, 1000)).isEqualTo(Duration.ofMillis(11_000));
        assertThat(QuiescenceAwaiter.recoveryBound(false, 5000, 1000)).isEqualTo(Duration.ZERO);
    }
}
