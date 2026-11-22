#!/bin/sh
# Zkouška hotového dmg pro macOS: zkontroluje obsah, spustí přenosnou verzi s Javou pro procesor počítače (přes GeoKuk.app) i s Javou pro Intel
# (pod Rosettou, s výměnou staženého jaru), v CI i aplikaci ve složce Aplikace s daty v ~/GeoKuk a ověří, že se GeoKuk nespustí přímo z dmg.
#   .github/skripty/test-dmg.sh GeoKuk-macos.dmg
set -u
DMG=$1
# Skutečná cesta (/private/var/…), s ní běží procesy.
KOREN=$(cd "$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/zkouska-dmg.XXXXXX")" && pwd -P)
MNT="$KOREN/mnt"
PROBLEMY=0
chyba() {
	echo "::error::$*"
	PROBLEMY=$((PROBLEMY + 1))
}
uklid() {
	pkill -9 -f "$KOREN/" 2>/dev/null
	hdiutil detach "$MNT" -force -quiet 2>/dev/null
}
trap uklid EXIT

# Bez kontroly aktualizací, ať program nečeká na síť a neotevírá dialog.
nastaveni() {
	mkdir -p "$1"
	cat > "$1/nastaveni.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8" standalone="no"?><!DOCTYPE preferences SYSTEM "http://java.sun.com/dtd/preferences.dtd"><preferences EXTERNAL_XML_VERSION="1.0"><root type="user"><map/><node name="geokuk"><map/><node name="current"><map/><node name="vseobecne"><map><entry key="nextUpdateCheckTimestamp" value="9223372036854775807"/></map></node></node></node></root></preferences>
XML
}

# Počká na proces GeoKuku z jaru $1 a na dokončený start v logu ve složce dat $2, vypíše PID.
cekej() {
	for _ in $(seq 1 120); do
		PID=$(pgrep -f -- "-jar $1" | head -n1)
		if [ -n "$PID" ] && grep -qs "start dokončen" "$2/log/geokuk.log"; then
			echo "$PID"
			return 0
		fi
		sleep 1
	done
	return 1
}

ukonci() {
	kill "$1" 2>/dev/null
	for _ in $(seq 1 20); do
		kill -0 "$1" 2>/dev/null || return 0
		sleep 1
	done
	kill -9 "$1" 2>/dev/null
}

mkdir "$MNT"
hdiutil attach -nobrowse -readonly -mountpoint "$MNT" "$DMG" -quiet || { echo "::error::dmg nejde připojit"; exit 1; }

echo "== Obsah"
C="$MNT/GeoKuk.app/Contents"
for f in "$C/MacOS/GeoKuk" "$C/Info.plist" "$C/PkgInfo" "$C/Resources/geokuk.icns" "$C/Resources/licence/LICENSE" "$C/Resources/program/geokuk.jar" \
	"$C/Resources/program/start.jar" "$C/Resources/program/runtime-arm64/bin/java" "$C/Resources/program/runtime-x64/bin/java" "$MNT/CTIMNE.txt"; do
	[ -e "$f" ] || chyba "V dmg chybí ${f#"$MNT"/}"
done
[ -x "$C/MacOS/GeoKuk" ] || chyba "Spouštěč GeoKuk.app není spustitelný"
[ "$(readlink "$MNT/Aplikace")" = /Applications ] || chyba "V dmg chybí odkaz Aplikace"
[ "$(lipo -archs "$C/Resources/program/runtime-arm64/bin/java")" = arm64 ] || chyba "runtime-arm64 není pro arm64"
[ "$(lipo -archs "$C/Resources/program/runtime-x64/bin/java")" = x86_64 ] || chyba "runtime-x64 není pro x86_64"
VERZE=$(unzip -p "$C/Resources/program/geokuk.jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Geokuk-Version: //p')
[ "$(defaults read "$C/Info.plist" CFBundleShortVersionString)" = "${VERZE%%-*}" ] || chyba "Verze v Info.plist neodpovídá geokuk.jar $VERZE"

if [ "$(sysctl -n hw.optional.arm64 2>/dev/null)" = 1 ]; then NATIVNI=runtime-arm64; else NATIVNI=runtime-x64; fi

echo "== Přenosná verze přes GeoKuk.app ($NATIVNI)"
G="$KOREN/prenosna/GeoKuk"
mkdir -p "$G"
ditto "$MNT/GeoKuk.app" "$G/GeoKuk.app"
P="$G/GeoKuk.app/Contents/Resources/program"
nastaveni "$G/data"
open "$G/GeoKuk.app"
if PID=$(cekej "$P/geokuk.jar" "$G/data"); then
	PRIKAZ=$(ps -o command= -p "$PID")
	case "$PRIKAZ" in "$P/$NATIVNI/bin/java "*) ;; *) chyba "GeoKuk neběží v $NATIVNI: $PRIKAZ" ;; esac
	case "$PRIKAZ" in *-Xdock:name=GeoKuk*) ;; *) chyba "GeoKuk nemá jméno v Docku: $PRIKAZ" ;; esac
	[ -f "$G/data/bezi.lock" ] || chyba "Ve složce data vedle aplikace chybí bezi.lock"
	ukonci "$PID"
