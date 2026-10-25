-- Idempotency-Key support (docs/API-DESIGN.md §6): one row per (caller, key), kept for 24 hours.
CREATE TABLE idempotency_keys (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL,   -- the caller the key is scoped to
    idempotency_key   UUID         NOT NULL,
    operation         VARCHAR(100) NOT NULL,   -- e.g. placeOrder
    request_hash      VARCHAR(64)  NOT NULL,   -- SHA-256 hex of operation + request body
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    response_body     TEXT,                    -- JSON of the stored response, once completed
    expires_at        TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_keys_user_key UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys (expires_at);
