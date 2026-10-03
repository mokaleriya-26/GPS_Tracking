# MIGRATION PLAN — GPS Vehicle Tracking / Fleet Management System

**Project:** GPS_Tracking
**Date:** 2026-10-02
**Migration From:** Django + SQLite + React/Vite
**Migration To:** Spring Boot + PostgreSQL/PostGIS + React/Vite + Python AI/ML Service

---

## 1. CURRENT ARCHITECTURE (BEFORE MIGRATION)

### 1.1 Current Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite 8, Bootstrap 5, react-bootstrap, lucide-react, jsPDF, Axios |
| Backend | Python 3, Django 5, Django REST Framework |
| Database | SQLite (db.sqlite3) |
| CSV Import | Django management command (poll_csv.py) |
| Notifications | Email (SMTP/Gmail), SMS (MSG91) |
| Authentication | None (no login, no token auth) |

### 1.2 Current Database Models (Django ORM -> SQLite)

| Model | Key Fields | Notes |
|-------|-----------|-------|
| Driver | driver_id (PK), name, contact, email | Minimal fields |
| Vehicle | vehicle_id (PK), number, driver_id, location, speed, ignition, last_updated | No IMEI, no vehicle_type |
| Alert | event_id, vehicle, driver, alert_type, location, speed, timestamp, min_speed, max_speed, trip_start_time, trip_end_time, trip_distance, trip_duration | No severity, no status |
| NotificationLog | alert (1:1), email_sent, sms_sent, whatsapp_sent, per-recipient flags, request IDs, is_silenced | Rich notification tracking per recipient |
| Trip | vehicle, driver, start_time, end_time, distance_km, duration_min, min_speed, max_speed | Limited fields |
| SystemConfig | key, value, updated_at | Key-value store for system state |

### 1.3 Current API Endpoints (DRF)

| Endpoint | Method | Description |
|---------|--------|-------------|
| /api/drivers/ | GET | List all drivers |
| /api/vehicles/ | GET | List all vehicles (with driver_name) |
| /api/alerts/ | GET | List all alerts (with notification) |
| /api/trips/ | GET | List all trips |
| /admin/ | All | Django Admin |

### 1.4 Current Frontend Pages

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | / | Fleet overview, vehicle table, recent alerts |
| Vehicles | /vehicles | Full vehicle list table |
| Alerts | /alerts | Alert cards with notification delivery status (Email/SMS/WhatsApp) |
| Reports | /reports | Trip/Daily/Weekly/Monthly/Custom report types + PDF generation |

### 1.5 Current Frontend Components

| Component | File | Description |
|-----------|------|-------------|
| Layout | Layout.jsx | Navbar + Sidebar + main content outlet |
| Navbar | Navbar.jsx | Top bar with alerts bell and admin user |
| Sidebar | Sidebar.jsx | Left nav with Dashboard/Vehicles/Alerts/Reports |

### 1.6 Current Frontend Logic

- usePolling.js — polls /api/vehicles/ and /api/alerts/ every 30 seconds
- api/axios.js — Axios instance with VITE_API_URL base URL
- api/alerts.js — getAlerts() function
- api/vehicles.js — getVehicles() function

### 1.7 Current CSV Import Logic (poll_csv.py)

- Reads dataset/vehicle_fleet_alerts_fake_dataset.csv
- Creates/updates: Driver, Vehicle, Trip
- Detects alert types: Overspeed Alert, Harsh Braking Alert, GPS Disconnect Alert, Night Driving Alert, Ignition Alert
- Generates deterministic event_id to avoid duplicate alerts
- Initial load: silences notifications (marks is_silenced=True)
- New alerts: sends Email (SMTP) + SMS (MSG91) to Fleet, Manager, Driver
- Uses SystemConfig table to track initial_import_completed state

### 1.8 Current Notification System (notifications.py)

- Email: SMTP via Gmail with 3 recipients (Fleet, Manager, Driver)
- SMS: MSG91 API with 3 recipients (Fleet, Manager, Driver)
- WhatsApp: UI placeholder only (disabled)
- Per-recipient tracking (fleet_email_sent, manager_email_sent, driver_email_sent, etc.)
- MSG91 error handling (418, 203, 211, 400 status codes)
- Phone number formatting for Indian numbers (91XXXXXXXXXX)

