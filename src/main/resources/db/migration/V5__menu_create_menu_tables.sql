CREATE TABLE menu_categories (
    id             UUID         PRIMARY KEY,
    restaurant_id  UUID         NOT NULL,   -- cross-module ref to restaurants: no FK
    name           VARCHAR(80)  NOT NULL,
    position       INTEGER      NOT NULL,   -- display order within the restaurant
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_menu_categories_restaurant ON menu_categories (restaurant_id, position);

CREATE TABLE menu_items (
    id              UUID           PRIMARY KEY,
    restaurant_id   UUID           NOT NULL,   -- cross-module ref to restaurants: no FK
    category_id     UUID           NOT NULL REFERENCES menu_categories (id),
    name            VARCHAR(120)   NOT NULL,
    description     VARCHAR(1000),
    price_amount    NUMERIC(12, 2) NOT NULL CHECK (price_amount >= 0),   -- base price; a chosen variant replaces it
    price_currency  CHAR(3)        NOT NULL,
    vegetarian      BOOLEAN        NOT NULL,
    available       BOOLEAN        NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_menu_items_restaurant ON menu_items (restaurant_id);
CREATE INDEX idx_menu_items_category ON menu_items (category_id);

CREATE TABLE item_variants (
    id              UUID           PRIMARY KEY,
    menu_item_id    UUID           NOT NULL REFERENCES menu_items (id) ON DELETE CASCADE,
    name            VARCHAR(60)    NOT NULL,   -- e.g. Half, Full, Large
    price_amount    NUMERIC(12, 2) NOT NULL CHECK (price_amount >= 0),   -- replaces the item's base price
    price_currency  CHAR(3)        NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_item_variants_item ON item_variants (menu_item_id);

CREATE TABLE item_add_ons (
    id              UUID           PRIMARY KEY,
    menu_item_id    UUID           NOT NULL REFERENCES menu_items (id) ON DELETE CASCADE,
    name            VARCHAR(60)    NOT NULL,   -- e.g. Extra cheese
    price_amount    NUMERIC(12, 2) NOT NULL CHECK (price_amount >= 0),   -- added to the unit price
    price_currency  CHAR(3)        NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_item_add_ons_item ON item_add_ons (menu_item_id);
