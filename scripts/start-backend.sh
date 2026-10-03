#!/usr/bin/env bash
# start-backend.sh — Start the Spring Boot backend
set -e
[ -f ".env" ] && export $(grep -v '^#' .env | xargs) && echo "Loaded .env"
cd backend-spring
echo "======================================================"
echo " GPS Fleet Backend on port ${SERVER_PORT:-8080}"
echo " DB: ${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-gps_tracking}"
echo " Swagger: http://localhost:${SERVER_PORT:-8080}/swagger-ui/index.html"
echo "======================================================"
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m"
