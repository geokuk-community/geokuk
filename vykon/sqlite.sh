#!/usr/bin/env bash
# Měření SqlJet × sqlite-jdbc nad syntetickou cache dlaždic, hlavně pro Windows s HDD (návod: poznamky/2026-11-06-sqlite-hdd-navod.md).
#
#   vykon/sqlite.sh
#
# Proměnné:
#   SQLITE_DIR   složka na měřeném disku, kde vznikne soubor mer-sqlite-cache.sqlite (výchozí target/vykon/sqlite); skutečná cache se nepoužije
#   MB           velikost souboru v MB (výchozí 2000; pro studená čtení větší než paměť počítače, nebo po restartu s SKIP_GEN=1)
#   OPAKOVANI    kolikrát se kolo zopakuje, pořadí knihoven se střídá (výchozí 3)
#   SKIP_GEN     1 = soubor se nezakládá znovu, použije se existující z minulého běhu
#   VYSTUP       soubor s výsledkem (výchozí SQLITE_DIR/<stroj>-<datum>.txt)
# Potřeba: JDK 21, Git Bash (na Windows), Maven wrapper z repa. Journal je DELETE (bez WAL), stejně jako cache programu.
set -euo pipefail

SADA="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$SADA/.." && pwd)"
SQLITE_DIR="${SQLITE_DIR:-$REPO/target/vykon/sqlite}"
MB="${MB:-2000}"
OPAKOVANI="${OPAKOVANI:-3}"
mkdir -p "$SQLITE_DIR"
VYSTUP="${VYSTUP:-$SQLITE_DIR/$(hostname)-$(date +%Y-%m-%d-%H%M).txt}"
SEP=":"
nativni() { echo "$1"; }
case "$(uname -s)" in
	MINGW* | MSYS* | CYGWIN*)
		SEP=";"
		nativni() { cygpath -w "$1"; }
		;;
esac

(cd "$REPO" && ./mvnw -B -q package -DskipTests >&2 && ./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=target/vykon-cp.txt >&2)
MER="$REPO/target/vykon/sqlite-mer"
rm -rf "$MER"
mkdir -p "$MER"
CP="$(nativni "$REPO/target/classes")$SEP$(cat "$REPO/target/vykon-cp.txt")"
javac -nowarn -encoding UTF-8 -d "$(nativni "$MER")" -cp "$CP" "$(nativni "$SADA/src/cz/geokuk/plugins/mapy/kachle/podklady/MerSqlite.java")" 2>/dev/null
SOUBOR="$SQLITE_DIR/mer-sqlite-cache.sqlite"
S="$(nativni "$SOUBOR")"

mer() { java -Xmx1g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$(nativni "$MER")$SEP$CP" cz.geokuk.plugins.mapy.kachle.podklady.MerSqlite "$@" 2>&1 | grep -v -E "^SLF4J|^Picked up|^[0-9:. ]+\[.*(INFO|DEBUG|WARN)" || true; }

# Spuštění s kódem výsledku, pro kontroly ochrany (mer výsledek zahazuje).
mer_rc() { java -Xmx1g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$(nativni "$MER")$SEP$CP" cz.geokuk.plugins.mapy.kachle.podklady.MerSqlite "$@" >/dev/null 2>&1; }

# Ochrana skutečné cache: samotest (odkaz, kopie bez značky, špatné jméno) musí projít, jinak se nic nespustí.
if ! mer_rc samotest "$(nativni "$SQLITE_DIR/samotest")"; then
	echo "Samotest ochrany selhal, měření se nespouští (spusť MerSqlite samotest ručně a podívej se na výstup)." >&2
	exit 1
fi

{
	echo "# Měření SqlJet × sqlite-jdbc $(date '+%Y-%m-%d %H:%M'), $(hostname), $(uname -s), $(java -version 2>&1 | grep -v JAVA_TOOL | head -1)"
	echo "složka: $SQLITE_DIR, velikost $MB MB, opakování $OPAKOVANI, journal DELETE"
	if [ -e "$SOUBOR" ] || [ -L "$SOUBOR" ]; then
		if ! mer_rc overit "$S"; then
			echo "Soubor $SOUBOR existuje a není měřicí soubor založený tímto nástrojem (nebo je to odkaz); nemažu ho a končím." >&2
			exit 1
		fi
	fi
	if [ "${SKIP_GEN:-}" != 1 ] || [ ! -f "$SOUBOR" ]; then
		rm -f "$SOUBOR" "$SOUBOR-journal"
		mer gen "$S" "$MB"
	else
		echo "použit existující soubor $(( $(stat -c %s "$SOUBOR" 2>/dev/null || stat -f %z "$SOUBOR") / 1048576 )) MB"
	fi
	for ((i = 1; i <= OPAKOVANI; i++)); do
		if ((i % 2 == 1)); then KNIHOVNY="sqljet jdbc"; else KNIHOVNY="jdbc sqljet"; fi
		echo "## Kolo $i (pořadí: $KNIHOVNY)"
		for k in $KNIHOVNY; do mer otevreni "$S" "$k"; done
		for k in $KNIHOVNY; do mer okno "$S" "$k" 30; done
		for k in $KNIHOVNY; do mer cteni "$S" "$k" 1 3000; done
		for k in $KNIHOVNY; do mer cteni "$S" "$k" 4 1500; done
		for k in $KNIHOVNY; do mer smiseny "$S" "$k" 3 50 20; done
		for k in $KNIHOVNY; do mer zapis "$S" "$k" 50 20; done
		for k in $KNIHOVNY; do mer zapis "$S" "$k" 300 5; done
	done
} 2>&1 | tee "$VYSTUP"
echo "Výsledek: $VYSTUP"
