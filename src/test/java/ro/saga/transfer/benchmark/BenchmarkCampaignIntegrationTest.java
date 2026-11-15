package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;
import ro.saga.transfer.bank.InvariantChecker;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.common.TransferService;
import ro.saga.transfer.fault.FaultProperties;
import ro.saga.transfer.fault.SimulatedFaultInjector;
import ro.saga.transfer.fraud.SimulatedFraudCheckService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** A tiny matrix end to end: V1 on the real databases, then the files the analysis reads. */
@TestPropertySource(properties = {"app.recovery.stale-after-ms=1000", "app.recovery.interval-ms=200"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BenchmarkCampaignIntegrationTest extends AbstractBankIntegrationTest {

    @TempDir
    Path output;

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

    private final FaultProperties noFaults = new FaultProperties(42, 0, null);

    @AfterEach
    void restore() {
        faults.reconfigure(noFaults);
        ledger.resetData();
    }

    @Test
    void writesOneGatedRowPerRunPlusRawFilesAndEnvironment() throws IOException {
        TestProperties properties = new TestProperties();
        properties.transfers = 120;
        properties.warmupTransfers = 10;
        properties.failureRates = List.of(0.0, 0.2);
        properties.threads = List.of(1, 4);
        properties.sampleIntervalMs = 20;
        BenchmarkProperties built = properties.build();
        BenchmarkEngine engine = new BenchmarkEngine(variants, ledger, invariants, faults, fraud, built, true, 1000, 200);
        BenchmarkOutput benchmarkOutput = new BenchmarkOutput(built, output, 20);

        BenchmarkCampaign.Summary summary = new BenchmarkCampaign(engine, benchmarkOutput, built, 20).run();

        assertThat(summary).isEqualTo(new BenchmarkCampaign.Summary(4, 0));
        List<String> lines = Files.readAllLines(output.resolve("runs.csv"));
        assertThat(lines).hasSize(5);
        assertThat(lines.getFirst()).isEqualTo(String.join(",", CsvResultWriter.HEADER));
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split(",", -1);
            assertThat(cells).hasSize(CsvResultWriter.HEADER.size());
            assertThat(cell(cells, "transfers")).isEqualTo("120");
            assertThat(cell(cells, "money_lost")).isEqualTo("0.00");
            assertThat(cell(cells, "pending_left")).isEqualTo("0");
            assertThat(cell(cells, "git_sha")).isEqualTo("test-sha");
            assertThat(cell(cells, "pool_size")).isEqualTo("20");
            assertThat(Double.parseDouble(cell(cells, "throughput_tps"))).isPositive();
            assertThat(Double.parseDouble(cell(cells, "latency_p99_ms")))
                    .isGreaterThanOrEqualTo(Double.parseDouble(cell(cells, "latency_p50_ms")));

            String runId = cell(cells, "run_id");
            assertThat(Files.readAllLines(output.resolve("raw/" + runId + ".csv"))).hasSize(121);
            assertThat(Files.readAllLines(output.resolve("raw/" + runId + "-inflight.csv"))).hasSizeGreaterThan(1);
        }
        try (Stream<Path> raw = Files.list(output.resolve("raw"))) {
            assertThat(raw.count()).isEqualTo(8);
        }
        assertThat(Files.readString(output.resolve("environment.json"))).contains("\"git_sha\": \"test-sha\"");
    }

    private static String cell(String[] cells, String column) {
        return cells[CsvResultWriter.HEADER.indexOf(column)];
    }
}
