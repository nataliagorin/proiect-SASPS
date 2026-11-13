package ro.saga.transfer.bank.alpha;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ro.saga.transfer.bank.AbstractBankTransfer;
import ro.saga.transfer.common.TransferStatus;

import java.math.BigDecimal;

@Entity
@Table(name = "transfer")
public class AlphaBankTransfer extends AbstractBankTransfer {

    protected AlphaBankTransfer() {
    }

    public AlphaBankTransfer(
            String idempotencyKey, long fromAccount, long toAccount, BigDecimal amount, TransferStatus status) {
        super(idempotencyKey, fromAccount, toAccount, amount, status);
    }
}
