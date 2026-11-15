package ro.saga.transfer.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Set;

@ConfigurationProperties("app.fraud")
public record FraudProperties(long latencyMs, BigDecimal maxAmount, Set<Long> blockedAccounts) {

    public FraudProperties {
        if (latencyMs < 0) {
            throw new IllegalArgumentException("app.fraud.latency-ms must not be negative");
        }
        blockedAccounts = blockedAccounts == null ? Set.of() : Set.copyOf(blockedAccounts);
    }
}
