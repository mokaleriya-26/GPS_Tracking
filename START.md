# TrackFleet GPS Fleet Tracking — Setup Guide

> **Quick summary:** 3 services to run. Takes ~10 minutes from clone to running app.

---

## Prerequisites

Install these before starting:

| Tool | Version | Download |
|------|---------|---------|
| Java JDK | 17 or 21 | https://adoptium.net |
| Apache Maven | 3.9+ | https://maven.apache.org/download |
| Node.js + npm | 20+ | https://nodejs.org |
| Python | 3.11+ | https://python.org/downloads |
| PostgreSQL + PostGIS | 17 | See below |

### PostgreSQL + PostGIS

**macOS:**
```bash
brew install postgresql@17 postgis
brew services start postgresql@17
```

**Windows (Docker — easiest):**
```bash
docker compose up -d postgres
```

**Windows (native):** Download PostgreSQL 17 from https://www.postgresql.org/download/windows/ and install the PostGIS bundle via Stack Builder after installation.

---

## 1. Clone the Repository

```bash
git clone https://github.com/mokaleriya-26/GPS_Tracking.git
cd GPS_Tracking
```

---

## 2. Create Environment File

```bash
# macOS / Linux
cp .env.example .env

# Windows (Command Prompt)
copy .env.example .env
```

Open `.env` and fill in your values:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gps_tracking
DB_USERNAME=postgres
DB_PASSWORD=your_password_here   # ← change this
```

> Email (Gmail) and SMS (MSG91) credentials are optional for development.
> Leave them as placeholders if you only want to test locally.

---

## 3. Set Up Database

### macOS / Linux

```bash
psql postgres -c "CREATE DATABASE gps_tracking;"
psql gps_tracking -c "CREATE EXTENSION IF NOT EXISTS postgis;"
psql -d gps_tracking -f data/seed.sql
```

### Windows (Command Prompt — native PostgreSQL)

```cmd
psql -U postgres -c "CREATE DATABASE gps_tracking;"
psql -U postgres -d gps_tracking -c "CREATE EXTENSION IF NOT EXISTS postgis;"
psql -U postgres -d gps_tracking -f data\seed.sql
```

### Windows (Docker)

```bash
# Start PostgreSQL in Docker first
docker compose up -d postgres

# Wait ~10 seconds, then load seed data
docker exec -i gps_postgres psql -U postgres -d gps_tracking < data/seed.sql
```

> **Seed data:** 17 drivers · 20 vehicles · 9,182 trips · 11,089 alerts (6 months of demo data)

---

## 4. Start Spring Boot Backend

Flyway migrations (V1–V6) run automatically on first start.

### macOS / Linux

```bash
cd backend-spring

# Load .env variables and start
export $(grep -v '^#' ../.env | xargs)
java -jar target/tracking-1.0.0.jar
```

If you haven't built the jar yet:
```bash
cd backend-spring
mvn clean package -DskipTests
export $(grep -v '^#' ../.env | xargs)
java -jar target/tracking-1.0.0.jar
```

### Windows (Command Prompt)

```cmd
cd backend-spring

REM Set environment variables from .env
for /f "usebackq tokens=1,* delims==" %%A in (`findstr /v "^#" ..\env`) do set %%A=%%B

java -jar target\tracking-1.0.0.jar
```

Or use PowerShell:
```powershell
cd backend-spring

