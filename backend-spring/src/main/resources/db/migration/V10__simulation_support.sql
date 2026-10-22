-- ============================================================
-- V10__simulation_support.sql
-- Interactive Live Truck Simulation & Isolation Support
-- ============================================================

-- 1. Add is_simulation flag to alerts, trips, and driver_penalties
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS is_simulation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS is_simulation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE driver_penalties
    ADD COLUMN IF NOT EXISTS is_simulation BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Create indexes for efficient filtering and dashboard separation
CREATE INDEX IF NOT EXISTS idx_alerts_is_simulation ON alerts (is_simulation);
CREATE INDEX IF NOT EXISTS idx_trips_is_simulation ON trips (is_simulation);
CREATE INDEX IF NOT EXISTS idx_driver_penalties_is_simulation ON driver_penalties (is_simulation);

-- 3. Seed dedicated simulation driver if not already exists
INSERT INTO drivers (code, name, phone, email, license_number, active, created_at)
VALUES (
    'DRV_SIM_01',
    'Rajesh Sharma (Simulation)',
    '9999900001',
    'sim.driver@trackfleet.local',
    'MH-01-2024-SIM001',
    TRUE,
    NOW()
)
ON CONFLICT (code) DO NOTHING;

-- 4. Seed dedicated simulation truck if not already exists
INSERT INTO vehicles (code, registration_number, vehicle_type, make_model, fuel_type, tank_capacity_litres, rated_mileage_kmpl, odometer_km, driver_id, active, created_at)
SELECT
    'VH_SIM_01',
    'MH01SM9999',
    'Heavy Truck',
    'BharatBenz 2823R Heavy Freight Truck',
    'Diesel',
    380.00,
    4.50,
    14250.00,
    d.id,
    TRUE,
    NOW()
FROM drivers d
WHERE d.code = 'DRV_SIM_01'
ON CONFLICT (code) DO NOTHING;
