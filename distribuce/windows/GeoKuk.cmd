@echo off
rem Spusti GeoKuk s pribalenou Javou.
start "" "%~dp0runtime\bin\javaw.exe" -jar "%~dp0start.jar" %*
