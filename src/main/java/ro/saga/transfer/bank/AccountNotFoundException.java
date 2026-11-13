package ro.saga.transfer.bank;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(long accountId) {
        super("Account " + accountId + " not found");
    }
}
