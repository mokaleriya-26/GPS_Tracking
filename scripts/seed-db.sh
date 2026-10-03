#!/usr/bin/env bash
# seed-db.sh — Load fake dataset into PostgreSQL
# Run AFTER the backend has started at least once (Flyway creates schema).
set -e
DB_NAME="${DB_NAME:-gps_tracking}"
DB_USER="${DB_USERNAME:-postgres}"
SEED_FILE="data/seed.sql"
echo "======================================================"
echo " GPS Fleet — Database Seed"
echo "======================================================"
if [ ! -f "$SEED_FILE" ]; then
    echo "Generating seed data..."
    python3 data/generate_seed.py
fi
echo "Loading seed data (may take 30-60 seconds)..."
psql -U "$DB_USER" -d "$DB_NAME" -f "$SEED_FILE" --quiet
echo ""
echo "Done! 17 drivers | 20 vehicles | 9082 trips | 11089 alerts"