### 1.9 Current Reports System

- Frontend-only PDF generation using jsPDF
- Hardcoded demo data (not from database)
- Report types: Trip, Daily, Weekly, Monthly, Custom
- PDF sections: Header, Performance Summary, Trip Summary, Alert Summary, Recent Alerts Table, Footer
- Driver/Vehicle selection from vehicle list prop

### 1.10 Existing Dataset

- dataset/vehicle_fleet_alerts_fake_dataset.csv
- 110 rows, 8 unique drivers (DRV001-DRV010), 10 unique vehicles (VH001-VH010)
- Fields: Vehicle ID, Vehicle Number, Driver ID, Driver Name, Driver Contact, Driver Email, Speed Min/Max, Location, Time, Trip Start/End Time, Trip Distance/Duration, No. of Trips, Alert columns (Overspeed, Harsh Braking, GPS Disconnect, Ignition, Night Driving)

---

## 2. NEW ARCHITECTURE (AFTER MIGRATION)

### 2.1 New Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite 8, Bootstrap 5, react-bootstrap, lucide-react, Axios |
| Backend | Java 17+, Spring Boot 3, Spring Web, Spring Data JPA |
| Database | PostgreSQL 15+ with PostGIS extension |
| DB Migrations | Flyway |
| API Docs | Springdoc OpenAPI 3 (Swagger UI) |
| AI/ML Service | Python 3.11+, Flask/FastAPI, scikit-learn |
| Reports | iText 7 / Apache PDFBox (server-side PDF generation) |
| Notifications | Email (Spring Mail/SMTP), SMS (MSG91 via HTTP) |

### 2.2 Architecture

```
React Frontend (Vite, port 5173)
         |
         | REST API (JSON)
         v
Java Spring Boot Backend (port 8080)
         |
         +---> PostgreSQL + PostGIS (port 5432)
         |         - All entity tables
         |         - Flyway migrations
         |
         +---> Python AI/ML Service (port 8000)
         |         - Driver behaviour analysis
         |         - Safety scoring
         |         - Anomaly detection
         |
         +---> Report Generation (iText/PDFBox)
         |         - Driver reports (PDF)
         |         - Vehicle reports (PDF)
         |         - Fleet reports (PDF)
         |
         +---> Notification Service
                   - Email (SMTP)
                   - SMS (MSG91)
```

### 2.3 New Database Tables

#### Fleet Management Tables

| Table | Key Fields Added vs Old |
|-------|------------------------|
| drivers | Added: code, license_number, joined_on, active, phone (renamed from contact) |
| vehicles | Added: code, imei, registration_number, vehicle_type, make_model, fuel_type, tank_capacity_litres, rated_mileage_kmpl, odometer_km, active |
| telemetry | NEW — GPS/sensor data per recording point |
| rejected_telemetry | NEW — invalid data rejection log |
| trips | Added: status, start/end lat/lon, moving_seconds, idle_seconds, overspeed_events, harsh_braking_events, harsh_acceleration_events, night_driving_seconds, alert_count, primary_road |
| alerts | Added: reference, severity, occurred_at, lat/lon, speed_limit fields, road_name, resolution fields, delivery tracking |
| cost_records | NEW — operational costs |
| maintenance_records | NEW — maintenance log |
| driver_daily_stats | NEW — pre-calculated daily driver analytics |

#### Raw Sensor Tables (NEW)

| Table | Sensor Source |
|-------|---------------|
| acceleration_sensor_data | ADXL335 (timestamp, x, y, z) |
| gps_aqi_sensor_data | GPS NEO-6M + SPS30 (timestamp, lat, lon, altitude, PM1.0-PM10) |
| camera_sensor_data | Raspberry Pi Camera Module 3 (timestamp, frame_no, resolution, fps, video_file) |
| audio_sensor_data | IMP34DT05 (timestamp, audio_sample_pcm) |
| ultrasonic_analysis_data | IMP23ABSU (timestamp, ultrasonic_sample_pcm) |
| ultrasonic_distance_data | AS8863 (timestamp, distance_cm) |

---

## 3. FILES THAT WILL BE CHANGED

### 3.1 Frontend

