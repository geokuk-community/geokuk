@echo off
rem Spustí GeoKuk přes javaw a hned skončí, volající nečeká na konec programu.
setlocal
set "BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "BIN=%JAVA_HOME%\bin\"
rem Paměť, kterou JVM nepřijme (32bitová Java), by javaw nespustila;
rem konzolová java ji ověří bez chybového okna.
set "XMX=-Xmx2048m"
"%BIN%java" %XMX% -version >nul 2>&1 || set "XMX=-Xmx1024m"
"%BIN%java" %XMX% -version >nul 2>&1 || set "XMX="
start "" "%BIN%javaw" %XMX% -Djava.net.useSystemProxies=true -jar "%~dp0geokuk.jar"
