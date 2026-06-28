@echo off
setlocal EnableExtensions
title Vibax Platform Launcher
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\jbr"
set "JAVA=%JAVA_HOME%\bin\java.exe"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ========================================
echo Vibax Platform
echo ========================================

if not exist "%JAVA%" (
    echo Java fehlt:
    echo %JAVA%
    pause
    exit /b 1
)

echo [1/4] Build...
call gradlew.bat build -x test
if errorlevel 1 (
    echo BUILD FAILED
    pause
    exit /b 1
)

copy /Y "config\storageapi.properties" "storageapi.properties" >nul

echo.
echo [2/4] Storage...

start "Storage-1" cmd /k ""%JAVA%" -jar StorageServer\build\libs\StorageServer-1.0-SNAPSHOT.jar --config config\storageserver1.properties --password vibax123"
timeout /t 2 >nul

start "Storage-2" cmd /k ""%JAVA%" -jar StorageServer\build\libs\StorageServer-1.0-SNAPSHOT.jar --config config\storageserver2.properties --password vibax123"
timeout /t 3 >nul

echo.
echo [3/4] Web...

start "Web-1" cmd /k "cd /d "%~dp0" & copy /Y config\storageapi.properties storageapi.properties >nul & %JAVA% -jar WebServer\build\libs\WebServer-1.0-SNAPSHOT.jar --port 8081"
timeout /t 2 >nul

start "Web-2" cmd /k "cd /d "%~dp0" & copy /Y config\storageapi.properties storageapi.properties >nul & %JAVA% -jar WebServer\build\libs\WebServer-1.0-SNAPSHOT.jar --port 8082"
timeout /t 3 >nul

echo.
echo [4/4] Proxy...

start "Proxy" cmd /k "cd /d "%~dp0" & %JAVA% -jar Proxy\build\libs\Proxy-1.0-SNAPSHOT.jar 8080 http://127.0.0.1:8081 http://127.0.0.1:8082"

echo.
echo Vibax laeuft!
start http://localhost:8080

pause >nul