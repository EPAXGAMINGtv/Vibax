@echo off
REM StorageAPI Test Script
REM This script tests the StorageAPI with the StorageServer

echo Starting StorageAPI test...
echo.

REM Check if Java is available
where java >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found in PATH. Please install Java 11+.
    echo.
    echo To install Java:
    echo   1. Download from https://adoptium.net/
    echo   2. Install JDK 11 or later
    echo   3. Add Java bin directory to your PATH
    echo   4. Restart this command prompt
    pause
    exit /b 1
)

echo Java found:
java -version
echo.

REM Build StorageAPI
echo Building StorageAPI...
gradlew :StorageAPI:jar
if errorlevel 1 (
    echo ERROR: Failed to build StorageAPI
    pause
    exit /b 1
)

echo.
echo StorageAPI built successfully!
echo.

REM Check if StorageServer is running on port 8000
netstat -ano | findstr :8000 >nul
if errorlevel 1 (
    echo WARNING: StorageServer not detected on port 8000
    echo.
    echo To test the API, please start StorageServer:
    echo   gradlew :StorageServer:run
    echo.
    echo Then run this test script again.
) else (
    echo StorageServer detected on port 8000 - running API tests...
    echo.
    java -cp "StorageAPI/build/libs/StorageAPI-1.0.0.jar" StorageAPITest
)

echo.
echo Test completed.
pause