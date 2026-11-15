package ro.saga.transfer.benchmark;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Waits until no transfer is {@code PENDING} (the system is quiet), for at most {@code maxWait}. In V1 the
 * leftovers are resolved by {@code RecoveryJob}, so the bound is {@code 2 x stale-after-ms + interval-ms}.
 */
public final class QuiescenceAwaiter {

    private final LongSupplier pendingTransfers;
    private final Duration maxWait;
    private final Duration pollInterval;

    public QuiescenceAwaiter(LongSupplier pendingTransfers, Duration maxWait, Duration pollInterval) {
        this.pendingTransfers = pendingTransfers;
        this.maxWait = maxWait;
        this.pollInterval = pollInterval;
    }

    /** Bound used for V1: long enough for two passes of the recovery job over every stale transfer. */
    public static Duration recoveryBound(boolean recoveryEnabled, long staleAfterMs, long intervalMs) {
        return recoveryEnabled ? Duration.ofMillis(2 * staleAfterMs + intervalMs) : Duration.ZERO;
    }

    /** Returns the transfers still {@code PENDING} when it stopped waiting; 0 means quiet. */
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
