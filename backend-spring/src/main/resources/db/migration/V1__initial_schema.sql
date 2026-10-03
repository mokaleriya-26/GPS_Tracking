-- ============================================================
-- V1__initial_schema.sql
-- Core Fleet Management Tables
-- GPS Vehicle Tracking System
-- ============================================================

-- Enable PostGIS extension for geospatial data
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- ============================================================
-- DRIVERS
-- ============================================================
CREATE TABLE IF NOT EXISTS drivers (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(50)  NOT NULL UNIQUE,        -- e.g. DRV001
    name                VARCHAR(100) NOT NULL,
    phone               VARCHAR(20),
    license_number      VARCHAR(50),
    joined_on           DATE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE drivers IS 'Fleet driver information';
COMMENT ON COLUMN drivers.code IS 'Human-readable driver code, e.g. DRV001';
COMMENT ON COLUMN drivers.phone IS 'Contact phone number (Indian format)';

-- ============================================================
-- VEHICLES
-- ============================================================
CREATE TABLE IF NOT EXISTS vehicles (
    id                      BIGSERIAL PRIMARY KEY,
    code                    VARCHAR(50)  NOT NULL UNIQUE,      -- e.g. VH001
    imei                    VARCHAR(20)  UNIQUE,               -- GPS tracker IMEI
    registration_number     VARCHAR(50)  NOT NULL UNIQUE,      -- e.g. MH04AB1234
    vehicle_type            VARCHAR(50),                       -- Truck, Van, Car, etc.
    make_model              VARCHAR(100),                      -- e.g. Tata Ace, Maruti Eeco
    fuel_type               VARCHAR(20),                       -- Diesel, Petrol, CNG, Electric
    tank_capacity_litres    DECIMAL(8,2),
    rated_mileage_kmpl      DECIMAL(8,2),
    odometer_km             DECIMAL(10,2) DEFAULT 0,
    driver_id               BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE vehicles IS 'Fleet vehicle information';
COMMENT ON COLUMN vehicles.imei IS 'GPS tracker hardware IMEI number';

-- ============================================================
-- TRIPS
-- ============================================================
CREATE TABLE IF NOT EXISTS trips (
    id                          BIGSERIAL PRIMARY KEY,
    vehicle_id                  BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    driver_id                   BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    start_time                  TIMESTAMPTZ NOT NULL,
    end_time                    TIMESTAMPTZ,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                -- ACTIVE, COMPLETED, CANCELLED
    start_latitude              DECIMAL(10,7),
    start_longitude             DECIMAL(10,7),
    end_latitude                DECIMAL(10,7),
    end_longitude               DECIMAL(10,7),
    distance_km                 DECIMAL(10,3) DEFAULT 0,
    duration_seconds            INTEGER DEFAULT 0,
    moving_seconds              INTEGER DEFAULT 0,
    idle_seconds                INTEGER DEFAULT 0,
    max_speed_kmph              DECIMAL(8,2),
    min_speed_kmph              DECIMAL(8,2),
    average_speed_kmph          DECIMAL(8,2),
    overspeed_events            INTEGER DEFAULT 0,
    harsh_braking_events        INTEGER DEFAULT 0,
    harsh_acceleration_events   INTEGER DEFAULT 0,
    night_driving_seconds       INTEGER DEFAULT 0,
    alert_count                 INTEGER DEFAULT 0,
    primary_road                VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE trips IS 'Vehicle trip records (entry/exit events)';
COMMENT ON COLUMN trips.status IS 'ACTIVE=trip in progress, COMPLETED=trip ended, CANCELLED=aborted';
COMMENT ON COLUMN trips.start_time IS 'Entry event - trip start (ignition ON)';
COMMENT ON COLUMN trips.end_time IS 'Exit event - trip end (ignition OFF/vehicle stopped)';

-- ============================================================
-- TELEMETRY
-- ============================================================
CREATE TABLE IF NOT EXISTS telemetry (
    id                          BIGSERIAL PRIMARY KEY,
    vehicle_id                  BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    recorded_at                 TIMESTAMPTZ NOT NULL,          -- Timestamp from GPS device
    received_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- Server receipt time
    latitude                    DECIMAL(10,7) NOT NULL,
    longitude                   DECIMAL(10,7) NOT NULL,
    speed_kmph                  DECIMAL(8,2) NOT NULL DEFAULT 0,
    heading                     DECIMAL(6,2),                  -- Degrees 0-360
    ignition                    BOOLEAN DEFAULT FALSE,
    battery_voltage             DECIMAL(6,3),
    gsm_signal                  INTEGER,                       -- Signal strength 0-100
    satellites                  INTEGER,                       -- GPS satellite count
    distance_from_previous_km   DECIMAL(10,4),
    seconds_from_previous       INTEGER,
    acceleration_mps2           DECIMAL(8,4),
    trip_id                     BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    road_status                 VARCHAR(20),                   -- ON_ROAD, OFF_ROAD, STATIONARY
    road_name                   VARCHAR(255),
    road_ref                    VARCHAR(100),
    highway_type                VARCHAR(50),
    osm_way_id                  BIGINT,
    road_distance_m             DECIMAL(10,2),
    speed_limit_kmph            DECIMAL(8,2),
    speed_limit_source          VARCHAR(50),                   -- OSM, INFERRED, DEFAULT
    speed_limit_inferred        BOOLEAN DEFAULT FALSE,
    source                      VARCHAR(50) DEFAULT 'GPS'      -- GPS, CSV_IMPORT, MANUAL
);

COMMENT ON TABLE telemetry IS 'Raw GPS telemetry records from vehicle trackers';
COMMENT ON COLUMN telemetry.recorded_at IS 'Timestamp as reported by GPS device';
COMMENT ON COLUMN telemetry.received_at IS 'Timestamp when server received the data';

-- ============================================================
-- REJECTED TELEMETRY
-- ============================================================
CREATE TABLE IF NOT EXISTS rejected_telemetry (
    id              BIGSERIAL PRIMARY KEY,
    imei            VARCHAR(20),
    vehicle_id      BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    recorded_at     TIMESTAMPTZ,
    reason_code     VARCHAR(50) NOT NULL,    -- INVALID_COORDS, MISSING_FIELD, OUT_OF_RANGE, etc.
    reason          TEXT NOT NULL,
    raw_payload     TEXT,                    -- Original raw record for debugging
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE rejected_telemetry IS 'Invalid telemetry records rejected during validation';
COMMENT ON COLUMN rejected_telemetry.reason_code IS 'Machine-readable rejection reason code';
COMMENT ON COLUMN rejected_telemetry.raw_payload IS 'Original raw data for debugging';

-- ============================================================
-- ALERTS
-- ============================================================
CREATE TABLE IF NOT EXISTS alerts (
    id                      BIGSERIAL PRIMARY KEY,
    reference               VARCHAR(100) UNIQUE,              -- Deterministic event ID (event_id from old system)
    alert_type              VARCHAR(100) NOT NULL,
                            -- Overspeed Alert, Harsh Braking Alert, GPS Disconnect Alert,
                            -- Night Driving Alert, Ignition Alert, Harsh Acceleration Alert,
                            -- Fatigue Alert, Geofence Alert
    severity                VARCHAR(20)  NOT NULL DEFAULT 'WARNING',
                            -- INFO, WARNING, CRITICAL
    vehicle_id              BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    driver_id               BIGINT REFERENCES drivers(id) ON DELETE SET NULL,
    trip_id                 BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    occurred_at             TIMESTAMPTZ NOT NULL,             -- When event happened
    latitude                DECIMAL(10,7),
    longitude               DECIMAL(10,7),
    speed_kmph              DECIMAL(8,2),
    speed_limit_kmph        DECIMAL(8,2),
    speed_limit_source      VARCHAR(50),
    speed_limit_inferred    BOOLEAN DEFAULT FALSE,
    road_name               VARCHAR(255),
    previous_speed_kmph     DECIMAL(8,2),                    -- For harsh braking calculation
    seconds_between         INTEGER,                          -- Time between data points
    acceleration_mps2       DECIMAL(8,4),                    -- Calculated acceleration
    message                 TEXT,                             -- Human-readable alert message

    -- Trip context (preserved from old system)
    trip_start_time         TIMESTAMPTZ,
    trip_end_time           TIMESTAMPTZ,
    trip_distance_km        DECIMAL(10,3),
    trip_duration_min       INTEGER,
    min_speed_kmph          DECIMAL(8,2),
    max_speed_kmph          DECIMAL(8,2),

    -- Alert lifecycle
    status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                            -- OPEN, RESOLVED, HISTORICAL (was is_silenced in old system)
    resolved_at             TIMESTAMPTZ,
    resolved_by             VARCHAR(100),

    -- Notification delivery (migrated from NotificationLog model)
    delivery_status         VARCHAR(20) DEFAULT 'PENDING',
                            -- PENDING, SENT, FAILED, SILENCED
    delivery_attempts       INTEGER DEFAULT 0,
    delivery_last_error     TEXT,
    delivery_next_attempt_at TIMESTAMPTZ,

    -- Email delivery status (per-recipient, from old NotificationLog)
    email_sent              BOOLEAN DEFAULT FALSE,
    email_sent_at           TIMESTAMPTZ,
    email_error             TEXT,
    fleet_email_sent        BOOLEAN DEFAULT FALSE,
    fleet_email_error       TEXT,
    manager_email_sent      BOOLEAN DEFAULT FALSE,
    manager_email_error     TEXT,
    driver_email_sent       BOOLEAN DEFAULT FALSE,
    driver_email_error      TEXT,

    -- SMS delivery status (per-recipient, from old NotificationLog)
    sms_sent                BOOLEAN DEFAULT FALSE,
    sms_sent_at             TIMESTAMPTZ,
    sms_error               TEXT,
    fleet_sms_sent          BOOLEAN DEFAULT FALSE,
    fleet_sms_error         TEXT,
    fleet_sms_request_id    VARCHAR(100),
    manager_sms_sent        BOOLEAN DEFAULT FALSE,
    manager_sms_error       TEXT,
    manager_sms_request_id  VARCHAR(100),
    driver_sms_sent         BOOLEAN DEFAULT FALSE,
    driver_sms_error        TEXT,
    driver_sms_request_id   VARCHAR(100),

    -- WhatsApp (placeholder, disabled)
    whatsapp_sent           BOOLEAN DEFAULT FALSE,
    whatsapp_error          TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE alerts IS 'Fleet alert events with notification delivery tracking';
COMMENT ON COLUMN alerts.reference IS 'Deterministic unique ID matching old event_id from poll_csv.py';
COMMENT ON COLUMN alerts.severity IS 'INFO=informational, WARNING=needs attention, CRITICAL=immediate action';
COMMENT ON COLUMN alerts.status IS 'OPEN=active, RESOLVED=handled, HISTORICAL=imported without notifications';

-- ============================================================
-- COST RECORDS
-- ============================================================
CREATE TABLE IF NOT EXISTS cost_records (
    id              BIGSERIAL PRIMARY KEY,
    vehicle_id      BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    trip_id         BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    cost_type       VARCHAR(50) NOT NULL,       -- FUEL, TOLL, MAINTENANCE, PARKING, OTHER
    amount_inr      DECIMAL(12,2) NOT NULL,
    quantity        DECIMAL(10,3),              -- Litres for fuel, etc.
    unit_price_inr  DECIMAL(10,2),
    odometer_km     DECIMAL(10,2),
    incurred_on     DATE NOT NULL,
    notes           TEXT,
    data_source     VARCHAR(50) DEFAULT 'MANUAL', -- MANUAL, CSV_IMPORT, AUTO
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE cost_records IS 'Vehicle operational costs (fuel, tolls, maintenance, etc.)';

-- ============================================================
-- MAINTENANCE RECORDS
-- ============================================================
CREATE TABLE IF NOT EXISTS maintenance_records (
    id                      BIGSERIAL PRIMARY KEY,
    vehicle_id              BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    service_type            VARCHAR(100) NOT NULL,  -- OIL_CHANGE, TIRE_ROTATION, BRAKE_SERVICE, etc.
    serviced_on             DATE NOT NULL,
    odometer_km             DECIMAL(10,2),
    cost_inr                DECIMAL(12,2),
    next_service_due_on     DATE,
    next_service_due_km     DECIMAL(10,2),
    notes                   TEXT,
    data_source             VARCHAR(50) DEFAULT 'MANUAL',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE maintenance_records IS 'Vehicle maintenance and service records';

-- ============================================================
-- DRIVER DAILY STATS
-- Pre-calculated daily analytics per driver (for reports and chatbot)
-- ============================================================
CREATE TABLE IF NOT EXISTS driver_daily_stats (
    id                          BIGSERIAL PRIMARY KEY,
    driver_id                   BIGINT NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    stat_date                   DATE NOT NULL,
    distance_km                 DECIMAL(10,3) DEFAULT 0,
    driving_seconds             INTEGER DEFAULT 0,
    idle_seconds                INTEGER DEFAULT 0,
    trip_count                  INTEGER DEFAULT 0,
    max_speed_kmph              DECIMAL(8,2),
    average_speed_kmph          DECIMAL(8,2),
    overspeed_events            INTEGER DEFAULT 0,
    harsh_braking_events        INTEGER DEFAULT 0,
    harsh_acceleration_events   INTEGER DEFAULT 0,
    night_driving_seconds       INTEGER DEFAULT 0,
    fatigue_events              INTEGER DEFAULT 0,
    safety_score                DECIMAL(5,2),               -- 0.00 to 100.00
    UNIQUE (driver_id, stat_date)
);

COMMENT ON TABLE driver_daily_stats IS 'Pre-calculated daily driver performance statistics for analytics';
COMMENT ON COLUMN driver_daily_stats.safety_score IS 'Calculated safety score 0-100. Formula documented in MIGRATION_PLAN.md Section 10.2';
