package ro.saga.transfer.bank;

import ro.saga.transfer.common.Bank;

import java.math.BigDecimal;

/** Balance changes on one bank. Each call is one local transaction on that bank. */
public interface BankOperations {

    Bank bank();

    /** @throws InsufficientFundsException if the balance is lower than {@code amount} */
    void debit(long accountId, BigDecimal amount);

    void credit(long accountId, BigDecimal amount);

    /** Reverts a debit. */
    void refund(long accountId, BigDecimal amount);

    BigDecimal balanceOf(long accountId);
}
