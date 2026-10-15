#!/usr/bin/env bash
# Osmosis 0.49.2 s pluginem mapsforge-map-writer 0.25.0 do $NASTROJE (výchozí ./nastroje); do $GITHUB_PATH přidá bin.
set -euo pipefail
D=$(cd "$(dirname "$0")" && pwd)
N=${NASTROJE:-$PWD/nastroje}
mkdir -p "$N"
url=$(curl -fsSL --retry 5 https://api.github.com/repos/openstreetmap/osmosis/releases/tags/0.49.2 \
  | grep -o '"browser_download_url": *"[^"]*\.tgz"' | head -1 | sed 's/.*"\(http[^"]*\)"/\1/')
curl -fL --retry 5 --retry-delay 10 -o "$N/osmosis.tgz" "$url"
mkdir -p "$N/osmosis"
tar -xzf "$N/osmosis.tgz" -C "$N/osmosis"
O=$(dirname "$(dirname "$(find "$N/osmosis" -path '*/bin/osmosis' | head -1)")")
mvn -q -B -f "$D/zapisovac/pom.xml" dependency:copy-dependencies -DoutputDirectory="$O/lib" -DincludeScope=runtime
"$O/bin/osmosis" -v | head -3 || true
echo "$O/bin" >> "${GITHUB_PATH:-/dev/null}"
echo "$O"
