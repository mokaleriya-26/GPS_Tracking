#!/bin/bash
# start-ai-service.sh — starts the Python AI/ML microservice on port 8000
# Works on macOS, Linux, and WSL2

set -e
cd "$(dirname "$0")/ai-service"

echo "=================================================="
echo " GPS Fleet AI/ML Service"
echo "=================================================="
echo ""

# Use venv if it exists, otherwise check system python3
if [ -f "venv/bin/activate" ]; then
  echo "[INFO] Activating existing virtual environment..."
  source venv/bin/activate
else
  echo "[INFO] Creating Python virtual environment..."
  python3 -m venv venv
  source venv/bin/activate
  echo "[INFO] Installing dependencies..."
  pip install --quiet -r requirements.txt
fi

PORT=${PORT:-8000}
FLASK_DEBUG=${FLASK_DEBUG:-0}

echo "[INFO] Starting AI service on http://localhost:${PORT}"
echo "[INFO] Endpoints: /health  /predict  /analyze-driver  /anomaly-detection"
echo ""

export FLASK_DEBUG
python3 main.py
