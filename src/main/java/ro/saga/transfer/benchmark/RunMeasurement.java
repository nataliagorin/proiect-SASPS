package ro.saga.transfer.benchmark;

import ro.saga.transfer.bank.InvariantReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Everything measured in one run, before any statistics.
 *
 * @param inconsistencyWindowsMs {@code finalized_at - created_at} of every finalized transfer, after quiescence
 * @param pendingLeft            transfers still {@code PENDING} when the quiescence wait ended
 */
public record RunMeasurement(
        RunConfig config,
        String runId,
        Instant startedAt,
        LoadExecutor.LoadResult load,
        List<InFlightSampler.Sample> samples,
        BigDecimal moneyBefore,
        InvariantReport invariants,
        List<Double> inconsistencyWindowsMs,
        long pendingLeft) {

    /** Acceptance gate I1-I3 from docs/experiments.md. */
    public boolean passedGate() {
        return invariants.isConsistent() && pendingLeft == 0;
    }
}
