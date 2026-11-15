package ro.saga.transfer.benchmark;

import java.util.Locale;

/** One cell of the run matrix and one repetition of it. Repetitions are numbered from 1. */
public record RunConfig(
        String variant, String scenario, double failureRate, int threads, int repetition, long seed, int poolSize) {

    /** Unique, file-name safe: {@code <campaign>-V1-baseline-f0.10-t8-p10-rep2}. */
    public String runId(String campaignId) {
        return String.format(Locale.ROOT, "%s-%s-%s-f%.2f-t%d-p%d-rep%d",
                campaignId, variant, scenario, failureRate, threads, poolSize, repetition);
    }

    /**
     * Seed of the injected faults. It includes the repetition so repetitions are independent samples, and stays
     * reproducible for the same seed.
     */
    public long faultSeed() {
        return seed * 31 + repetition;
    }
}
