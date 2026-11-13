package ro.saga.transfer.bank;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long accountId, BigDecimal amount) {
        super("Insufficient funds in account " + accountId + " for amount " + amount);
    }
}
