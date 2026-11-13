package ro.saga.transfer.bank;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;

/** SQL shared by both banks. The balance check and the update are a single atomic statement. */
final class AccountSql {

    private AccountSql() {
    }

    static void debit(JdbcClient jdbc, long accountId, BigDecimal amount) {
        requirePositive(amount);
        int updated = jdbc.sql("UPDATE account SET balance = balance - :amount WHERE id = :id AND balance >= :amount")
                .param("amount", amount)
                .param("id", accountId)
                .update();
        if (updated == 0) {
            requireExists(jdbc, accountId);
            throw new InsufficientFundsException(accountId, amount);
        }
    }

    static void credit(JdbcClient jdbc, long accountId, BigDecimal amount) {
        requirePositive(amount);
        int updated = jdbc.sql("UPDATE account SET balance = balance + :amount WHERE id = :id")
                .param("amount", amount)
                .param("id", accountId)
                .update();
        if (updated == 0) {
            throw new AccountNotFoundException(accountId);
        }
    }

    static BigDecimal balanceOf(JdbcClient jdbc, long accountId) {
        return jdbc.sql("SELECT balance FROM account WHERE id = :id")
                .param("id", accountId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private static void requireExists(JdbcClient jdbc, long accountId) {
        Long count = jdbc.sql("SELECT count(*) FROM account WHERE id = :id")
                .param("id", accountId)
                .query(Long.class)
                .single();
        if (count == 0) {
            throw new AccountNotFoundException(accountId);
        }
    }
}
