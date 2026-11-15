package ro.saga.transfer.benchmark;

import java.time.Duration;
import java.util.function.LongSupplier;

public final class QuiescenceAwaiter {

    private final LongSupplier pendingTransfers;
    private final Duration maxWait;
    private final Duration pollInterval;

    public QuiescenceAwaiter(LongSupplier pendingTransfers, Duration maxWait, Duration pollInterval) {
        this.pendingTransfers = pendingTransfers;
        this.maxWait = maxWait;
        this.pollInterval = pollInterval;
    }

    public static Duration recoveryBound(boolean recoveryEnabled, long staleAfterMs, long intervalMs) {
        return recoveryEnabled ? Duration.ofMillis(2 * staleAfterMs + intervalMs) : Duration.ZERO;
    }

    public long await() {
        long deadline = System.nanoTime() + maxWait.toNanos();
        long pending = pendingTransfers.getAsLong();
        while (pending > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return pendingTransfers.getAsLong();
            }
            pending = pendingTransfers.getAsLong();
        }
        return pending;
    }
}
