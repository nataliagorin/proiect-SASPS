package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class LoadExecutorTest {

    private final WorkloadGenerator workload = new WorkloadGenerator(AccountDistribution.UNIFORM, 10, 1.0, 1, 50);
    private final LoadExecutor executor = new LoadExecutor();

    @Test
    void eachListRunsOnItsNamedThread() {
        Map<String, String> threadByKey = new ConcurrentHashMap<>();
        List<List<TransferRequest>> transfers = workload.generate(42, 1, 4, 40, "run");

        LoadExecutor.LoadResult result = executor.run(request -> {
            threadByKey.put(request.idempotencyKey(), Thread.currentThread().getName());
            return TransferResult.completed(1);
        }, transfers);

        assertThat(result.transfers()).isEqualTo(40);
        assertThat(result.count(TransferOutcome.COMPLETED)).isEqualTo(40);
        for (int thread = 0; thread < 4; thread++) {
            for (TransferRequest request : transfers.get(thread)) {
                assertThat(threadByKey.get(request.idempotencyKey())).isEqualTo("benchmark-" + thread);
            }
        }
    }

    @Test
    void recordsLatencyAndOutcomeOfEveryCall() {
        List<List<TransferRequest>> transfers = workload.generate(42, 1, 2, 10, "run");

        LoadExecutor.LoadResult result = executor.run(request -> {
            sleep(2);
            return request.idempotencyKey().endsWith("0")
                    ? TransferResult.rejectedFraud(1)
                    : TransferResult.completed(1);
        }, transfers);

        assertThat(result.count(TransferOutcome.REJECTED_FRAUD)).isEqualTo(2);
        assertThat(result.count(TransferOutcome.COMPLETED)).isEqualTo(8);
        for (long[] thread : result.latenciesNanos()) {
            assertThat(thread).hasSize(5);
            assertThat(Arrays.stream(thread).min().orElseThrow()).isGreaterThanOrEqualTo(2_000_000);
        }
        assertThat(result.durationNanos()).isGreaterThanOrEqualTo(5 * 2_000_000L);
    }

    @Test
    void anExceptionCountsAsTechnicalFailureAndTheRunContinues() {
        List<List<TransferRequest>> transfers = workload.generate(42, 1, 1, 3, "run");
        Set<String> boom = Set.of("run-0-1");

        LoadExecutor.LoadResult result = executor.run(request -> {
            if (boom.contains(request.idempotencyKey())) {
                throw new IllegalStateException("boom");
            }
            return TransferResult.completed(1);
        }, transfers);

        assertThat(result.exceptions()).isEqualTo(1);
        assertThat(result.outcomes()[0]).containsExactly(
                TransferOutcome.COMPLETED, TransferOutcome.FAILED_TECHNICAL, TransferOutcome.COMPLETED);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