| File | Change |
|------|--------|
| frontend/src/api/axios.js | Change base URL to port 8080 (Spring Boot) |
| frontend/src/api/alerts.js | Update endpoint path |
| frontend/src/api/vehicles.js | Update endpoint path + response fields |
| frontend/src/hooks/usePolling.js | Expand to include drivers, trips |
| frontend/src/App.jsx | Add new routes (Drivers, Trips, Maintenance, Costs, Sensors, Settings) |
| frontend/src/components/Sidebar.jsx | Add new nav links |
| frontend/src/components/Navbar.jsx | Minor updates |
| frontend/src/pages/Dashboard.jsx | Connect to Spring Boot APIs |
| frontend/src/pages/Vehicles.jsx | Expand fields (IMEI, registration, type, etc.) |
| frontend/src/pages/Alerts.jsx | Use severity, status, resolution fields |
| frontend/src/pages/Reports.jsx | Backend-driven reports with real data + PDF download |
| frontend/src/index.css | Add chatbot widget styles |
| frontend/vite.config.js | Add proxy config for Spring Boot |
| frontend/.env | Update VITE_API_URL to port 8080 |

### 3.2 Root Files

| File | Change |
|------|--------|
| .env.example | Add PostgreSQL, Spring Boot, Python AI service variables |
| .env | Add new variables (not committed to git) |
| README.md | Replace with comprehensive WSL2 setup guide |

---

## 4. FILES THAT WILL BE CREATED

### 4.1 Backend (Spring Boot) — New Directory: backend-spring/

```
backend-spring/
  pom.xml
  src/main/java/com/gps/tracking/
    GpsTrackingApplication.java
    config/
      SecurityConfig.java
      CorsConfig.java
      SwaggerConfig.java
      PythonAiConfig.java
    controller/
      DriverController.java
      VehicleController.java
      TelemetryController.java
      TripController.java
      AlertController.java
      ReportController.java
      CostController.java
      MaintenanceController.java
      StatsController.java
      SensorController.java
      ChatController.java
      AiController.java
    service/
      DriverService.java
      VehicleService.java
      TelemetryService.java
      TripService.java
      AlertService.java
      ReportService.java
      CostService.java
      MaintenanceService.java
      StatsService.java
      SensorService.java
      ChatService.java
      NotificationService.java
      PdfGenerationService.java
      PythonAiService.java
    repository/
      DriverRepository.java
      VehicleRepository.java
      TelemetryRepository.java
      TripRepository.java
      AlertRepository.java
      CostRepository.java
      MaintenanceRepository.java
      StatsRepository.java
    entity/
      Driver.java
      Vehicle.java
      Telemetry.java
      RejectedTelemetry.java
      Trip.java
      Alert.java
      CostRecord.java
      MaintenanceRecord.java
      DriverDailyStats.java
      AccelerationSensorData.java
      GpsAqiSensorData.java
      CameraSensorData.java
      AudioSensorData.java
      UltrasonicAnalysisData.java
      UltrasonicDistanceData.java
    dto/
      DriverDTO.java
      VehicleDTO.java
      AlertDTO.java
      TripDTO.java
      ReportRequestDTO.java
      ReportResponseDTO.java
      ChatMessageDTO.java
      ChatResponseDTO.java
      DriverRankingDTO.java
    exception/
      GlobalExceptionHandler.java
      ResourceNotFoundException.java
      ValidationException.java
  src/main/resources/
    application.yml
    db/migration/
      V1__initial_schema.sql
      V2__add_sensor_tables.sql
      V3__add_indexes.sql
      V4__seed_reference_data.sql
```

### 4.2 Frontend New Files

```
frontend/src/
  pages/
    Drivers.jsx
    Trips.jsx
    Maintenance.jsx
    Costs.jsx
    SensorData.jsx
    Settings.jsx
  api/
    drivers.js
    trips.js
    reports.js
    chat.js
    maintenance.js
    costs.js
  chatbot/
    ChatbotWidget.jsx
    ChatMessage.jsx
    ChatInput.jsx
    ReportDownloadCard.jsx
```

### 4.3 Python AI Service — New Directory: ai-service/

```
ai-service/
  main.py
  requirements.txt
  app/
    __init__.py
    api/
      predict.py
      analyze_driver.py
      anomaly_detection.py
  models/
    safety_scorer.py
  services/
    driver_analysis.py
    safety_scoring.py
    anomaly_detector.py
```

