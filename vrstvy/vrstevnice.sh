#!/usr/bin/env bash
# Vrstevnice (krok 10 m, hlavní po 100, střední po 50) z HGT zipu do mapsforge .map.
# Vstup (env): HGT_ZIP, BBOX, ZAKLAD, VYSTUP, PRACE; volitelně TYP_ZAPISU (výchozí hd), JAVA_OPTS. Vyžaduje pyhgtmap a osmium.
set -euo pipefail
: "${HGT_ZIP:?}" "${BBOX:?}" "${ZAKLAD:?}" "${VYSTUP:?}" "${PRACE:?}"
D=$(cd "$(dirname "$0")" && pwd)
source "$D/vstupy.sh"
mkdir -p "$VYSTUP" "$PRACE/hgt" "$PRACE/kont"
export VYSTUP
unzip -q -o "$HGT_ZIP" -d "$PRACE/hgt"
cd "$PRACE"
i=0
: > prikazy.txt
for f in hgt/*.hgt; do
  i=$((i + 1))
  b=$(basename "$f" .hgt)
  echo "pyhgtmap --step=10 --line-cat=100,50 --pbf --max-nodes-per-way=1000 --simplifyContoursEpsilon=0.00001 --max-nodes-per-tile=0 --start-node-id=$((i * 1000000000)) --start-way-id=$((i * 1000000000)) --output-prefix=kont/$b $f" >> prikazy.txt
done
( while sleep 30; do echo "pamet: $(free -m | sed -n 2p) | disk: $(df -m / | tail -1 | tr -s " " | cut -d" " -f3,4) MB | zatez: $(cut -d" " -f1-3 /proc/loadavg) | kont: $(du -sm kont | cut -f1) MB, $(ls kont | wc -l) souboru"; done ) &
sledovani=$!
"$D/mer.sh" pyhgtmap bash -c "xargs -P ${PARALELNE:-2} -I{} bash -c '{}' < prikazy.txt"
kill "$sledovani" 2>/dev/null || true
# Soubory dlaždic jsou seřazené a mají disjunktní id, merge běží streamově; sort drží vše v paměti.
"$D/mer.sh" osmium-merge osmium merge --overwrite -o kont.pbf kont/*.pbf || "$D/mer.sh" osmium-sort osmium sort --overwrite -o kont.pbf kont/*.pbf
out="$VYSTUP/${ZAKLAD}.vrstevnice.map"
"$D/mer.sh" zapis-map osmosis -q --rb file=kont.pbf \
  --mw file="$out" type="${TYP_ZAPISU:-hd}" bbox="$BBOX" threads="$(nproc)" \
  tag-conf-file="$D/tag-mapping-vrstevnice.xml" zoom-interval-conf=5,0,7,10,8,11,14,12,21 \
  comment="Contours from Copernicus GLO-30 DEM. Produced using Copernicus WorldDEM-30 (c) DLR e.V. 2010-2014 and (c) Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved. The organisations in charge of the Copernicus programme by law or by delegation do not incur any liability for any use of the Copernicus WorldDEM-30."
cp "$D/LICENCE-COPERNICUS.txt" "$VYSTUP/${ZAKLAD}.vrstevnice.LICENCE.txt"
"$D/meta.py" vrstevnice "$out" "$(python3 -I -c 'import json,sys;print(json.load(open(sys.argv[1]))["datumDat"])' "${HGT_ZIP%.zip}.meta.json" 2>/dev/null || date -u +%F)" "Copernicus GLO-30 DEM (AWS Open Data)" "$D/LICENCE-COPERNICUS.txt" '{"zoom":{"min":12,"max":21},"krokVrstevnic":10}'
