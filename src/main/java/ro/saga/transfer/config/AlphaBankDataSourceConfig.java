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
import ro.saga.transfer.bank.alpha.AlphaBankAccount;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackageClasses = AlphaBankAccount.class,
        entityManagerFactoryRef = AlphaBankDataSourceConfig.ENTITY_MANAGER_FACTORY,
        transactionManagerRef = AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
public class AlphaBankDataSourceConfig {

    public static final String BANK_NAME = "alpha_bank";
    public static final String DATA_SOURCE = "alphaBankDataSource";
    public static final String TRANSACTION_MANAGER = "alphaBankTransactionManager";
    static final String ENTITY_MANAGER_FACTORY = "alphaBankEntityManagerFactory";
    private static final String DATA_SOURCE_PROPERTIES = "alphaBankDataSourceProperties";
    private static final String FLYWAY = "alphaBankFlyway";

    private final BankPersistenceFactory persistenceFactory;

    public AlphaBankDataSourceConfig(BankPersistenceFactory persistenceFactory) {
        this.persistenceFactory = persistenceFactory;
    }

    @Bean(DATA_SOURCE_PROPERTIES)
    @ConfigurationProperties("app.datasource.alpha-bank")
    DataSourceProperties alphaBankDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(DATA_SOURCE)
    DataSource alphaBankDataSource(@Qualifier(DATA_SOURCE_PROPERTIES) DataSourceProperties properties) {
        return persistenceFactory.createDataSource(properties, BANK_NAME);
    }

    @Bean(name = FLYWAY, initMethod = "migrate")
    Flyway alphaBankFlyway(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        return persistenceFactory.createFlyway(dataSource, BANK_NAME);
    }

    @Bean(ENTITY_MANAGER_FACTORY)
    @DependsOn(FLYWAY)
    LocalContainerEntityManagerFactoryBean alphaBankEntityManagerFactory(@Qualifier(DATA_SOURCE) DataSource dataSource) {
        return persistenceFactory.createEntityManagerFactory(dataSource, BANK_NAME, AlphaBankAccount.class.getPackageName());
    }

    @Bean(TRANSACTION_MANAGER)
    PlatformTransactionManager alphaBankTransactionManager(
            @Qualifier(ENTITY_MANAGER_FACTORY) EntityManagerFactory entityManagerFactory) {
        return persistenceFactory.createTransactionManager(entityManagerFactory);
    }
}
