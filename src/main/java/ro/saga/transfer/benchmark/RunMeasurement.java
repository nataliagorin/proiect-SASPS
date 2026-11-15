package ro.saga.transfer.benchmark;

import ro.saga.transfer.bank.InvariantReport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    public boolean passedGate() {
        return invariants.isConsistent() && pendingLeft == 0;
    }
}