### 4.4 Data Directory

```
data/
  drivers.csv
  vehicles.csv
  telemetry.csv
  rejected_telemetry.csv
  trips.csv
  alerts.csv
  cost_records.csv
  maintenance_records.csv
  driver_daily_stats.csv
  acceleration_sensor_data.csv
  gps_aqi_sensor_data.csv
  camera_sensor_data.csv
  audio_sensor_data.csv
  ultrasonic_analysis_data.csv
  ultrasonic_distance_data.csv
  seed.sql
```

### 4.5 Other Root Files

```
reports/
  generated/   (gitignored)

docs/
  architecture/
    architecture.md
  api/
    api_reference.md

docker-compose.yml
MIGRATION_PLAN.md  (this file)
```

---

## 5. DATABASE MIGRATION STRATEGY

### 5.1 Approach

- Flyway versioned migrations
- No manual pgAdmin table creation
- Migrations are version-controlled

### 5.2 Migration Versions

| Version | Description |
|---------|-------------|
| V1__initial_schema.sql | Core fleet tables: drivers, vehicles, trips, telemetry, rejected_telemetry, alerts, cost_records, maintenance_records, driver_daily_stats |
| V2__add_sensor_tables.sql | Raw sensor tables: acceleration_sensor_data, gps_aqi_sensor_data, camera_sensor_data, audio_sensor_data, ultrasonic_analysis_data, ultrasonic_distance_data |
| V3__add_indexes.sql | Performance indexes on driver_id, vehicle_id, trip_id, occurred_at, recorded_at, alert_type, severity, status + PostGIS spatial indexes |
| V4__seed_reference_data.sql | Optional: system config seed data |

### 5.3 Existing SQLite Data

- The existing SQLite database (db.sqlite3) contains imported alert/vehicle/trip data from the CSV dataset
- This data will NOT be directly migrated (it was synthetic test data)
- The new fake dataset in /data/ will replace it with a richer, more realistic synthetic dataset
- Existing notification logs are preserved conceptually in the new alerts table delivery tracking fields

### 5.4 PostGIS

- Enable PostGIS extension in V1 migration: CREATE EXTENSION IF NOT EXISTS postgis;
- telemetry table: use GEOGRAPHY(POINT, 4326) for lat/lon
- trips table: use GEOGRAPHY(POINT, 4326) for start/end coordinates
- alerts table: use GEOGRAPHY(POINT, 4326) for alert location
- Spatial indexes: CREATE INDEX ... USING GIST(...)

---

## 6. API MIGRATION STRATEGY

| Old Endpoint (Django/DRF) | New Endpoint (Spring Boot) | Notes |
|--------------------------|--------------------------|-------|
| GET /api/drivers/ | GET /api/drivers | Fields expanded |
| GET /api/vehicles/ | GET /api/vehicles | Fields expanded, IMEI added |
| GET /api/alerts/ | GET /api/alerts | Severity/status/resolution added |
| GET /api/trips/ | GET /api/trips | More trip metrics added |
| N/A | GET /api/drivers/ranking | NEW: driver safety ranking |
| N/A | POST /api/reports/... | NEW: server-side PDF reports |
| N/A | POST /api/chat/message | NEW: chatbot endpoint |
| N/A | POST /api/ai/... | NEW: AI/ML integration |
| N/A | GET /api/stats/driver/{id} | NEW: daily stats |

### New API Endpoints (Spring Boot)

Authentication:
- POST /api/auth/login
- GET /api/auth/me

Drivers:
- GET /api/drivers
- GET /api/drivers/{id}
- POST /api/drivers
- PUT /api/drivers/{id}
- DELETE /api/drivers/{id}
- GET /api/drivers/ranking
- GET /api/drivers/{id}/statistics

Vehicles:
- GET /api/vehicles
- GET /api/vehicles/{id}
- POST /api/vehicles
- PUT /api/vehicles/{id}

Telemetry:
- GET /api/telemetry
- POST /api/telemetry
- GET /api/telemetry/vehicle/{vehicleId}

