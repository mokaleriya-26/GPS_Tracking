-- ============================================================
-- V9__progressive_alert_penalty_system.sql
-- Progressive Alert Warning and Penalty Management System
-- ============================================================

-- 1. Extend alert_penalty_configs with progressive settings
ALTER TABLE alert_penalty_configs
    ADD COLUMN IF NOT EXISTS penalty_increment NUMERIC(10,2) NOT NULL DEFAULT 30.00,
    ADD COLUMN IF NOT EXISTS warning_threshold INTEGER NOT NULL DEFAULT 2,
    ADD COLUMN IF NOT EXISTS penalty_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- 2. Update / Insert standard alert penalty configurations with prompt base penalties
-- Base penalties:
-- Harsh Braking Alert: ₹50
-- Over-speeding Alert / Overspeed Alert: ₹100
-- Harsh Acceleration Alert: ₹50
-- GPS Disconnect Alert: ₹0
-- Night Driving Alert: ₹100
-- Ignition Alert: ₹0
-- Fatigue Alert: ₹200
-- Others: ₹0 until configured

UPDATE alert_penalty_configs SET default_fine_amount = 50.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type = 'Harsh Braking Alert';
UPDATE alert_penalty_configs SET default_fine_amount = 100.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type IN ('Overspeed Alert', 'Over-speeding Alert', 'Over-Speeding Alert');
UPDATE alert_penalty_configs SET default_fine_amount = 50.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type = 'Harsh Acceleration Alert';
UPDATE alert_penalty_configs SET default_fine_amount = 0.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type = 'GPS Disconnect Alert';
UPDATE alert_penalty_configs SET default_fine_amount = 100.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type = 'Night Driving Alert';
UPDATE alert_penalty_configs SET default_fine_amount = 0.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type = 'Ignition Alert';
UPDATE alert_penalty_configs SET default_fine_amount = 200.00, penalty_increment = 30.00, warning_threshold = 2 WHERE alert_type = 'Fatigue Alert';

-- Insert Over-speeding Alert if not already present
INSERT INTO alert_penalty_configs (alert_type, weight, default_fine_amount, penalty_increment, warning_threshold, penalty_enabled, description, is_active, updated_by)
VALUES ('Over-speeding Alert', 5.0, 100.00, 30.00, 2, TRUE, 'Progressive penalty for over-speeding alerts', TRUE, 'SYSTEM')
ON CONFLICT (alert_type) DO UPDATE SET default_fine_amount = 100.00, penalty_increment = 30.00, warning_threshold = 2;

-- 3. Extend driver_penalties table to store full progressive penalty details
ALTER TABLE driver_penalties
    ADD COLUMN IF NOT EXISTS alert_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS occurrence_number INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS classification VARCHAR(30) NOT NULL DEFAULT 'WARNING',
    ADD COLUMN IF NOT EXISTS base_penalty NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS penalty_increment NUMERIC(10,2) NOT NULL DEFAULT 30.00,
    ADD COLUMN IF NOT EXISTS calculation_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS notification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

-- 4. Unique partial index to ensure idempotent penalty processing per alert and alert_type
CREATE UNIQUE INDEX IF NOT EXISTS uq_driver_penalties_alert_and_type
    ON driver_penalties (alert_id, alert_type)
    WHERE alert_id IS NOT NULL AND alert_type IS NOT NULL;

-- 5. Performance and counting indexes
CREATE INDEX IF NOT EXISTS idx_driver_penalties_driver_alert_type
    ON driver_penalties (driver_id, alert_type, status);

CREATE INDEX IF NOT EXISTS idx_driver_penalties_classification
    ON driver_penalties (classification);

CREATE INDEX IF NOT EXISTS idx_driver_penalties_calc_time
    ON driver_penalties (calculation_timestamp DESC);
