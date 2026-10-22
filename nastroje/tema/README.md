# Mapové téma GeoKuk

`python3 nastroje/tema/sestav-tema.py JAR VYSTUP` sestaví ze `zdroj/`:
- `VYSTUP/geokuk/GeoKuk.xml`, `GeoKuk_detail.xml` (+ `symbols/`): vestavěná témata programu
  (`src/main/resources/cz/geokuk/plugins/mapy/kachle/podklady/geokuk/`),
- `VYSTUP/trasy.xml`: překryv Turistické a cyklotrasy (`podklady/trasy.xml`),
- `VYSTUP/GeoKuk-tema.zip`: téma pro aplikace s mapami mapsforge (aDrake, Locus, OruxMaps), soubor vydání.

`JAR` je `geokuk.jar` (symboly mapsforge ve složce `assets/`). Vlastní symboly jsou v resources programu.

Zdroj: `zdroj/geokuk-spolecne.xml` (společná pravidla; na místa `<!--#DATA-TRASY#-->` a `<!--#DATA-NAZVY#-->`
se vloží bloky dat), `data-geokuk-*.xml` (trasy z vrstvy GeoKuku), `data-osmc-trasy.xml` (trasy z tagů `osmc_*`,
jen zip), `modul-detail.xml` (pravidla navíc ve variantě detail), `generuj-trasy.py` (trasy překryvu).
Po změně zdroje sestavte témata znovu a výstup zkopírujte do resources.
