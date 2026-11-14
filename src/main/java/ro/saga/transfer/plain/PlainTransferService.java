package ro.saga.transfer.plain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ro.saga.transfer.bank.BankOperations;
import ro.saga.transfer.bank.Banks;
import ro.saga.transfer.bank.DuplicateTransferException;
import ro.saga.transfer.bank.InsufficientFundsException;
import ro.saga.transfer.bank.TransferRecord;
import ro.saga.transfer.common.AuditService;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.FraudCheckService;
import ro.saga.transfer.common.FraudDecision;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferService;
import ro.saga.transfer.common.TransferStatus;

import java.util.Optional;

/**
 * V1 Plain-Correct baseline: executes the entire 5-step transfer procedurally without design patterns.
 */
@Service("V1")
public class PlainTransferService implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(PlainTransferService.class);
    public static final String FRAUD_REJECTED = "FRAUD_REJECTED";

    private final Banks banks;
    private final FaultInjector faults;
    private final FraudCheckService fraud;
    private final AuditService audit;
    private final int maxCompensationAttempts;
    private final long compensationBackoffMs;

    @Autowired
    public PlainTransferService(
            Banks banks,
            FaultInjector faults,
            FraudCheckService fraud,
            AuditService audit,
            @Value("${app.compensation.max-attempts:3}") int maxCompensationAttempts,
            @Value("${app.compensation.backoff-ms:50}") long compensationBackoffMs) {
        this.banks = banks;
        this.faults = faults;
        this.fraud = fraud;
        this.audit = audit;
        this.maxCompensationAttempts = maxCompensationAttempts;
        this.compensationBackoffMs = compensationBackoffMs;
    }

    public PlainTransferService(Banks banks, FaultInjector faults, FraudCheckService fraud, AuditService audit) {
        this(banks, faults, fraud, audit, 3, 50L);
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        BankOperations source = banks.of(request.fromBank());
        BankOperations target = banks.of(request.toBank());

        // Step 1: Debit and record transfer as PENDING in source bank
        long transferId;
        try {
            transferId = source.debitAndRecordPending(request);
        } catch (InsufficientFundsException e) {
            return TransferResult.rejectedInsufficientFunds();
        } catch (DuplicateTransferException e) {
            return handleDuplicate(source, request.idempotencyKey());
        } catch (BankUnavailableException e) {
            return TransferResult.failedBeforeDebit(e.getMessage());
        }

        // Step 2: Injected fault point after debit
        try {
            faults.maybeFail(FaultPoint.AFTER_DEBIT);
        } catch (BankUnavailableException e) {
            return compensateWithRetry(source, transferId, e.getMessage());
        }

        // Step 3: Fraud check (simulated fraud check handles FRAUD_TIMEOUT internally)
        try {
            FraudDecision decision = fraud.check(request);
            if (decision == FraudDecision.REJECTED) {
                return compensateWithRetry(source, transferId, FRAUD_REJECTED);
            }
        } catch (Exception e) {
            return compensateWithRetry(source, transferId, e.getMessage());
        }

        // Step 4: Credit in target bank (idempotent pivot step)
        try {
            target.creditOnce(request);
        } catch (Exception e) {
            boolean creditSucceeded = false;
            try {
                creditSucceeded = target.findTransfer(request.idempotencyKey()).isPresent();
            } catch (Exception checkEx) {
                log.warn("Cannot verify target credit existence for key {}: {}", request.idempotencyKey(), checkEx.getMessage());
                return TransferResult.awaitingRecovery(transferId, "Target state uncertain: " + e.getMessage());
            }

            if (creditSucceeded) {
                log.info("Credit succeeded despite exception for transfer {}", transferId);
            } else {
                return compensateWithRetry(source, transferId, e.getMessage());
            }
        }

        // Step 5: Finalize transfer as COMPLETED in source bank
        boolean faultAfterCredit = false;
        try {
            faults.maybeFail(FaultPoint.AFTER_CREDIT);
        } catch (Exception e) {
            log.warn("Fault AFTER_CREDIT injected for transfer {}: {}", transferId, e.getMessage());
            faultAfterCredit = true;
        }

        if (!retryMarkCompleted(source, transferId)) {
            return TransferResult.awaitingRecovery(transferId, faultAfterCredit ? "Fault after credit" : "Could not mark completed");
        }

        // Step 6: Audit / notification (AUDIT_FAILURE is injected internally by SimulatedAuditService)
        recordAuditWithRetry(transferId, TransferOutcome.COMPLETED);

        return TransferResult.completed(transferId);
    }

    private TransferResult handleDuplicate(BankOperations source, String idempotencyKey) {
        Optional<TransferRecord> existing = source.findTransfer(idempotencyKey);
        if (existing.isEmpty()) {
            return TransferResult.failedBeforeDebit("Duplicate transfer detected but record not found");
        }
        TransferRecord record = existing.get();
        return switch (record.status()) {
            case COMPLETED -> TransferResult.completed(record.id());
            case COMPENSATED -> FRAUD_REJECTED.equals(record.failureReason())
                    ? TransferResult.rejectedFraud(record.id())
                    : TransferResult.compensated(record.id(), record.failureReason() != null ? record.failureReason() : "Compensated");
            case PENDING -> TransferResult.awaitingRecovery(record.id(), "Transfer still pending");
            case FAILED -> TransferResult.failedBeforeDebit(record.failureReason() != null ? record.failureReason() : "Transfer failed");
        };
    }

    private TransferResult compensateWithRetry(BankOperations source, long transferId, String reason) {
        long backoff = compensationBackoffMs;
        for (int attempt = 1; attempt <= maxCompensationAttempts; attempt++) {
            try {
                faults.maybeFail(FaultPoint.DURING_COMPENSATION);
                boolean compensated = source.compensate(transferId, reason);
                if (compensated) {
                    if (FRAUD_REJECTED.equals(reason)) {
                        return TransferResult.rejectedFraud(transferId);
                    }
                    return TransferResult.compensated(transferId, reason);
                } else {
                    Optional<TransferRecord> record = source.findTransfer(transferId);
                    if (record.isPresent()) {
                        TransferRecord r = record.get();
                        if (r.status() == TransferStatus.COMPLETED) {
                            return TransferResult.completed(transferId);
                        }
                        if (FRAUD_REJECTED.equals(r.failureReason())) {
                            return TransferResult.rejectedFraud(transferId);
                        }
                        return TransferResult.compensated(transferId, r.failureReason() != null ? r.failureReason() : reason);
                    }
                    return TransferResult.compensated(transferId, reason);
                }
            } catch (Exception e) {
                log.warn("Compensation attempt {} failed for transfer {}: {}", attempt, transferId, e.getMessage());
                try {
                    source.recordAttempt(transferId, reason + ": " + e.getMessage());
                } catch (Exception ex) {
                    log.warn("Failed to record attempt for transfer {}: {}", transferId, ex.getMessage());
                }
                if (attempt < maxCompensationAttempts) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoff *= 2;
                }
            }
        }
        log.error("Compensation exhausted after {} attempts for transfer {}; leaving PENDING for recovery", maxCompensationAttempts, transferId);
        return TransferResult.awaitingRecovery(transferId, reason);
    }

    private boolean retryMarkCompleted(BankOperations source, long transferId) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (source.markCompleted(transferId)) {
                    return true;
                }
                Optional<TransferRecord> record = source.findTransfer(transferId);
                if (record.isPresent() && record.get().status() == TransferStatus.COMPLETED) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("markCompleted attempt {} failed for transfer {}: {}", attempt, transferId, e.getMessage());
            }
        }
        return false;
    }

    private void recordAuditWithRetry(long transferId, TransferOutcome outcome) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                audit.record(transferId, outcome);
                return;
            } catch (Exception e) {
                log.warn("Audit attempt {} failed for transfer {}: {}", attempt, transferId, e.getMessage());
            }
        }
        log.error("Audit service unreachable after 3 attempts for transfer {}; proceeding since transfer is completed", transferId);
    }
}
