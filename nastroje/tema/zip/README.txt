Mapové téma GeoKuk pro aplikace s mapami ve formátu mapsforge
(například c:geo, aDrake)

GeoKuk.xml         základní téma
GeoKuk_detail.xml  téma s dalšími body od většího přiblížení
symbols/, patterns/  symboly a vzorky obou témat

Který zip k jakým mapám
Téma vyšlo ve dvou zipech, které se liší jen tím, odkud kreslí turistické
a cyklotrasy:
- GeoKuk-tema.zip: k mapám GeoKuku. Základní mapa je z download.mapsforge.org
  (například europe/czech-republic.map), k ní se jako další mapové soubory
  přidají vrstvy GeoKuku z https://github.com/geokuk-community/geokuk-maps/releases
  (stahuje je i Správce offline map v programu GeoKuk):
  .tourist.map  turistické a cyklotrasy, jejich jména, skály a body
                pro orientaci (rozcestníky, posedy, kameny),
  .contours.map vrstevnice.
  Aplikace musí umět kreslit více mapových souborů najednou.
- GeoKuk-tema-PAWS.zip: k mapám, které mají turistické a cyklotrasy přímo
  v mapovém souboru v tagech osmc_*, například mapy z osm.paws.cz.
Se špatným zipem se trasy nekreslí, zbytek mapy ano.

Stínování terénu kreslí aplikace, která ho umí a má výšková data (soubory
.hgt, ve vrstvách GeoKuku jako .dem.zip). V c:geo se zapíná v nastavení
offline map spolu se složkou pro výšková data.

Zip rozbalte nebo vložte do složky témat aplikace podle jejího návodu.
c:geo: zip vložte do složky cgeo/maps/_themes/, nebo jeho obsah rozbalte
do složky cgeo/maps/_themes/geokuk/. Po novém spuštění c:geo je téma
v nabídce motivů mapy.
Téma neobsahuje mapová data; atribuci dat zobrazuje aplikace.

Licence
Témata jsou upravenou kopií default.xml z mapsforge-themes 0.25.0.
Původní dílo: Copyright 2010-2013 mapsforge.org, 2014 Ludwig M Brinckmann,
2014-2024 devemux86, 2024-2025 Sublimis a další přispěvatelé
(https://github.com/mapsforge/mapsforge). Upraveno pro GeoKuk v roce 2026.
Témata, symboly a vzorky jsou pod GNU LGPL verze 3 nebo novější
(LICENSE-LGPL-3.0.txt, doplňuje GNU GPL verze 3 v LICENSE-GPL-3.0.txt).
Ikony ve složce symbols/osmic a symbols/informace.svg jsou z projektu Osmic
(https://github.com/gmgeo/osmic, gmgeo a přispěvatelé), volné dílo
pod CC0 1.0 (https://creativecommons.org/publicdomain/zero/1.0/).

GeoKuk: https://github.com/geokuk-community/geokuk
