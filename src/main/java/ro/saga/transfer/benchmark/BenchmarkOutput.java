package ro.saga.transfer.benchmark;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Everything a campaign writes under {@code app.benchmark.output-dir}:
 * <pre>
 * results/
 *   runs.csv                    one row per run (docs/contracts.md)
 *   environment.json            machine, JVM, git SHA
 *   raw/&lt;run_id&gt;.csv           latency of every transfer
 *   raw/&lt;run_id&gt;-inflight.csv  money-in-flight samples
 *   raw/&lt;run_id&gt;-windows.csv   inconsistency window of every finalized transfer
 * </pre>
 */
@Component
@Profile("benchmark")
public class BenchmarkOutput {

    public static final String RESULTS_FILE = "runs.csv";
    public static final String ENVIRONMENT_FILE = "environment.json";
    public static final String RAW_DIRECTORY = "raw";

    private final BenchmarkProperties properties;
    private final Path directory;
    private final CsvResultWriter csv;
    private final RawDataWriter raw;

    @Autowired
    public BenchmarkOutput(BenchmarkProperties properties) {
        this(properties, Path.of(properties.outputDir()));
    }

    BenchmarkOutput(BenchmarkProperties properties, Path directory) {
        this.properties = properties;
        this.directory = directory;
        this.csv = new CsvResultWriter(directory.resolve(RESULTS_FILE));
        this.raw = new RawDataWriter(directory.resolve(RAW_DIRECTORY));
    }

    public Path directory() {
        return directory;
    }

    /** At the start of a campaign. */
    public void writeEnvironment() {
        EnvironmentReport.write(directory.resolve(ENVIRONMENT_FILE), EnvironmentReport.collect(properties));
    }

    /** After every run: raw files first, then the CSV row that points to them. */
    public RunResult record(RunMeasurement measurement) {
        raw.writeLatencies(measurement.runId(), measurement.load());
        raw.writeInFlight(measurement.runId(), measurement.samples());
        raw.writeWindows(measurement.runId(), measurement.inconsistencyWindowsMs());
        RunResult result = RunResult.from(measurement, properties.gitSha());
        csv.append(result);
        return result;
    }
}
