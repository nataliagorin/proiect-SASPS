package ro.saga.transfer.benchmark;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class RawDataWriter {

    private final Path directory;

    public RawDataWriter(Path directory) {
        this.directory = directory;
    }

    public Path latencyFile(String runId) {
        return directory.resolve(runId + ".csv");
    }

    public Path inFlightFile(String runId) {
        return directory.resolve(runId + "-inflight.csv");
    }

    public Path windowsFile(String runId) {
        return directory.resolve(runId + "-windows.csv");
    }

    public void writeWindows(String runId, List<Double> windowsMs) {
        write(windowsFile(runId), out -> {
            out.write("window_ms\n");
            for (double window : windowsMs) {
                out.write(String.format(Locale.ROOT, "%.3f\n", window));
            }
        });
    }

    public void writeLatencies(String runId, LoadExecutor.LoadResult load) {
        write(latencyFile(runId), out -> {
            out.write("thread,sequence,outcome,latency_ms\n");
            for (int thread = 0; thread < load.latenciesNanos().length; thread++) {
                long[] latencies = load.latenciesNanos()[thread];
                for (int i = 0; i < latencies.length; i++) {
                    out.write(String.format(Locale.ROOT, "%d,%d,%s,%.3f\n",
                            thread, i, load.outcomes()[thread][i], latencies[i] / 1_000_000.0));
                }
            }
        });
    }

    public void writeInFlight(String runId, List<InFlightSampler.Sample> samples) {
        write(inFlightFile(runId), out -> {
            out.write("elapsed_ms,total_money,inflight_money\n");
            for (InFlightSampler.Sample sample : samples) {
                out.write(sample.elapsedMs() + "," + sample.totalMoney().toPlainString() + ","
                        + sample.inFlightMoney().toPlainString() + "\n");
            }
        });
    }

    private interface Body {
        void writeTo(BufferedWriter out) throws IOException;
    }

    private void write(Path file, Body body) {
        try {
            Files.createDirectories(directory);
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                body.writeTo(out);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