Trips:
- GET /api/trips
- GET /api/trips/{id}
- GET /api/trips/search
- GET /api/trips/vehicle/{vehicleId}
- GET /api/trips/driver/{driverId}

Alerts:
- GET /api/alerts
- GET /api/alerts/{id}
- GET /api/alerts/search
- PUT /api/alerts/{id}/resolve

Reports:
- POST /api/reports/driver/{driverId}/monthly
- POST /api/reports/driver/{driverId}/daily
- POST /api/reports/driver/{driverId}/weekly
- POST /api/reports/driver/{driverId}/custom
- POST /api/reports/vehicle/{vehicleId}/monthly
- POST /api/reports/fleet/monthly
- GET /api/reports/{reportId}/pdf
- GET /api/reports/{reportId}/download

Costs and Maintenance:
- GET /api/costs
- POST /api/costs
- GET /api/maintenance
- POST /api/maintenance

Daily Stats:
- GET /api/stats/driver/{driverId}
- GET /api/stats/driver/{driverId}/date/{date}

Sensor Data:
- POST /api/sensors/acceleration
- POST /api/sensors/gps-aqi
- POST /api/sensors/camera
- POST /api/sensors/audio
- POST /api/sensors/ultrasonic-analysis
- POST /api/sensors/ultrasonic-distance

Chatbot:
- POST /api/chat/message
- GET /api/chat/history/{sessionId}
- GET /api/chat/report/{reportId}

AI/ML Integration:
- POST /api/ai/analyze-driver
- POST /api/ai/anomaly-detection
- GET /api/ai/driver-ranking

---

## 7. FRONTEND MIGRATION STRATEGY

### 7.1 Preserved

- React + Vite stack (no change)
- Bootstrap 5 (no change)
- React Router (no change)
- Axios (no change)
- jsPDF (kept for client-side preview, server-side PDF is primary)
- lucide-react icons (no change)
- Existing UI layout (Navbar, Sidebar, Layout pattern)
- Polling pattern (usePolling hook)

### 7.2 Updated

- VITE_API_URL changed to http://localhost:8080
- API response field names updated to match Spring Boot DTOs
- Vehicles: add IMEI, registration, type, make/model fields
- Alerts: add severity badges, status, resolution info
- Reports: replace hardcoded demo data with real API data
- Dashboard: add trips count, distance, driver count cards

### 7.3 Added

- New pages: Drivers, Trips, Maintenance, Costs, SensorData, Settings
- Chatbot widget (bottom-right floating button, globally mounted)
- API functions: drivers.js, trips.js, reports.js, chat.js, maintenance.js, costs.js
- Sidebar: new navigation links for all new pages

---

## 8. REPORT MIGRATION STRATEGY

### 8.1 Old System

- 100% frontend (jsPDF)
- Hardcoded demo data
- No real database queries

### 8.2 New System

- Backend generates PDF (iText 7 in Spring Boot)
- Frontend requests report generation via POST /api/reports/...
- Backend queries PostgreSQL for all report data
- PDF is stored in reports/generated/ directory
- Frontend receives download URL
- jsPDF kept for lightweight in-browser preview if needed

### 8.3 Report Types Preserved

- Trip Report
- Daily Report
- Weekly Report
- Monthly Report
- Custom Date Range Report

### 8.4 New Report Types

- Driver Monthly Report (with full alert analysis)
- Vehicle Monthly Report
- Fleet Monthly Report
- Driver Ranking Report

### 8.5 PDF Sections (expanded from existing)

All existing sections PLUS:
- Driver behaviour metrics (safety score, overspeed, harsh braking, harsh acceleration)
- Entry/exit tracking
- Night driving analysis
- Alert severity breakdown
- Notification delivery status
- Fuel/cost summary
- Maintenance summary

---

## 9. CHATBOT ARCHITECTURE

```
React ChatbotWidget
      |
      | POST /api/chat/message
      v
Spring Boot ChatController
      |
      +---> ChatService
      |         |
      |         +---> Intent parsing (keyword/NLP rules)
      |         +---> Driver/Vehicle name resolution
      |         +---> Date/month parsing
      |         +---> Context management (session state)
      |
      +---> ReportService (if report requested)
      |         |
      |         +---> PostgreSQL queries
      |         +---> PdfGenerationService
      |         +---> Returns PDF URL
      |
      +---> AlertService (if alert query)
      |
      +---> DriverService (if driver ranking query)
      |
      +---> PythonAiService (optional, for complex NLP/ML)
```

