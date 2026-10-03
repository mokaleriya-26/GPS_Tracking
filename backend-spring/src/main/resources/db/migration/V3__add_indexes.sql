-- ============================================================
-- V3__add_indexes.sql
-- Performance Indexes for Fleet Management System
-- ============================================================

-- ============================================================
-- DRIVERS
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_drivers_code ON drivers(code);
CREATE INDEX IF NOT EXISTS idx_drivers_active ON drivers(active);
CREATE INDEX IF NOT EXISTS idx_drivers_name ON drivers(name);

-- ============================================================
-- VEHICLES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_vehicles_code ON vehicles(code);
CREATE INDEX IF NOT EXISTS idx_vehicles_driver_id ON vehicles(driver_id);
CREATE INDEX IF NOT EXISTS idx_vehicles_active ON vehicles(active);
CREATE INDEX IF NOT EXISTS idx_vehicles_imei ON vehicles(imei);
CREATE INDEX IF NOT EXISTS idx_vehicles_registration ON vehicles(registration_number);

-- ============================================================
-- TELEMETRY
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_telemetry_vehicle_id ON telemetry(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_trip_id ON telemetry(trip_id);
CREATE INDEX IF NOT EXISTS idx_telemetry_recorded_at ON telemetry(recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_vehicle_time ON telemetry(vehicle_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_ignition ON telemetry(ignition);

-- ============================================================
-- REJECTED TELEMETRY
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_rejected_telemetry_vehicle_id ON rejected_telemetry(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_rejected_telemetry_created_at ON rejected_telemetry(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rejected_telemetry_reason_code ON rejected_telemetry(reason_code);

-- ============================================================
-- TRIPS
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_trips_vehicle_id ON trips(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_trips_driver_id ON trips(driver_id);
CREATE INDEX IF NOT EXISTS idx_trips_start_time ON trips(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);
CREATE INDEX IF NOT EXISTS idx_trips_vehicle_start ON trips(vehicle_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_trips_driver_start ON trips(driver_id, start_time DESC);

-- ============================================================
-- ALERTS
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_alerts_vehicle_id ON alerts(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_alerts_driver_id ON alerts(driver_id);
CREATE INDEX IF NOT EXISTS idx_alerts_trip_id ON alerts(trip_id);
CREATE INDEX IF NOT EXISTS idx_alerts_alert_type ON alerts(alert_type);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_occurred_at ON alerts(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_alerts_reference ON alerts(reference);
CREATE INDEX IF NOT EXISTS idx_alerts_delivery_status ON alerts(delivery_status);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_alerts_vehicle_type ON alerts(vehicle_id, alert_type);
CREATE INDEX IF NOT EXISTS idx_alerts_driver_type ON alerts(driver_id, alert_type);
CREATE INDEX IF NOT EXISTS idx_alerts_type_status ON alerts(alert_type, status);
CREATE INDEX IF NOT EXISTS idx_alerts_severity_status ON alerts(severity, status);

-- ============================================================
-- COST RECORDS
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_cost_records_vehicle_id ON cost_records(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_cost_records_trip_id ON cost_records(trip_id);
CREATE INDEX IF NOT EXISTS idx_cost_records_incurred_on ON cost_records(incurred_on DESC);
CREATE INDEX IF NOT EXISTS idx_cost_records_cost_type ON cost_records(cost_type);

-- ============================================================
-- MAINTENANCE RECORDS
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_maintenance_vehicle_id ON maintenance_records(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_serviced_on ON maintenance_records(serviced_on DESC);
CREATE INDEX IF NOT EXISTS idx_maintenance_service_type ON maintenance_records(service_type);

-- ============================================================
-- DRIVER DAILY STATS
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_driver_daily_stats_driver_id ON driver_daily_stats(driver_id);
CREATE INDEX IF NOT EXISTS idx_driver_daily_stats_stat_date ON driver_daily_stats(stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_driver_daily_stats_driver_date ON driver_daily_stats(driver_id, stat_date DESC);
CREATE INDEX IF NOT EXISTS idx_driver_daily_stats_safety_score ON driver_daily_stats(safety_score DESC);

-- ============================================================
-- SENSOR DATA TABLES
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_accel_vehicle_id ON acceleration_sensor_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_accel_trip_id ON acceleration_sensor_data(trip_id);
CREATE INDEX IF NOT EXISTS idx_accel_timestamp ON acceleration_sensor_data(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_gps_aqi_vehicle_id ON gps_aqi_sensor_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_gps_aqi_trip_id ON gps_aqi_sensor_data(trip_id);
CREATE INDEX IF NOT EXISTS idx_gps_aqi_timestamp ON gps_aqi_sensor_data(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_camera_vehicle_id ON camera_sensor_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_camera_trip_id ON camera_sensor_data(trip_id);
CREATE INDEX IF NOT EXISTS idx_camera_timestamp ON camera_sensor_data(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_audio_vehicle_id ON audio_sensor_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_audio_trip_id ON audio_sensor_data(trip_id);
CREATE INDEX IF NOT EXISTS idx_audio_timestamp ON audio_sensor_data(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_ultrasonic_analysis_vehicle_id ON ultrasonic_analysis_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_ultrasonic_analysis_timestamp ON ultrasonic_analysis_data(timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_ultrasonic_distance_vehicle_id ON ultrasonic_distance_data(vehicle_id);
CREATE INDEX IF NOT EXISTS idx_ultrasonic_distance_timestamp ON ultrasonic_distance_data(timestamp DESC);
