-- Phase 8 compliance:
-- 1. Reviews move from VISIBLE/HIDDEN to the moderation workflow
--    PENDING -> APPROVED / REJECTED. New and edited reviews are PENDING;
--    only APPROVED reviews are public.
-- 2. Notification types grow to the full customer lifecycle set plus the
--    account/auth events (WELCOME, PASSWORD_CHANGED, PASSWORD_RESET,
--    PAYMENT_SUCCEEDED, definitive PAYMENT_FAILED). ORDER_PLACED is renamed
--    ORDER_CREATED.
-- 3. Audit categories grow to cover catalog, category, variant, inventory
--    and promotion actions.
-- 4. A new admin_alerts table carries transition-based, deduplicated
--    operational alerts (LOW_STOCK_ALERT, FAILED_PAYMENT_ALERT).

ALTER TABLE reviews DROP CONSTRAINT ck_reviews_status;
UPDATE reviews SET status = 'APPROVED' WHERE status = 'VISIBLE';
UPDATE reviews SET status = 'REJECTED' WHERE status = 'HIDDEN';
ALTER TABLE reviews
    ADD CONSTRAINT ck_reviews_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE notifications DROP CONSTRAINT ck_notifications_type;
UPDATE notifications SET type = 'ORDER_CREATED' WHERE type = 'ORDER_PLACED';
ALTER TABLE notifications
    ADD CONSTRAINT ck_notifications_type CHECK (type IN (
        'WELCOME', 'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'ORDER_CREATED', 'ORDER_CONFIRMED', 'ORDER_PROCESSING', 'ORDER_SHIPPED',
        'ORDER_DELIVERED', 'ORDER_CANCELLED',
        'PAYMENT_SUCCEEDED', 'PAYMENT_FAILED', 'REFUND_COMPLETED'
    ));

ALTER TABLE audit_logs DROP CONSTRAINT ck_audit_logs_category;
ALTER TABLE audit_logs
    ADD CONSTRAINT ck_audit_logs_category CHECK (category IN (
        'ACCOUNT', 'REVIEW', 'ORDER', 'PAYMENT', 'AUTH', 'SYSTEM',
        'CATALOG', 'CATEGORY', 'VARIANT', 'INVENTORY', 'PROMOTION'
    ));

CREATE TABLE admin_alerts (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    dedup_key VARCHAR(255) NOT NULL UNIQUE,
    acknowledged_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_alerts_type CHECK (type IN ('LOW_STOCK_ALERT', 'FAILED_PAYMENT_ALERT')),
    CONSTRAINT ck_admin_alerts_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_admin_alerts_body CHECK (btrim(body) <> ''),
    CONSTRAINT ck_admin_alerts_resource_type CHECK (btrim(resource_type) <> ''),
    CONSTRAINT ck_admin_alerts_resource_id CHECK (btrim(resource_id) <> '')
);
CREATE INDEX idx_admin_alerts_created ON admin_alerts(created_at DESC);
CREATE INDEX idx_admin_alerts_unacknowledged ON admin_alerts(acknowledged_at) WHERE acknowledged_at IS NULL;