package ro.saga.transfer.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Component
public class BankPersistenceFactory {

    private static final String MIGRATION_LOCATION_PREFIX = "classpath:db/migration/";

    private static final Map<String, Object> HIBERNATE_PROPERTIES = Map.of(
            AvailableSettings.HBM2DDL_AUTO, "validate",
            AvailableSettings.PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategySnakeCaseImpl.class.getName());

    public HikariDataSource createDataSource(DataSourceProperties properties, String bankName) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName(bankName);
        return dataSource;
    }

    public Flyway createFlyway(DataSource dataSource, String bankName) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION_PREFIX + bankName)
                .load();
    }

    public LocalContainerEntityManagerFactoryBean createEntityManagerFactory(
            DataSource dataSource, String bankName, String entityPackage) {
        LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setPersistenceUnitName(bankName);
        entityManagerFactory.setDataSource(dataSource);
        entityManagerFactory.setPackagesToScan(entityPackage);
        entityManagerFactory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        entityManagerFactory.setJpaPropertyMap(HIBERNATE_PROPERTIES);
        return entityManagerFactory;
    }

    public PlatformTransactionManager createTransactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
