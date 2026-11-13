package ro.saga.transfer.common;

import java.util.Objects;

/** {@code transferId} is null when no transfer row was created. */
public record TransferResult(Long transferId, TransferOutcome outcome, TransferStatus status, String message) {

    public TransferResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static TransferResult completed(long transferId) {
        return new TransferResult(transferId, TransferOutcome.COMPLETED, TransferStatus.COMPLETED,
                "Transfer completed");
    }

    public boolean isSuccess() {
        return outcome == TransferOutcome.COMPLETED;
    }
}
