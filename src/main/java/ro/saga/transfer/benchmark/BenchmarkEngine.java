package ro.saga.transfer.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ro.saga.transfer.bank.InvariantChecker;
import ro.saga.transfer.bank.InvariantReport;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferService;
import ro.saga.transfer.fault.FaultProperties;
import ro.saga.transfer.fault.SimulatedFaultInjector;
import ro.saga.transfer.fraud.FraudProperties;
import ro.saga.transfer.fraud.SimulatedFraudCheckService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes one run of the matrix against a {@link TransferService}, following the protocol in
 * docs/experiments.md: configure faults, reset, warmup, wait, reset, measured load with sampling, wait for
 * quiescence, check the invariants.
 */
@Component
@Profile("benchmark")
public class BenchmarkEngine {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkEngine.class);
    private static final Duration QUIESCENCE_POLL = Duration.ofMillis(100);

    private final Map<String, TransferService> variants;
    private final LedgerService ledger;
    private final InvariantChecker invariantChecker;
    private final SimulatedFaultInjector faults;
    private final SimulatedFraudCheckService fraud;
    private final BenchmarkProperties properties;
    private final WorkloadGenerator workload;
    private final LoadExecutor executor = new LoadExecutor();
    private final Duration quiescenceBound;
    private final Set<FaultPoint> faultPoints;
    private final FraudProperties fraudProperties;

    public BenchmarkEngine(
            Map<String, TransferService> variants,
            LedgerService ledger,
            InvariantChecker invariantChecker,
            SimulatedFaultInjector faults,
            SimulatedFraudCheckService fraud,
            BenchmarkProperties properties,
            @Value("${app.recovery.enabled:true}") boolean recoveryEnabled,
            @Value("${app.recovery.stale-after-ms:5000}") long staleAfterMs,
            @Value("${app.recovery.interval-ms:1000}") long recoveryIntervalMs) {
        for (String variant : properties.variants()) {
            if (!variants.containsKey(variant)) {
                throw new IllegalArgumentException(
                        "app.benchmark.variants: no TransferService bean named " + variant + "; available: "
                                + variants.keySet());
            }
        }
        this.variants = Map.copyOf(variants);
        this.ledger = ledger;
        this.invariantChecker = invariantChecker;
        this.faults = faults;
        this.fraud = fraud;
        this.properties = properties;
        this.workload = new WorkloadGenerator(properties);
        this.quiescenceBound = QuiescenceAwaiter.recoveryBound(recoveryEnabled, staleAfterMs, recoveryIntervalMs);
        // The points and the fraud rule come from app.fault.points and app.fraud.*; only the rate varies.
        this.faultPoints = faults.properties().points();
        this.fraudProperties = fraud.properties();
    }

    public RunMeasurement execute(RunConfig config, String runId) {
        TransferService service = variants.get(config.variant());
        QuiescenceAwaiter quiescence = new QuiescenceAwaiter(ledger::countPendingTransfers, quiescenceBound,
                QUIESCENCE_POLL);

        if (properties.warmupTransfers() > 0) {
            configureFaults(config);
            ledger.resetData();
            executor.run(service, workload.generate(~config.seed(), config.repetition(), config.threads(),
                    properties.warmupTransfers(), "warmup-" + runId));
            long left = quiescence.await();
            if (left > 0) {
                log.warn("{}: {} warmup transfers still PENDING after {}", runId, left, quiescenceBound);
            }
        }

        ledger.resetData();
        // A new FaultProperties object restarts every thread's fault sequence, so the run is reproducible.
        configureFaults(config);
        List<List<TransferRequest>> transfers = workload.generate(config.seed(), config.repetition(),
                config.threads(), properties.transfers(), runId);
        BigDecimal moneyBefore = ledger.getTotalMoney();
        InFlightSampler sampler = new InFlightSampler(ledger::getTotalMoney, ledger::getInFlightMoney,
                Duration.ofMillis(properties.sampleIntervalMs()));

        Instant startedAt = Instant.now();
        sampler.start();
        LoadExecutor.LoadResult load;
        long pendingLeft;
        List<InFlightSampler.Sample> samples;
        try {
            load = executor.run(service, transfers);
            pendingLeft = quiescence.await();
        } finally {
            // Sampling continues through quiescence, so G4 shows how the leftovers are resolved.
            samples = sampler.stop();
        }
        InvariantReport report = invariantChecker.check();
        List<Double> windows = ledger.inconsistencyWindowsMillis();
        return new RunMeasurement(config, runId, startedAt, load, samples, moneyBefore, report, windows, pendingLeft);
    }

    private void configureFaults(RunConfig config) {
        faults.reconfigure(new FaultProperties(config.faultSeed(), config.failureRate(), faultPoints));
        fraud.reconfigure(new FraudProperties(fraudProperties.latencyMs(), fraudProperties.maxAmount(),
                fraudProperties.blockedAccounts()));
    }
}
