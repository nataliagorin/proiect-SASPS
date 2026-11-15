package ro.saga.transfer.benchmark;

import java.util.Locale;

public record RunConfig(
        String variant, String scenario, double failureRate, int threads, int repetition, long seed, int poolSize) {

    public String runId(String campaignId) {
        return String.format(Locale.ROOT, "%s-%s-%s-f%.2f-t%d-p%d-rep%d",
                campaignId, variant, scenario, failureRate, threads, poolSize, repetition);
    }

    public long faultSeed() {
        return seed * 31 + repetition;
    }
}
