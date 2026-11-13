package ro.saga.transfer.bank;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

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

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public long debitAndRecordPending(TransferRequest request) {
        return TransferSql.debitAndRecordPending(jdbc, request);
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public boolean creditOnce(TransferRequest request) {
        return TransferSql.creditOnce(jdbc, request);
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public boolean markCompleted(long transferId) {
        return TransferSql.markCompleted(jdbc, transferId);
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public boolean compensate(long transferId, String reason) {
        return TransferSql.compensate(jdbc, transferId, reason);
    }

    @Override
    @Transactional(AlphaBankDataSourceConfig.TRANSACTION_MANAGER)
    public int recordAttempt(long transferId, String reason) {
        return TransferSql.recordAttempt(jdbc, transferId, reason);
    }

    @Override
    @Transactional(value = AlphaBankDataSourceConfig.TRANSACTION_MANAGER, readOnly = true)
    public Optional<TransferRecord> findTransfer(long transferId) {
        return TransferSql.findById(jdbc, transferId);
    }

    @Override
    @Transactional(value = AlphaBankDataSourceConfig.TRANSACTION_MANAGER, readOnly = true)
    public Optional<TransferRecord> findTransfer(String idempotencyKey) {
        return TransferSql.findByIdempotencyKey(jdbc, idempotencyKey);
    }

    @Override
    @Transactional(value = AlphaBankDataSourceConfig.TRANSACTION_MANAGER, readOnly = true)
    public List<TransferRecord> findPendingOlderThan(Duration age) {
        return TransferSql.findPendingOlderThan(jdbc, age);
    }
}
