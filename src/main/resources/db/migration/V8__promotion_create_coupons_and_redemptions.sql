-- Coupons (ARCHITECTURE §8). Codes are stored upper-case and are unique platform-wide, because a
-- customer applies a coupon by code alone. Which discount columns are set depends on the type.
CREATE TABLE coupons (
    id                          UUID           PRIMARY KEY,
    code                        VARCHAR(30)    NOT NULL CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{2,29}$'),
    restaurant_id               UUID,          -- cross-module ref to restaurants: no FK; null = platform coupon
    description                 VARCHAR(200),
    type                        VARCHAR(20)    NOT NULL CHECK (type IN ('FLAT', 'PERCENT', 'FREE_DELIVERY')),
    amount_off_amount           NUMERIC(12, 2) CHECK (amount_off_amount > 0),       -- FLAT only
    amount_off_currency         CHAR(3),
    percent_off                 NUMERIC(5, 2)  CHECK (percent_off > 0 AND percent_off <= 100),   -- PERCENT only
    max_discount_amount         NUMERIC(12, 2) CHECK (max_discount_amount > 0),     -- PERCENT only (required cap)
    max_discount_currency       CHAR(3),
    min_order_subtotal_amount   NUMERIC(12, 2) NOT NULL CHECK (min_order_subtotal_amount >= 0),
    min_order_subtotal_currency CHAR(3)        NOT NULL,
    valid_from                  TIMESTAMPTZ    NOT NULL,
    valid_until                 TIMESTAMPTZ    NOT NULL,
    active                      BOOLEAN        NOT NULL,
    per_customer_limit          INTEGER        CHECK (per_customer_limit >= 1),     -- null = unlimited
    total_limit                 INTEGER        CHECK (total_limit >= 1),            -- null = unlimited
    first_order_only            BOOLEAN        NOT NULL,
    created_at                  TIMESTAMPTZ    NOT NULL,
    updated_at                  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_coupons_code UNIQUE (code),
    CONSTRAINT chk_coupons_window CHECK (valid_until > valid_from),
    CONSTRAINT chk_coupons_type_terms CHECK (
        (type = 'FLAT' AND amount_off_amount IS NOT NULL AND amount_off_currency IS NOT NULL
            AND percent_off IS NULL AND max_discount_amount IS NULL AND max_discount_currency IS NULL)
        OR (type = 'PERCENT' AND percent_off IS NOT NULL AND max_discount_amount IS NOT NULL
            AND max_discount_currency IS NOT NULL AND amount_off_amount IS NULL AND amount_off_currency IS NULL)
        OR (type = 'FREE_DELIVERY' AND amount_off_amount IS NULL AND amount_off_currency IS NULL
            AND percent_off IS NULL AND max_discount_amount IS NULL AND max_discount_currency IS NULL))
);
CREATE INDEX idx_coupons_restaurant ON coupons (restaurant_id, created_at);
CREATE INDEX idx_coupons_active_window ON coupons (active, valid_until);

-- One row per order that used a coupon. Usage limits count the REDEEMED rows while the coupon row
-- is locked (SELECT ... FOR UPDATE), so there is no separate counter to drift.
CREATE TABLE coupon_redemptions (
    id                UUID           PRIMARY KEY,
    coupon_id         UUID           NOT NULL REFERENCES coupons (id),
    customer_id       UUID           NOT NULL,   -- cross-module ref to users: no FK
    order_id          UUID           NOT NULL,   -- cross-module ref to orders: no FK
    discount_amount   NUMERIC(12, 2) NOT NULL CHECK (discount_amount >= 0),
    discount_currency CHAR(3)        NOT NULL,
    status            VARCHAR(20)    NOT NULL CHECK (status IN ('REDEEMED', 'RELEASED')),
    released_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_coupon_redemptions_order UNIQUE (order_id),
    CONSTRAINT chk_coupon_redemptions_released CHECK ((status = 'RELEASED') = (released_at IS NOT NULL))
);
CREATE INDEX idx_coupon_redemptions_coupon ON coupon_redemptions (coupon_id, status);
CREATE INDEX idx_coupon_redemptions_customer ON coupon_redemptions (coupon_id, customer_id, status);
