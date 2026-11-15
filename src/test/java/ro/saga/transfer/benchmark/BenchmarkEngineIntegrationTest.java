package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;
import ro.saga.transfer.bank.InvariantChecker;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferService;
import ro.saga.transfer.fault.FaultProperties;
import ro.saga.transfer.fault.SimulatedFaultInjector;
import ro.saga.transfer.fraud.FraudProperties;
import ro.saga.transfer.fraud.SimulatedFraudCheckService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Runs the engine against V1 and the real bank databases. A short recovery period keeps the quiescence wait short;
 * the context is closed afterwards so its RecoveryJob cannot touch the rows of later tests.
 */
@TestPropertySource(properties = {"app.recovery.stale-after-ms=1000", "app.recovery.interval-ms=200"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BenchmarkEngineIntegrationTest extends AbstractBankIntegrationTest {

    @Autowired
    private Map<String, TransferService> variants;

    @Autowired
    private LedgerService ledger;

    @Autowired
    private InvariantChecker invariants;

    @Autowired
    private SimulatedFaultInjector faults;

    @Autowired
    private SimulatedFraudCheckService fraud;

    private FaultProperties originalFaults;
    private FraudProperties originalFraud;

    @BeforeEach
    void rememberConfiguration() {
        originalFaults = faults.properties();
        originalFraud = fraud.properties();
    }

    @AfterEach
    void restoreConfiguration() {
        faults.reconfigure(originalFaults);
        fraud.reconfigure(originalFraud);
        ledger.resetData();
    }

    @Test
    void runWithFailuresPassesTheAcceptanceGate() {
        TestProperties properties = new TestProperties();
        properties.transfers = 400;
        properties.warmupTransfers = 40;
        properties.sampleIntervalMs = 20;
        RunConfig config = new RunConfig("V1", "baseline", 0.2, 4, 1, 42, 20);

        RunMeasurement measurement = engine(properties.build()).execute(config, "it-gate");

        LoadExecutor.LoadResult load = measurement.load();
        assertThat(load.transfers()).isEqualTo(400);
        assertThat(load.exceptions()).isZero();
        assertThat(load.count(TransferOutcome.COMPLETED)).isPositive();
        assertThat(load.count(TransferOutcome.COMPENSATED)).isPositive();
        assertThat(measurement.moneyBefore()).isEqualByComparingTo(LedgerService.EXPECTED_TOTAL);
        assertThat(measurement.invariants().moneyLost()).isEqualByComparingTo("0");
        assertThat(measurement.invariants().negativeBalances()).isZero();
        assertThat(measurement.pendingLeft()).isZero();
        assertThat(measurement.passedGate()).isTrue();
        // Every debited transfer is finalized, so it has an inconsistency window; warmup rows were reset.
        long debited = load.transfers() - load.count(TransferOutcome.REJECTED_INSUFFICIENT_FUNDS);
        assertThat(measurement.inconsistencyWindowsMs()).hasSize((int) debited).allSatisfy(w -> assertThat(w).isNotNegative());
        assertThat(measurement.samples()).isNotEmpty();
        assertThat(InFlightSampler.maxInFlight(measurement.samples())).isNotNegative();
    }

    @Test
    void sameConfigurationGivesTheSameOutcomes() {
        TestProperties properties = new TestProperties();
        properties.transfers = 300;
        properties.warmupTransfers = 20;
        BenchmarkEngine engine = engine(properties.build());
        RunConfig config = new RunConfig("V1", "baseline", 0.2, 1, 1, 42, 20);

        RunMeasurement first = engine.execute(config, "it-repeat");
        RunMeasurement second = engine.execute(config, "it-repeat");

        assertThat(first.passedGate()).isTrue();
        assertThat(second.passedGate()).isTrue();
        assertThat(Arrays.deepEquals(first.load().outcomes(), second.load().outcomes())).isTrue();
        assertThat(first.load().count(TransferOutcome.COMPENSATED)).isPositive();
    }

    @Test
    void blockedAccountsGiveAboutFivePercentFraudRejections() {
        fraud.reconfigure(new FraudProperties(0, null, Set.of(1L, 2L, 3L, 4L, 5L)));
        TestProperties properties = new TestProperties();
        properties.transfers = 1000;
        RunConfig config = new RunConfig("V1", "fraud", 0.0, 4, 1, 42, 20);

        RunMeasurement measurement = engine(properties.build()).execute(config, "it-fraud");

        double fraudRate = measurement.load().count(TransferOutcome.REJECTED_FRAUD) / 1000.0;
        assertThat(fraudRate).isCloseTo(0.05, within(0.02));
        assertThat(measurement.passedGate()).isTrue();
    }

    @Test
    void unknownVariantIsRejectedAtStartup() {
        TestProperties properties = new TestProperties();
        properties.variants = List.of("V9");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> engine(properties.build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("V9")
                .hasMessageContaining("V1");
    }

    private BenchmarkEngine engine(BenchmarkProperties properties) {
        return new BenchmarkEngine(variants, ledger, invariants, faults, fraud, properties, true, 1000, 200);
    }
}
