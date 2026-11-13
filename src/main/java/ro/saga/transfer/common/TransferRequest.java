package ro.saga.transfer.common;

import java.math.BigDecimal;
import java.util.Objects;

/** The same {@code idempotencyKey} sent twice must have the effect of one request. */
public record TransferRequest(
        String idempotencyKey,
        Bank fromBank,
        long fromAccountId,
        Bank toBank,
        long toAccountId,
        BigDecimal amount) {

    public TransferRequest {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        Objects.requireNonNull(fromBank, "fromBank must not be null");
        Objects.requireNonNull(toBank, "toBank must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (fromBank == toBank) {
            throw new IllegalArgumentException("A transfer must be between two different banks");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("amount must have at most two decimals");
        }
    }
}
