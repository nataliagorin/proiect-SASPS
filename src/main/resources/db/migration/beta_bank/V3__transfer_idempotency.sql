-- NOT NULL without a default is safe here: transfer is empty when this migration runs.
ALTER TABLE transfer
    ADD COLUMN idempotency_key VARCHAR(128) NOT NULL,
    ADD COLUMN failure_reason  VARCHAR(255),
    ADD COLUMN finalized_at    TIMESTAMPTZ,
    ADD COLUMN attempt_count   INT          NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_transfer_idempotency_key UNIQUE (idempotency_key),
    ADD CONSTRAINT chk_transfer_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'COMPENSATED'));
