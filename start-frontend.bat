@echo off
REM start-frontend.bat — Starts the React Vite frontend on port 5173 (Windows)
cd /d "%~dp0\frontend"

echo ======================================================
echo  GPS Fleet Frontend (React + Vite)
echo  URL: http://localhost:5173
echo ======================================================
echo.

IF NOT EXIST "node_modules" (
    echo [INFO] Installing frontend dependencies...
    call npm install
)

npm run dev
