package ro.saga.transfer.benchmark;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Appends one row per run to the benchmark CSV, flushing after every run so a crash keeps the finished runs.
 * Numbers always use a decimal point ({@link Locale#ROOT}); a Romanian default locale would write commas.
 * NaN (no values, e.g. no finalized transfer) is written as an empty cell.
 */
public final class CsvResultWriter {

    /** The schema of docs/contracts.md, frozen with the contracts. */
    public static final List<String> HEADER = List.of(
            "run_id", "git_sha", "timestamp", "variant", "scenario", "repetition", "seed", "failure_rate", "threads",
            "pool_size",
            "transfers", "completed", "rejected_insufficient", "rejected_fraud", "failed_technical", "compensated",
            "pending_left",
            "money_before", "money_after", "money_lost",
            "inflight_mean_ms", "inflight_p95_ms", "inflight_max_money",
            "latency_mean_ms", "latency_p50_ms", "latency_p95_ms", "latency_p99_ms",
            "duration_s", "throughput_tps");

    private final Path file;

    public CsvResultWriter(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public synchronized void append(RunResult r) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            boolean writeHeader = !Files.exists(file) || Files.size(file) == 0;
            StringBuilder out = new StringBuilder();
            if (writeHeader) {
                out.append(String.join(",", HEADER)).append('\n');
            }
            out.append(row(r)).append('\n');
            Files.writeString(file, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    static String row(RunResult r) {
        return Stream.of(
                        r.runId(), r.gitSha(), r.timestamp().toString(), r.variant(), r.scenario(),
                        Integer.toString(r.repetition()), Long.toString(r.seed()), number(r.failureRate(), 4),
                        Integer.toString(r.threads()), Integer.toString(r.poolSize()),
                        Integer.toString(r.transfers()), Long.toString(r.completed()),
                        Long.toString(r.rejectedInsufficient()), Long.toString(r.rejectedFraud()),
                        Long.toString(r.failedTechnical()), Long.toString(r.compensated()),
                        Long.toString(r.pendingLeft()),
                        money(r.moneyBefore()), money(r.moneyAfter()), money(r.moneyLost()),
                        number(r.inflightMeanMs(), 3), number(r.inflightP95Ms(), 3), money(r.inflightMaxMoney()),
                        number(r.latencyMeanMs(), 3), number(r.latencyP50Ms(), 3), number(r.latencyP95Ms(), 3),
                        number(r.latencyP99Ms(), 3),
                        number(r.durationS(), 3), number(r.throughputTps(), 2))
                .map(CsvResultWriter::cell)
                .collect(Collectors.joining(","));
    }

    static String number(double value, int decimals) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "" : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    /** RFC 4180 quoting, in case a label ever contains a comma or a quote. */
    private static String cell(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
