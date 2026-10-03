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
GeoKuk se neinstaluje: rozbalí se do složky a spouští se z ní. Všechno,
co si program ukládá, je ve složce `data` vedle něj, takže rozhoduje jen to,
odkud ho spustíte. Složku s programem můžete přesunout nebo zkopírovat
i s daty a nastavením.

### Windows

Stáhněte `GeoKuk-windows.zip` a rozbalte ho třeba do `C:\`. Zip obsahuje
složku `GeoKuk`, vznikne tedy `C:\GeoKuk`. Je v ní i Java, žádnou jinou není potřeba instalovat. Nerozbalujte ho do
`Program Files` (tam program nesmí zapisovat) ani do složky, kterou
synchronizuje OneDrive, Dropbox nebo Google Disk (pozor, OneDrive často
zálohuje i Plochu a Dokumenty).

Poprvé spusťte `GeoKuk-prvni-spusteni.cmd`. Windows se zeptají, jestli
soubor od neznámého vydavatele spustit; zvolte Spustit. Dotazu se
vyhnete, když před rozbalením ve vlastnostech staženého zipu zaškrtnete
Odblokovat. GeoKuk pak vedle vytvoří zástupce `GeoKuk.lnk`; dál
spouštějte jeho, klidně ho zkopírujte na plochu nebo připněte na hlavní
panel. Do nabídky Start ho přidá Soubor > Vytvořit zástupce. Když
složku přesunete, spusťte nejdřív znovu `GeoKuk-prvni-spusteni.cmd`,
zástupce ve složce i jeho kopie na ploše a v nabídce Start se opraví.
Do té doby zástupce hlásí chybu „Unable to access jarfile“.

Když GeoKuk najde novou verzi, stáhne ji a nabídne restart; jinak se
nainstaluje při příštím spuštění. Javu aktualizuje nový zip. Rozbalte ho
tam, kam ten první (třeba do `C:\`), přes stávající složku `GeoKuk`;
data a nastavení zůstanou.

### Linux a macOS

Stáhněte `geokuk.jar` do vlastní složky a spusťte ho `java -jar geokuk.jar`
(potřeba je Java 8 nebo novější). Nové verze se instalují samy.

### Složka s programem

```
GeoKuk
├── GeoKuk-prvni-spusteni.cmd    první spuštění ve Windows
├── GeoKuk.lnk                   zástupce, vytvoří ho GeoKuk
├── program
│   ├── geokuk.jar               program
│   ├── start.jar                spouštěč
│   ├── geokuk.ico
│   └── runtime                  Java
└── data                         všechno, co si GeoKuk ukládá
    ├── nastaveni.xml
    ├── uzivatelske-mapy.properties
    ├── cache                    dlaždice map, lze smazat
    ├── gpx                      výchozí složka pro keše z GPX
    ├── cesty
    ├── ikony                    vlastní ikony (moje) a od jiných (ostatni)
    ├── vylety                   lovim.ggt a tedne.ggt
    ├── render
    └── log                      log a chybová hlášení
```

Paměť pro program zvolí spouštěč podle počítače (polovina paměti, 1 až
3 GB), změnit ji jde v Soubor > Paměť programu.

### Přechod ze starší verze

Nastavení se při prvním spuštění převezme samo. Data starší verze
zůstanou ve složce `%USERPROFILE%\geokuk` (na Linuxu a macOS
`~/geokuk`), GeoKuk je odtud nečte. Co chcete dál používat, zkopírujte
do složky `data`:

- keše z GPX uložené přímo v `%USERPROFILE%\geokuk` (výchozí složka
  starší verze) do `data\gpx`, nebo jejich složku nastavte v Soubor >
  Umístění souborů,
- výlety `lovim.ggt` a `tedne.ggt` do `data\vylety`,
- cesty ze složky `cesty` do `data\cesty`,
- vlastní ikony z `imagesMy` do `data\ikony\moje` a ikony od jiných
  z `images3rdParty` do `data\ikony\ostatni`,
- dlaždice map z `prchave\kachle` do `data\cache`.

Ve Windows se verze 6.0.0 na novou verzi aktualizuje sama, ale jen
samotný program ve stávající složce, bez přibalené Javy. Další
aktualizace pak už sama nenainstaluje. Pro automatické aktualizace
a přibalenou Javu stáhněte `GeoKuk-windows.zip` a data přeneste podle
postupu výše.

## Uživatelské mapy

Vlastní mapové podklady se zadávají v souboru `data/uzivatelske-mapy.properties`
a v menu Mapy jsou ve skupině „Uživatelské mapy“.
Popis a příklady jsou v
[`priklady/uzivatelske-mapy.properties`](priklady/uzivatelske-mapy.properties).

## Dálkové ovládání

Soubor > Dálkové ovládání (nebo parametr `--ovladani[=port]`, výchozí port
48321) povolí jiným programům na tomto počítači ovládat GeoKuk přes HTTP:

```
GET  http://127.0.0.1:48321/stav
POST http://127.0.0.1:48321/pozice?lat=50.08&lon=14.42&meritko=15
POST http://127.0.0.1:48321/kes?kod=GC12345
POST http://127.0.0.1:48321/podklad?jmeno=TURIST_M
POST http://127.0.0.1:48321/prenacti
```

Požadavky z webového prohlížeče program odmítá.

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

Autorům aplikace aDrake pro Android za inspiraci: zobrazení keší na
oddálené mapě jako barevných teček podle typu vychází z ní. Jsou to
[LudekV](https://www.geocaching.com/p/?u=LudekV) (původní autor),
[Karas0911](https://www.geocaching.com/p/?u=Karas0911) (Trnkáči, sada ikon) a
[lnavrat](https://www.geocaching.com/p/?u=lnavrat), který pokračuje
ve vývoji.

## Build

```sh
./mvnw -B clean package
```

Stačí JDK 21, výsledek je v `target/`. Zip pro Windows s přibalenou Javou
staví workflow Build na Windows runneru.

Smoke test celého programu nad falešným mapovým serverem (projde mapu,
menu a otevřené dialogy) potřebuje displej, na Linuxu třeba přes xvfb:

```sh
xvfb-run -a -s "-screen 0 1400x900x24" ./mvnw -B -P smoke verify
```

Na GitHubu běží workflow Smoke každou noc na Linuxu, ručně ho jde
spustit pro Linux i Windows.

## Licence

[GNU GPL v3](LICENSE)
