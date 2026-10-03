# GPS Fleet Tracking System

A full-stack GPS Vehicle Tracking / Fleet Management system built with **Spring Boot**, **React**, **PostgreSQL/PostGIS**, and a **Python AI/ML microservice**.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 19, Vite 8, Bootstrap 5, Axios, Lucide React |
| **Backend** | Java 17, Spring Boot 3.2.5, Spring Data JPA, Flyway |
| **Database** | PostgreSQL 17 + PostGIS extension |
| **AI/ML Service** | Python 3.11+, Flask 3, scikit-learn |
| **Reports** | iText 7 (server-side PDF generation) |
| **Notifications** | SMTP Email + MSG91 SMS |

---

## Prerequisites

### macOS (local development)
- Java 17+ (`brew install openjdk@17`)
- Maven 3.9+ (`brew install maven`)
- Node.js 20+ and npm (`brew install node`)
- PostgreSQL 17 with PostGIS (`brew install postgresql@17 postgis`)
- Python 3.11+ (`brew install python@3.11`)

### WSL2 (Ubuntu/Debian)
```bash
# Java 17
sudo apt update && sudo apt install -y openjdk-17-jdk maven

# Node.js 20
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs

# PostgreSQL 17 + PostGIS
sudo apt install -y postgresql-17 postgresql-17-postgis-3

# Python 3.11
sudo apt install -y python3.11 python3.11-venv python3-pip
```

---

## 1. Database Setup

### macOS
```bash
# Start PostgreSQL
brew services start postgresql@17

# Create database and enable PostGIS
psql postgres -c "CREATE DATABASE gps_tracking;"
psql gps_tracking -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### WSL2
```bash
sudo service postgresql start
sudo -u postgres psql -c "CREATE DATABASE gps_tracking;"
sudo -u postgres psql -d gps_tracking -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

---

## 2. Environment Configuration

Copy and edit the `.env` file:
```bash
cp .env.example .env   # if .env.example exists
# OR edit .env directly
```

Required variables in `.env`:
```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=gps_tracking
DB_USERNAME=postgres
DB_PASSWORD=your_password

# Backend
SERVER_PORT=8080

# Email (SMTP/Gmail)
EMAIL_HOST=smtp.gmail.com
EMAIL_PORT=587
EMAIL_USER=your_email@gmail.com
EMAIL_PASSWORD=your_app_password
EMAIL_FROM=no-reply@trackfleet.com
FLEET_EMAIL=fleet@trackfleet.com
MANAGER_EMAIL=manager@trackfleet.com

# SMS (MSG91)
MSG91_AUTH_KEY=your_msg91_auth_key
MSG91_SMS_TEMPLATE_ID=your_template_id
MSG91_SENDER_ID=TRKFLT
FLEET_PHONE=919876543200
MANAGER_PHONE=919876543201

# Python AI Service
PYTHON_AI_SERVICE_URL=http://localhost:8000

# Frontend
VITE_API_URL=http://localhost:8080
```

> **Note:** Notifications only fire for **live** alerts (created via API/telemetry).  
> Historical CSV imports are always SILENCED — no emails or SMS are sent.

---

## 3. Load Seed Data

The rich seed dataset (17 drivers, 20 vehicles, 9,082 trips, 11,089 alerts, 6 months of data) is in `data/seed.sql`:

```bash
psql -d gps_tracking -f data/seed.sql
```

> This is done **once**. Flyway migrations (V1–V6) run automatically on backend startup.

---

## 4. Run the System

### Option A — Individual terminals (recommended for development)

**Terminal 1 — Backend (Spring Boot)**
```bash
cd backend-spring
export $(grep -v '^#' ../.env | xargs)
mvn spring-boot:run
# OR use pre-built jar:
java -jar target/tracking-1.0.0.jar
```
Backend starts at: http://localhost:8080  
Swagger UI: http://localhost:8080/swagger-ui.html

**Terminal 2 — Frontend (React/Vite)**
```bash
cd frontend
npm install       # first time only
npm run dev
```
Frontend starts at: http://localhost:5173

