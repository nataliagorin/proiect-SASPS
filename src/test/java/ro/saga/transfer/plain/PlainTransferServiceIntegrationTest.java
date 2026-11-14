package ro.saga.transfer.plain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import ro.saga.transfer.bank.AbstractBankIntegrationTest;
import ro.saga.transfer.bank.BankOperations;
import ro.saga.transfer.bank.Banks;
import ro.saga.transfer.bank.InvariantChecker;
import ro.saga.transfer.bank.LedgerService;
import ro.saga.transfer.bank.TransferRecord;
import ro.saga.transfer.common.AuditService;
import ro.saga.transfer.common.Bank;
import ro.saga.transfer.common.BankUnavailableException;
import ro.saga.transfer.common.FaultInjector;
import ro.saga.transfer.common.FaultPoint;
import ro.saga.transfer.common.FraudCheckService;
import ro.saga.transfer.common.FraudDecision;
import ro.saga.transfer.common.TransferOutcome;
import ro.saga.transfer.common.TransferRequest;
import ro.saga.transfer.common.TransferResult;
import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.recovery.enabled=false")
class PlainTransferServiceIntegrationTest extends AbstractBankIntegrationTest {

    private static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");

    @Autowired
    private Banks banks;

    @Autowired
    private LedgerService ledger;

    @Autowired
    private InvariantChecker invariants;

    @Autowired
    private RecoveryJob recoveryJob;

    private BankOperations alpha;
    private BankOperations beta;

    @BeforeEach
    void resetData() {
        ledger.resetData();
        alpha = banks.of(Bank.ALPHA);
        beta = banks.of(Bank.BETA);
    }

    private PlainTransferService createService(FaultInjector faults, FraudCheckService fraud, AuditService audit) {
        return new PlainTransferService(banks, faults, fraud, audit, 3, 10L);
    }

    private PlainTransferService defaultService() {
        return createService(FaultInjector.NONE, request -> FraudDecision.APPROVED, (id, outcome) -> {});
    }

    private static TransferRequest request(String key, BigDecimal amount) {
        return new TransferRequest(key, Bank.ALPHA, 1, Bank.BETA, 2, amount);
    }

