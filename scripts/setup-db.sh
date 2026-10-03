#!/usr/bin/env bash
# setup-db.sh — Set up PostgreSQL + PostGIS database
# Run once before starting the backend for the first time.
set -e
DB_NAME="${DB_NAME:-gps_tracking}"
DB_USER="${DB_USERNAME:-postgres}"
echo "======================================================"
echo " GPS Fleet Tracking System — Database Setup"
echo " Database: $DB_NAME | User: $DB_USER"
echo "======================================================"
if ! pg_isready -q 2>/dev/null; then
    echo "Starting PostgreSQL..."
    sudo service postgresql start 2>/dev/null || sudo systemctl start postgresql 2>/dev/null || true
    sleep 2
fi
echo "[1/3] Creating database..."
psql -U "$DB_USER" -c "CREATE DATABASE $DB_NAME;" 2>/dev/null && echo "  Created" || echo "  Already exists"
echo "[2/3] Enabling PostGIS..."
psql -U "$DB_USER" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS postgis;" && echo "  PostGIS enabled"
psql -U "$DB_USER" -d "$DB_NAME" -c "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";" 2>/dev/null || true
echo "[3/3] Verifying PostGIS..."
VERSION=$(psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT PostGIS_version();" 2>/dev/null | xargs || echo "not installed")
echo "  PostGIS: $VERSION"
echo ""
echo "Done! Next: bash scripts/start-backend.sh"