# Read .env and set variables
Get-Content ..\.env | Where-Object { $_ -notmatch "^#" -and $_ -match "=" } | ForEach-Object {
    $parts = $_ -split "=", 2
    [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
}

java -jar target\tracking-1.0.0.jar
```

**Expected output:**
```
Started GpsTrackingApplication in 2.6 seconds
[CSV IMPORT] Skipped — rich seed data already loaded (9186 trips in DB).
```

Backend → http://localhost:8080  
Swagger UI → http://localhost:8080/swagger-ui.html

---

## 5. Start Python AI Service *(optional)*

The backend falls back to rule-based scoring automatically if this service is down.

### macOS / Linux

```bash
cd ..   # back to GPS_Tracking root
bash start-ai-service.sh
```

### Windows (Command Prompt)

```cmd
cd ..   # back to GPS_Tracking root
start-ai-service.bat
```

Or manually:
```cmd
cd ai-service
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

AI Service → http://localhost:8000  
Health check → http://localhost:8000/health

---

## 6. Start React Frontend

```bash
cd frontend

# First time only — install dependencies
npm install

# Start development server
npm run dev
```

**Same command on macOS, Linux, and Windows.**

Frontend → http://localhost:5173

---

## All 3 Services Running ✅

| Service | URL | Terminal |
|---------|-----|---------|
| React Frontend | http://localhost:5173 | Terminal 1 |
| Spring Boot Backend | http://localhost:8080 | Terminal 2 |
| Python AI Service | http://localhost:8000 | Terminal 3 (optional) |

Open **http://localhost:5173** in your browser.

---

## Quick Health Checks

```bash
# Backend responding?
curl http://localhost:8080/api/drivers

# Driver ranking working?
curl http://localhost:8080/api/drivers/ranking

# AI service up?
curl http://localhost:8000/health

# Database has data?
psql -d gps_tracking -c "SELECT COUNT(*) FROM trips;"
```

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
| Settings | http://localhost:5173/settings |

**Chatbot:** 💬 button in the bottom-right corner of every page.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `Port 8080 already in use` | `lsof -ti:8080 \| xargs kill -9` (macOS/Linux) or `netstat -ano \| findstr 8080` then `taskkill /PID <pid> /F` (Windows) |
| `Port 5173 already in use` | `lsof -ti:5173 \| xargs kill -9` (macOS/Linux) |
| `Connection refused: 8080` | Backend not started yet |
| Flyway migration error | Check `.env` DB credentials; ensure PostgreSQL is running |
| `PostGIS extension not found` | Run `CREATE EXTENSION postgis;` in the `gps_tracking` DB |
| PDF download 404 | Backend must be running; `reports/generated/` is created automatically |
| Ranking shows 0 drivers | Load seed data: `psql -d gps_tracking -f data/seed.sql` |
| AI service unavailable | Normal — system falls back to rule-based scoring automatically |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 19, Vite 8, Bootstrap 5, Axios, Lucide React |
| Backend | Java 17, Spring Boot 3.2.5, Spring Data JPA, Flyway |
| Database | PostgreSQL 17 + PostGIS |
| AI/ML Service | Python 3.11+, Flask 3 |
| PDF Reports | iText 7 (server-side) |
| Notifications | SMTP Email + MSG91 SMS |

---

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_HOST` | ✅ | PostgreSQL host (usually `localhost`) |
| `DB_PORT` | ✅ | PostgreSQL port (default `5432`) |
| `DB_NAME` | ✅ | Database name (use `gps_tracking`) |
| `DB_USERNAME` | ✅ | PostgreSQL username |
| `DB_PASSWORD` | ✅ | PostgreSQL password |
| `SERVER_PORT` | ✅ | Spring Boot port (default `8080`) |
| `EMAIL_USER` | ⬜ Optional | Gmail address for alert emails |
| `EMAIL_PASSWORD` | ⬜ Optional | Gmail App Password |
| `MSG91_AUTH_KEY` | ⬜ Optional | MSG91 auth key for SMS |
| `PYTHON_AI_SERVICE_URL` | ⬜ Optional | AI service URL (default `http://localhost:8000`) |
| `VITE_API_URL` | ⬜ Optional | Backend URL for frontend (default `http://localhost:8080`) |

> **Security:** Never commit `.env` to Git. It is gitignored. Only commit `.env.example`.

---

## Flyway Migrations (run automatically on backend start)

| Version | Description |
|---------|-------------|
| V1 | Core fleet tables: drivers, vehicles, trips, alerts, telemetry |
| V2 | Sensor tables: acceleration, GPS/AQI, camera, audio, ultrasonic |
| V3 | Performance indexes + PostGIS spatial index |
| V4 | Reference seed data (system config) |
| V5 | Add `driver_daily_stats.created_at` column |
| V6 | Increase `alerts.reference` to VARCHAR(200) |
