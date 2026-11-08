CREATE TABLE account
(
    id      BIGSERIAL PRIMARY KEY,
    owner   VARCHAR(255)   NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    CONSTRAINT chk_account_balance_non_negative CHECK (balance >= 0)
);

-- No foreign keys on the account columns: one side of a transfer always lives in the other bank.
CREATE TABLE transfer
(
    id           BIGSERIAL PRIMARY KEY,
    from_account BIGINT         NOT NULL,
    to_account   BIGINT         NOT NULL,
    amount       NUMERIC(19, 2) NOT NULL,
    status       VARCHAR(32)    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_transfer_amount_positive CHECK (amount > 0)
);
