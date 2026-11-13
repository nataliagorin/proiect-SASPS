package ro.saga.transfer.common;

/** A bank, the fraud check or the audit system did not answer. Treated as a technical failure. */
public class BankUnavailableException extends RuntimeException {

    public BankUnavailableException(String message) {
        super(message);
    }

    public BankUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
