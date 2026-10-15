#!/usr/bin/env bash
# Trasy (hiking, foot, bicycle, mtb, horse, ski) z Geofabrik pbf do mapsforge .map.
# Vstup (env): REGION (cesta na Geofabriku), BBOX (jih,západ,sever,východ), ZAKLAD (jméno souboru), VYSTUP, PRACE; volitelně TYP_ZAPISU (ram|hd), JAVA_OPTS.
set -euo pipefail
: "${REGION:?}" "${BBOX:?}" "${ZAKLAD:?}" "${VYSTUP:?}" "${PRACE:?}"
D=$(cd "$(dirname "$0")" && pwd)
source "$D/vstupy.sh"
mkdir -p "$VYSTUP" "$PRACE"
export VYSTUP
pbf="$PRACE/zdroj.osm.pbf"
"$D/mer.sh" stazeni-pbf curl -fL --retry 5 --retry-delay 10 -o "$pbf" "https://download.geofabrik.de/${REGION}-latest.osm.pbf"
curl -fsSL --retry 5 "https://download.geofabrik.de/${REGION}-latest.osm.pbf.md5" | awk -v f="$pbf" '{print $1"  "f}' | md5sum -c -
datum=$(osmium fileinfo -g header.option.osmosis_replication_timestamp "$pbf" || true)
datum=${datum:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}
"$D/mer.sh" osmium-filtr osmium tags-filter --overwrite -o "$PRACE/trasy.pbf" "$pbf" r/route=hiking,foot,bicycle,mtb,horse,ski
rm -f "$pbf"
"$D/mer.sh" addfmreltags perl "$D/addfmreltags.pl" "$PRACE/trasy.pbf" "$PRACE"
test -s "$PRACE/trasy.pbf-fmrel.pbf"
out="$VYSTUP/${ZAKLAD}_trasy.map"
"$D/mer.sh" zapis-map osmosis -q --rb file="$PRACE/trasy.pbf-fmrel.pbf" \
  --mw file="$out" type="${TYP_ZAPISU:-ram}" bbox="$BBOX" threads="$(nproc)" \
  tag-conf-file="$D/tag-mapping-trasy.xml" zoom-interval-conf=5,0,7,10,8,11,14,12,21 \
  comment="(c) OpenStreetMap contributors (ODbL), Geofabrik $datum"
cp "$D/LICENCE-OSM.txt" "$VYSTUP/${ZAKLAD}_trasy.LICENCE.txt"
"$D/meta.py" trasy "$out" "${datum%%T*}" "Geofabrik ${REGION}-latest.osm.pbf ($datum)" "$D/LICENCE-OSM.txt" '{"zoom":{"min":0,"max":21}}'
curl -fsSL --retry 3 -o "$VYSTUP/${ZAKLAD}.poly" "https://download.geofabrik.de/${REGION}.poly" || true
