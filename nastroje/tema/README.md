# Mapové téma GeoKuk

`python3 nastroje/tema/sestav-tema.py JAR VYSTUP` sestaví ze `zdroj/`:
- `VYSTUP/geokuk/GeoKuk.xml`, `GeoKuk_detail.xml` (+ `symbols/`): vestavěná témata programu
  (`src/main/resources/cz/geokuk/plugins/mapy/kachle/podklady/geokuk/`),
- `VYSTUP/trasy.xml`: překryv Turistické a cyklotrasy (`podklady/trasy.xml`),
- `VYSTUP/GeoKuk-tema.zip`: téma pro aplikace s mapami mapsforge (c:geo, aDrake) k mapám GeoKuku: základní mapa
  z download.mapsforge.org a vrstvy GeoKuku (`.tourist.map` s trasami v tagech `fmrel*` a body, `.contours.map`),
  soubor vydání,
- `VYSTUP/GeoKuk-tema-PAWS.zip`: totéž téma k mapám s trasami v tagech `osmc_*` (například mapy z osm.paws.cz), soubor vydání.

Oba zipy mají stejná pravidla kromě tras, zdroje jsou v nich relativně (`file:`) a stínování terénu kreslí aplikace,
která ho umí. Skript ověří, že každý zip kreslí trasy jen z jedné sady tagů. Načtení zipů tak, jak je čtou aplikace
(`ZipXmlThemeResourceProvider`), hlídá `TemaZipTest`, který CI spouští po sestavení zipů.

`JAR` je `geokuk.jar` (symboly mapsforge ve složce `assets/`). Vlastní symboly jsou v resources programu.

Zdroj: `zdroj/geokuk-spolecne.xml` (společná pravidla; na místa `<!--#DATA-TRASY#-->` a `<!--#DATA-NAZVY#-->`
se vloží bloky dat), `data-geokuk-*.xml` (trasy z vrstvy GeoKuku; vestavěné téma a `GeoKuk-tema.zip`), `data-osmc-trasy.xml` (trasy z tagů
`osmc_*`, jen `GeoKuk-tema-PAWS.zip`), `modul-detail.xml` (pravidla navíc ve variantě detail), `generuj-trasy.py` (trasy překryvu).
Po změně zdroje sestavte témata znovu a výstup zkopírujte do resources.
