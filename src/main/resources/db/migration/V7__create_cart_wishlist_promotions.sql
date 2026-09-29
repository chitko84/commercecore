CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    promotion_type VARCHAR(24) NOT NULL,
    promotion_scope VARCHAR(24) NOT NULL,
    discount_value NUMERIC(19,4),
    minimum_spend NUMERIC(19,2) NOT NULL DEFAULT 0,
    maximum_discount NUMERIC(19,2),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    total_usage_limit BIGINT,
    per_user_usage_limit BIGINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    target_product_id UUID REFERENCES products(id) ON DELETE RESTRICT,
    target_category_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_promotion_code CHECK (code = upper(btrim(code)) AND code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_promotion_type CHECK (promotion_type IN ('PERCENTAGE','FIXED_AMOUNT','FREE_SHIPPING')),
    CONSTRAINT ck_promotion_scope CHECK (promotion_scope IN ('CART','PRODUCT','CATEGORY')),
    CONSTRAINT ck_promotion_discount CHECK (
        (promotion_type = 'PERCENTAGE' AND discount_value > 0 AND discount_value <= 100) OR
        (promotion_type = 'FIXED_AMOUNT' AND discount_value > 0) OR
        (promotion_type = 'FREE_SHIPPING' AND discount_value IS NULL)
    ),
    CONSTRAINT ck_promotion_money CHECK (minimum_spend >= 0 AND (maximum_discount IS NULL OR maximum_discount > 0)),
    CONSTRAINT ck_promotion_window CHECK (starts_at < ends_at),
    CONSTRAINT ck_promotion_limits CHECK ((total_usage_limit IS NULL OR total_usage_limit > 0) AND (per_user_usage_limit IS NULL OR per_user_usage_limit > 0)),
    CONSTRAINT ck_promotion_target CHECK (
        (promotion_scope = 'CART' AND target_product_id IS NULL AND target_category_id IS NULL) OR
        (promotion_scope = 'PRODUCT' AND target_product_id IS NOT NULL AND target_category_id IS NULL) OR
        (promotion_scope = 'CATEGORY' AND target_product_id IS NULL AND target_category_id IS NOT NULL)
    )
);
CREATE INDEX idx_promotions_active_window ON promotions(active, starts_at, ends_at);
CREATE INDEX idx_promotions_target_product ON promotions(target_product_id) WHERE target_product_id IS NOT NULL;
CREATE INDEX idx_promotions_target_category ON promotions(target_category_id) WHERE target_category_id IS NOT NULL;

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    applied_promotion_id UUID REFERENCES promotions(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id UUID REFERENCES product_variants(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);
CREATE UNIQUE INDEX uq_cart_item_direct_product ON cart_items(cart_id, product_id) WHERE variant_id IS NULL;
CREATE UNIQUE INDEX uq_cart_item_variant ON cart_items(cart_id, variant_id) WHERE variant_id IS NOT NULL;
CREATE INDEX idx_cart_items_cart ON cart_items(cart_id, created_at);

CREATE TABLE wishlist_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    variant_id UUID REFERENCES product_variants(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_wishlist_direct_product ON wishlist_items(user_id, product_id) WHERE variant_id IS NULL;
CREATE UNIQUE INDEX uq_wishlist_variant ON wishlist_items(user_id, variant_id) WHERE variant_id IS NOT NULL;
CREATE INDEX idx_wishlist_user ON wishlist_items(user_id, created_at);

CREATE TABLE promotion_redemptions (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reference_key VARCHAR(160) NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_promotion_redemption_reference UNIQUE(promotion_id, reference_key),
    CONSTRAINT ck_promotion_redemption_reference CHECK (length(btrim(reference_key)) BETWEEN 1 AND 160)
);
CREATE INDEX idx_redemptions_promotion ON promotion_redemptions(promotion_id);
CREATE INDEX idx_redemptions_promotion_user ON promotion_redemptions(promotion_id, user_id);
