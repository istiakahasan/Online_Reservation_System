-- =============================================================================
-- Migration: V2__cancellation_and_payments.sql
-- Description: Business cancellation config, payments, and notifications schema
-- =============================================================================

-- 1. Business cancellation configuration
ALTER TABLE businesses 
    ADD COLUMN IF NOT EXISTS cancellation_window_hours INT NOT NULL DEFAULT 24;

ALTER TABLE businesses 
    ADD COLUMN IF NOT EXISTS refund_policy_type VARCHAR(50) NOT NULL DEFAULT 'FULL';

-- 2. Table: payments
CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    reservation_id UUID NOT NULL UNIQUE,
    stripe_payment_intent_id VARCHAR(255) UNIQUE,
    stripe_refund_id VARCHAR(255),
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_payments_business FOREIGN KEY (business_id) 
        REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_reservation FOREIGN KEY (reservation_id) 
        REFERENCES reservations (id) ON DELETE CASCADE
);

-- 3. Table: notifications
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notifications_business FOREIGN KEY (business_id) 
        REFERENCES businesses (id) ON DELETE CASCADE
);

-- 4. Indexes
CREATE INDEX IF NOT EXISTS idx_payments_business_id ON payments (business_id);
CREATE INDEX IF NOT EXISTS idx_payments_reservation_id ON payments (reservation_id);
CREATE INDEX IF NOT EXISTS idx_payments_stripe_pi ON payments (stripe_payment_intent_id);
CREATE INDEX IF NOT EXISTS idx_notifications_business_id ON notifications (business_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications (status);
