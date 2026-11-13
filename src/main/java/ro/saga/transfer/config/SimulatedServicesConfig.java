package ro.saga.transfer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ro.saga.transfer.common.AuditService;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FraudCheckService;
import ro.saga.transfer.common.FraudDecision;

/** Placeholders that never fail, so variants can be wired now. Replaced by feature/fault-injection. */
@Configuration(proxyBeanMethods = false)
public class SimulatedServicesConfig {

    @Bean
    FaultInjector faultInjector() {
        return FaultInjector.NONE;
    }

    @Bean
    FraudCheckService fraudCheckService() {
        return request -> FraudDecision.APPROVED;
    }

    @Bean
    AuditService auditService() {
        return (transferId, outcome) -> {
        };
    }
}
