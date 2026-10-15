#!/usr/bin/env bash
# DEM Copernicus GLO-30 -> HGT 1" v zipu. Vstup (env): BBOX, ZAKLAD, VYSTUP, PRACE.
set -euo pipefail
: "${BBOX:?}" "${ZAKLAD:?}" "${VYSTUP:?}" "${PRACE:?}"
D=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$VYSTUP" "$PRACE"
export VYSTUP
"$D/mer.sh" cop2hgt python3 "$D/cop2hgt.py" "$BBOX" "$PRACE/hgt" "$PRACE"
out="$VYSTUP/${ZAKLAD}_hgt.zip"
rm -f "$out"
"$D/mer.sh" zip-hgt bash -c "cd '$PRACE/hgt' && zip -q -j -9 '$out' *.hgt"
datum=$(date -u -d "$(curl -fsSI https://copernicus-dem-30m.s3.amazonaws.com/Copernicus_DSM_COG_10_N50_00_E013_00_DEM/Copernicus_DSM_COG_10_N50_00_E013_00_DEM.tif | sed -n 's/^[Ll]ast-[Mm]odified: *//p' | tr -d '\r')" +%F 2>/dev/null || date -u +%F)
"$D/meta.sh" dem "$out" "$datum" "Copernicus GLO-30 DEM (AWS Open Data copernicus-dem-30m)"
