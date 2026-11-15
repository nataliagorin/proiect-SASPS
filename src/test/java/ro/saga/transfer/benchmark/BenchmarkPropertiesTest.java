package ro.saga.transfer.benchmark;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenchmarkPropertiesTest {

    @Test
    void defaultsMatchTheDocumentedMatrix() {
        BenchmarkProperties properties = bind(Map.of());

        assertThat(properties.variants()).containsExactly("V1");
        assertThat(properties.scenario()).isEqualTo("baseline");
        assertThat(properties.transfers()).isEqualTo(10_000);
        assertThat(properties.warmupTransfers()).isEqualTo(200);
        assertThat(properties.failureRates()).containsExactly(0.0, 0.05, 0.1, 0.2, 0.3);
        assertThat(properties.threads()).containsExactly(1, 2, 4, 8, 16);
        assertThat(properties.repetitions()).isEqualTo(3);
        assertThat(properties.seed()).isEqualTo(42);
        assertThat(properties.outputDir()).isEqualTo("results");
        assertThat(properties.distribution()).isEqualTo(AccountDistribution.UNIFORM);
        assertThat(properties.minAmount()).isEqualTo(1);
        assertThat(properties.maxAmount()).isEqualTo(50);
    }

    @Test
    void bindsKebabCaseKeysAndLowercaseDistribution() {
        BenchmarkProperties properties = bind(Map.of(
                "app.benchmark.failure-rates", "0.1,0.2",
                "app.benchmark.threads", "4",
                "app.benchmark.distribution", "zipf",
                "app.benchmark.hot-accounts", "5",
                "app.benchmark.git-sha", "abc1234"));

        assertThat(properties.failureRates()).containsExactly(0.1, 0.2);
        assertThat(properties.threads()).containsExactly(4);
        assertThat(properties.distribution()).isEqualTo(AccountDistribution.ZIPF);
        assertThat(properties.hotAccounts()).isEqualTo(5);
        assertThat(properties.gitSha()).isEqualTo("abc1234");
    }

    @Test
    void rejectsInvalidValues() {
        assertInvalid(p -> p.failureRates = List.of(1.5), "failure-rates");
        assertInvalid(p -> p.failureRates = List.of(), "failure-rates");
        assertInvalid(p -> p.threads = List.of(0), "threads");
        assertInvalid(p -> p.transfers = 0, "transfers");
        assertInvalid(p -> p.warmupTransfers = -1, "warmup-transfers");
        assertInvalid(p -> p.repetitions = 0, "repetitions");
        assertInvalid(p -> p.variants = List.of(), "variants");
        assertInvalid(p -> p.scenario = "base line,", "scenario");
        assertInvalid(p -> p.hotAccounts = 101, "hot-accounts");
        assertInvalid(p -> p.zipfExponent = 0, "zipf-exponent");
        assertInvalid(p -> {
            p.minAmount = 60;
            p.maxAmount = 50;
        }, "amounts");
        assertInvalid(p -> p.sampleIntervalMs = 1, "sample-interval-ms");
    }

    private static void assertInvalid(Consumer<TestProperties> change, String message) {
        TestProperties properties = new TestProperties();
        change.accept(properties);
        assertThatThrownBy(properties::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private static BenchmarkProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("app.benchmark", BenchmarkProperties.class);
    }
}
