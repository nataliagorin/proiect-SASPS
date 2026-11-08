package ro.saga.transfer.bank;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record BanksHealth(HealthStatus status, Map<String, BankHealth> banks) {

    static BanksHealth of(Map<String, BankHealth> banks) {
        boolean allBanksUp = banks.values().stream()
                .allMatch(bank -> bank.status() == HealthStatus.UP);
        HealthStatus overallStatus = allBanksUp ? HealthStatus.UP : HealthStatus.DOWN;
        return new BanksHealth(overallStatus, Collections.unmodifiableMap(new LinkedHashMap<>(banks)));
    }
}
