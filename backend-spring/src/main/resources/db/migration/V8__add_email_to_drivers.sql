-- ============================================================
-- V8__add_email_to_drivers.sql
-- Add email column to drivers table for dynamic alert notifications
-- ============================================================

ALTER TABLE drivers ADD COLUMN IF NOT EXISTS email VARCHAR(150);

COMMENT ON COLUMN drivers.email IS 'Driver contact email for dynamic alert notifications';

CREATE INDEX IF NOT EXISTS idx_drivers_email ON drivers(email);

-- Populate email for existing test driver Merin Joys if present
UPDATE drivers SET email = 'merinjoys28@gmail.com' WHERE code = 'DRV089' AND email IS NULL;
