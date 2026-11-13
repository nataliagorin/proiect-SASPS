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

    public static TransferResult rejectedInsufficientFunds() {
        return new TransferResult(null, TransferOutcome.REJECTED_INSUFFICIENT_FUNDS, TransferStatus.FAILED,
                "Insufficient funds");
    }

    /** The debit was refunded. */
    public static TransferResult rejectedFraud(long transferId) {
        return new TransferResult(transferId, TransferOutcome.REJECTED_FRAUD, TransferStatus.COMPENSATED,
                "Rejected by the fraud check");
    }

    /** A technical failure after the debit; the debit was refunded. */
    public static TransferResult compensated(long transferId, String reason) {
        return new TransferResult(transferId, TransferOutcome.COMPENSATED, TransferStatus.COMPENSATED,
                "Transfer compensated: " + reason);
    }

    /** A technical failure before any effect, so no transfer row exists. */
    public static TransferResult failedBeforeDebit(String reason) {
        return new TransferResult(null, TransferOutcome.FAILED_TECHNICAL, TransferStatus.FAILED,
                "Transfer failed: " + reason);
    }

    /** The refund failed too; the transfer stays PENDING for recovery. */
    public static TransferResult awaitingRecovery(long transferId, String reason) {
        return new TransferResult(transferId, TransferOutcome.FAILED_TECHNICAL, TransferStatus.PENDING,
                "Transfer awaiting recovery: " + reason);
    }

    public boolean isSuccess() {
        return outcome == TransferOutcome.COMPLETED;
    }
}