**Terminal 3 — Python AI Service** *(optional — system degrades gracefully without it)*
```bash
bash start-ai-service.sh
```
AI service starts at: http://localhost:8000

### Option B — Quick scripts
```bash
# Each in a separate terminal
bash start-backend.sh
bash start-frontend.sh
bash start-ai-service.sh
```

---

## 5. Application Pages

| Page | URL | Description |
|------|-----|-------------|
| Dashboard | http://localhost:5173/ | KPI cards, live fleet overview, top drivers, recent alerts |
| Vehicles | http://localhost:5173/vehicles | Vehicle cards with speed bars, GPS links, status filters |
| Drivers | http://localhost:5173/drivers | Driver list + safety ranking leaderboard |
| Trips | http://localhost:5173/trips | Entry/exit tracking, paginated trip history |
| Alerts | http://localhost:5173/alerts | Alert management with search, filter, resolve, delivery status |
| Reports | http://localhost:5173/reports | Server-side PDF generation (driver/fleet/custom date range) |
| Maintenance | http://localhost:5173/maintenance | Vehicle maintenance records |
| Costs | http://localhost:5173/costs | Operational cost tracking (fuel, tolls, insurance) |
| Sensors | http://localhost:5173/sensors | Raw sensor data (GPS, AQI, acceleration, camera, audio, ultrasonic) |
| Settings | http://localhost:5173/settings | System health, service status, stack version |

**Chatbot:** Click the **💬** button (bottom-right corner) on any page.

---

## 6. API Reference

Full Swagger documentation: http://localhost:8080/swagger-ui.html

Key endpoints:

```
GET  /api/drivers                    — List all drivers
GET  /api/drivers/ranking            — Safety ranking (real scores from driver_daily_stats)
GET  /api/vehicles                   — List all vehicles with driver info
GET  /api/trips?page=0&size=30       — Paginated trip list
GET  /api/alerts?status=OPEN         — Alert list with delivery tracking
POST /api/alerts/{id}/resolve        — Resolve an alert

POST /api/reports/driver/{id}/monthly   — Generate driver monthly PDF
POST /api/reports/driver/{id}/daily     — Generate driver daily PDF
POST /api/reports/driver/{id}/custom    — Custom date range PDF
POST /api/reports/fleet/monthly         — Fleet monthly PDF
GET  /api/reports/{reportId}/pdf        — View PDF inline
GET  /api/reports/{reportId}/download   — Download PDF

POST /api/chat/message               — Chatbot (natural language queries)

POST /api/ai/analyze-driver          — AI driver behaviour analysis
POST /api/ai/anomaly-detection       — Telemetry anomaly detection
GET  /api/ai/driver-ranking          — AI-enhanced ranking
GET  /api/ai/health                  — Python AI service status

POST /api/sensors/acceleration       — Submit acceleration sensor data
POST /api/sensors/gps-aqi            — Submit GPS + AQI sensor data

GET  /api/costs                      — Cost records
GET  /api/maintenance                — Maintenance records
GET  /api/stats/driver/{id}          — Driver daily statistics
```

---

## 7. Chatbot Examples

Open the 💬 chatbot and try:

| Query | Result |
|-------|--------|
| `Who is the safest driver?` | Top-5 driver ranking with scores |
| `Monthly report of DRV001` | Generates and returns PDF link |
| `Show open alerts` | Lists current open alerts |
| `How many trips this month?` | Entry/exit count |
| `Report for Rohan` | Finds driver by name, generates report |
| `Fleet monthly report` | Fleet-wide PDF |

---

## 8. PDF Reports

Reports are generated server-side by Spring Boot using **iText 7** and saved to `reports/generated/`.

Sections included:
- Driver profile header
- Safety score with deductions breakdown
- Trip summary (total trips, distance, duration)
- Alert summary by type and severity
- Overspeed / harsh braking / night driving analysis
- Notification delivery status

---

## 9. Safety Score Formula

