#!/usr/bin/env bash
# meta.sh TYP SOUBOR DATUM_DAT ZDROJ_DAT [JSON_NAVIC] – zapíše SOUBOR.meta.json pro katalog (velikost, sha256)
set -euo pipefail
typ=$1; soubor=$2; datum=$3; zdroj=$4; navic=${5:-}
printf '{"typ":"%s","nazev":"%s","velikost":%s,"sha256":"%s","datumDat":"%s","zdrojDat":"%s","vyrobeno":"%s"%s}\n' \
  "$typ" "$(basename "$soubor")" "$(stat -c %s "$soubor")" "$(sha256sum "$soubor" | cut -d' ' -f1)" \
  "$datum" "$zdroj" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${navic:+,$navic}" > "${soubor%.*}.meta.json"
