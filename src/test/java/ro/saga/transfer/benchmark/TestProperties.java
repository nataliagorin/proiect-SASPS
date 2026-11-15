package ro.saga.transfer.benchmark;

import java.util.List;

final class TestProperties {

    List<String> variants = List.of("V1");
    String scenario = "baseline";
    int transfers = 100;
    int warmupTransfers = 0;
    List<Double> failureRates = List.of(0.0);
    List<Integer> threads = List.of(1);
    int repetitions = 1;
    long seed = 42;
    String outputDir = "results";
    AccountDistribution distribution = AccountDistribution.UNIFORM;
    int hotAccounts = 10;
    double zipfExponent = 1.0;
    int minAmount = 1;
    int maxAmount = 50;
    long sampleIntervalMs = 100;

    BenchmarkProperties build() {
        return new BenchmarkProperties(variants, scenario, transfers, warmupTransfers, failureRates, threads,
                repetitions, seed, outputDir, "test-sha", "test-docker", distribution, hotAccounts, zipfExponent,
                minAmount, maxAmount, sampleIntervalMs);
    }
}
