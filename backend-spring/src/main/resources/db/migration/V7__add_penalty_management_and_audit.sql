-- ============================================================
-- V7__add_penalty_management_and_audit.sql
-- Alert penalty configuration, driver penalty management, and audit log
-- ============================================================

CREATE TABLE IF NOT EXISTS alert_penalty_configs (
    id                  BIGSERIAL PRIMARY KEY,
    alert_type          VARCHAR(100) NOT NULL UNIQUE,
    weight              NUMERIC(6,2) NOT NULL DEFAULT 5.0,
    default_fine_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    description         VARCHAR(255),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by          VARCHAR(100) DEFAULT 'SYSTEM',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE alert_penalty_configs IS 'Configurable alert weights and fine templates for safety scoring and penalty calculation';

INSERT INTO alert_penalty_configs (alert_type, weight, default_fine_amount, description, is_active, updated_by) VALUES
    ('Overspeed Alert', 5.0, 500.00, 'Penalty weight per 1000 km normalized overspeed events', TRUE, 'SYSTEM'),
    ('Harsh Braking Alert', 4.0, 300.00, 'Penalty weight per 1000 km normalized harsh braking events', TRUE, 'SYSTEM'),
    ('Harsh Acceleration Alert', 4.0, 300.00, 'Penalty weight per 1000 km normalized harsh acceleration events', TRUE, 'SYSTEM'),
    ('GPS Disconnect Alert', 6.0, 1000.00, 'Penalty weight per 1000 km normalized GPS disconnect events', TRUE, 'SYSTEM'),
    ('Night Driving Alert', 3.0, 200.00, 'Penalty weight per 1000 km normalized night driving events/exposure', TRUE, 'SYSTEM'),
    ('Fatigue Alert', 8.0, 1000.00, 'Penalty weight per 100 trips normalized fatigue events', TRUE, 'SYSTEM'),
    ('Ignition Alert', 2.0, 100.00, 'Penalty weight per 1000 km normalized unexpected ignition events', TRUE, 'SYSTEM')
ON CONFLICT (alert_type) DO NOTHING;

CREATE TABLE IF NOT EXISTS driver_penalties (
    id                  BIGSERIAL PRIMARY KEY,
    driver_id           BIGINT NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    alert_id            BIGINT REFERENCES alerts(id) ON DELETE SET NULL,
    trip_id             BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    vehicle_id          BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    penalty_type        VARCHAR(50) NOT NULL, -- 'MANUAL_SAFETY_DEDUCTION', 'FINANCIAL_FINE', 'POLICY_VIOLATION', 'WARNING'
    reason              TEXT NOT NULL,
    penalty_points      NUMERIC(6,2) NOT NULL DEFAULT 0.0,
    monetary_amount     NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    date_applied        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_by          VARCHAR(100) NOT NULL DEFAULT 'Fleet Manager',
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'REVOKED', 'WAIVED'
    deduct_from_score   BOOLEAN NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE driver_penalties IS 'Driver penalties including manager-applied safety deductions and financial penalties';

CREATE INDEX IF NOT EXISTS idx_driver_penalties_driver_id ON driver_penalties(driver_id);
CREATE INDEX IF NOT EXISTS idx_driver_penalties_alert_id ON driver_penalties(alert_id);
CREATE INDEX IF NOT EXISTS idx_driver_penalties_trip_id ON driver_penalties(trip_id);
CREATE INDEX IF NOT EXISTS idx_driver_penalties_status ON driver_penalties(status);
CREATE INDEX IF NOT EXISTS idx_driver_penalties_date_applied ON driver_penalties(date_applied DESC);

CREATE TABLE IF NOT EXISTS penalty_audit_logs (
    id                  BIGSERIAL PRIMARY KEY,
    penalty_id          BIGINT NOT NULL REFERENCES driver_penalties(id) ON DELETE CASCADE,
    action              VARCHAR(50) NOT NULL, -- 'CREATED', 'UPDATED', 'REVOKED', 'WAIVED'
    changed_by          VARCHAR(100) NOT NULL,
    reason              TEXT NOT NULL,
    old_status          VARCHAR(30),
    new_status          VARCHAR(30),
    details             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE penalty_audit_logs IS 'Audit trail for all driver penalty modifications, revocations, and creations';

CREATE INDEX IF NOT EXISTS idx_penalty_audit_logs_penalty_id ON penalty_audit_logs(penalty_id);
CREATE INDEX IF NOT EXISTS idx_penalty_audit_logs_created_at ON penalty_audit_logs(created_at DESC);
