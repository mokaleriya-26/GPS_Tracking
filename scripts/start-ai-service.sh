#!/usr/bin/env bash
# start-ai-service.sh — Start the Python AI/ML microservice
set -e
cd ai-service
[ ! -d ".venv" ] && python3 -m venv .venv
source .venv/bin/activate
pip install -q -r requirements.txt
echo "======================================================"
echo " GPS Fleet AI/ML Service on port 8000"
echo "======================================================"
python3 main.py
