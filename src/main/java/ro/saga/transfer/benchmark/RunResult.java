package ro.saga.transfer.benchmark;

import ro.saga.transfer.common.TransferOutcome;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of the benchmark CSV, with the columns of docs/contracts.md in the same order. Times in ms. */
public record RunResult(
        String runId,
        String gitSha,
        Instant timestamp,
        String variant,
        String scenario,
        int repetition,
        long seed,
        double failureRate,
        int threads,
        int poolSize,
        int transfers,
        long completed,
        long rejectedInsufficient,
        long rejectedFraud,
        long failedTechnical,
        long compensated,
        long pendingLeft,
        BigDecimal moneyBefore,
        BigDecimal moneyAfter,
        BigDecimal moneyLost,
        double inflightMeanMs,
        double inflightP95Ms,
        BigDecimal inflightMaxMoney,
        double latencyMeanMs,
        double latencyP50Ms,
        double latencyP95Ms,
        double latencyP99Ms,
        double durationS,
        double throughputTps) {

    public static RunResult from(RunMeasurement m, String gitSha) {
        RunConfig config = m.config();
        LoadExecutor.LoadResult load = m.load();
        double[] latencies = Statistics.sortedMillis(load.latenciesNanos());
        double[] windows = Statistics.sorted(m.inconsistencyWindowsMs());
        double durationS = load.durationNanos() / 1e9;
        return new RunResult(
                m.runId(),
                gitSha,
                m.startedAt(),
                config.variant(),
                config.scenario(),
                config.repetition(),
                config.seed(),
                config.failureRate(),
                config.threads(),
                config.poolSize(),
                load.transfers(),
                load.count(TransferOutcome.COMPLETED),
                load.count(TransferOutcome.REJECTED_INSUFFICIENT_FUNDS),
                load.count(TransferOutcome.REJECTED_FRAUD),
                load.count(TransferOutcome.FAILED_TECHNICAL),
                load.count(TransferOutcome.COMPENSATED),
                m.pendingLeft(),
                m.moneyBefore(),
                m.invariants().totalMoney(),
                m.invariants().moneyLost(),
                Statistics.mean(windows),
                Statistics.percentile(windows, 95),
                InFlightSampler.maxInFlight(m.samples()),
                Statistics.mean(latencies),
                Statistics.percentile(latencies, 50),
                Statistics.percentile(latencies, 95),
                Statistics.percentile(latencies, 99),
                durationS,
                durationS > 0 ? load.transfers() / durationS : Double.NaN);
    }
}
