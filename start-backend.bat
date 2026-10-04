@echo off
REM start-backend.bat — Starts the Spring Boot backend on port 8080 (Windows)
cd /d "%~dp0"

IF NOT DEFINED JAVA_HOME (
    IF EXIST "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    )
)

IF NOT EXIST "backend-spring\target\tracking-app.jar" (
    echo [INFO] JAR not found. Building Spring Boot backend with Maven...
    cd backend-spring
    call mvn package -DskipTests
    cd ..
)

echo ======================================================
echo  GPS Fleet Backend (Spring Boot)
echo  Port: 8080
echo  Swagger UI: http://localhost:8080/swagger-ui.html
echo ======================================================
echo.

IF EXIST ".env" (
    echo [INFO] Loading environment variables from .env...
    for /f "usebackq tokens=1,* delims==" %%A in (`findstr /v "^#" .env`) do (
        set "%%A=%%B"
    )
)

java -jar backend-spring\target\tracking-app.jar
