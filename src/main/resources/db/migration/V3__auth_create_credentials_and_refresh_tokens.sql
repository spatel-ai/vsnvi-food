CREATE TABLE credentials (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL,      -- cross-module ref to users: no FK
    password_hash  VARCHAR(100) NOT NULL,      -- DelegatingPasswordEncoder format, e.g. {bcrypt}$2a$12$...
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_credentials_user UNIQUE (user_id)
);

CREATE TABLE refresh_tokens (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL,         -- cross-module ref to users: no FK
    token_hash   VARCHAR(64) NOT NULL,         -- SHA-256 hex; the raw token is never stored
    family_id    UUID        NOT NULL,         -- all rotations of one login
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,                  -- set when rotated; reuse after this revokes the family
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
