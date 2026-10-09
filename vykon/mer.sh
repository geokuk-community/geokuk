#!/usr/bin/env bash
# Měření výkonu: stará verze proti nové na stejných syntetických datech.
#
#   vykon/mer.sh [STARA_REF] [NOVA_REF]      výchozí origin/main a HEAD
#
# Proměnné:
#   CO        části: db (načtení databází GeoGetu a GSAKu), hint, kresleni, program (spuštěný program),
#             kesoidy (kreslení ikon a teček pro počty v IKONY a TECKY, jen nová verze; ve výchozích není),
#             paralelne (obě databáze naráz v 1 a 2 vláknech, jen nová verze; ve výchozích není),
#             offline (vykreslování offline mapy .map, jen nová verze; ve výchozích není),
#             výchozí db hint kresleni program
#   N, POPIS  keší v databázích a délka popisu (100000, 20000)
#   MAPY, TEMA, MISTO, ZOOMY  offline mapa: složka s .map (výchozí syntetická testovací mapa), téma,
#             místo lat,lon a zoomy (výchozí 50.003,14.405 a 13,15,17)
#   PRACOVNI  složka pro worktree a databáze (výchozí target/vykon)
#   VYSTUP    soubor s výsledkem (výchozí PRACOVNI/<stroj>-<datum>.txt)
set -euo pipefail

SADA="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SADA/.." && pwd)"
STARA="${1:-origin/main}"
NOVA="${2:-HEAD}"
CO="${CO:-db hint kresleni program}"
N="${N:-100000}"
POPIS="${POPIS:-20000}"
PRACOVNI="${PRACOVNI:-$REPO/target/vykon}"
mkdir -p "$PRACOVNI"
VYSTUP="${VYSTUP:-$PRACOVNI/$(hostname)-$(date +%Y-%m-%d-%H%M).txt}"
PY="$(command -v python3 || command -v python)"
SEP=":"
nativni() { echo "$1"; }
case "$(uname -s)" in
	MINGW* | MSYS* | CYGWIN*)
		SEP=";"
		nativni() { cygpath -w "$1"; }
		;;
esac
LINUX=""
[ "$(uname -s)" = Linux ] && LINUX=1

# Sestaví verzi do worktree a vypíše classpath s harnessem (třídy + závislosti).
priprav() {
	local jmeno="$1" ref="$2" wt="$PRACOVNI/$1"
	git -C "$REPO" worktree remove --force "$wt" >/dev/null 2>&1 || rm -rf "$wt"
	git -C "$REPO" worktree add -q --detach "$wt" "$ref" >&2
	(cd "$wt" && ./mvnw -B -q package -DskipTests >&2 && ./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=cp.txt >&2)
	mkdir -p "$wt/mer"
	local cp
	cp="$(nativni "$wt/target/classes")$SEP$(cat "$wt/cp.txt")"
	# Harness, který verze neumí (třeba hint z databáze ve staré), se přeskočí.
	local f
	for f in $(cd "$SADA/src" && find . -name '*.java'); do
		javac -nowarn -encoding UTF-8 -d "$(nativni "$wt/mer")" -cp "$cp" "$(nativni "$SADA/src/$f")" 2>/dev/null || echo "  ($jmeno: $f nejde přeložit, přeskočeno)" >&2
	done
	echo "$(nativni "$wt/mer")$SEP$cp"
}

java_() { java -Xmx3g -Djava.awt.headless=true -Dstdout.encoding=UTF-8 "$@" 2>/dev/null; }

# Vyhodí soubor z cache operačního systému (jen Linux).
studena() { "$PY" -c "import os,sys;fd=os.open(sys.argv[1],os.O_RDONLY);os.posix_fadvise(fd,0,0,os.POSIX_FADV_DONTNEED)" "$1" 2>/dev/null || true; }

# Smoke testy s velkými daty ve worktree verze: program se spustí celý, s oknem.
program() {
	local wt="$PRACOVNI/$1" displej=()
	if [ -n "$LINUX" ] && [ -z "${DISPLAY:-}" ]; then
		displej=(xvfb-run -a -s "-screen 0 1400x900x24")
	fi
	(cd "$wt" && "${displej[@]}" ./mvnw -B -q -P smoke verify -Dtest=Zadny -Dsurefire.failIfNoSpecifiedTests=false \
		-Dit.test='SmokeIT#velkaData+velkaDatabazeGeogetu' >"$wt/smoke.log" 2>&1) || echo "  (smoke testy skončily chybou, viz $wt/smoke.log)"
	local beh popis zprava
	for beh in "velka:GPX 50000 keší" "geoget:databáze GeoGetu 200000 keší"; do
		popis="${beh#*:}"
		zprava="$wt/target/smoke/${beh%%:*}/${beh%%:*}.properties"
		if [ -f "$zprava" ]; then
			echo "$popis: okno $(hodnota "$zprava" start.oknoMs) ms, načtení keší $(hodnota "$zprava" start.keseMs) ms, paměť po scénáři $(hodnota "$zprava" pamet.mb) MB, nejdelší událost na EDT $(hodnota "$zprava" edt.nejdelsiMs) ms"
		else
			echo "$popis: bez výsledku"
		fi
	done
}

