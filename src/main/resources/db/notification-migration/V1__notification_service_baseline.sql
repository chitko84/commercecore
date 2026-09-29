CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    resource_type VARCHAR(32),
    resource_id VARCHAR(128),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ns_notifications_recipient ON notifications(recipient_id,created_at DESC);
CREATE INDEX idx_ns_notifications_unread ON notifications(recipient_id) WHERE read_at IS NULL;
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    consumer_name VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
