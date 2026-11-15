package ro.saga.transfer.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferService;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LoadExecutor {

    public static final String THREAD_PREFIX = "benchmark-";

    private static final Logger log = LoggerFactory.getLogger(LoadExecutor.class);

    public record LoadResult(long[][] latenciesNanos, TransferOutcome[][] outcomes, int exceptions, long durationNanos) {

        public int transfers() {
            int total = 0;
            for (long[] thread : latenciesNanos) {
                total += thread.length;
            }
            return total;
        }

        public long count(TransferOutcome outcome) {
            long count = 0;
            for (TransferOutcome[] thread : outcomes) {
                for (TransferOutcome current : thread) {
                    if (current == outcome) {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    public LoadResult run(TransferService service, List<List<TransferRequest>> workload) {
        int threads = workload.size();
        long[][] latencies = new long[threads][];
        TransferOutcome[][] outcomes = new TransferOutcome[threads][];
        AtomicInteger exceptions = new AtomicInteger();
        AtomicReference<Throwable> crash = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            int index = t;
            List<TransferRequest> transfers = workload.get(t);
            latencies[t] = new long[transfers.size()];
            outcomes[t] = new TransferOutcome[transfers.size()];
            workers[t] = Thread.ofPlatform().name(THREAD_PREFIX + t).unstarted(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int i = 0; i < transfers.size(); i++) {
                        long begin = System.nanoTime();
                        TransferOutcome outcome;
                        try {
                            TransferResult result = service.transfer(transfers.get(i));
                            outcome = result.outcome();
                        } catch (RuntimeException e) {
                            exceptions.incrementAndGet();
                            log.warn("Transfer {} threw instead of returning a result: {}",
                                    transfers.get(i).idempotencyKey(), e.toString());
                            outcome = TransferOutcome.FAILED_TECHNICAL;
                        }
                        latencies[index][i] = System.nanoTime() - begin;
                        outcomes[index][i] = outcome;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    crash.compareAndSet(null, e);
                } catch (Throwable e) {
                    crash.compareAndSet(null, e);
                }
            });
            workers[t].start();
        }

        try {
            ready.await();
            long begin = System.nanoTime();
            start.countDown();
            for (Thread worker : workers) {
                worker.join();
            }
            long duration = System.nanoTime() - begin;
            if (crash.get() != null) {
                throw new IllegalStateException("A benchmark thread failed", crash.get());
            }
            return new LoadResult(latencies, outcomes, exceptions.get(), duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            for (Thread worker : workers) {
                worker.interrupt();
            }
            throw new IllegalStateException("Interrupted while waiting for the benchmark threads", e);
        }
    }
}
