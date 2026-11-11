#!/usr/bin/env bash
# Měření výkonu: stará verze proti nové na stejných syntetických datech.
#
#   vykon/mer.sh [STARA_REF] [NOVA_REF]      výchozí origin/main a HEAD
#
# Proměnné:
#   CO        části: db (načtení databází GeoGetu a GSAKu), hint, kresleni, program (spuštěný program),
#             start (čas do otevření okna, medián ze START_BEHU střídavých běhů; končí chybou, když je nová verze
#             o víc než START_PRAH procent pomalejší; ve výchozích není),
#             kesoidy (kreslení ikon a teček pro počty v IKONY a TECKY, jen nová verze; ve výchozích není),
#             paralelne (obě databáze naráz v 1 a 2 vláknech, jen nová verze; ve výchozích není),
#             offline (vykreslování offline mapy .map, jen nová verze; ve výchozích není),
#             citace (deterministické čítače práce prvních CITACE_S s po startu, CITACE_BEHU běhů, variační koeficient; jen Linux, ve výchozích není),
#             výchozí db hint kresleni program
#   N, POPIS  keší v databázích a délka popisu (100000, 20000)
#   MAPY, TEMA, MISTO, ZOOMY, VLAKNA  offline mapa: složka s .map (výchozí syntetická testovací mapa), téma,
#             místo lat,lon, zoomy a počty vláken (výchozí 50.003,14.405, 13,15,17 a 1,2)
#   CITACE_BEHU, CITACE_S, CITACE_MAPY, CITACE_VERZE  opakování (5), délka scénáře v s (30), počet kopií mapy ve složce offline map (52),
#             verze pro měření (nova; i stara: "stara nova"). Běh je omezen na jádra CITACE_JADRA (0,1) a ActiveProcessorCount=4.
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
		# Dvakrát: poprvé se symboly tématu vykreslují, podruhé se načtou ze souboru jako při dalším spuštění programu.
		rm -rf "$PRACOVNI/offline-symboly"
		java -Xmx3g -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$CP_NOVA" cz.geokuk.plugins.mapy.kachle.podklady.MerOffline slozka="$(nativni "${MAPY:-$REPO/src/test/resources/offline-mapy}")" tema="${TEMA:-}" \
			symboly="$(nativni "$PRACOVNI/offline-symboly")" zoomy=15 n=1 vlakna=1 2>&1 | grep -v -E " (INFO|DEBUG) |^Picked up JAVA_TOOL_OPTIONS|^z15|^halda" | sed 's/^/1. spuštění: /' || true
		echo -n "2. spuštění: "
		# Chybový výstup zůstává vidět: bez něj by špatná cesta k mapám skončila prázdným výsledkem.
		java -Xmx3g -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -cp "$CP_NOVA" cz.geokuk.plugins.mapy.kachle.podklady.MerOffline slozka="$(nativni "${MAPY:-$REPO/src/test/resources/offline-mapy}")" tema="${TEMA:-}" \
			symboly="$(nativni "$PRACOVNI/offline-symboly")" lat="${misto%,*}" lon="${misto#*,}" zoomy="${ZOOMY:-13,15,17}" vlakna="${VLAKNA:-1,2}" 2>&1 | grep -v -E " (INFO|DEBUG) |^Picked up JAVA_TOOL_OPTIONS"
	fi
	if [[ "$CO" == *citace* && -n "$LINUX" ]]; then
		echo "## Čítače práce prvních ${CITACE_S:-30} s po startu (${CITACE_BEHU:-5} běhů, offline mapa, jádra ${CITACE_JADRA:-0,1}, ActiveProcessorCount=4, halda 1 GB, síť odmítnuta)"
		displej=()
		if [ -n "$LINUX" ] && [ -z "${DISPLAY:-}" ]; then
			displej=(xvfb-run -a -s "-screen 0 1400x900x24")
		fi
		omez=()
		command -v taskset >/dev/null 2>&1 && omez=(taskset -c "${CITACE_JADRA:-0,1}")
		for v in ${CITACE_VERZE:-nova}; do
			cp="$CP_NOVA"
			[ "$v" = stara ] && cp="$CP_STARA"
			echo "### $v"
			soubory=()
			for i in $(seq 1 "${CITACE_BEHU:-5}"); do
				f="$PRACOVNI/citace-$v-$i.txt"
				"${displej[@]}" "${omez[@]}" java -XX:ActiveProcessorCount=4 -Xms1g -Xmx1g -XX:+UseG1GC \
					-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=1 -Dhttp.nonProxyHosts=localhost -Dstdout.encoding=UTF-8 -cp "$cp" cz.geokuk.core.program.MerStart \
					mapa="$(nativni "${CITACE_MAPA:-$REPO/src/test/resources/offline-mapy/kukov.map}")" sekund="${CITACE_S:-30}" mapy="${CITACE_MAPY:-52}" 2>/dev/null \
					| grep -E '^(scenar|cpu_ms|alokace_kB|gc|jit|pocitadlo|vykon|jfr|top)[.a-zA-Z0-9_ěščřžýáíéúůťďňĚŠČŘŽÝÁÍÉÚŮ]*=' >"$f" || echo "  (běh $i bez výsledku)"
				soubory+=("$f")
			done
			"$PY" "$(nativni "$SADA/citace.py")" "${soubory[@]}"
		done
	fi
	if [[ "$CO" == *start* ]]; then
		echo "## Start programu (okno otevřeno, medián ze ${START_BEHU:-7} střídavých běhů, prázdná data)"
		jar_stara="$(ls "$PRACOVNI"/stara/target/geokuk-[0-9]*.jar | head -1)"
		jar_nova="$(ls "$PRACOVNI"/nova/target/geokuk-[0-9]*.jar | head -1)"
		displej=()
		if [ -n "$LINUX" ] && [ -z "${DISPLAY:-}" ]; then
			displej=(xvfb-run -a -s "-screen 0 1400x900x24")
		fi
		"${displej[@]}" "$PY" "$(nativni "$SADA/start.py")" "${START_BEHU:-7}" "${START_PRAH:-15}" "$(nativni "$jar_stara")" "$(nativni "$jar_nova")" || touch "$PRACOVNI/start-kod-$?"
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
# Start programu: 3 = nová verze je pomalejší než práh, 2 = okno se neotevřelo, jiný kód = chyba skriptu.
for soubor in "$PRACOVNI"/start-kod-*; do
	if [ -e "$soubor" ]; then
		kod="${soubor##*start-kod-}"
		rm -f "$PRACOVNI"/start-kod-*
		exit "$kod"
	fi
done
