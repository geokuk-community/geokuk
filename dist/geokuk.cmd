@echo off
rem Spustí GeoKuk přes javaw a hned skončí, volající nečeká na konec programu.
setlocal
rem Aktualizace stažená programem: nový jar nahradí starý, ten zůstane jako .bak.
if exist "%~dp0geokuk.jar.new" (
  if exist "%~dp0geokuk.jar" move /y "%~dp0geokuk.jar" "%~dp0geokuk.jar.bak" >nul
  move /y "%~dp0geokuk.jar.new" "%~dp0geokuk.jar" >nul
)
set "BIN="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "BIN=%JAVA_HOME%\bin\"
rem Paměť, kterou JVM nepřijme (32bitová Java), by javaw nespustila;
rem konzolová java ji ověří bez chybového okna.
set "XMX=-Xmx3072m"
"%BIN%java" %XMX% -version >nul 2>&1 || set "XMX=-Xmx2048m"
"%BIN%java" %XMX% -version >nul 2>&1 || set "XMX=-Xmx1024m"
"%BIN%java" %XMX% -version >nul 2>&1 || set "XMX="
start "" "%BIN%javaw" %XMX% -Djava.net.useSystemProxies=true -jar "%~dp0geokuk.jar"
