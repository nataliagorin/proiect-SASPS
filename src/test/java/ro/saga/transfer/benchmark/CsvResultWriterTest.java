package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CsvResultWriterTest {

    @TempDir
    Path directory;

    @Test
    void headerMatchesTheSchemaInContractsMd() throws IOException {
        String contracts = Files.readString(Path.of("docs/contracts.md"));
        String section = contracts.substring(contracts.indexOf("## Schema CSV"));
        int start = section.indexOf("```") + 3;
        String block = section.substring(start, section.indexOf("```", start));
        List<String> documented = Arrays.stream(block.split(","))
                .map(String::trim)
                .filter(column -> !column.isEmpty())
                .toList();

        assertThat(CsvResultWriter.HEADER).containsExactlyElementsOf(documented);
    }

    @Test
    void writesTheHeaderOnceAndAppendsRows() throws IOException {
        CsvResultWriter writer = new CsvResultWriter(directory.resolve("nested/runs.csv"));

        writer.append(result("run-1", 12.5));
        writer.append(result("run-2", 7.25));

        List<String> lines = Files.readAllLines(writer.file());
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(String.join(",", CsvResultWriter.HEADER));
        assertThat(lines.get(1)).startsWith("run-1,abc1234,2026-11-15T10:00:00Z,V1,baseline,1,42,0.1000,4,10,");
        assertThat(lines.get(2)).startsWith("run-2,");
        assertThat(lines.get(1).split(",", -1)).hasSize(CsvResultWriter.HEADER.size());
    }

    @Test
    void usesDecimalPointsUnderARomanianLocale() throws IOException {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("ro", "RO"));
            CsvResultWriter writer = new CsvResultWriter(directory.resolve("runs.csv"));

            writer.append(result("run-ro", 12.5));

            String row = Files.readAllLines(writer.file()).get(1);
            assertThat(row.split(",", -1)).hasSize(CsvResultWriter.HEADER.size());
            assertThat(row).contains(",12.500,").doesNotContain("12,500");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void nanBecomesAnEmptyCell() {
        assertThat(CsvResultWriter.number(Double.NaN, 3)).isEmpty();
        assertThat(CsvResultWriter.number(1.23456, 3)).isEqualTo("1.235");
    }

    @Test
    void rowHasTheValuesInSchemaOrder() {
        String[] cells = CsvResultWriter.row(result("run-1", 12.5)).split(",", -1);

        assertThat(cells[CsvResultWriter.HEADER.indexOf("completed")]).isEqualTo("90");
        assertThat(cells[CsvResultWriter.HEADER.indexOf("money_lost")]).isEqualTo("0.00");
        assertThat(cells[CsvResultWriter.HEADER.indexOf("inflight_mean_ms")]).isEmpty();
        assertThat(cells[CsvResultWriter.HEADER.indexOf("latency_p95_ms")]).isEqualTo("12.500");
        assertThat(cells[CsvResultWriter.HEADER.indexOf("throughput_tps")]).isEqualTo("50.00");
    }

    static RunResult result(String runId, double p95) {
        return new RunResult(runId, "abc1234", Instant.parse("2026-11-15T10:00:00Z"), "V1", "baseline", 1, 42, 0.1,
                4, 10, 100, 90, 1, 2, 3, 4, 0,
                new BigDecimal("200000.00"), new BigDecimal("200000.00"), new BigDecimal("0.00"),
                Double.NaN, Double.NaN, new BigDecimal("75.00"),
                5.0, 4.0, p95, 20.0, 2.0, 50.0);
    }
}