### 9.1 Chat Intent Categories

| Intent | Examples |
|--------|---------|
| DRIVER_REPORT | "Give me monthly report of DRV001", "Rohan's report" |
| VEHICLE_REPORT | "Report for MH14XX1234", "Vehicle VH001 report" |
| FLEET_REPORT | "Generate fleet monthly report" |
| DRIVER_RANKING | "Who is the most trustworthy driver?", "Top 5 drivers" |
| ALERT_QUERY | "Show unresolved alerts", "Alerts for DRV001" |
| DRIVER_STATS | "How many trips did DRV001 complete?" |
| VEHICLE_STATS | "Which vehicle travelled most?" |
| ENTRY_EXIT | "How many entries and exits?" |
| UNKNOWN | Fallback with suggestions |

### 9.2 Chatbot UI

- Floating button: bottom-right corner, appears on all pages
- Chat panel: slides up, 400px wide, 600px tall
- Message history: scrollable
- PDF in chat: ReportDownloadCard with View PDF and Download PDF buttons
- Context: session-based (last driver/vehicle/month remembered within conversation)

---

## 10. AI/ML ARCHITECTURE

### 10.1 Python Service Endpoints

| Endpoint | Input | Output |
|---------|-------|--------|
| POST /predict | Driver metrics | Safety score prediction |
| POST /analyze-driver | Driver stats JSON | Behaviour analysis |
| POST /anomaly-detection | Telemetry data | Anomaly flags |

### 10.2 Safety Score Calculation (Documented)

```
safety_score = 100
             - (overspeed_events x 5)
             - (harsh_braking_events x 4)
             - (harsh_acceleration_events x 4)
             - (night_driving_seconds / 3600 x 3)
             - (fatigue_events x 8)
             + (trips_without_incident x 1)
             (clamped between 0 and 100)
```

### 10.3 Driver Ranking (Transparent)

- Calculated from driver_daily_stats table
- Fields: safety_score, overspeed_events, harsh_braking_events, harsh_acceleration_events, night_driving_seconds, fatigue_events, trip_count, distance_km
- Ranked by weighted composite score
- Chatbot explains the metric basis

### 10.4 Fallback

- If Python AI service is unavailable, Spring Boot uses a rule-based fallback
- Normal fleet management functions are NOT affected by AI service failure

---

## 11. ENTRY/EXIT TRACKING

### 11.1 Current State

- No explicit entry/exit model in existing system
- Trips effectively represent entry (start_time) and exit (end_time) events

### 11.2 New Approach

- Entry = Trip start event (ignition ON, vehicle begins moving)
- Exit = Trip end event (vehicle stopped, ignition OFF)
- trips table captures: start_time, end_time, start_lat/lon, end_lat/lon, driver_id, vehicle_id
- Reports derive entry/exit from trips table
- Alert types include: "Ignition Alert" (from existing CSV import logic) — preserved

### 11.3 Report Fields

- Number of entries = number of trips started
- Number of exits = number of trips ended
- Entry timestamp = trip.start_time
- Exit timestamp = trip.end_time
- Location = start/end lat/lon
- Duration = trip.duration_seconds

---

## 12. SECURITY STRATEGY

### 12.1 Secrets Management

- All credentials via environment variables
- Backend: application.yml uses ${ENV_VAR:default} syntax
- Frontend: VITE_* env vars for non-sensitive config only
- No credentials in source code
- .gitignore updated to exclude .env files

### 12.2 Environment Variables Required

```
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gps_tracking
DB_USERNAME=postgres
DB_PASSWORD=your_password

# Spring Boot
SERVER_PORT=8080

# Email (SMTP)
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your_email@gmail.com
EMAIL_PASSWORD=your_app_password
EMAIL_FROM=no-reply@trackfleet.com
FLEET_EMAIL=fleet@trackfleet.com
MANAGER_EMAIL=manager@trackfleet.com

# MSG91 SMS
MSG91_AUTH_KEY=your_msg91_auth_key
MSG91_SMS_TEMPLATE_ID=your_template_id
MSG91_SENDER_ID=your_sender_id
FLEET_PHONE=919876543200
MANAGER_PHONE=919876543201

# Python AI Service
PYTHON_AI_SERVICE_URL=http://localhost:8000

# Frontend
VITE_API_URL=http://localhost:8080
```

