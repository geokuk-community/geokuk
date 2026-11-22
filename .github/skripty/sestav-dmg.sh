#!/bin/sh
# Sestaví GeoKuk-macos.dmg: GeoKuk.app se dvěma přibalenými Javami (Apple Silicon a Intel), spouštěč vybere tu pro procesor Macu.
# Spouští se z kořene repozitáře na macOS (jlink x64 Javy běží pod Rosettou), okno dmg rozmístí dmgbuild 1.6.7
# (pip install dmgbuild==1.6.7, jiný než v PATH jde zadat proměnnou DMGBUILD):
#   .github/skripty/sestav-dmg.sh <složka s geokuk.jar a start.jar> <JDK 21 arm64> <JDK 21 x64> <výstupní dmg>
set -eu
JARY=$1
JDK_ARM64=$2
JDK_X64=$3
DMG=$4

# Stejné moduly jako pro Windows, bez jdk.crypto.mscapi (jen Windows).
MODULY=java.base,java.compiler,java.desktop,java.logging,java.management,java.naming,java.prefs,java.sql,java.xml,\
jdk.accessibility,jdk.charsets,jdk.crypto.ec,jdk.httpserver,jdk.localedata,jdk.management,jdk.unsupported,jdk.zipfs

PRACOVNI=$(mktemp -d)
trap 'rm -rf "$PRACOVNI"' EXIT
OBSAH="$PRACOVNI/obsah"
APP="$OBSAH/GeoKuk.app"
PROGRAM="$APP/Contents/Resources/program"
mkdir -p "$APP/Contents/MacOS" "$PROGRAM"

VERZE=$(unzip -p "$JARY/geokuk.jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Geokuk-Version: //p')
[ -n "$VERZE" ] || { echo "geokuk.jar nemá Geokuk-Version"; exit 1; }
DOPORUCENA=$(sed -n 's/^doporucena=//p' src/main/resources/java.properties | tr -d ' \r')

JAVA=""
for ARCH in arm64 x64; do
	if [ "$ARCH" = arm64 ]; then JDK=$JDK_ARM64; MACH=arm64; else JDK=$JDK_X64; MACH=x86_64; fi
	RUNTIME="$PROGRAM/runtime-$ARCH"
	"$JDK/bin/jlink" --add-modules "$MODULY" --strip-debug --no-header-files --no-man-pages --generate-cds-archive --output "$RUNTIME"
	[ "$(lipo -archs "$RUNTIME/bin/java")" = "$MACH" ] || { echo "runtime-$ARCH není pro $MACH"; exit 1; }
	# Java z Temurinu je podepsaná, jlink podpisy zachová; nepodepsaný kód by Apple Silicon nespustil.
	codesign --verify --strict "$RUNTIME/bin/java"
	codesign --verify --strict "$RUNTIME/lib/server/libjvm.dylib"
	VERZE_JAVY=$(sed -n 's/^JAVA_VERSION="\(.*\)"$/\1/p' "$RUNTIME/release")
	"$RUNTIME/bin/java" -version
	echo "Přibalená Java $ARCH $VERZE_JAVY, doporučená $DOPORUCENA"
	case "$VERZE_JAVY" in 21.*) ;; *) echo "Přibalená Java $VERZE_JAVY není 21"; exit 1 ;; esac
	[ "$(printf '%s\n%s\n' "$DOPORUCENA" "$VERZE_JAVY" | sort -V | head -n1)" = "$DOPORUCENA" ] \
		|| { echo "Přibalená Java $VERZE_JAVY je starší než doporučená $DOPORUCENA"; exit 1; }
	[ -z "$JAVA" ] || [ "$JAVA" = "$VERZE_JAVY" ] || { echo "Javy pro arm64 ($JAVA) a x64 ($VERZE_JAVY) se liší"; exit 1; }
	JAVA=$VERZE_JAVY
done

cp "$JARY/geokuk.jar" "$JARY/start.jar" "$PROGRAM/"
cp distribuce/macos/GeoKuk "$APP/Contents/MacOS/GeoKuk"
chmod +x "$APP/Contents/MacOS/GeoKuk"
# Info.plist chce číselnou verzi, bez přípony -beta.1 nebo -dev.N.
sed "s/@VERZE@/${VERZE%%-*}/g" distribuce/macos/Info.plist > "$APP/Contents/Info.plist"
plutil -lint "$APP/Contents/Info.plist"
printf 'APPL????' > "$APP/Contents/PkgInfo"

# Ikona z PNG ikony okna, největší je 256 px.
SADA="$PRACOVNI/geokuk.iconset"
IMG=src/main/resources/img
mkdir "$SADA"
cp "$IMG/geokuk16.png" "$SADA/icon_16x16.png"
cp "$IMG/geokuk32.png" "$SADA/icon_16x16@2x.png"
cp "$IMG/geokuk32.png" "$SADA/icon_32x32.png"
cp "$IMG/geokuk64.png" "$SADA/icon_32x32@2x.png"
cp "$IMG/geokuk128.png" "$SADA/icon_128x128.png"
cp "$IMG/geokuk256.png" "$SADA/icon_128x128@2x.png"
cp "$IMG/geokuk256.png" "$SADA/icon_256x256.png"
iconutil -c icns "$SADA" -o "$APP/Contents/Resources/geokuk.icns"

# Licence uvnitř aplikace, ať je má i ten, kdo dmg zahodí.
mkdir "$APP/Contents/Resources/licence"
cp LICENSE licence/* "$APP/Contents/Resources/licence/"

# Aplikace se nepodepisuje: bez účtu Apple Developer by šlo jen o podpis ad-hoc a ten by aktualizace geokuk.jar uvnitř balíku porušila.
cp distribuce/macos/CTIMNE.txt "$OBSAH/"
rm -f "$DMG"
# Okno dmg: pozadí se šipkou do Aplikací, rozmístění ikon a formát jsou v dmg-nastaveni.py.
"${DMGBUILD:-dmgbuild}" -s distribuce/macos/dmg-nastaveni.py \
	-D app="$APP" -D ctimne="$OBSAH/CTIMNE.txt" \
	-D pozadi="$PWD/distribuce/macos/dmg-pozadi.png" -D ikona="$APP/Contents/Resources/geokuk.icns" \
	GeoKuk "$DMG"
echo "Sestaven $DMG, GeoKuk $VERZE, Java $JAVA"
[ -z "${GITHUB_STEP_SUMMARY:-}" ] || echo "Přibalená Java pro macOS $JAVA (arm64 i x64), doporučená $DOPORUCENA" >> "$GITHUB_STEP_SUMMARY"