else
	chyba "Přenosná verze se nespustila"
fi

echo "== Java pro Intel a výměna staženého jaru"
cp "$P/geokuk.jar" "$P/geokuk.jar.new"
rm -f "$G/data/log/geokuk.log"
(cd "$P" && runtime-x64/bin/java -XX:-UsePerfData -jar start.jar)
if PID=$(cekej "$P/geokuk.jar" "$G/data"); then
	case "$(ps -o command= -p "$PID")" in "$P/runtime-x64/bin/java "*) ;; *) chyba "GeoKuk neběží v runtime-x64" ;; esac
	ukonci "$PID"
else
	chyba "GeoKuk s Javou pro Intel se nespustil"
fi
[ ! -e "$P/geokuk.jar.new" ] && [ -f "$P/geokuk.jar.bak" ] || chyba "Spouštěč nevyměnil geokuk.jar.new"

# Jen na čistém runneru, lokálně by zkouška zapsala do Aplikací a do ~/GeoKuk.
if [ "${CI:-}" = true ]; then
	echo "== Aplikace ve složce Aplikace, data v ~/GeoKuk"
	if [ -e /Applications/GeoKuk.app ] || [ -e "$HOME/GeoKuk" ]; then
		chyba "GeoKuk už je v /Applications nebo v ~/GeoKuk"
	else
		ditto "$MNT/GeoKuk.app" /Applications/GeoKuk.app
		nastaveni "$HOME/GeoKuk/data"
		open /Applications/GeoKuk.app
		if PID=$(cekej /Applications/GeoKuk.app/Contents/Resources/program/geokuk.jar "$HOME/GeoKuk/data"); then
			[ -f "$HOME/GeoKuk/data/bezi.lock" ] || chyba "V ~/GeoKuk/data chybí bezi.lock"
			[ ! -e /Applications/data ] || chyba "GeoKuk v Aplikacích založil /Applications/data"
			ukonci "$PID"
		else
			chyba "GeoKuk ve složce Aplikace se nespustil"
		fi
		rm -rf /Applications/GeoKuk.app
	fi
fi

echo "== Spuštění přímo z dmg se odmítne"
open "$MNT/GeoKuk.app"
# Nový dmg macOS při prvním spuštění teprve zaregistruje, spouštěč se může objevit až po chvíli.
HLASKA=""
for _ in $(seq 1 30); do
	pgrep -f -- "$MNT/GeoKuk.app/Contents/Resources/program/runtime" >/dev/null && HLASKA=ano && break
	sleep 1
done
sleep 5
pgrep -f -- "-jar $MNT/GeoKuk.app/Contents/Resources/program/geokuk.jar" >/dev/null && chyba "GeoKuk se spustil přímo z dmg"
if [ -z "$HLASKA" ] || ! pgrep -f -- "$MNT/GeoKuk.app/Contents/Resources/program/runtime" >/dev/null; then
	chyba "Spouštěč z dmg neukazuje hlášku"
	ps -axo pid,command | grep -i geokuk | grep -v grep
fi
# Spouštěč s modálním dialogem na SIGTERM neskončí.
pkill -9 -f -- "$MNT/GeoKuk.app/"

if [ "$PROBLEMY" -gt 0 ]; then
	echo "Zkouška dmg: $PROBLEMY problémů"
	exit 1
fi
echo "Zkouška dmg v pořádku"
