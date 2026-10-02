@echo off
rem Spusti GeoKuk s pribalenou Javou, GeoKuk vedle vytvori zastupce GeoKuk.lnk.
start "" "%~dp0program\runtime\bin\javaw.exe" -XX:-UsePerfData -jar "%~dp0program\start.jar" %*
