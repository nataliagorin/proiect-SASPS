package ro.saga.transfer.bank;

import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.TransferRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface BankOperations {

    Bank bank();

    void debit(long accountId, BigDecimal amount);

    void credit(long accountId, BigDecimal amount);

    void refund(long accountId, BigDecimal amount);

    BigDecimal balanceOf(long accountId);

    long debitAndRecordPending(TransferRequest request);

    boolean creditOnce(TransferRequest request);

    boolean markCompleted(long transferId);

    boolean compensate(long transferId, String reason);

    int recordAttempt(long transferId, String reason);

    Optional<TransferRecord> findTransfer(long transferId);

    Optional<TransferRecord> findTransfer(String idempotencyKey);

    List<TransferRecord> findPendingOlderThan(Duration age);
}
