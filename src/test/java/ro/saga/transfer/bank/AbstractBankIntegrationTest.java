package ro.saga.transfer.bank;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;
import ro.saga.transfer.config.BetaBankDataSourceConfig;

/**
 * Starts the two bank databases once for all tests that extend it. They are stopped when the JVM exits.
 */
@SpringBootTest
abstract class AbstractBankIntegrationTest {

    private static final String POSTGRES_IMAGE = "postgres:17";

    private static final PostgreSQLContainer ALPHA_BANK_DATABASE = bankDatabase(AlphaBankDataSourceConfig.BANK_NAME);
    private static final PostgreSQLContainer BETA_BANK_DATABASE = bankDatabase(BetaBankDataSourceConfig.BANK_NAME);

    static {
        ALPHA_BANK_DATABASE.start();
        BETA_BANK_DATABASE.start();
    }

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
        // 16 test threads must not queue on the default pool of 10.
        registry.add(prefix + ".hikari.maximum-pool-size", () -> 20);
    }
}