hodnota() { grep -m1 "^$2=" "$1" | cut -d= -f2- | tr -d '\r'; }

{
	echo "# Měření $(date '+%Y-%m-%d %H:%M'), $(hostname), $(uname -s), $(nproc 2>/dev/null || echo ?) CPU, $(java -version 2>&1 | grep -v JAVA_TOOL | head -1)"
	echo "stará: $STARA ($(git -C "$REPO" rev-parse --short "$STARA")), nová: $NOVA ($(git -C "$REPO" rev-parse --short "$NOVA")), N=$N, popis=$POPIS"
} | tee "$VYSTUP"

CP_STARA="$(priprav stara "$STARA")"
CP_NOVA="$(priprav nova "$NOVA")"
DB="$PRACOVNI/db"

if [[ "$CO" == *db* || "$CO" == *hint* || "$CO" == *paralelne* ]] && [ ! -f "$DB/geoget.db3" ]; then
	echo "Generuji databáze do $DB ..." >&2
	"$PY" "$(nativni "$SADA/gen.py")" "$(nativni "$DB")" "$N" "$POPIS"
fi

{
	if [[ "$CO" == *db* ]]; then
		if [ -n "$LINUX" ]; then
			echo "## Načtení databáze (studená = soubor vyhozený z cache)"
		else
			echo "## Načtení databáze (soubor v cache systému)"
		fi
		for db in geoget gsak; do
			for v in stara nova; do
				cp="$CP_STARA"
				[ "$v" = nova ] && cp="$CP_NOVA"
				if [ -n "$LINUX" ]; then
					for i in 1 2; do
						studena "$DB/$db.db3"
						echo "$v studená: $(java_ -cp "$cp" cz.geokuk.plugins.kesoid.importek.MerDb "$db" "$(nativni "$DB/$db.db3")" | grep 'ms,')"
					done
				fi
				for i in 1 2 3; do echo "$v: $(java_ -cp "$cp" cz.geokuk.plugins.kesoid.importek.MerDb "$db" "$(nativni "$DB/$db.db3")" | grep 'ms,')"; done
			done
		done
	fi
	if [[ "$CO" == *hint* ]]; then
		echo "## Hint z databáze (nová verze)"
		for db in geoget gsak; do
			java_ -cp "$CP_NOVA" cz.geokuk.plugins.kesoid.importek.MerHint "$(nativni "$DB/$db.db3")" "$db" || echo "$db: verze hint z databáze neumí"
		done
	fi
	if [[ "$CO" == *kresleni* ]]; then
		echo "## Kreslení (medián, okno 1400x900, dlaždice = clip 256x256)"
		for v in stara nova; do
			cp="$CP_STARA"
			[ "$v" = nova ] && cp="$CP_NOVA"
			echo "### $v"
			java_ -cp "$cp" cz.geokuk.plugins.cesty.MerKresleni
		done
	fi
	if [[ "$CO" == *kesoidy* ]]; then
		echo "## Kreslení kešoidů (nová verze, medián 7 překreslení, okno 1400x900, ikony bez limitu)"
		java_ -cp "$CP_NOVA" cz.geokuk.plugins.kesoid.importek.MerKesoidy ikony "${IKONY:-30000,60000,90000,120000}" tecky "${TECKY:-100000,300000,600000,1000000}" | grep "N="
	fi
	if [[ "$CO" == *paralelne* ]]; then
		echo "## Databáze naráz, každá ve vlastním vlákně (nová verze, prázdný builder)"
		java_ -cp "$CP_NOVA" cz.geokuk.plugins.kesoid.importek.MerRozpad geoget="$(nativni "$DB/geoget.db3")" gsak="$(nativni "$DB/gsak.db3")" kola=3 vlakna=1,2 | grep -E "^(zdroj|kolo)"
	fi
	if [[ "$CO" == *offline* ]]; then
		echo "## Offline mapa (nová verze, dlaždice 256 px s popisky)"
		misto="${MISTO:-50.003,14.405}"
		java_ -cp "$CP_NOVA" cz.geokuk.plugins.mapy.kachle.podklady.MerOffline slozka="$(nativni "${MAPY:-$REPO/src/test/resources/offline-mapy}")" tema="${TEMA:-}" \
			lat="${misto%,*}" lon="${misto#*,}" zoomy="${ZOOMY:-13,15,17}"
	fi
	if [[ "$CO" == *program* ]]; then
		echo "## Spuštěný program (smoke testy s velkými daty)"
		for v in stara nova; do
			echo "### $v"
			program "$v"
		done
	fi
} 2>&1 | tee -a "$VYSTUP"

for v in stara nova; do git -C "$REPO" worktree remove --force "$PRACOVNI/$v" >/dev/null 2>&1 || true; done
echo "Výsledek: $VYSTUP"
