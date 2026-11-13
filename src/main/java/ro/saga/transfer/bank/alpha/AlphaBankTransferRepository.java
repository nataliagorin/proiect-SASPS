package ro.saga.transfer.bank.alpha;

import org.springframework.data.jpa.repository.JpaRepository;
import ro.saga.transfer.common.TransferStatus;

import java.util.List;
import java.util.Optional;

public interface AlphaBankTransferRepository extends JpaRepository<AlphaBankTransfer, Long> {

    Optional<AlphaBankTransfer> findByIdempotencyKey(String idempotencyKey);

    List<AlphaBankTransfer> findByStatus(TransferStatus status);
}
