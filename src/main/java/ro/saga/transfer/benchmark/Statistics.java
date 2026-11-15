package ro.saga.transfer.benchmark;

import java.util.Arrays;
import java.util.Collection;

public final class Statistics {

    private Statistics() {
    }

    public static double[] sorted(Collection<Double> values) {
        double[] array = values.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(array);
        return array;
    }

    public static double[] sortedMillis(long[][] nanosPerThread) {
        double[] millis = Arrays.stream(nanosPerThread)
                .flatMapToLong(Arrays::stream)
                .mapToDouble(nanos -> nanos / 1_000_000.0)
                .toArray();
        Arrays.sort(millis);
        return millis;
    }

    public static double mean(double[] values) {
        return values.length == 0 ? Double.NaN : Arrays.stream(values).sum() / values.length;
    }

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