---

## 13. DOCKER / WSL2 STRATEGY

### 13.1 docker-compose.yml Services

- postgres — PostgreSQL 15 with PostGIS extension
- backend (optional) — Spring Boot backend
- ai-service (optional) — Python AI service

### 13.2 WSL2 Commands

- All commands are Linux-compatible
- No Windows-only paths
- Documented step-by-step in README.md

---

## 14. TESTING STRATEGY

### 14.1 Backend Tests

- Spring Boot integration tests
- Repository tests (DataJpaTest)
- Service unit tests (Mockito)
- PostgreSQL connection test
- PostGIS extension test
- Flyway migration test
- Seed data load test

### 14.2 API Tests

- All CRUD endpoints
- Report generation endpoints
- Chatbot endpoints
- AI endpoint (with mock when AI service unavailable)

### 14.3 Frontend Tests

- All pages load
- API calls return data
- Filters work
- Report generation UI works
- PDF download works
- Chatbot opens on all pages
- Chatbot sends and receives messages

### 14.4 Report Tests

- Daily, Monthly, Custom date range reports
- Driver, Vehicle, Fleet reports
- PDF is valid and readable

### 14.5 Chatbot Tests

- Driver report request
- Vehicle report request
- Alert query
- Driver ranking
- Entry/exit query
- Ambiguous driver name -> ask for clarification
- Invalid query -> helpful fallback

### 14.6 AI Service Tests

- Python service starts
- All endpoints respond
- Spring Boot handles AI service failure gracefully

---

## 15. IMPLEMENTATION PHASES

| Phase | Description | Status |
|-------|-------------|--------|
| Phase 1 | Inspect existing project + create MIGRATION_PLAN.md | DONE |
| Phase 2 | PostgreSQL/PostGIS schema + Flyway migrations | NEXT |
| Phase 3 | Spring Boot backend skeleton + entities + repositories | PENDING |
| Phase 4 | Spring Boot APIs (controllers + services + DTOs) | PENDING |
| Phase 5 | Connect React frontend to Spring Boot | PENDING |
| Phase 6 | Fake dataset (CSVs + seed.sql) | PENDING |
| Phase 7 | Reports (server-side PDF) | PENDING |
| Phase 8 | Python AI/ML service | PENDING |
| Phase 9 | Chatbot (backend + frontend) | PENDING |
| Phase 10 | Swagger/OpenAPI documentation | PENDING |
| Phase 11 | Testing + bug fixing | PENDING |
| Phase 12 | README + WSL2 setup documentation | PENDING |

---

## 16. FEATURES PRESERVED FROM OLD SYSTEM

| Feature | Old Implementation | New Implementation |
|---------|-------------------|-------------------|
| Driver list | Django model -> DRF | Spring Boot entity -> JPA |
| Vehicle list | Django model -> DRF | Spring Boot entity -> JPA |
| Alert list | Django model -> DRF | Spring Boot entity -> JPA |
| Notification delivery status | NotificationLog model | alerts table delivery fields |
| Email notifications | SMTP via Python | Spring Mail / SMTP |
| SMS notifications | MSG91 via Python requests | MSG91 via Spring RestTemplate |
| Silenced notifications (historical) | NotificationLog.is_silenced | alerts.status = HISTORICAL |
| CSV import | poll_csv.py management command | Spring Boot CSV import service |
| Alert type: Overspeed | YES | YES |
| Alert type: Harsh Braking | YES | YES |
| Alert type: GPS Disconnect | YES | YES |
| Alert type: Night Driving | YES | YES |
| Alert type: Ignition | YES | YES |
| Trip metrics | YES | YES (expanded) |
| PDF report generation | jsPDF frontend | iText backend + jsPDF preview |
| Dashboard overview | YES | YES (expanded) |
| Vehicles page | YES | YES (expanded) |
| Alerts page | YES | YES (expanded with severity/status) |
| Reports page | YES | YES (connected to real data) |

---

*This document was generated during Phase 1 (Project Inspection) and will be updated after each phase is completed.*
