package ro.saga.transfer.benchmark;

import java.util.Arrays;
import java.util.Collection;

/**
 * Exact summary statistics over raw values. Percentiles use linear interpolation between closest ranks (R type 7,
 * the numpy and pandas default), so the CSV and analysis/plot_m1.py agree on the same data.
 */
public final class Statistics {

    private Statistics() {
    }

    /** Sorted copy, ready for {@link #percentile(double[], double)}. */
    public static double[] sorted(Collection<Double> values) {
        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(array);
        return array;
    }

    /** Nanoseconds of every thread, merged, converted to milliseconds and sorted. */
    public static double[] sortedMillis(long[][] nanosPerThread) {
        double[] millis = Arrays.stream(nanosPerThread)
                .flatMapToLong(Arrays::stream)
                .mapToDouble(nanos -> nanos / 1_000_000.0)
                .toArray();
        Arrays.sort(millis);
        return millis;
    }

    /** NaN when there are no values. */
    public static double mean(double[] values) {
        return values.length == 0 ? Double.NaN : Arrays.stream(values).sum() / values.length;
    }

    /**
     * @param sorted     ascending values
     * @param percentile between 0 and 100
     * @return NaN when there are no values
     */
    public static double percentile(double[] sorted, double percentile) {
        if (percentile < 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be between 0 and 100");
        }
        if (sorted.length == 0) {
            return Double.NaN;
        }
        double rank = percentile / 100 * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        return sorted[lower] + (rank - lower) * (sorted[upper] - sorted[lower]);
    }
}
