package ro.saga.transfer.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ro.saga.transfer.common.TransferOutcome;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Runs the whole matrix from {@code app.benchmark.*}, one run after the other, and records every run. */
@Component
@Profile("benchmark")
public class BenchmarkCampaign {

    /** Runs executed and runs that failed the acceptance gate I1-I3. */
    public record Summary(int runs, int failedRuns) {
    }

    private static final Logger log = LoggerFactory.getLogger(BenchmarkCampaign.class);
    private static final DateTimeFormatter CAMPAIGN_ID = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final BenchmarkEngine engine;
    private final BenchmarkOutput output;
    private final BenchmarkProperties properties;
    private final int poolSize;

    public BenchmarkCampaign(
            BenchmarkEngine engine,
            BenchmarkOutput output,
            BenchmarkProperties properties,
            @Value("${app.datasource.alpha-bank.hikari.maximum-pool-size:10}") int poolSize) {
        this.engine = engine;
        this.output = output;
        this.properties = properties;
        this.poolSize = poolSize;
    }

    public Summary run() {
        List<RunConfig> runs = BenchmarkMatrix.expand(properties, poolSize);
        String campaignId = LocalDateTime.now().format(CAMPAIGN_ID);
        log.info("Benchmark {}: {} runs, {} measured transfers each, output in {}", campaignId, runs.size(),
                properties.transfers(), output.directory().toAbsolutePath());
        output.writeEnvironment();

        int failed = 0;
        for (int i = 0; i < runs.size(); i++) {
            RunConfig config = runs.get(i);
            RunMeasurement measurement = engine.execute(config, config.runId(campaignId));
            output.record(measurement);
            if (!measurement.passedGate()) {
                failed++;
            }
            log.info(describe(i + 1, runs.size(), measurement));
        }
        log.info("Benchmark {} finished: {} runs, {} failed the acceptance gate", campaignId, runs.size(), failed);
        return new Summary(runs.size(), failed);
    }

    private static String describe(int index, int total, RunMeasurement m) {
        LoadExecutor.LoadResult load = m.load();
        double seconds = load.durationNanos() / 1e9;
        return String.format(Locale.ROOT,
                "[%d/%d] %s: %d transfers in %.2f s (%.0f tps), completed=%d compensated=%d fraud=%d "
                        + "insufficient=%d technical=%d, money_lost=%s pending_left=%d %s",
                index, total, m.runId(), load.transfers(), seconds, load.transfers() / seconds,
                load.count(TransferOutcome.COMPLETED), load.count(TransferOutcome.COMPENSATED),
                load.count(TransferOutcome.REJECTED_FRAUD), load.count(TransferOutcome.REJECTED_INSUFFICIENT_FUNDS),
                load.count(TransferOutcome.FAILED_TECHNICAL), m.invariants().moneyLost().toPlainString(),
                m.pendingLeft(), m.passedGate() ? "OK" : "GATE FAILED");
    }
}
