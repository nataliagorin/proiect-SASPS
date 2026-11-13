package ro.saga.transfer.fault;

import org.junit.jupiter.api.Test;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultPoint;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SimulatedFaultInjectorTest {

    private static final int CALLS = 100_000;

    @Test
    void effectiveRateMatchesTheConfiguredRate() {
        for (double rate : new double[] {0.05, 0.1, 0.3}) {
            SimulatedFaultInjector injector = new SimulatedFaultInjector(new FaultProperties(42, rate, Set.of()));

            double effective = (double) countFailures(injector, FaultPoint.AFTER_DEBIT) / CALLS;

            assertThat(effective).isCloseTo(rate, within(0.01));
        }
    }

    @Test
    void zeroNeverFailsAndOneAlwaysFails() {
        assertThat(countFailures(new SimulatedFaultInjector(new FaultProperties(1, 0, Set.of())),
                FaultPoint.AFTER_DEBIT)).isZero();
        assertThatThrownBy(() -> new SimulatedFaultInjector(new FaultProperties(1, 1, Set.of()))
                .maybeFail(FaultPoint.AUDIT_FAILURE))
                .isInstanceOf(BankUnavailableException.class)
                .hasMessageContaining("AUDIT_FAILURE");
    }

    @Test
    void onlyConfiguredPointsFail() {
        SimulatedFaultInjector injector =
                new SimulatedFaultInjector(new FaultProperties(1, 1, EnumSet.of(FaultPoint.AFTER_CREDIT)));

        injector.maybeFail(FaultPoint.AFTER_DEBIT);
        assertThatThrownBy(() -> injector.maybeFail(FaultPoint.AFTER_CREDIT))
                .isInstanceOf(BankUnavailableException.class);
    }

    @Test
    void emptyPointsMeansEveryPoint() {
        assertThat(new FaultProperties(1, 0, null).points()).containsExactlyInAnyOrder(FaultPoint.values());
    }

    @Test
    void rejectsRatesOutsideZeroToOne() {
        assertThatThrownBy(() -> new FaultProperties(1, 1.5, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FaultProperties(1, -0.1, Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameSeedAndThreadNameGiveTheSameFailures() throws Exception {
        FaultProperties properties = new FaultProperties(7, 0.2, Set.of());

        assertThat(failurePattern(new SimulatedFaultInjector(properties), "worker-0"))
                .isEqualTo(failurePattern(new SimulatedFaultInjector(properties), "worker-0"))
                .isNotEqualTo(failurePattern(new SimulatedFaultInjector(properties), "worker-1"));
    }

    @Test
    void reconfigureRestartsTheSequence() {
        SimulatedFaultInjector injector = new SimulatedFaultInjector(new FaultProperties(7, 0.2, Set.of()));
        List<Boolean> first = pattern(injector);

        injector.reconfigure(new FaultProperties(7, 0.2, Set.of()));

        assertThat(pattern(injector)).isEqualTo(first);
    }

    private static int countFailures(SimulatedFaultInjector injector, FaultPoint point) {
        int failures = 0;
        for (int i = 0; i < CALLS; i++) {
            try {
                injector.maybeFail(point);
            } catch (BankUnavailableException e) {
                failures++;
            }
        }
        return failures;
    }

    private static List<Boolean> failurePattern(SimulatedFaultInjector injector, String threadName) throws Exception {
        AtomicReference<List<Boolean>> result = new AtomicReference<>();
        Thread thread = new Thread(() -> result.set(pattern(injector)), threadName);
        thread.start();
        thread.join();
        return result.get();
    }

    private static List<Boolean> pattern(SimulatedFaultInjector injector) {
        List<Boolean> failed = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            try {
                injector.maybeFail(FaultPoint.AFTER_DEBIT);
                failed.add(false);
            } catch (BankUnavailableException e) {
                failed.add(true);
            }
        }
        return failed;
    }
}
