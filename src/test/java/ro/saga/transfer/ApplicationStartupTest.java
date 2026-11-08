package ro.saga.transfer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;
import ro.saga.transfer.config.BetaBankDataSourceConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApplicationStartupTest {

    private static final String POSTGRES_IMAGE = "postgres:17";

    @Container
    private static final PostgreSQLContainer ALPHA_BANK_DATABASE = bankDatabase(AlphaBankDataSourceConfig.BANK_NAME);

    @Container
    private static final PostgreSQLContainer BETA_BANK_DATABASE = bankDatabase(BetaBankDataSourceConfig.BANK_NAME);

    private final List<Flyway> bankMigrations;
    private final MockMvcTester mockMvc;

    @Autowired
    ApplicationStartupTest(List<Flyway> bankMigrations, MockMvcTester mockMvc) {
        this.bankMigrations = bankMigrations;
        this.mockMvc = mockMvc;
    }

    // Same server setting as docker-compose.yml, so the tests run against an equivalent database.
    private static PostgreSQLContainer bankDatabase(String databaseName) {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName(databaseName)
                .withCommand("postgres", "-c", "max_prepared_transactions=100");
    }

    @DynamicPropertySource
    static void bankDatabaseProperties(DynamicPropertyRegistry registry) {
        registerBankDatabase(registry, "alpha-bank", ALPHA_BANK_DATABASE);
        registerBankDatabase(registry, "beta-bank", BETA_BANK_DATABASE);
    }

    private static void registerBankDatabase(
            DynamicPropertyRegistry registry, String bankPropertyKey, PostgreSQLContainer database) {
        String prefix = "app.datasource." + bankPropertyKey;
        registry.add(prefix + ".url", database::getJdbcUrl);
        registry.add(prefix + ".username", database::getUsername);
        registry.add(prefix + ".password", database::getPassword);
    }

    @Test
    void startsWithMigratedBankDatabasesAndReportsBothBanksUp() {
        assertThat(bankMigrations)
                .hasSize(2)
                .allSatisfy(flyway -> {
                    assertThat(flyway.info().current()).isNotNull();
                    assertThat(flyway.info().pending()).isEmpty();
                });

        assertThat(mockMvc.get().uri("/health/banks"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "status": "UP",
                          "banks": {
                            "alpha_bank": {"status": "UP"},
                            "beta_bank": {"status": "UP"}
                          }
                        }
                        """);
    }
}
