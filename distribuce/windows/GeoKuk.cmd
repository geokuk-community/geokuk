@echo off
rem Spusti GeoKuk s pribalenou Javou.
start "" "%~dp0runtime\bin\javaw.exe" -XX:-UsePerfData -jar "%~dp0start.jar" %*
