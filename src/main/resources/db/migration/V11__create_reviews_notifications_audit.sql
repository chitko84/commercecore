CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL,
    title VARCHAR(150),
    body VARCHAR(2000),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reviews_customer_product UNIQUE(customer_id, product_id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
    CONSTRAINT ck_reviews_title CHECK (title IS NULL OR btrim(title) <> ''),
    CONSTRAINT ck_reviews_body CHECK (body IS NULL OR btrim(body) <> ''),
    CONSTRAINT ck_reviews_content CHECK (title IS NOT NULL OR body IS NOT NULL)
);
CREATE INDEX idx_reviews_product_visible ON reviews(product_id, status, created_at DESC);
CREATE INDEX idx_reviews_customer ON reviews(customer_id, created_at DESC);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    resource_type VARCHAR(32),
    resource_id VARCHAR(64),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'ORDER_PLACED', 'ORDER_CONFIRMED', 'ORDER_PROCESSING', 'ORDER_SHIPPED',
        'ORDER_DELIVERED', 'ORDER_CANCELLED', 'REFUND_COMPLETED'
    )),
    CONSTRAINT ck_notifications_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_notifications_body CHECK (btrim(body) <> '')
);
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(recipient_id) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    details VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_logs_action CHECK (btrim(action) <> ''),
    CONSTRAINT ck_audit_logs_category CHECK (category IN ('ACCOUNT', 'REVIEW', 'ORDER', 'PAYMENT', 'AUTH', 'SYSTEM')),
    CONSTRAINT ck_audit_logs_resource_type CHECK (btrim(resource_type) <> ''),
    CONSTRAINT ck_audit_logs_resource_id CHECK (btrim(resource_id) <> '')
);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_user_id, created_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action, created_at DESC);
