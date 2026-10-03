-- ============================================================
-- V2__add_sensor_tables.sql
-- Raw Sensor Data Tables
-- Based on DATA FORMAT PDF specifications
-- ============================================================

-- ============================================================
-- ACCELERATION SENSOR DATA
-- Source: ADXL335 accelerometer
-- Sampling rate: 1 Hz (1 sample/second)
-- Unit: g (gravitational acceleration)
-- Decimal places: up to 3
-- ============================================================
CREATE TABLE IF NOT EXISTS acceleration_sensor_data (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    trip_id     BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    x           DECIMAL(8,3) NOT NULL,   -- X-axis acceleration in g
    y           DECIMAL(8,3) NOT NULL,   -- Y-axis acceleration in g
    z           DECIMAL(8,3) NOT NULL,   -- Z-axis acceleration in g
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE acceleration_sensor_data IS 'ADXL335 vibration/acceleration sensor data. 1 Hz sampling rate, unit: g';
COMMENT ON COLUMN acceleration_sensor_data.x IS 'X-axis acceleration in g, up to 3 decimal places';
COMMENT ON COLUMN acceleration_sensor_data.y IS 'Y-axis acceleration in g, up to 3 decimal places';
COMMENT ON COLUMN acceleration_sensor_data.z IS 'Z-axis acceleration in g, up to 3 decimal places';

-- ============================================================
-- GPS AND AQI (AIR QUALITY) SENSOR DATA
-- GPS Source: GPS NEO-6M module
-- AQI Source: SPS30 particulate matter sensor
-- Fields: Timestamp, Latitude, Longitude, Altitude, PM1.0, PM2.5, PM4.0, PM10
-- ============================================================
CREATE TABLE IF NOT EXISTS gps_aqi_sensor_data (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    trip_id     BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    latitude    DECIMAL(10,7) NOT NULL,     -- GPS NEO-6M latitude
    longitude   DECIMAL(10,7) NOT NULL,     -- GPS NEO-6M longitude
    altitude    DECIMAL(10,3),              -- GPS NEO-6M altitude in meters
    pm1_0       DECIMAL(10,4),             -- SPS30: PM1.0 in µg/m³
    pm2_5       DECIMAL(10,4),             -- SPS30: PM2.5 in µg/m³
    pm4_0       DECIMAL(10,4),             -- SPS30: PM4.0 in µg/m³
    pm10        DECIMAL(10,4),             -- SPS30: PM10 in µg/m³
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE gps_aqi_sensor_data IS 'Combined GPS NEO-6M location data and SPS30 air quality (PM) data';
COMMENT ON COLUMN gps_aqi_sensor_data.pm1_0 IS 'SPS30: Particulate matter PM1.0 concentration in µg/m³';
COMMENT ON COLUMN gps_aqi_sensor_data.pm2_5 IS 'SPS30: Particulate matter PM2.5 concentration in µg/m³';
COMMENT ON COLUMN gps_aqi_sensor_data.pm4_0 IS 'SPS30: Particulate matter PM4.0 concentration in µg/m³';
COMMENT ON COLUMN gps_aqi_sensor_data.pm10 IS 'SPS30: Particulate matter PM10 concentration in µg/m³';

-- ============================================================
-- CAMERA SENSOR DATA
-- Source: Raspberry Pi Camera Module 3
-- Resolution: 1920 x 1080
-- Frame rate: 1 FPS
-- Codec: H.264
-- ============================================================
CREATE TABLE IF NOT EXISTS camera_sensor_data (
    id          BIGSERIAL PRIMARY KEY,
    vehicle_id  BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    trip_id     BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    timestamp   TIMESTAMPTZ NOT NULL,
    frame_no    BIGINT NOT NULL,                -- Frame number
    resolution  VARCHAR(20) DEFAULT '1920x1080', -- e.g. 1920x1080
    fps         DECIMAL(6,2) DEFAULT 1.0,        -- Frames per second (1 FPS)
    video_file  VARCHAR(500),                    -- Path/name of video file (H.264)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE camera_sensor_data IS 'Raspberry Pi Camera Module 3 video recording metadata. 1920x1080, 1 FPS, H.264 codec';
COMMENT ON COLUMN camera_sensor_data.frame_no IS 'Sequential frame number within the recording session';
COMMENT ON COLUMN camera_sensor_data.resolution IS 'Video resolution, default 1920x1080';
COMMENT ON COLUMN camera_sensor_data.fps IS 'Frames per second, default 1 FPS as per specification';
COMMENT ON COLUMN camera_sensor_data.video_file IS 'File path or filename of the H.264 video file';

-- ============================================================
-- AUDIO SENSOR DATA
-- Source: IMP34DT05 MEMS microphone (voice/noise sensor)
-- Format: PCM audio sample
-- ============================================================
CREATE TABLE IF NOT EXISTS audio_sensor_data (
    id                  BIGSERIAL PRIMARY KEY,
    vehicle_id          BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    trip_id             BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    timestamp           TIMESTAMPTZ NOT NULL,
    audio_sample_pcm    TEXT,                   -- PCM audio sample data (base64 or path)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audio_sensor_data IS 'IMP34DT05 MEMS microphone audio/voice/noise PCM sample data';
COMMENT ON COLUMN audio_sensor_data.audio_sample_pcm IS 'PCM audio sample from IMP34DT05. May be base64-encoded or a file path reference';

-- ============================================================
-- ULTRASONIC ANALYSIS DATA
-- Source: IMP23ABSU ultrasonic microphone
-- Format: PCM ultrasonic sample
-- ============================================================
CREATE TABLE IF NOT EXISTS ultrasonic_analysis_data (
    id                      BIGSERIAL PRIMARY KEY,
    vehicle_id              BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    trip_id                 BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    timestamp               TIMESTAMPTZ NOT NULL,
    ultrasonic_sample_pcm   TEXT,               -- Ultrasonic PCM sample data
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ultrasonic_analysis_data IS 'IMP23ABSU ultrasonic microphone PCM analysis sample data';
COMMENT ON COLUMN ultrasonic_analysis_data.ultrasonic_sample_pcm IS 'PCM ultrasonic sample from IMP23ABSU. May be base64-encoded or a file path reference';

-- ============================================================
-- ULTRASONIC DISTANCE DATA
-- Source: AS8863 ultrasonic distance sensor
-- Measurement: distance in cm
-- ============================================================
CREATE TABLE IF NOT EXISTS ultrasonic_distance_data (
    id              BIGSERIAL PRIMARY KEY,
    vehicle_id      BIGINT REFERENCES vehicles(id) ON DELETE SET NULL,
    trip_id         BIGINT REFERENCES trips(id) ON DELETE SET NULL,
    timestamp       TIMESTAMPTZ NOT NULL,
    distance_cm     DECIMAL(10,3) NOT NULL,     -- Measured distance in centimeters
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ultrasonic_distance_data IS 'AS8863 ultrasonic distance sensor data. Measurement in centimeters';
COMMENT ON COLUMN ultrasonic_distance_data.distance_cm IS 'Distance measured by AS8863 ultrasonic sensor in centimeters';
