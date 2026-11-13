package ro.saga.transfer.bank;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;

import javax.sql.DataSource;
import java.math.BigDecimal;

@Service
public class AlphaBankOperations implements BankOperations {

    private final JdbcClient jdbc;

    public AlphaBankOperations(@Qualifier(AlphaBankDataSourceConfig.DATA_SOURCE) DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @Override
    public Bank bank() {
        return Bank.ALPHA;
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public void debit(long accountId, BigDecimal amount) {
        AccountSql.debit(jdbc, accountId, amount);
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public void credit(long accountId, BigDecimal amount) {
        AccountSql.credit(jdbc, accountId, amount);
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public void refund(long accountId, BigDecimal amount) {
        AccountSql.credit(jdbc, accountId, amount);
    }

    @Override
    @Transactional(value = AlphaBankDataSourceConfig.TRANSACTION_MANAGER, readOnly = true)
    public BigDecimal balanceOf(long accountId) {
        return AccountSql.balanceOf(jdbc, accountId);
    }
}
