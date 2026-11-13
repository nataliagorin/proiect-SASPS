package ro.saga.transfer.bank;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.config.BetaBankDataSourceConfig;

import javax.sql.DataSource;
import java.math.BigDecimal;

@Service
public class BetaBankOperations implements BankOperations {

    private final JdbcClient jdbc;

    public BetaBankOperations(@Qualifier(BetaBankDataSourceConfig.DATA_SOURCE) DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Bank bank() {
        return Bank.BETA;
    }

    @Override
    @Transactional(BetaBankDataSourceConfig.TRANSACTION_MANAGER)
    public void debit(long accountId, BigDecimal amount) {
        AccountSql.debit(jdbc, accountId, amount);
    }

    @Override
    @Transactional(BetaBankDataSourceConfig.TRANSACTION_MANAGER)
    public void credit(long accountId, BigDecimal amount) {
        AccountSql.credit(jdbc, accountId, amount);
    }

    @Override
    @Transactional(BetaBankDataSourceConfig.TRANSACTION_MANAGER)
    public void refund(long accountId, BigDecimal amount) {
        AccountSql.credit(jdbc, accountId, amount);
    }

    @Override
    @Transactional(value = BetaBankDataSourceConfig.TRANSACTION_MANAGER, readOnly = true)
    public BigDecimal balanceOf(long accountId) {
        return AccountSql.balanceOf(jdbc, accountId);
    }
}
