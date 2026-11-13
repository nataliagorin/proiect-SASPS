package ro.saga.transfer.common;

/** Simulated fraud check, called after the debit. Has no side effects. */
public interface FraudCheckService {

    /** @throws BankUnavailableException on timeout */
    FraudDecision check(TransferRequest request);
}
