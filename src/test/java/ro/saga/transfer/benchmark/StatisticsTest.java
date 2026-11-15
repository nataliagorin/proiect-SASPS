package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class StatisticsTest {

    @Test
    void percentilesInterpolateLikeNumpy() {
        double[] values = Statistics.sorted(List.of(15.0, 20.0, 35.0, 40.0, 50.0));

        // numpy.percentile([15, 20, 35, 40, 50], [0, 25, 40, 50, 95, 100]) -> 15, 20, 29, 35, 48, 50
        assertThat(Statistics.percentile(values, 0)).isEqualTo(15.0);
        assertThat(Statistics.percentile(values, 25)).isEqualTo(20.0);
        assertThat(Statistics.percentile(values, 40)).isCloseTo(29.0, within(1e-9));
        assertThat(Statistics.percentile(values, 50)).isEqualTo(35.0);
        assertThat(Statistics.percentile(values, 95)).isCloseTo(48.0, within(1e-9));
        assertThat(Statistics.percentile(values, 100)).isEqualTo(50.0);
    }

    @Test
    void percentilesOfOneToHundred() {
        double[] values = new double[100];
        for (int i = 0; i < 100; i++) {
            values[i] = i + 1;
        }

        assertThat(Statistics.percentile(values, 50)).isCloseTo(50.5, within(1e-9));
        assertThat(Statistics.percentile(values, 95)).isCloseTo(95.05, within(1e-9));
        assertThat(Statistics.percentile(values, 99)).isCloseTo(99.01, within(1e-9));
        assertThat(Statistics.mean(values)).isCloseTo(50.5, within(1e-9));
    }

    @Test
    void mergesThreadsAndConvertsNanosToMillis() {
        long[][] nanos = {{3_000_000, 1_000_000}, {2_500_000}, {}};

        assertThat(Statistics.sortedMillis(nanos)).containsExactly(1.0, 2.5, 3.0);
    }

    @Test
    void emptyInputGivesNaN() {
        assertThat(Statistics.mean(new double[0])).isNaN();
        assertThat(Statistics.percentile(new double[0], 95)).isNaN();
    }

    @Test
    void singleValueIsEveryPercentile() {
        assertThat(Statistics.percentile(new double[] {7.0}, 99)).isEqualTo(7.0);
    }

    @Test
    void rejectsPercentilesOutsideTheRange() {
        assertThatThrownBy(() -> Statistics.percentile(new double[] {1}, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
