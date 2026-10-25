CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    email       VARCHAR(254) NOT NULL,                -- stored lower-cased
    full_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(16),                          -- E.164
    role        VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'RESTAURANT_OWNER', 'RIDER', 'ADMIN')),
    status      VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);
-- Admin listing filters by role and status; AdminBootstrap checks whether an ADMIN exists.
CREATE INDEX idx_users_role_status ON users (role, status);
CREATE INDEX idx_users_created_at ON users (created_at);

CREATE TABLE addresses (
    id           UUID             PRIMARY KEY,
    user_id      UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label        VARCHAR(30),
    line1        VARCHAR(200)     NOT NULL,
    line2        VARCHAR(200),
    city         VARCHAR(100)     NOT NULL,
    state        VARCHAR(100)     NOT NULL,
    postal_code  VARCHAR(10)      NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude    DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    created_at   TIMESTAMPTZ      NOT NULL,
    updated_at   TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_addresses_user ON addresses (user_id);
