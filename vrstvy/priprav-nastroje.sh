#!/usr/bin/env bash
# Osmosis 0.49.2 s pluginem mapsforge-map-writer 0.25.0 do $NASTROJE (výchozí ./nastroje); do $GITHUB_PATH přidá bin.
set -euo pipefail
D=$(cd "$(dirname "$0")" && pwd)
N=${NASTROJE:-$PWD/nastroje}
mkdir -p "$N"
curl -fsSL --retry 5 -o "$N/release.json" https://api.github.com/repos/openstreetmap/osmosis/releases/tags/0.49.2
grep -o '"browser_download_url": *"[^"]*"' "$N/release.json" || true
url=$(grep -o '"browser_download_url": *"[^"]*\.tar"' "$N/release.json" | head -1 | sed 's/.*"\(http[^"]*\)"/\1/')
[ -n "$url" ] || { echo "Archiv Osmosis nenalezen" >&2; exit 1; }
curl -fL --retry 5 --retry-delay 10 -o "$N/osmosis.tar" "$url"
OSMOSIS_SHA256=${OSMOSIS_SHA256:-}
sha=$(sha256sum "$N/osmosis.tar" | cut -d' ' -f1)
echo "osmosis.tar sha256: $sha"
if [ -n "$OSMOSIS_SHA256" ] && [ "$sha" != "$OSMOSIS_SHA256" ]; then echo "Nesouhlasí sha256 Osmosis" >&2; exit 1; fi
mkdir -p "$N/osmosis"
tar -xf "$N/osmosis.tar" -C "$N/osmosis"
O=$(dirname "$(dirname "$(find "$N/osmosis" -path '*/bin/osmosis' | head -1)")")
mvn -q -B -f "$D/zapisovac/pom.xml" dependency:copy-dependencies -DoutputDirectory="$O/lib" -DincludeScope=runtime
mkdir -p "$HOME/.openstreetmap/osmosis/plugins"
mv "$O"/lib/mapsforge-map-writer-*.jar "$HOME/.openstreetmap/osmosis/plugins/"
"$O/bin/osmosis" -v | head -3 || true
echo "$O/bin" >> "${GITHUB_PATH:-/dev/null}"
echo "$O"
