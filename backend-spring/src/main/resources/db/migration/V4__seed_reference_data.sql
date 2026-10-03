-- ============================================================
-- V4__seed_reference_data.sql
-- System configuration seed data
-- ============================================================

-- Alert type severity mapping reference (informational)
-- These are the alert types recognized by the system.
-- Overspeed and GPS Disconnect are CRITICAL; others are WARNING.

-- This table stores system-wide configuration
CREATE TABLE IF NOT EXISTS system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE system_config IS 'System-wide configuration key-value store';

INSERT INTO system_config (key, value, description) VALUES
    ('app.version', '1.0.0', 'Application version'),
    ('alert.overspeed.threshold_kmph', '80', 'Default speed threshold for overspeed alerts in km/h'),
    ('alert.harsh_braking.threshold_mps2', '-3.5', 'Deceleration threshold for harsh braking alert in m/s²'),
    ('alert.harsh_acceleration.threshold_mps2', '3.0', 'Acceleration threshold for harsh acceleration alert in m/s²'),
    ('alert.night_driving.start_hour', '22', 'Hour (24h) after which night driving alert applies'),
    ('alert.night_driving.end_hour', '5', 'Hour (24h) before which night driving alert applies'),
    ('alert.fatigue.max_continuous_driving_min', '240', 'Max continuous driving minutes before fatigue alert'),
    ('notification.silence_historical', 'true', 'Silence notifications for historically imported data'),
    ('safety_score.overspeed_penalty', '5', 'Safety score penalty per overspeed event'),
    ('safety_score.harsh_braking_penalty', '4', 'Safety score penalty per harsh braking event'),
    ('safety_score.harsh_acceleration_penalty', '4', 'Safety score penalty per harsh acceleration event'),
    ('safety_score.night_driving_penalty_per_hour', '3', 'Safety score penalty per hour of night driving'),
    ('safety_score.fatigue_penalty', '8', 'Safety score penalty per fatigue event'),
    ('safety_score.clean_trip_bonus', '1', 'Safety score bonus per incident-free trip'),
    ('data.import.initial_completed', 'false', 'Whether initial CSV data import has been completed')
ON CONFLICT (key) DO NOTHING;