```
score = 100
      - (overspeed_events × 5)
      - (harsh_braking_events × 4)
      - (harsh_acceleration_events × 4)
      - (night_driving_seconds / 3600 × 3)
      - (fatigue_events × 8)
      + (clean_trips × 1)

Clamped between 0 and 100.
```

---

## 10. Database Schema

| Table | Rows (seed) | Description |
|-------|-------------|-------------|
| drivers | 17 | Driver profiles |
| vehicles | 20 | Vehicle inventory with IMEI, registration |
| trips | 9,082 | Entry/exit events (trip start = entry, end = exit) |
| alerts | 11,089 | Alert events with delivery tracking |
| driver_daily_stats | 2,172 | Pre-computed daily analytics per driver |
| cost_records | 343 | Fuel, toll, insurance, maintenance costs |
| maintenance_records | 82 | Service and repair records |
| telemetry | 0 | Live GPS points (populated via POST /api/telemetry) |
| acceleration_sensor_data | 0 | ADXL335 readings |
| gps_aqi_sensor_data | 0 | GPS + air quality readings |
| camera_sensor_data | 0 | Camera frame metadata |
| audio_sensor_data | 0 | Microphone samples |
| ultrasonic_distance_data | 0 | Distance sensor readings |

---

## 11. Project Structure

```
GPS_Tracking/
├── backend-spring/          # Java Spring Boot backend
│   ├── src/main/java/com/gps/tracking/
│   │   ├── controller/      # 11 REST controllers
│   │   ├── service/         # 13 services (including ChatService, ReportService)
│   │   ├── entity/          # 14 JPA entities
│   │   ├── repository/      # 9 Spring Data repositories
│   │   ├── dto/             # Data transfer objects
│   │   └── config/          # Security, CORS, Swagger, AI config
│   ├── src/main/resources/
│   │   ├── application.yml  # Spring configuration
│   │   └── db/migration/    # Flyway V1–V6 migrations
│   └── pom.xml
│
├── frontend/                # React 19 + Vite 8
│   └── src/
│       ├── pages/           # 10 pages
│       ├── components/      # Layout, Navbar, Sidebar
│       ├── chatbot/         # ChatbotWidget
│       ├── hooks/           # usePolling
│       └── api/             # Axios API clients
│
├── ai-service/              # Python Flask AI/ML service
│   ├── main.py              # Flask app with 3 endpoints
│   └── requirements.txt
│
├── data/
│   ├── seed.sql             # Rich 6-month demo dataset
│   └── generate_seed.py     # Dataset generator
│
├── reports/generated/       # PDF output directory (gitignored)
├── dataset/                 # Legacy CSV dataset
│
├── .env                     # Environment variables (gitignored)
├── docker-compose.yml       # PostgreSQL + optional services
├── start-backend.sh
├── start-frontend.sh
├── start-ai-service.sh
└── README.md
```

---

## 12. Docker (PostgreSQL only)

```bash
# Start only PostgreSQL with PostGIS
docker-compose up -d postgres

# Then run backend + frontend + AI service locally
```

---

## 13. Troubleshooting

| Problem | Fix |
|---------|-----|
| `Connection refused :8080` | Backend not started. Run `bash start-backend.sh` |
| `Flyway migration failed` | Check PostgreSQL is running and `DB_*` env vars are correct |
| `PostGIS not found` | Run `CREATE EXTENSION postgis;` in your `gps_tracking` DB |
| PDF download 404 | Backend must be running; check `reports/generated/` exists |
| Ranking shows 0 drivers | Run `psql -d gps_tracking -f data/seed.sql` to load demo data |
| AI service unavailable | Normal — system falls back to rule-based scoring automatically |
| CSV reimport on restart | Intentionally skipped when ≥1000 trips exist (seed data detected) |
| Port 8080 in use | `lsof -ti:8080 \| xargs kill -9` |
| Port 5173 in use | `lsof -ti:5173 \| xargs kill -9` |

---

## 14. Git

The following are gitignored by default:
- `.env` and `.env.*`
- `reports/generated/`
- `ai-service/venv/`
- `backend-spring/target/`
- `frontend/node_modules/`
- `frontend/dist/`