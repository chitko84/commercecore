CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description VARCHAR(1000),
    parent_category_id UUID REFERENCES categories (id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_categories_slug_canonical CHECK (
        slug = lower(slug) AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    ),
    CONSTRAINT ck_categories_not_own_parent CHECK (parent_category_id IS NULL OR parent_category_id <> id)
);

CREATE INDEX idx_categories_parent_active ON categories (parent_category_id, active);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    brand VARCHAR(120) NOT NULL,
    base_price NUMERIC(19,2) NOT NULL,
    sale_price NUMERIC(19,2),
    category_id UUID NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_products_description_not_blank CHECK (btrim(description) <> ''),
    CONSTRAINT ck_products_brand_not_blank CHECK (btrim(brand) <> ''),
    CONSTRAINT ck_products_slug_canonical CHECK (
        slug = lower(slug) AND slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'
    ),
    CONSTRAINT ck_products_sku_canonical CHECK (
        sku = upper(sku) AND sku ~ '^[A-Z0-9][A-Z0-9._-]*$'
    ),
    CONSTRAINT ck_products_base_price_positive CHECK (base_price > 0),
    CONSTRAINT ck_products_sale_price_valid CHECK (
        sale_price IS NULL OR (sale_price > 0 AND sale_price <= base_price)
    )
);

CREATE INDEX idx_products_category_active ON products (category_id, active);
CREATE INDEX idx_products_brand_lower ON products (lower(brand));
CREATE INDEX idx_products_public_newest ON products (active, created_at DESC);
CREATE INDEX idx_products_effective_price ON products ((COALESCE(sale_price, base_price)));

CREATE TABLE product_media (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    media_url VARCHAR(2048) NOT NULL,
    alt_text VARCHAR(255) NOT NULL,
    display_order INTEGER NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_media_display_order UNIQUE (product_id, display_order),
    CONSTRAINT ck_product_media_url_not_blank CHECK (btrim(media_url) <> ''),
    CONSTRAINT ck_product_media_alt_not_blank CHECK (btrim(alt_text) <> ''),
    CONSTRAINT ck_product_media_display_order_nonnegative CHECK (display_order >= 0)
);

CREATE INDEX idx_product_media_product_order ON product_media (product_id, display_order);
CREATE UNIQUE INDEX uq_product_media_one_primary ON product_media (product_id) WHERE is_primary;
