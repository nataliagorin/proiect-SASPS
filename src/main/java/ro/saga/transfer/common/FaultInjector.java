package ro.saga.transfer.common;

/** Shared source of simulated failures, so every variant is hit the same way. */
@FunctionalInterface
public interface FaultInjector {

    FaultInjector NONE = point -> {
    };

    /** Throws {@link BankUnavailableException} with the configured probability, or returns. */
    void maybeFail(FaultPoint point);
}
