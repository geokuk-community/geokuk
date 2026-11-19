#!/bin/sh
# Vygeneruje ikony, geokuk.ico, logo načítání a úvodní obrázek ze SVG v grafika/zdroje do grafika/vystup.
set -e
cd "$(dirname "$0")"
node vykresli.js ../zdroje ../vystup/mezivysledky
python3 -I dokonci.py ../vystup/mezivysledky ../vystup
rm -rf ../vystup/mezivysledky
