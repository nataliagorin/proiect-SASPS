package ro.saga.transfer.common;

/** Implemented by every transfer variant. Must be thread-safe. See docs/contracts.md. */
public interface TransferService {

    /** Rejections and technical failures are returned in the result, not thrown. */
    TransferResult transfer(TransferRequest request);
}
