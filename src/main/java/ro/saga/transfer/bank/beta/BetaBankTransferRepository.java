package ro.saga.transfer.bank.beta;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.saga.transfer.common.TransferStatus;

import java.util.List;
import java.util.Optional;

public interface BetaBankTransferRepository extends JpaRepository<BetaBankTransfer, Long> {

    Optional<BetaBankTransfer> findByIdempotencyKey(String idempotencyKey);

    List<BetaBankTransfer> findByStatus(TransferStatus status);
}
