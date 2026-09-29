CREATE TABLE payments (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES orders(id), customer_id UUID NOT NULL REFERENCES users(id),
 payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('CARD','ONLINE_BANKING','E_WALLET')), status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','AUTHORIZED','PAID','FAILED','CANCELLED','REFUNDED','PARTIALLY_REFUNDED')),
 amount NUMERIC(19,2) NOT NULL CHECK (amount > 0), currency_code VARCHAR(3) NOT NULL DEFAULT 'USD', idempotency_key VARCHAR(160) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL,
 provider_name VARCHAR(64) NOT NULL, provider_payment_reference VARCHAR(160), provider_idempotency_key VARCHAR(160) NOT NULL, failure_code VARCHAR(64), failure_message VARCHAR(240), retryable_failure BOOLEAN NOT NULL DEFAULT FALSE, reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_payment_key UNIQUE(order_id,idempotency_key), CONSTRAINT uq_provider_key UNIQUE(provider_idempotency_key)
);
CREATE INDEX ix_payments_customer ON payments(customer_id,created_at DESC);
CREATE UNIQUE INDEX uq_one_paid_payment ON payments(order_id) WHERE status IN ('AUTHORIZED','PAID','PARTIALLY_REFUNDED','REFUNDED');
CREATE TABLE payment_events (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), payment_id UUID NOT NULL REFERENCES payments(id), from_status VARCHAR(24), to_status VARCHAR(24), event_type VARCHAR(40) NOT NULL,
 provider_reference VARCHAR(160), failure_code VARCHAR(64), note VARCHAR(240), occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_events_payment ON payment_events(payment_id,occurred_at,id);
CREATE TABLE payment_refunds (
 id UUID PRIMARY KEY DEFAULT gen_random_uuid(), payment_id UUID NOT NULL REFERENCES payments(id), amount NUMERIC(19,2) NOT NULL CHECK(amount>0), status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','COMPLETED','FAILED')),
 idempotency_key VARCHAR(160) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL, provider_refund_reference VARCHAR(160), failure_code VARCHAR(64), failure_message VARCHAR(240), reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE,
 created_by UUID REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT uq_refund_key UNIQUE(payment_id,idempotency_key)
);
CREATE INDEX ix_refunds_payment ON payment_refunds(payment_id,created_at);
