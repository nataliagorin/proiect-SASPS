package ro.saga.transfer.bank;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.config.AlphaBankDataSourceConfig;
import ro.saga.transfer.config.BetaBankDataSourceConfig;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads and resets the state of both banks. Used by the benchmark and the invariant checks. */
@Service
public class LedgerService {

    public static final BigDecimal INITIAL_BALANCE = new BigDecimal("1000.00");
    public static final BigDecimal EXPECTED_TOTAL = new BigDecimal("200000.00");

    private record BankAccess(JdbcClient jdbc, TransactionTemplate transaction) {
    }

    private record PendingTransfer(String idempotencyKey, BigDecimal amount) {
    }

    private final Map<Bank, BankAccess> banks = new EnumMap<>(Bank.class);

    public LedgerService(
            @Qualifier(AlphaBankDataSourceConfig.DATA_SOURCE) DataSource alphaDataSource,
            @Qualifier(AlphaBankDataSourceConfig.TRANSACTION_MANAGER) PlatformTransactionManager alphaTransactions,
            @Qualifier(BetaBankDataSourceConfig.DATA_SOURCE) DataSource betaDataSource,
            @Qualifier(BetaBankDataSourceConfig.TRANSACTION_MANAGER) PlatformTransactionManager betaTransactions) {
        banks.put(Bank.ALPHA, new BankAccess(JdbcClient.create(alphaDataSource),
                new TransactionTemplate(alphaTransactions)));
        banks.put(Bank.BETA, new BankAccess(JdbcClient.create(betaDataSource),
                new TransactionTemplate(betaTransactions)));
    }

    /** Sum of all balances in both banks. */
    public BigDecimal getTotalMoney() {
        BigDecimal total = BigDecimal.ZERO;
        for (BankAccess bank : banks.values()) {
            total = total.add(bank.jdbc().sql("SELECT COALESCE(SUM(balance), 0) FROM account")
                    .query(BigDecimal.class)
                    .single());
        }
        return total;
    }

    /**
     * Money debited but not yet credited: amount of PENDING transfers whose key has no row in the other bank.
     * Relies on the convention that only the source bank's row is ever PENDING.
     */
    public BigDecimal getInFlightMoney() {
        BigDecimal inFlight = BigDecimal.ZERO;
        for (Bank source : Bank.values()) {
            List<PendingTransfer> pending = pendingTransfers(source);
            if (pending.isEmpty()) {
                continue;
            }
            Set<String> credited = keysPresentIn(other(source),
                    pending.stream().map(PendingTransfer::idempotencyKey).toList());
            for (PendingTransfer transfer : pending) {
                if (!credited.contains(transfer.idempotencyKey())) {
                    inFlight = inFlight.add(transfer.amount());
                }
            }
        }
        return inFlight;
    }

    /** Empties {@code transfer} and sets every balance back to {@link #INITIAL_BALANCE}. */
    public void resetData() {
        for (BankAccess bank : banks.values()) {
            bank.transaction().executeWithoutResult(status -> {
                bank.jdbc().sql("TRUNCATE TABLE transfer RESTART IDENTITY").update();
                bank.jdbc().sql("UPDATE account SET balance = :balance")
                        .param("balance", INITIAL_BALANCE)
                        .update();
            });
        }
    }

    public long countNegativeBalances() {
        return count("SELECT count(*) FROM account WHERE balance < 0");
    }

    public long countPendingTransfers() {
        return count("SELECT count(*) FROM transfer WHERE status = 'PENDING'");
    }

    /**
     * Time between {@code created_at} (debit committed) and {@code finalized_at} (final state) of every finalized
     * transfer, in milliseconds. Only source rows are finalized.
     */
    public List<Double> inconsistencyWindowsMillis() {
        List<Double> windows = new ArrayList<>();
        for (BankAccess bank : banks.values()) {
            windows.addAll(bank.jdbc()
                    .sql("SELECT EXTRACT(EPOCH FROM (finalized_at - created_at)) * 1000 "
                            + "FROM transfer WHERE finalized_at IS NOT NULL")
                    .query(Double.class)
                    .list());
        }
        return windows;
    }

    private long count(String sql) {
        long total = 0;
        for (BankAccess bank : banks.values()) {
            total += bank.jdbc().sql(sql).query(Long.class).single();
        }
        return total;
    }

    private List<PendingTransfer> pendingTransfers(Bank bank) {
        return banks.get(bank).jdbc()
                .sql("SELECT idempotency_key, amount FROM transfer WHERE status = 'PENDING'")
                .query((rs, rowNum) -> new PendingTransfer(rs.getString("idempotency_key"), rs.getBigDecimal("amount")))
                .list();
    }

    private Set<String> keysPresentIn(Bank bank, List<String> keys) {
        return banks.get(bank).jdbc()
                .sql("SELECT idempotency_key FROM transfer WHERE idempotency_key IN (:keys)")
                .param("keys", keys)
                .query(String.class)
                .set();
    }

    private static Bank other(Bank bank) {
        return bank == Bank.ALPHA ? Bank.BETA : Bank.ALPHA;
    }
}
