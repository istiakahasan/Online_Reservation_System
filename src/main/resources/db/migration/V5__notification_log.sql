-- =============================================================================
-- Migration: V5__notification_log.sql
-- Description: Audit and delivery tracking log for outgoing notifications
-- =============================================================================

CREATE TABLE IF NOT EXISTS notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID NOT NULL,
    channel VARCHAR(50) NOT NULL,
    template VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notification_log_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notification_log_reservation ON notification_log (reservation_id);
CREATE INDEX IF NOT EXISTS idx_notification_log_status ON notification_log (status);
CREATE INDEX IF NOT EXISTS idx_notification_log_sent_at ON notification_log (sent_at);
