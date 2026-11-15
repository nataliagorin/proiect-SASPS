package ro.saga.transfer.bank;

import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;

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
