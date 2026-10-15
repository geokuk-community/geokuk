#!/usr/bin/env bash
# DEM Copernicus GLO-30 -> HGT 1" v zipu. Vstup (env): BBOX, ZAKLAD, VYSTUP, PRACE.
set -euo pipefail
: "${BBOX:?}" "${ZAKLAD:?}" "${VYSTUP:?}" "${PRACE:?}"
D=$(cd "$(dirname "$0")" && pwd)
source "$D/vstupy.sh"
mkdir -p "$VYSTUP" "$PRACE"
export VYSTUP
"$D/mer.sh" cop2hgt python3 "$D/cop2hgt.py" "$BBOX" "$PRACE/hgt" "$PRACE"
out="$VYSTUP/${ZAKLAD}_hgt.zip"
rm -f "$out"
"$D/mer.sh" zip-hgt bash -c "cp '$D/LICENCE-COPERNICUS.txt' '$PRACE/hgt/LICENCE.txt' && cd '$PRACE/hgt' && zip -q -j -9 '$out' *.hgt LICENCE.txt"
datum=$(cat "$PRACE/datum-dem.txt" 2>/dev/null || date -u +%F)
"$D/meta.py" dem "$out" "$datum" "Copernicus GLO-30 DEM (AWS Open Data copernicus-dem-30m)" "$D/LICENCE-COPERNICUS.txt"
