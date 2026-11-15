-- =============================================================================
-- Migration: V1__init.sql
-- Description: Core schema for Multi-Tenant Online Reservation System
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================================================
-- 1. Table: businesses (Tenants)
-- =============================================================================
CREATE TABLE businesses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_businesses_slug UNIQUE (slug)
);

-- =============================================================================
-- 2. Table: users (Customers, Staff, Administrators)
-- =============================================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_users_business FOREIGN KEY (business_id) 
        REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT uq_users_business_email UNIQUE (business_id, email),
    CONSTRAINT chk_users_role CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN'))
);

-- =============================================================================
-- 3. Table: refresh_tokens (Authentication Sessions)
-- =============================================================================
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(512) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token)
);

-- =============================================================================
-- 4. Table: services (Bookable Offerings)
-- =============================================================================
CREATE TABLE services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    duration_minutes INT NOT NULL,
    buffer_minutes INT NOT NULL DEFAULT 0,
    price_amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_services_business FOREIGN KEY (business_id) 
        REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT chk_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_services_buffer CHECK (buffer_minutes >= 0),
    CONSTRAINT chk_services_price CHECK (price_amount >= 0)
);

-- =============================================================================
-- 5. Table: staff_working_hours (Weekly Shift Schedules)
-- =============================================================================
CREATE TABLE staff_working_hours (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_staff_working_hours_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_working_hours_staff FOREIGN KEY (staff_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_staff_working_hours_time CHECK (end_time > start_time)
);

-- =============================================================================
-- 6. Table: staff_time_off (Exceptions & Holidays)
-- =============================================================================
CREATE TABLE staff_time_off (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_staff_time_off_business FOREIGN KEY (business_id)
        REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_time_off_staff FOREIGN KEY (staff_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_staff_time_off_range CHECK (end_time > start_time)
);

-- =============================================================================
-- 7. Table: reservations (Booking Transactions)
-- =============================================================================
CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    service_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    hold_expires_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_by VARCHAR(255) NOT NULL DEFAULT 'SYSTEM',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservations_business FOREIGN KEY (business_id) 
        REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_reservations_customer FOREIGN KEY (customer_id) 
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_service FOREIGN KEY (service_id) 
        REFERENCES services (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservations_staff FOREIGN KEY (staff_id) 
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_reservations_time_range CHECK (end_time > start_time),
    CONSTRAINT chk_reservations_price CHECK (total_price >= 0),
    CONSTRAINT chk_reservations_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')
    )
);

-- =============================================================================
-- 8. Indexes & Performance Optimization
-- =============================================================================

-- Businesses
CREATE UNIQUE INDEX idx_businesses_slug ON businesses (slug);

-- Users
CREATE INDEX idx_users_business_id ON users (business_id);
CREATE INDEX idx_users_business_role ON users (business_id, role);

-- Refresh Tokens
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- Services
CREATE INDEX idx_services_business_active ON services (business_id, is_active);

-- Staff Scheduling & Time-Off
CREATE INDEX idx_staff_working_hours ON staff_working_hours (staff_id, day_of_week);
CREATE INDEX idx_staff_time_off ON staff_time_off (staff_id, start_time, end_time);

-- Reservations: Operational and Calendar Lookups
CREATE INDEX idx_reservations_business_start_time ON reservations (business_id, start_time);
CREATE INDEX idx_reservations_customer_history ON reservations (customer_id, start_time);
CREATE INDEX idx_reservations_staff_schedule ON reservations (staff_id, start_time);
CREATE INDEX idx_reservations_business_status ON reservations (business_id, status);

-- Partial Index for Background Hold Expiration Sweeper
CREATE INDEX idx_reservations_pending_expiration ON reservations (hold_expires_at) 
    WHERE status = 'PENDING';

-- Partial Unique Index: Double-Booking Prevention
CREATE UNIQUE INDEX uq_reservations_staff_active_slot 
    ON reservations (staff_id, start_time) 
    WHERE status IN ('PENDING', 'CONFIRMED');
