package ro.saga.transfer.fraud;

import org.junit.jupiter.api.Test;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.FraudDecision;
import ro.saga.transfer.common.TransferRequest;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedFraudCheckServiceTest {

    private static final FraudProperties NO_RULES = new FraudProperties(0, null, Set.of());

    @Test
    void approvesWithoutRules() {
        assertThat(service(NO_RULES).check(request(1, "999.99"))).isEqualTo(FraudDecision.APPROVED);
    }

    @Test
    void rejectsAboveTheMaximumAmount() {
        SimulatedFraudCheckService fraud = service(new FraudProperties(0, new BigDecimal("100"), Set.of()));

        assertThat(fraud.check(request(1, "100.00"))).isEqualTo(FraudDecision.APPROVED);
        assertThat(fraud.check(request(1, "100.01"))).isEqualTo(FraudDecision.REJECTED);
    }

    @Test
    void rejectsBlockedSourceAccounts() {
        SimulatedFraudCheckService fraud = service(new FraudProperties(0, null, Set.of(7L)));

        assertThat(fraud.check(request(7, "1"))).isEqualTo(FraudDecision.REJECTED);
        assertThat(fraud.check(request(8, "1"))).isEqualTo(FraudDecision.APPROVED);
    }

    @Test
    void timeoutComesFromTheFaultInjector() {
        FaultInjector timeouts = point -> {
            if (point == FaultPoint.FRAUD_TIMEOUT) {
                throw new BankUnavailableException("timeout");
            }
        };
        SimulatedFraudCheckService fraud = new SimulatedFraudCheckService(timeouts, NO_RULES);

        assertThatThrownBy(() -> fraud.check(request(1, "1"))).isInstanceOf(BankUnavailableException.class);
    }

    @Test
    void waitsForTheConfiguredLatency() {
        SimulatedFraudCheckService fraud = service(new FraudProperties(50, null, Set.of()));

        long start = System.nanoTime();
        fraud.check(request(1, "1"));

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(50);
    }

    @Test
    void reconfigureChangesTheRule() {
        SimulatedFraudCheckService fraud = service(NO_RULES);

        fraud.reconfigure(new FraudProperties(0, null, Set.of(1L)));

        assertThat(fraud.check(request(1, "1"))).isEqualTo(FraudDecision.REJECTED);
    }

    private static SimulatedFraudCheckService service(FraudProperties properties) {
        return new SimulatedFraudCheckService(FaultInjector.NONE, properties);
    }

    private static TransferRequest request(long fromAccount, String amount) {
        return new TransferRequest("key", Bank.ALPHA, fromAccount, Bank.BETA, 2, new BigDecimal(amount));
    }
}
