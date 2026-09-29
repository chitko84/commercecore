CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL UNIQUE,
    price_override NUMERIC(19,2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_variant_sku_canonical CHECK (sku = upper(btrim(sku)) AND sku ~ '^[A-Z0-9][A-Z0-9._-]*$'),
    CONSTRAINT ck_variant_price_positive CHECK (price_override IS NULL OR price_override > 0)
);

CREATE INDEX idx_product_variants_product_active ON product_variants(product_id, active);

CREATE TABLE variant_attributes (
    id UUID PRIMARY KEY,
    variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    attribute_name VARCHAR(80) NOT NULL,
    attribute_value VARCHAR(200) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_variant_attribute_name CHECK (length(btrim(attribute_name)) BETWEEN 1 AND 80),
    CONSTRAINT ck_variant_attribute_value CHECK (length(btrim(attribute_value)) BETWEEN 1 AND 200),
    CONSTRAINT ck_variant_attribute_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_variant_attribute_name_ci ON variant_attributes(variant_id, lower(attribute_name));
CREATE INDEX idx_variant_attributes_order ON variant_attributes(variant_id, display_order, attribute_name);

CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    product_id UUID REFERENCES products(id) ON DELETE CASCADE,
    variant_id UUID REFERENCES product_variants(id) ON DELETE CASCADE,
    total_quantity BIGINT NOT NULL DEFAULT 0,
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    sold_quantity BIGINT NOT NULL DEFAULT 0,
    low_stock_threshold BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_exactly_one_owner CHECK ((product_id IS NULL) <> (variant_id IS NULL)),
    CONSTRAINT ck_inventory_nonnegative CHECK (
        total_quantity >= 0 AND reserved_quantity >= 0 AND sold_quantity >= 0 AND low_stock_threshold >= 0
    ),
    CONSTRAINT ck_inventory_available_nonnegative CHECK (total_quantity - reserved_quantity - sold_quantity >= 0)
);

CREATE UNIQUE INDEX uq_inventory_product ON inventory(product_id) WHERE product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_inventory_variant ON inventory(variant_id) WHERE variant_id IS NOT NULL;

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    quantity BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference_key VARCHAR(160) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_inventory_reservation_reference UNIQUE(inventory_id, reference_key),
    CONSTRAINT ck_inventory_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservation_status CHECK (status IN ('ACTIVE','RELEASED','CONFIRMED','EXPIRED')),
    CONSTRAINT ck_inventory_reservation_reference CHECK (length(btrim(reference_key)) BETWEEN 1 AND 160)
);

CREATE INDEX idx_inventory_reservations_active ON inventory_reservations(inventory_id, status) WHERE status = 'ACTIVE';

CREATE TABLE inventory_history (
    id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL REFERENCES inventory(id) ON DELETE CASCADE,
    movement_type VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    before_total BIGINT NOT NULL,
    before_reserved BIGINT NOT NULL,
    before_sold BIGINT NOT NULL,
    after_total BIGINT NOT NULL,
    after_reserved BIGINT NOT NULL,
    after_sold BIGINT NOT NULL,
    reference_key VARCHAR(160),
    reason VARCHAR(500),
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_history_type CHECK (movement_type IN (
        'RESTOCK','ADJUSTMENT_INCREASE','ADJUSTMENT_DECREASE','RESERVATION','RELEASE','SALE','RESERVATION_EXPIRED'
    ))
);

CREATE INDEX idx_inventory_history_timeline ON inventory_history(inventory_id, occurred_at, id);
