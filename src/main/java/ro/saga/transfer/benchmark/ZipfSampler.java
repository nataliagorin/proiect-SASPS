package ro.saga.transfer.benchmark;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/** Ranks 1..n with probability proportional to 1/rank^exponent, from a precomputed cumulative table. */
final class ZipfSampler {

    private final double[] cumulative;

    ZipfSampler(int n, double exponent) {
        if (n < 1) {
            throw new IllegalArgumentException("n must be at least 1");
        }
        cumulative = new double[n];
        double sum = 0;
        for (int rank = 1; rank <= n; rank++) {
            sum += 1.0 / Math.pow(rank, exponent);
            cumulative[rank - 1] = sum;
        }
        for (int i = 0; i < n; i++) {
            cumulative[i] /= sum;
        }
        cumulative[n - 1] = 1.0;
    }

    /** A rank between 1 and n. */
    int sample(RandomGenerator random) {
        int index = Arrays.binarySearch(cumulative, random.nextDouble());
        return (index >= 0 ? index + 1 : -index - 1) + 1;
    }
}
