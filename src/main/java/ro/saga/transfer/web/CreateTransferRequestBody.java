package ro.saga.transfer.web;

import ro.saga.transfer.common.Bank;

import java.math.BigDecimal;

public record CreateTransferRequestBody(
        Bank fromBank,
        long fromAccountId,
        Bank toBank,
        long toAccountId,
        BigDecimal amount) {
}
