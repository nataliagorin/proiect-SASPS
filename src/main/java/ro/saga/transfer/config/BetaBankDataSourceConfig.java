package ro.saga.transfer.config;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import ro.saga.transfer.bank.beta.BetaBankAccount;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackageClasses = BetaBankAccount.class,
        entityManagerFactoryRef = BetaBankDataSourceConfig.ENTITY_MANAGER_FACTORY,
        transactionManagerRef = BetaBankDataSourceConfig.TRANSACTION_MANAGER)
public class BetaBankDataSourceConfig {

    public static final String BANK_NAME = "beta_bank";
    public static final String DATA_SOURCE = "betaBankDataSource";
    public static final String TRANSACTION_MANAGER = "betaBankTransactionManager";
    static final String ENTITY_MANAGER_FACTORY = "betaBankEntityManagerFactory";
    private static final String DATA_SOURCE_PROPERTIES = "betaBankDataSourceProperties";
    private static final String FLYWAY = "betaBankFlyway";

    private final BankPersistenceFactory persistenceFactory;

    public BetaBankDataSourceConfig(BankPersistenceFactory persistenceFactory) {
        this.persistenceFactory = persistenceFactory;
    }

    @Bean(DATA_SOURCE_PROPERTIES)
    @ConfigurationProperties("app.datasource.beta-bank")
    DataSourceProperties betaBankDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(DATA_SOURCE)
    DataSource betaBankDataSource(@Qualifier(DATA_SOURCE_PROPERTIES) DataSourceProperties properties) {
        return persistenceFactory.createDataSource(properties, BANK_NAME);
    }

    @Bean(name = FLYWAY, initMethod = "migrate")
    Flyway betaBankFlyway(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        return persistenceFactory.createFlyway(dataSource, BANK_NAME);
    }

    @Bean(ENTITY_MANAGER_FACTORY)
    @DependsOn(FLYWAY)
    LocalContainerEntityManagerFactoryBean betaBankEntityManagerFactory(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        return persistenceFactory.createEntityManagerFactory(dataSource, BANK_NAME, BetaBankAccount.class.getPackageName());
    }

    @Bean(TRANSACTION_MANAGER)
    PlatformTransactionManager betaBankTransactionManager(
            @Qualifier(ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return persistenceFactory.createTransactionManager(entityManagerFactory);
    }
}
