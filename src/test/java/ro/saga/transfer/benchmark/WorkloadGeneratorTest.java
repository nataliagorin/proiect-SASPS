package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WorkloadGeneratorTest {

    private final WorkloadGenerator uniform = new WorkloadGenerator(AccountDistribution.UNIFORM, 10, 1.0, 1, 50);
    private final WorkloadGenerator zipf = new WorkloadGenerator(AccountDistribution.ZIPF, 10, 1.0, 1, 50);

    @Test
    void sameSeedGivesTheSameWorkload() {
        assertThat(uniform.generate(42, 1, 4, 1000, "run"))
                .isEqualTo(uniform.generate(42, 1, 4, 1000, "run"));
    }

    @Test
    void seedAndRepetitionChangeTheWorkload() {
        List<List<TransferRequest>> base = uniform.generate(42, 1, 1, 100, "run");

        assertThat(uniform.generate(43, 1, 1, 100, "run")).isNotEqualTo(base);
        assertThat(uniform.generate(42, 2, 1, 100, "run")).isNotEqualTo(base);
    }

    @Test
    void threadWorkloadDoesNotDependOnTheOtherThreads() {
        List<List<TransferRequest>> four = uniform.generate(42, 1, 4, 400, "run");
        List<List<TransferRequest>> two = uniform.generate(42, 1, 2, 200, "run");

        assertThat(four.get(0)).isEqualTo(two.get(0));
        assertThat(four.get(1)).isEqualTo(two.get(1));
    }

    @Test
    void splitsTheTotalAcrossThreads() {
        assertThat(WorkloadGenerator.split(10, 4)).containsExactly(3, 3, 2, 2);
        assertThat(WorkloadGenerator.split(3, 4)).containsExactly(1, 1, 1, 0);

        List<List<TransferRequest>> workload = uniform.generate(42, 1, 3, 1000, "run");
        assertThat(workload).hasSize(3);
        assertThat(workload.stream().mapToInt(List::size).sum()).isEqualTo(1000);
    }

    @Test
    void valuesStayInRangeAndKeysAreUnique() {
        List<TransferRequest> all = uniform.generate(42, 1, 8, 10_000, "run").stream().flatMap(List::stream).toList();
        Set<String> keys = new HashSet<>();

        for (TransferRequest transfer : all) {
            assertThat(transfer.fromBank()).isNotEqualTo(transfer.toBank());
            assertThat(transfer.fromAccountId()).isBetween(1L, 100L);
            assertThat(transfer.toAccountId()).isBetween(1L, 100L);
            assertThat(transfer.amount()).isBetween(new BigDecimal("1.00"), new BigDecimal("50.00"));
            assertThat(transfer.amount().scale()).isEqualTo(2);
            assertThat(keys.add(transfer.idempotencyKey())).isTrue();
        }
        assertThat(all.getFirst().idempotencyKey()).isEqualTo("run-0-0");
    }

    @Test
    void directionsAreBalancedAndEveryAccountIsUsed() {
        List<TransferRequest> all = uniform.generate(42, 1, 1, 10_000, "run").getFirst();

        double alphaToBeta = all.stream().filter(t -> t.fromBank() == Bank.ALPHA).count() / (double) all.size();
        assertThat(alphaToBeta).isCloseTo(0.5, within(0.02));
        assertThat(all.stream().map(TransferRequest::fromAccountId).distinct().count()).isEqualTo(100);
    }

    @Test
    void zipfConcentratesTrafficOnTheHotAccounts() {
        List<TransferRequest> all = zipf.generate(42, 1, 1, 20_000, "run").getFirst();
        long[] uses = new long[11];
        for (TransferRequest transfer : all) {
            assertThat(transfer.fromAccountId()).isBetween(1L, 10L);
            uses[(int) transfer.fromAccountId()]++;
        }

        // Zipf s=1 over 10 ranks: P(1) = 1 / H(10) ~ 0.341, P(1)/P(2) = 2.
        assertThat(uses[1] / (double) all.size()).isCloseTo(0.341, within(0.02));
        assertThat(uses[1] / (double) uses[2]).isCloseTo(2.0, within(0.2));
        for (int rank = 1; rank < 10; rank++) {
            assertThat(uses[rank]).isGreaterThan(uses[rank + 1]);
        }
    }
}
