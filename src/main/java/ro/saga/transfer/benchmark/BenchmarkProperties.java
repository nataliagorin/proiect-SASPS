package ro.saga.transfer.benchmark;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.regex.Pattern;

@ConfigurationProperties("app.benchmark")
public record BenchmarkProperties(
        @DefaultValue("V1") List<String> variants,
        @DefaultValue("baseline") String scenario,
        @DefaultValue("10000") int transfers,
        @DefaultValue("200") int warmupTransfers,
        @DefaultValue({"0.0", "0.05", "0.1", "0.2", "0.3"}) List<Double> failureRates,
        @DefaultValue({"1", "2", "4", "8", "16"}) List<Integer> threads,
        @DefaultValue("3") int repetitions,
        @DefaultValue("42") long seed,
        @DefaultValue("results") String outputDir,
        @DefaultValue("unknown") String gitSha,
        @DefaultValue("unknown") String dockerVersion,
        @DefaultValue("uniform") AccountDistribution distribution,
        @DefaultValue("10") int hotAccounts,
        @DefaultValue("1.0") double zipfExponent,
        @DefaultValue("1") int minAmount,
        @DefaultValue("50") int maxAmount,
        @DefaultValue("100") long sampleIntervalMs) {

    public static final int ACCOUNTS_PER_BANK = 100;

    private static final Pattern SAFE_LABEL = Pattern.compile("[A-Za-z0-9_]+");

    public BenchmarkProperties {
        variants = List.copyOf(require(variants, "variants"));
        failureRates = List.copyOf(require(failureRates, "failure-rates"));
        threads = List.copyOf(require(threads, "threads"));
        variants.forEach(variant -> check(SAFE_LABEL.matcher(variant).matches(),
                "variants must contain only letters, digits and _: " + variant));
        check(scenario != null && SAFE_LABEL.matcher(scenario).matches(),
                "scenario must contain only letters, digits and _: " + scenario);
        check(transfers >= 1, "transfers must be at least 1");
        check(warmupTransfers >= 0, "warmup-transfers must not be negative");
        failureRates.forEach(rate -> check(rate >= 0 && rate <= 1, "failure-rates must be between 0 and 1: " + rate));
        threads.forEach(count -> check(count >= 1, "threads must be at least 1: " + count));
        check(repetitions >= 1, "repetitions must be at least 1");
        check(outputDir != null && !outputDir.isBlank(), "output-dir must not be blank");
        check(distribution != null, "distribution must be uniform or zipf");
        check(hotAccounts >= 1 && hotAccounts <= ACCOUNTS_PER_BANK,
                "hot-accounts must be between 1 and " + ACCOUNTS_PER_BANK);
        check(zipfExponent > 0, "zipf-exponent must be positive");
        check(minAmount >= 1 && minAmount <= maxAmount, "amounts must satisfy 1 <= min-amount <= max-amount");
        check(sampleIntervalMs >= 10, "sample-interval-ms must be at least 10");
    }

    private static <T> List<T> require(List<T> values, String name) {
        check(values != null && !values.isEmpty(), name + " must not be empty");
        return values;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("app.benchmark." + message);
        }
    }
}
