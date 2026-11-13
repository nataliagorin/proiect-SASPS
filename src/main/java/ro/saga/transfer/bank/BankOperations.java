package ro.saga.transfer.bank;

import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Balance and transfer-row changes on one bank. Each call is one local transaction on that bank. */
public interface BankOperations {

    Bank bank();

    /** @throws InsufficientFundsException if the balance is lower than {@code amount} */
    void debit(long accountId, BigDecimal amount);

    void credit(long accountId, BigDecimal amount);

    /** Reverts a debit. */
    void refund(long accountId, BigDecimal amount);

    BigDecimal balanceOf(long accountId);

    /**
     * Source bank: inserts the PENDING row and debits. Returns the row id.
     *
     * @throws InsufficientFundsException nothing is written
     * @throws DuplicateTransferException the key already has a row here; nothing is written
     */
    long debitAndRecordPending(TransferRequest request);

    /** Destination bank: inserts the row and credits. False if this key was already credited. */
    boolean creditOnce(TransferRequest request);

    /** PENDING to COMPLETED. False if the row was no longer PENDING. */
    boolean markCompleted(long transferId);

    /** Refunds the debit and moves PENDING to COMPENSATED. False if the row was no longer PENDING. */
    boolean compensate(long transferId, String reason);

    /** Counts a failed retry. Returns the new {@code attempt_count}. */
    int recordAttempt(long transferId, String reason);

    Optional<TransferRecord> findTransfer(long transferId);

    Optional<TransferRecord> findTransfer(String idempotencyKey);

    /** For recovery: PENDING rows created more than {@code age} ago. */
    List<TransferRecord> findPendingOlderThan(Duration age);
}
