package ro.saga.transfer.common;

@FunctionalInterface
public interface FaultInjector {

    FaultInjector NONE = point -> {
    };

    void maybeFail(FaultPoint point);
}
