@echo off
REM start-ai-service.bat — starts the Python AI/ML microservice on port 8000 (Windows)
REM Requires: Python 3.11+ installed and on PATH

cd /d "%~dp0\ai-service"

echo ==================================================
echo  GPS Fleet AI/ML Service ^(Windows^)
echo ==================================================
echo.

IF EXIST "venv\Scripts\activate.bat" (
    echo [INFO] Activating existing virtual environment...
    call venv\Scripts\activate.bat
) ELSE (
    echo [INFO] Creating Python virtual environment...
    python -m venv venv
    call venv\Scripts\activate.bat
    echo [INFO] Installing dependencies...
    pip install -r requirements.txt
)

SET PORT=%PORT:=8000%
echo [INFO] Starting AI service on http://localhost:%PORT%
echo [INFO] Endpoints: /health  /predict  /analyze-driver  /anomaly-detection
echo.

python main.py
