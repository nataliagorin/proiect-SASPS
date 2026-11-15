package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real {@code benchmark} profile, so the Spring wiring of the benchmark beans and the binding of
 * application-benchmark.yml are checked. The runner is replaced, because the real one runs the matrix and exits.
 */
@ActiveProfiles("benchmark")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BenchmarkProfileWiringTest extends AbstractBankIntegrationTest {

    @MockitoBean
    private BenchmarkRunner runner;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BenchmarkProperties properties;

    @Test
    void benchmarkBeansAreWiredAndTheProfileMatrixIsBound() {
        assertThat(context.getBean(BenchmarkEngine.class)).isNotNull();
        assertThat(context.getBean(BenchmarkCampaign.class)).isNotNull();
        assertThat(context.getBean(BenchmarkOutput.class).directory()).hasToString("results");

        assertThat(properties.variants()).containsExactly("V1");
        assertThat(properties.transfers()).isEqualTo(10_000);
        assertThat(properties.failureRates()).containsExactly(0.0, 0.05, 0.1, 0.2, 0.3);
        assertThat(properties.threads()).containsExactly(1, 2, 4, 8, 16);
        assertThat(BenchmarkMatrix.expand(properties, 10)).hasSize(75);
    }
}
