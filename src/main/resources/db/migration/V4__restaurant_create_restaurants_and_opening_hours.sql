CREATE TABLE restaurants (
    id                        UUID             PRIMARY KEY,
    owner_id                  UUID             NOT NULL,   -- cross-module ref to users: no FK
    name                      VARCHAR(120)     NOT NULL,
    description               VARCHAR(1000),
    cuisines                  TEXT[]           NOT NULL,   -- lower-case kebab-case, e.g. {north-indian,chinese}
    address_line              VARCHAR(300)     NOT NULL,
    city                      VARCHAR(100)     NOT NULL,
    latitude                  DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude                 DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    serviceable_radius_km     DOUBLE PRECISION NOT NULL CHECK (serviceable_radius_km > 0 AND serviceable_radius_km <= 30),
    time_zone                 VARCHAR(40)      NOT NULL,   -- IANA zone for opening hours, e.g. Asia/Kolkata
    delivery_fee_amount       NUMERIC(12, 2)   NOT NULL CHECK (delivery_fee_amount >= 0),
    delivery_fee_currency     CHAR(3)          NOT NULL,
    packaging_charge_amount   NUMERIC(12, 2)   NOT NULL CHECK (packaging_charge_amount >= 0),
    packaging_charge_currency CHAR(3)          NOT NULL,
    status                    VARCHAR(20)      NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'SUSPENDED')),
    temporarily_closed        BOOLEAN          NOT NULL,   -- the owner's open/close switch
    rating                    NUMERIC(2, 1)    CHECK (rating BETWEEN 0 AND 5),   -- null until rated
    created_at                TIMESTAMPTZ      NOT NULL,
    updated_at                TIMESTAMPTZ      NOT NULL
);
CREATE INDEX idx_restaurants_owner ON restaurants (owner_id);
CREATE INDEX idx_restaurants_status ON restaurants (status);
CREATE INDEX idx_restaurants_cuisines ON restaurants USING gin (cuisines);

CREATE TABLE opening_hours (
    id             UUID        PRIMARY KEY,
    restaurant_id  UUID        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    day_of_week    SMALLINT    NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),   -- ISO: 1 = Monday
    opens_at       TIME        NOT NULL,
    closes_at      TIME        NOT NULL,   -- earlier than opens_at means the slot runs past midnight
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CHECK (opens_at <> closes_at)
);
CREATE INDEX idx_opening_hours_restaurant ON opening_hours (restaurant_id, day_of_week);
