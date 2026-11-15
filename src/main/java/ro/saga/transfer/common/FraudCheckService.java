package ro.saga.transfer.common;

public interface FraudCheckService {

    FraudDecision check(TransferRequest request);
}
