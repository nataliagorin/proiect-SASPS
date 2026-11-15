package ro.saga.transfer.benchmark;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.regex.Pattern;

/**
 * {@code app.benchmark.*}: the run matrix and the workload. See docs/experiments.md.
 *
 * @param variants         {@code TransferService} bean names to measure, e.g. {@code V1}
 * @param scenario         label written in the CSV ({@code baseline}, {@code pool}, {@code contention}, ...)
 * @param transfers        measured transfers per run
 * @param warmupTransfers  unmeasured transfers before every run
 * @param failureRates     values for {@code app.fault.rate}, 0-1
 * @param threads          numbers of concurrent client threads
 * @param repetitions      runs per configuration
 * @param seed             base seed of the workload and of the injected faults
 * @param outputDir        where the CSV, raw data and environment.json go
 * @param gitSha           commit measured, passed in by the run script
 * @param dockerVersion    Docker version, passed in by the run script for environment.json
 * @param distribution     how accounts are picked: {@code uniform} over all accounts or {@code zipf} over the hot ones
 * @param hotAccounts      accounts used by the {@code zipf} distribution (ids 1..hotAccounts)
 * @param zipfExponent     skew of the {@code zipf} distribution; 1.0 is classic Zipf
 * @param minAmount        smallest transfer amount, whole lei
 * @param maxAmount        largest transfer amount, whole lei
 * @param sampleIntervalMs interval of the money-in-flight sampler
 */
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

    /** Accounts seeded in each bank by the Flyway migrations (ids 1-100). */
    public static final int ACCOUNTS_PER_BANK = 100;

    /** Scenario names end up in file names and CSV cells. */
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
