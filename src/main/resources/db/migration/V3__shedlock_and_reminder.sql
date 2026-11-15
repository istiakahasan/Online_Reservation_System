-- =============================================================================
-- Migration: V3__shedlock_and_reminder.sql
-- Description: ShedLock distributed lock table and reminder_sent_at column
-- =============================================================================

-- 1. Table: shedlock (Distributed Lock Coordination)
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- 2. Add reminder_sent_at column to reservations for idempotent reminders
ALTER TABLE reservations 
    ADD COLUMN IF NOT EXISTS reminder_sent_at TIMESTAMPTZ;

-- 3. Partial Index for reminder job lookup (CONFIRMED without prior reminder)
CREATE INDEX IF NOT EXISTS idx_reservations_reminders 
    ON reservations (status, start_time) 
    WHERE status = 'CONFIRMED' AND reminder_sent_at IS NULL;

