#!/usr/bin/env bash
# Osmosis 0.49.2 s pluginem mapsforge-map-writer 0.25.0 do $NASTROJE (výchozí ./nastroje); do $GITHUB_PATH přidá bin.
set -euo pipefail
D=$(cd "$(dirname "$0")" && pwd)
N=${NASTROJE:-$PWD/nastroje}
mkdir -p "$N"
curl -fL --retry 5 --retry-delay 10 -o "$N/osmosis.tgz" \
  https://github.com/openstreetmap/osmosis/releases/download/0.49.2/osmosis-0.49.2.tgz
tar -xzf "$N/osmosis.tgz" -C "$N"
O="$N/osmosis-0.49.2"
mvn -q -B -f "$D/zapisovac/pom.xml" dependency:copy-dependencies -DoutputDirectory="$O/lib" -DincludeScope=runtime
"$O/bin/osmosis" -v | head -3 || true
echo "$O/bin" >> "${GITHUB_PATH:-/dev/null}"
echo "$O"
