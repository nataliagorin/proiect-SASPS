package ro.saga.transfer.bank;

public class DuplicateTransferException extends RuntimeException {

    public DuplicateTransferException(String idempotencyKey) {
        super("A transfer with idempotency key " + idempotencyKey + " already exists");
    }
}
