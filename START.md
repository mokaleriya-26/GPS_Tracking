# START.md — How to Run the GPS Fleet Tracking System

> **TL;DR:** 3 terminals. Backend → Frontend → AI Service (optional).

---

## Quick Start (macOS / WSL2)

### Step 1 — Start Backend (Spring Boot)

```bash
cd backend-spring
export $(grep -v '^#' ../.env | xargs)
java -jar target/tracking-1.0.0.jar
```

Or rebuild first:
```bash
cd backend-spring
export $(grep -v '^#' ../.env | xargs)
mvn clean package -DskipTests
java -jar target/tracking-1.0.0.jar
```

**Expected output:**
```
Started GpsTrackingApplication in 2.6 seconds
[CSV IMPORT] Skipped — rich seed data already loaded (9186 trips in DB).
```

Backend → http://localhost:8080  
Swagger UI → http://localhost:8080/swagger-ui.html

---

### Step 2 — Start Frontend (React/Vite)

```bash
cd frontend
npm run dev
```

Frontend → http://localhost:5173

---

### Step 3 — Start Python AI Service *(optional)*

```bash
bash start-ai-service.sh
```

AI Service → http://localhost:8000  
*(If not running, backend automatically uses rule-based safety scoring)*

---

## First Time Setup

### 1. Database (one-time)

```bash
# macOS
brew services start postgresql@17
psql postgres -c "CREATE DATABASE gps_tracking;"
psql gps_tracking -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# WSL2
sudo service postgresql start
sudo -u postgres psql -c "CREATE DATABASE gps_tracking;"
sudo -u postgres psql -d gps_tracking -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### 2. Seed Data (one-time)

```bash
psql -d gps_tracking -f data/seed.sql
```

Loads: **17 drivers · 20 vehicles · 9,082 trips · 11,089 alerts · 343 cost records · 82 maintenance records**

### 3. Frontend Dependencies (one-time)

```bash
cd frontend && npm install
```

---

## What's Running Where

| Service | URL | Port |
|---------|-----|------|
| React Frontend | http://localhost:5173 | 5173 |
| Spring Boot Backend | http://localhost:8080 | 8080 |
| Swagger API Docs | http://localhost:8080/swagger-ui.html | 8080 |
| Python AI Service | http://localhost:8000 | 8000 |
| PostgreSQL | localhost | 5432 |

---

## Application Pages

| Page | URL |
|------|-----|
| Dashboard | http://localhost:5173/ |
| Vehicles | http://localhost:5173/vehicles |
| Drivers | http://localhost:5173/drivers |
| Trips | http://localhost:5173/trips |
| Alerts | http://localhost:5173/alerts |
| Reports (PDF) | http://localhost:5173/reports |
| Maintenance | http://localhost:5173/maintenance |
| Costs | http://localhost:5173/costs |
| Sensor Data | http://localhost:5173/sensors |
| Settings | http://localhost:5173/settings |

**Chatbot:** 💬 button (bottom-right on all pages)

---

## Quick Checks

```bash
# Is backend up?
curl -s http://localhost:8080/api/drivers | python3 -m json.tool | head -10

# Driver ranking (real scores)?
curl -s http://localhost:8080/api/drivers/ranking | python3 -c \
  "import json,sys; d=json.load(sys.stdin); [print(f'#{r[\"rank\"]} {r[\"driverName\"]} = {r[\"safetyScore\"]}') for r in (d.get('data') or [])[:5]]"

# Generate a PDF report?
curl -s -X POST http://localhost:8080/api/reports/driver/1/monthly \
  -H "Content-Type: application/json" \
  -d '{"year":2026,"month":8}' | python3 -m json.tool

# AI service health?
curl -s http://localhost:8000/health

# DB row counts?
psql -d gps_tracking -c "SELECT
  (SELECT COUNT(*) FROM drivers) AS drivers,
  (SELECT COUNT(*) FROM vehicles) AS vehicles,
  (SELECT COUNT(*) FROM trips) AS trips,
  (SELECT COUNT(*) FROM alerts) AS alerts;"
```

---

## Kill All Services

```bash
lsof -ti:8080 | xargs kill -9 2>/dev/null   # Backend
lsof -ti:5173 | xargs kill -9 2>/dev/null   # Frontend
lsof -ti:8000 | xargs kill -9 2>/dev/null   # AI Service
```

---

## Flyway Migrations (run automatically on backend start)

| Version | Description |
|---------|-------------|
| V1 | Core fleet tables (drivers, vehicles, trips, alerts, telemetry) |
| V2 | Raw sensor tables (acceleration, GPS/AQI, camera, audio, ultrasonic) |
| V3 | Performance indexes (driver_id, vehicle_id, occurred_at, PostGIS spatial) |
| V4 | Seed reference data (system config) |
| V5 | Add driver_daily_stats.created_at column |
| V6 | Increase alerts.reference to VARCHAR(200) |

---

## Environment Variables (`.env`)

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gps_tracking
DB_USERNAME=postgres
DB_PASSWORD=your_password
SERVER_PORT=8080
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your_email@gmail.com
EMAIL_PASSWORD=your_app_password
FLEET_EMAIL=fleet@trackfleet.com
MANAGER_EMAIL=manager@trackfleet.com
MSG91_AUTH_KEY=your_key
FLEET_PHONE=919876543200
MANAGER_PHONE=919876543201
PYTHON_AI_SERVICE_URL=http://localhost:8000
VITE_API_URL=http://localhost:8080
```
