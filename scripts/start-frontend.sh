#!/usr/bin/env bash
# start-frontend.sh — Start the React frontend
set -e
cd frontend
[ ! -d "node_modules" ] && npm install
echo "======================================================"
echo " GPS Fleet Frontend: http://localhost:5173"
echo " API proxy -> http://localhost:8080"
echo "======================================================"
npm run dev
