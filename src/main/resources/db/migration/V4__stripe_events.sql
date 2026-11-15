-- =============================================================================
-- Migration: V4__stripe_events.sql
-- Description: Idempotent Stripe Webhook Event Store
-- =============================================================================

CREATE TABLE IF NOT EXISTS stripe_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSED',
    payload TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'STRIPE_WEBHOOK',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_stripe_events_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_stripe_events_event_id ON stripe_events (event_id);
CREATE INDEX IF NOT EXISTS idx_stripe_events_event_type ON stripe_events (event_type);
