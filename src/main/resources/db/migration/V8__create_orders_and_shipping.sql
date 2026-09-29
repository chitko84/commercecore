CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(32) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL,
    checkout_idempotency_key VARCHAR(160) NOT NULL,
    checkout_request_fingerprint VARCHAR(64) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    merchandise_discount NUMERIC(19,2) NOT NULL,
    discounted_merchandise_total NUMERIC(19,2) NOT NULL,
    shipping_fee_before_discount NUMERIC(19,2) NOT NULL,
    shipping_discount NUMERIC(19,2) NOT NULL,
    shipping_fee NUMERIC(19,2) NOT NULL,
    tax_amount NUMERIC(19,2) NOT NULL,
    grand_total NUMERIC(19,2) NOT NULL,
    promotion_id UUID REFERENCES promotions(id) ON DELETE SET NULL,
    promotion_code VARCHAR(64),
    promotion_name VARCHAR(160),
    promotion_type VARCHAR(24),
    promotion_discount NUMERIC(19,2),
    shipping_service_level VARCHAR(16) NOT NULL,
    shipping_estimated_days INTEGER NOT NULL,
    reservation_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_checkout_key UNIQUE(customer_id, checkout_idempotency_key),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    CONSTRAINT ck_order_shipping_level CHECK (shipping_service_level IN ('STANDARD','EXPRESS')),
    CONSTRAINT ck_order_shipping_days CHECK (shipping_estimated_days > 0),
    CONSTRAINT ck_order_money_nonnegative CHECK (subtotal >= 0 AND merchandise_discount >= 0 AND discounted_merchandise_total >= 0 AND shipping_fee_before_discount >= 0 AND shipping_discount >= 0 AND shipping_fee >= 0 AND tax_amount >= 0 AND grand_total >= 0),
    CONSTRAINT ck_order_money_consistent CHECK (discounted_merchandise_total = subtotal - merchandise_discount AND shipping_fee = shipping_fee_before_discount - shipping_discount AND grand_total = discounted_merchandise_total + shipping_fee + tax_amount),
    CONSTRAINT ck_order_promotion_snapshot CHECK ((promotion_id IS NULL AND promotion_code IS NULL AND promotion_name IS NULL AND promotion_type IS NULL AND promotion_discount IS NULL) OR (promotion_id IS NOT NULL AND promotion_code IS NOT NULL AND promotion_name IS NOT NULL AND promotion_type IS NOT NULL AND promotion_discount IS NOT NULL)),
    CONSTRAINT ck_order_idempotency CHECK (length(btrim(checkout_idempotency_key)) BETWEEN 8 AND 160),
    CONSTRAINT ck_order_fingerprint CHECK (checkout_request_fingerprint ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_orders_customer_created ON orders(customer_id, created_at DESC);
CREATE INDEX idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX idx_orders_expiry ON orders(reservation_expires_at) WHERE status = 'PENDING';

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    source_product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    source_variant_id UUID REFERENCES product_variants(id) ON DELETE SET NULL,
    inventory_reservation_id UUID UNIQUE REFERENCES inventory_reservations(id) ON DELETE RESTRICT,
    product_name VARCHAR(200) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    line_subtotal NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_order_item_money CHECK (unit_price > 0 AND line_subtotal = unit_price * quantity)
);
CREATE INDEX idx_order_items_order ON order_items(order_id, created_at, id);

CREATE TABLE order_item_attributes (
    id UUID PRIMARY KEY,
    order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    attribute_name VARCHAR(80) NOT NULL,
    attribute_value VARCHAR(200) NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_item_attribute UNIQUE(order_item_id, attribute_name),
    CONSTRAINT ck_order_item_attribute CHECK (length(btrim(attribute_name)) BETWEEN 1 AND 80 AND length(btrim(attribute_value)) BETWEEN 1 AND 200 AND display_order >= 0)
);

CREATE TABLE order_addresses (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    address_type VARCHAR(16) NOT NULL,
    source_address_id UUID REFERENCES addresses(id) ON DELETE SET NULL,
    recipient_name VARCHAR(150) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    address_line_1 VARCHAR(200) NOT NULL,
    address_line_2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    state_region VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    CONSTRAINT uq_order_address_type UNIQUE(order_id, address_type),
    CONSTRAINT ck_order_address_type CHECK (address_type IN ('SHIPPING','BILLING')),
    CONSTRAINT ck_order_address_country CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reason VARCHAR(500),
    reference_key VARCHAR(160),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_history_from CHECK (from_status IS NULL OR from_status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED')),
    CONSTRAINT ck_order_history_to CHECK (to_status IN ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED'))
);
CREATE INDEX idx_order_status_history_timeline ON order_status_history(order_id, occurred_at, id);

CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    service_level VARCHAR(16) NOT NULL,
    carrier VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    estimated_delivery_at TIMESTAMPTZ,
    shipped_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipment_level CHECK (service_level IN ('STANDARD','EXPRESS')),
    CONSTRAINT ck_shipment_carrier CHECK (length(btrim(carrier)) BETWEEN 1 AND 100),
    CONSTRAINT ck_shipment_tracking CHECK (tracking_number ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{2,99}$')
);
CREATE UNIQUE INDEX uq_shipment_carrier_tracking ON shipments(carrier, tracking_number);

CREATE TABLE shipment_tracking_events (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    tracking_status VARCHAR(24) NOT NULL,
    location VARCHAR(160),
    note VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_tracking_status CHECK (tracking_status IN ('LABEL_CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','EXCEPTION'))
);
CREATE INDEX idx_tracking_event_timeline ON shipment_tracking_events(shipment_id, occurred_at, id);
