package ro.saga.transfer.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Outcome and status pairs from the table in docs/contracts.md. */
class TransferResultTest {

    private static void assertResult(TransferResult result, Long id, TransferOutcome outcome, TransferStatus status) {
        assertEquals(id, result.transferId());
        assertEquals(outcome, result.outcome());
        assertEquals(status, result.status());
    }

    @Test
    void completed() {
        TransferResult result = TransferResult.completed(7);

        assertResult(result, 7L, TransferOutcome.COMPLETED, TransferStatus.COMPLETED);
        assertTrue(result.isSuccess());
    }

    @Test
    void rejectedInsufficientFundsHasNoRow() {
        TransferResult result = TransferResult.rejectedInsufficientFunds();

        assertResult(result, null, TransferOutcome.REJECTED_INSUFFICIENT_FUNDS, TransferStatus.FAILED);
        assertFalse(result.isSuccess());
    }

    @Test
    void rejectedFraudIsCompensated() {
        assertResult(TransferResult.rejectedFraud(7), 7L, TransferOutcome.REJECTED_FRAUD, TransferStatus.COMPENSATED);
    }

    @Test
    void compensatedKeepsTheReason() {
        TransferResult result = TransferResult.compensated(7, "Beta Bank unavailable");

        assertResult(result, 7L, TransferOutcome.COMPENSATED, TransferStatus.COMPENSATED);
        assertTrue(result.message().contains("Beta Bank unavailable"));
    }

    @Test
    void failedBeforeDebitHasNoRow() {
        assertResult(TransferResult.failedBeforeDebit("Alpha Bank unavailable"), null,
                TransferOutcome.FAILED_TECHNICAL, TransferStatus.FAILED);
    }

    @Test
    void awaitingRecoveryStaysPending() {
        assertResult(TransferResult.awaitingRecovery(7, "refund failed"), 7L,
                TransferOutcome.FAILED_TECHNICAL, TransferStatus.PENDING);
    }
}
