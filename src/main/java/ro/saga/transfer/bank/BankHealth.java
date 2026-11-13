package ro.saga.transfer.bank;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BankHealth(HealthStatus status, String error) {

    static BankHealth up() {
        return new BankHealth(HealthStatus.UP, null);
    }

    static BankHealth down(String error) {
        return new BankHealth(HealthStatus.DOWN, error);
    }
}
