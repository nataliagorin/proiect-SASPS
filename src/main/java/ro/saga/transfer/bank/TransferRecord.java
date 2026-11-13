package ro.saga.transfer.bank;

import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** A row of the {@code transfer} table of one bank. */
public record TransferRecord(
        long id,
        String idempotencyKey,
        long fromAccount,
        long toAccount,
        BigDecimal amount,
        TransferStatus status,
        Instant createdAt,
        Instant finalizedAt,
        String failureReason,
        int attemptCount) {
}
