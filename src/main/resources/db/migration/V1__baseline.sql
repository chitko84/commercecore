-- V1__baseline.sql
--
-- Phase 1 (S001B) harmless baseline migration. Its only purpose is to
-- prove Flyway is wired up correctly against PostgreSQL before any
-- CommerceCore business/domain tables exist.
--
-- It intentionally creates NO business/domain tables. It records a
-- single marker row in a small technical bookkeeping table so future
-- sessions/agents can confirm Flyway has successfully run at least
-- once against a given database.
--
-- Do not add business tables to this file. Add new, separately
-- numbered migrations (V2__..., V3__..., etc.) for real schema changes.

CREATE TABLE IF NOT EXISTS schema_bootstrap (
    id          SMALLINT PRIMARY KEY DEFAULT 1,
    initialized_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    note        VARCHAR(200) NOT NULL,
    CONSTRAINT schema_bootstrap_singleton CHECK (id = 1)
);

INSERT INTO schema_bootstrap (id, note)
VALUES (1, 'CommerceCore Flyway baseline (Phase 1 / S001B) -- no business tables yet')
ON CONFLICT (id) DO NOTHING;
