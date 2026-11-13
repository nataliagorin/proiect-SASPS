package ro.saga.transfer.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferRequestTest {

    private static TransferRequest request(String key, Bank from, Bank to, String amount) {
        return new TransferRequest(key, from, 1L, to, 2L, new BigDecimal(amount));
    }

    @Test
    void acceptsValidRequest() {
        assertDoesNotThrow(() -> request("key-1", Bank.ALPHA, Bank.BETA, "10.50"));
        assertDoesNotThrow(() -> request("key-2", Bank.BETA, Bank.ALPHA, "10.500"));
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> request(" ", Bank.ALPHA, Bank.BETA, "10"));
        assertThrows(IllegalArgumentException.class, () -> request(null, Bank.ALPHA, Bank.BETA, "10"));
    }

    @Test
    void rejectsSameBank() {
        assertThrows(IllegalArgumentException.class, () -> request("key", Bank.ALPHA, Bank.ALPHA, "10"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> request("key", Bank.ALPHA, Bank.BETA, "0"));
        assertThrows(IllegalArgumentException.class, () -> request("key", Bank.ALPHA, Bank.BETA, "-5"));
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThrows(IllegalArgumentException.class, () -> request("key", Bank.ALPHA, Bank.BETA, "10.005"));
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(NullPointerException.class,
                () -> new TransferRequest("key", null, 1L, Bank.BETA, 2L, BigDecimal.TEN));
        assertThrows(NullPointerException.class,
                () -> new TransferRequest("key", Bank.ALPHA, 1L, Bank.BETA, 2L, null));
    }

    @Test
    void completedResultIsSuccess() {
        TransferResult result = TransferResult.completed(7L);

        assertTrue(result.isSuccess());
        assertEquals(TransferStatus.COMPLETED, result.status());
        assertEquals(7L, result.transferId());
    }

    @Test
    void otherOutcomesAreNotSuccess() {
        TransferResult result = new TransferResult(null, TransferOutcome.REJECTED_FRAUD, TransferStatus.COMPENSATED,
                "Rejected by the fraud check");

        assertFalse(result.isSuccess());
    }

    @Test
    void noneInjectorNeverFails() {
        for (FaultPoint point : FaultPoint.values()) {
            assertDoesNotThrow(() -> FaultInjector.NONE.maybeFail(point));
        }
    }
}
