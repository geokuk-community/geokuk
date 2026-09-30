# GeoKuk

[![Build](https://github.com/geokuk-community/geokuk/actions/workflows/build.yml/badge.svg)](https://github.com/geokuk-community/geokuk/actions/workflows/build.yml)
[![Licence: GPL v3](https://img.shields.io/badge/licence-GPL%20v3-blue.svg)](LICENSE)

**Plánování geovýletů na českých mapách.**

* podpora pro geocaching
* desktopový platformově nezávislý program (Java)
* online i offline režim

![Snímek GeoKuku](doc/img/screenshot-3.6.0.jpg)

## Stažení

Aktuální verze je v [Releases](https://github.com/geokuk-community/geokuk/releases).
Ke spuštění je potřeba Java 8 nebo novější.

Ve Windows stačí stáhnout `geokuk.jar` a poprvé ho spustit dvojklikem.
Geokuk vedle sebe vytvoří spouštěč `geokuk.cmd`, přes který ho pak
spouštějte. Když Geokuk najde novou verzi, stáhne ji a spouštěč ji při
dalším spuštění nainstaluje. Aktualizace přepisuje `geokuk.cmd`, pokud
potřebujete svůj upravený spouštěč, tak si ho prosím uložte pod jiným jménem.

Jinde stačí `java -jar geokuk.jar`.

## Uživatelské mapy

Vlastní mapové podklady se zadávají v souboru `uzivatelske-mapy.properties`
vedle `geokuk.jar` a v menu Mapy jsou ve skupině „Uživatelské mapy“.
Popis a příklady jsou v
[`priklady/uzivatelske-mapy.properties`](priklady/uzivatelske-mapy.properties).

## Původ

GeoKuk napsal Martin Veverka se spoluautory. Toto repo navazuje na jeho
práci a přebírá opravy z dalších forků:

* [marvertin/geokuk](https://github.com/marvertin/geokuk): původní projekt,
  základem je větev `master` (commit `e4a5cbf`, 2020).
* [JiriBilek/geokuk](https://github.com/JiriBilek/geokuk): úpravy map
  Jiřího Bílka, větev `zmena_map_5.1` (verze 5.1.1c).

Převzaté commity mají původního autora a v popisu odkaz na zdrojový commit
(`cherry picked from commit …`). Z Bílkovy větve nejsou převzaté jen
sestavené jary v `releases/`. Co je nového oproti 5.1.1c, popisuje
[CHANGELOG](CHANGELOG.md).

## Poděkování

Martinu Veverkovi za GeoKuk, Danielu Stahrovi za spoluautorství, Jiřímu
Bílkovi za udržování map v chodu a všem dalším přispěvatelům z historie
projektu, mimo jiné Bohuslavu Roztočilovi, Petru Pitkovi a Petru
Schönmannovi.

## Build

```sh
./mvnw -B clean package
```

Stačí JDK 21, výsledek je v `target/`.

Smoke test celého programu nad falešným mapovým serverem (projde mapu,
menu a otevřené dialogy) potřebuje displej, na Linuxu třeba přes xvfb:

```sh
xvfb-run -a -s "-screen 0 1400x900x24" ./mvnw -B -P smoke verify
```

Na GitHubu se spouští ručně workflow Smoke.

## Licence

[GNU GPL v3](LICENSE)