    @Test
    void happyPath_completesSuccessfullyAndUpdatesBalances() {
        PlainTransferService service = defaultService();
        TransferRequest req = request("happy-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.COMPLETED);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.transferId()).isNotNull();

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1100.00");

        Optional<TransferRecord> sourceRow = alpha.findTransfer(result.transferId());
        assertThat(sourceRow).isPresent();
        assertThat(sourceRow.get().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(sourceRow.get().finalizedAt()).isNotNull();

        Optional<TransferRecord> targetRow = beta.findTransfer("happy-1");
        assertThat(targetRow).isPresent();

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void insufficientFunds_rejectsWithoutModifyingBalances() {
        PlainTransferService service = defaultService();
        TransferRequest req = request("insufficient-1", new BigDecimal("1500.00"));

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.REJECTED_INSUFFICIENT_FUNDS);
        assertThat(result.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.transferId()).isNull();

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1000.00");

        assertThat(alpha.findTransfer("insufficient-1")).isEmpty();
        assertThat(beta.findTransfer("insufficient-1")).isEmpty();

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void fraudRejection_compensatesDebitAndReturnsRejectedFraud() {
        FraudCheckService fraudReject = req -> FraudDecision.REJECTED;
        PlainTransferService service = createService(FaultInjector.NONE, fraudReject, (id, outcome) -> {});
        TransferRequest req = request("fraud-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.REJECTED_FRAUD);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPENSATED);

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1000.00");

        Optional<TransferRecord> record = alpha.findTransfer(result.transferId());
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(TransferStatus.COMPENSATED);
        assertThat(record.get().failureReason()).isEqualTo(PlainTransferService.FRAUD_REJECTED);

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void afterDebitFailure_compensatesDebitAndReturnsCompensated() {
        FaultInjector faultAfterDebit = point -> {
            if (point == FaultPoint.AFTER_DEBIT) {
                throw new BankUnavailableException("Simulated failure after debit");
            }
        };
        PlainTransferService service = createService(faultAfterDebit, req -> FraudDecision.APPROVED, (id, outcome) -> {});
        TransferRequest req = request("fault-debit-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.COMPENSATED);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPENSATED);

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1000.00");
        assertThat(beta.findTransfer("fault-debit-1")).isEmpty();

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void fraudTimeout_compensatesDebitAndReturnsCompensated() {
        FraudCheckService fraudTimeout = req -> {
            throw new BankUnavailableException("Fraud service timed out");
        };
        PlainTransferService service = createService(FaultInjector.NONE, fraudTimeout, (id, outcome) -> {});
        TransferRequest req = request("fraud-timeout-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.COMPENSATED);
        assertThat(result.status()).isEqualTo(TransferStatus.COMPENSATED);

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1000.00");

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void afterCreditFailure_completesWithoutDoubleCredit() {
        FaultInjector faultAfterCredit = point -> {
            if (point == FaultPoint.AFTER_CREDIT) {
                throw new BankUnavailableException("Simulated failure after credit");
            }
        };
        PlainTransferService service = createService(faultAfterCredit, req -> FraudDecision.APPROVED, (id, outcome) -> {});
        TransferRequest req = request("fault-credit-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.COMPLETED);

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1100.00");

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void duringCompensationFailure_leavesPendingAndRecoveryJobReconciles() {
        FaultInjector faultComp = point -> {
            if (point == FaultPoint.AFTER_DEBIT || point == FaultPoint.DURING_COMPENSATION) {
                throw new BankUnavailableException("Simulated persistent failure");
            }
        };
        PlainTransferService service = createService(faultComp, req -> FraudDecision.APPROVED, (id, outcome) -> {});
        TransferRequest req = request("comp-fail-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        // Compensation exhausted, left in PENDING
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.FAILED_TECHNICAL);
        assertThat(result.status()).isEqualTo(TransferStatus.PENDING);

        Optional<TransferRecord> pendingRecord = alpha.findTransfer(result.transferId());
        assertThat(pendingRecord).isPresent();
        assertThat(pendingRecord.get().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00"); // money still debited

        // Now run RecoveryJob manually (with zero duration threshold)
        int recovered = recoveryJob.recoverPendingTransfers(Duration.ZERO);
        assertThat(recovered).isEqualTo(1);

        // RecoveryJob sees no credit in Beta, so it compensates Alpha
        Optional<TransferRecord> reconciledRecord = alpha.findTransfer(result.transferId());
        assertThat(reconciledRecord).isPresent();
        assertThat(reconciledRecord.get().status()).isEqualTo(TransferStatus.COMPENSATED);
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00"); // money refunded

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void auditFailure_transferRemainsCompleted() {
        AtomicInteger auditAttempts = new AtomicInteger();
        AuditService failingAudit = (id, outcome) -> {
            auditAttempts.incrementAndGet();
            throw new BankUnavailableException("Audit service unavailable");
        };

        PlainTransferService service = createService(FaultInjector.NONE, req -> FraudDecision.APPROVED, failingAudit);
        TransferRequest req = request("audit-fail-1", AMOUNT_100);

        TransferResult result = service.transfer(req);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outcome()).isEqualTo(TransferOutcome.COMPLETED);
        assertThat(auditAttempts.get()).isGreaterThanOrEqualTo(3);

        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1100.00");

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void duplicateKeyReplay_serial_returnsConsistentResultAndMovesMoneyOnce() {
        PlainTransferService service = defaultService();
        TransferRequest req = request("dup-serial-1", AMOUNT_100);

        TransferResult first = service.transfer(req);
        assertThat(first.isSuccess()).isTrue();
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1100.00");

        // Replay same request
        TransferResult second = service.transfer(req);
        assertThat(second.isSuccess()).isTrue();
        assertThat(second.transferId()).isEqualTo(first.transferId());
        assertThat(second.outcome()).isEqualTo(TransferOutcome.COMPLETED);

        // Balances must remain unchanged (only moved once)
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1100.00");

        assertThat(invariants.check().isConsistent()).isTrue();
    }

    @Test
    void crashAfterDebit_reconciledByRecoveryJob() {
        TransferRequest req = request("crash-1", AMOUNT_100);

        // Simulate crash right after debit: only debitAndRecordPending is called
        long transferId = alpha.debitAndRecordPending(req);
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("900.00");
        assertThat(beta.balanceOf(2)).isEqualByComparingTo("1000.00");

        // Money is in flight
        assertThat(ledger.getInFlightMoney()).isEqualByComparingTo("100.00");

        // Reconcile via RecoveryJob
        int recovered = recoveryJob.recoverPendingTransfers(Duration.ZERO);
        assertThat(recovered).isEqualTo(1);

        // Should be compensated because target bank has no record
        Optional<TransferRecord> record = alpha.findTransfer(transferId);
        assertThat(record).isPresent();
        assertThat(record.get().status()).isEqualTo(TransferStatus.COMPENSATED);

        // Balances restored and no money in flight
        assertThat(alpha.balanceOf(1)).isEqualByComparingTo("1000.00");
        assertThat(ledger.getInFlightMoney()).isEqualByComparingTo("0.00");
        assertThat(invariants.check().isConsistent()).isTrue();
    }
}
