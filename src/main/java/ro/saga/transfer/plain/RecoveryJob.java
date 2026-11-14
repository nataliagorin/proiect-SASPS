package ro.saga.transfer.plain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.saga.transfer.bank.BankOperations;
import ro.saga.transfer.bank.Banks;
import ro.saga.transfer.bank.TransferRecord;
import ro.saga.transfer.common.Bank;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Periodically reconciles stale PENDING transfers across both banks.
 * Can be disabled via app.recovery.enabled=false in crash tests and triggered manually.
 */
@Component
public class RecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(RecoveryJob.class);

    private final Banks banks;
    private final boolean enabled;
    private final long staleAfterMs;

    public RecoveryJob(
            Banks banks,
            @Value("${app.recovery.enabled:true}") boolean enabled,
            @Value("${app.recovery.stale-after-ms:5000}") long staleAfterMs) {
        this.banks = banks;
        this.enabled = enabled;
        this.staleAfterMs = staleAfterMs;
    }

    @Scheduled(fixedDelayString = "${app.recovery.interval-ms:1000}")
    public void run() {
        if (!enabled) {
            return;
        }
        recoverPendingTransfers();
    }

    public int recoverPendingTransfers() {
        int recovered = 0;
        for (Bank sourceBank : Bank.values()) {
            Bank targetBank = (sourceBank == Bank.ALPHA) ? Bank.BETA : Bank.ALPHA;
            BankOperations source = banks.of(sourceBank);
            BankOperations target = banks.of(targetBank);

            List<TransferRecord> stalePending;
            try {
                stalePending = source.findPendingOlderThan(Duration.ofMillis(staleAfterMs));
            } catch (Exception e) {
                log.error("RecoveryJob: failed to query stale pending transfers for {}: {}", sourceBank, e.getMessage());
                continue;
            }

            for (TransferRecord record : stalePending) {
                recovered += reconcile(source, target, record);
            }
        }
        return recovered;
    }

    private int reconcile(BankOperations source, BankOperations target, TransferRecord record) {
        long id = record.id();
        String key = record.idempotencyKey();

        try {
            Optional<TransferRecord> targetRecord = target.findTransfer(key);
            if (targetRecord.isPresent()) {
                boolean completed = source.markCompleted(id);
                if (completed) {
                    log.info("RecoveryJob: reconciled pending transfer {} as COMPLETED (credit found in {})", id, target.bank());
                    return 1;
                } else {
                    log.warn("RecoveryJob: markCompleted returned false for transfer {} (not in PENDING status)", id);
                }
            } else {
                boolean compensated = source.compensate(id, "Recovered: no credit");
                if (compensated) {
                    log.info("RecoveryJob: reconciled pending transfer {} as COMPENSATED (no credit in {})", id, target.bank());
                    return 1;
                } else {
                    log.warn("RecoveryJob: compensate returned false for transfer {} (not in PENDING status)", id);
                }
            }
        } catch (Exception e) {
            log.error("RecoveryJob: failed to reconcile transfer {} (key: {}): {}", id, key, e.getMessage());
        }
        return 0;
    }
}
