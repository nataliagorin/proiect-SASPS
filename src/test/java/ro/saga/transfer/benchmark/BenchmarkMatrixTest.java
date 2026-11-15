package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkMatrixTest {

    @Test
    void expandsEveryCombinationInOrder() {
        TestProperties properties = new TestProperties();
        properties.failureRates = List.of(0.0, 0.1);
        properties.threads = List.of(1, 4, 16);
        properties.repetitions = 3;

        List<RunConfig> runs = BenchmarkMatrix.expand(properties.build(), 10);

        assertThat(runs).hasSize(2 * 3 * 3);
        assertThat(runs.getFirst()).isEqualTo(new RunConfig("V1", "baseline", 0.0, 1, 1, 42, 10));
        assertThat(runs.get(1).repetition()).isEqualTo(2);
        assertThat(runs.getLast()).isEqualTo(new RunConfig("V1", "baseline", 0.1, 16, 3, 42, 10));
    }

    @Test
    void runIdsAreUniqueAndReadable() {
        TestProperties properties = new TestProperties();
        properties.failureRates = List.of(0.0, 0.05, 0.1);
        properties.threads = List.of(1, 2);
        properties.repetitions = 2;

        List<RunConfig> runs = BenchmarkMatrix.expand(properties.build(), 20);
        Set<String> ids = new HashSet<>();
        runs.forEach(run -> ids.add(run.runId("20261115T120000")));

        assertThat(ids).hasSize(runs.size());
        assertThat(runs.get(3).runId("20261115T120000")).isEqualTo("20261115T120000-V1-baseline-f0.00-t2-p20-rep2");
    }

    @Test
    void faultSeedDiffersPerRepetition() {
        RunConfig first = new RunConfig("V1", "baseline", 0.1, 4, 1, 42, 10);
        RunConfig second = new RunConfig("V1", "baseline", 0.1, 4, 2, 42, 10);

        assertThat(first.faultSeed()).isNotEqualTo(second.faultSeed());
        assertThat(first.faultSeed()).isEqualTo(new RunConfig("V1", "pool", 0.3, 16, 1, 42, 20).faultSeed());
    }
}
