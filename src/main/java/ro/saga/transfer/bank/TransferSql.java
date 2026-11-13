package ro.saga.transfer.bank;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** SQL on the {@code transfer} table, shared by both banks. Callers run it in a local transaction. */
final class TransferSql {

    private record Debited(long fromAccount, BigDecimal amount) {
    }

    private static final String COLUMNS = "id, idempotency_key, from_account, to_account, amount, status, "
            + "created_at, finalized_at, failure_reason, attempt_count";

    private static final RowMapper<TransferRecord> ROW_MAPPER = (rs, rowNum) -> new TransferRecord(
            rs.getLong("id"),
            rs.getString("idempotency_key"),
            rs.getLong("from_account"),
            rs.getLong("to_account"),
            rs.getBigDecimal("amount"),
            TransferStatus.valueOf(rs.getString("status")),
            toInstant(rs.getObject("created_at", OffsetDateTime.class)),
            toInstant(rs.getObject("finalized_at", OffsetDateTime.class)),
            rs.getString("failure_reason"),
            rs.getInt("attempt_count"));

    private TransferSql() {
    }

    static long debitAndRecordPending(JdbcClient jdbc, TransferRequest request) {
        long id = insert(jdbc, request, TransferStatus.PENDING)
                .orElseThrow(() -> new DuplicateTransferException(request.idempotencyKey()));
        AccountSql.debit(jdbc, request.fromAccountId(), request.amount());
        return id;
    }

    static boolean creditOnce(JdbcClient jdbc, TransferRequest request) {
        if (insert(jdbc, request, TransferStatus.COMPLETED).isEmpty()) {
            return false;
        }
        AccountSql.credit(jdbc, request.toAccountId(), request.amount());
        return true;
    }

    static boolean markCompleted(JdbcClient jdbc, long transferId) {
        return jdbc.sql("UPDATE transfer SET status = 'COMPLETED', finalized_at = now() "
                        + "WHERE id = :id AND status = 'PENDING'")
                .param("id", transferId)
                .update() == 1;
    }

    static boolean compensate(JdbcClient jdbc, long transferId, String reason) {
        Optional<Debited> debited = jdbc.sql("UPDATE transfer SET status = 'COMPENSATED', finalized_at = now(), "
                        + "failure_reason = :reason WHERE id = :id AND status = 'PENDING' "
                        + "RETURNING from_account, amount")
                .param("reason", reason)
                .param("id", transferId)
                .query((rs, rowNum) -> new Debited(rs.getLong("from_account"), rs.getBigDecimal("amount")))
                .optional();
        debited.ifPresent(row -> AccountSql.credit(jdbc, row.fromAccount(), row.amount()));
        return debited.isPresent();
    }

    static int recordAttempt(JdbcClient jdbc, long transferId, String reason) {
        return jdbc.sql("UPDATE transfer SET attempt_count = attempt_count + 1, failure_reason = :reason "
                        + "WHERE id = :id RETURNING attempt_count")
                .param("reason", reason)
                .param("id", transferId)
                .query(Integer.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Transfer " + transferId + " not found"));
    }

    static Optional<TransferRecord> findById(JdbcClient jdbc, long transferId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transfer WHERE id = :id")
                .param("id", transferId)
                .query(ROW_MAPPER)
                .optional();
    }

    static Optional<TransferRecord> findByIdempotencyKey(JdbcClient jdbc, String idempotencyKey) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transfer WHERE idempotency_key = :key")
                .param("key", idempotencyKey)
                .query(ROW_MAPPER)
                .optional();
    }

    static List<TransferRecord> findPendingOlderThan(JdbcClient jdbc, Duration age) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM transfer WHERE status = 'PENDING' "
                        + "AND created_at < now() - :ms * interval '1 millisecond' ORDER BY id")
                .param("ms", age.toMillis())
                .query(ROW_MAPPER)
                .list();
    }

    // The unique key makes a second insert a no-op, so concurrent duplicates cannot both pass.
    private static Optional<Long> insert(JdbcClient jdbc, TransferRequest request, TransferStatus status) {
        return jdbc.sql("INSERT INTO transfer (idempotency_key, from_account, to_account, amount, status) "
                        + "VALUES (:key, :from, :to, :amount, :status) "
                        + "ON CONFLICT (idempotency_key) DO NOTHING RETURNING id")
                .param("key", request.idempotencyKey())
                .param("from", request.fromAccountId())
                .param("to", request.toAccountId())
                .param("amount", request.amount())
                .param("status", status.name())
                .query(Long.class)
                .optional();
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
