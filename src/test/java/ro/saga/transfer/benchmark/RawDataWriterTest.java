package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ro.saga.transfer.common.TransferOutcome;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class RawDataWriterTest {

    @TempDir
    Path directory;

    @Test
    void writesOneLinePerTransfer() throws IOException {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("ro", "RO"));
            RawDataWriter writer = new RawDataWriter(directory.resolve("raw"));
            LoadExecutor.LoadResult load = new LoadExecutor.LoadResult(
                    new long[][] {{1_500_000, 2_000_000}, {3_250_000}},
                    new TransferOutcome[][] {
                            {TransferOutcome.COMPLETED, TransferOutcome.COMPENSATED},
                            {TransferOutcome.REJECTED_FRAUD}},
                    0, 10_000_000);

            writer.writeLatencies("run-1", load);

            assertThat(Files.readAllLines(writer.latencyFile("run-1"))).containsExactly(
                    "thread,sequence,outcome,latency_ms",
                    "0,0,COMPLETED,1.500",
                    "0,1,COMPENSATED,2.000",
                    "1,0,REJECTED_FRAUD,3.250");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void writesTheInFlightSamples() throws IOException {
        RawDataWriter writer = new RawDataWriter(directory.resolve("raw"));

        writer.writeInFlight("run-1", List.of(
                new InFlightSampler.Sample(0, new BigDecimal("200000.00"), BigDecimal.ZERO),
                new InFlightSampler.Sample(100, new BigDecimal("199950.00"), new BigDecimal("50.00"))));

        assertThat(writer.inFlightFile("run-1")).hasFileName("run-1-inflight.csv");
        assertThat(Files.readAllLines(writer.inFlightFile("run-1"))).containsExactly(
                "elapsed_ms,total_money,inflight_money",
                "0,200000.00,0",
                "100,199950.00,50.00");
    }
}
