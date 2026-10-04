@echo off
REM load-seed-data.bat — Loads demo seed data into PostgreSQL container (Windows)
cd /d "%~dp0"

echo ======================================================
echo  GPS Fleet — Loading Seed Data into PostgreSQL
echo ======================================================
echo.

docker exec -i gps_postgres psql -U postgres -d gps_tracking < data\seed.sql

if %ERRORLEVEL% equ 0 (
    echo.
    echo [SUCCESS] Seed data loaded successfully! (17 drivers, 20 vehicles, 9182 trips, 11089 alerts)
) else (
    echo.
    echo [ERROR] Failed to load seed data. Ensure docker container 'gps_postgres' is running:
    echo   docker compose up -d postgres
)
