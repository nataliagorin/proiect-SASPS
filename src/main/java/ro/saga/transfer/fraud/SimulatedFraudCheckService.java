package ro.saga.transfer.fraud;

import org.springframework.stereotype.Service;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.FraudCheckService;
import ro.saga.transfer.common.FraudDecision;
import ro.saga.transfer.common.TransferRequest;

/** Deterministic rule plus configurable latency. Timeouts come from {@link FaultPoint#FRAUD_TIMEOUT}. */
@Service
public class SimulatedFraudCheckService implements FraudCheckService {

    private final FaultInjector faults;
    private volatile FraudProperties properties;

    public SimulatedFraudCheckService(FaultInjector faults, FraudProperties properties) {
        this.faults = faults;
        this.properties = properties;
    }

    public FraudProperties properties() {
        return properties;
    }

    /** For the benchmark, between runs. */
    public void reconfigure(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public FraudDecision check(TransferRequest request) {
        FraudProperties current = properties;
        sleep(current.latencyMs());
        faults.maybeFail(FaultPoint.FRAUD_TIMEOUT);
        boolean tooLarge = current.maxAmount() != null && request.amount().compareTo(current.maxAmount()) > 0;
        boolean blocked = current.blockedAccounts().contains(request.fromAccountId());
        return tooLarge || blocked ? FraudDecision.REJECTED : FraudDecision.APPROVED;
    }

    private static void sleep(long millis) {
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BankUnavailableException("Fraud check interrupted", e);
        }
    }
}
