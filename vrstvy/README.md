# Offline vrstvy

Skripty workflow **Vrstvy** (`.github/workflows/vrstvy.yml`, ruční spuštění): z dat OpenStreetMap (Geofabrik) a výškového modelu Copernicus GLO-30 vyrábí pro zvolený region vrstvy ve formátu mapsforge.

- `gen-tag-mapping-trasy.py` – vygeneruje `tag-mapping-trasy.xml` ze všech kombinací, které vyrábí `addfmreltags.pl`
- `trasy.sh` – turistické, cyklo, MTB, jezdecké a lyžařské trasy (`osmium tags-filter`, `addfmreltags.pl`, Osmosis + mapsforge-map-writer, `tag-mapping-trasy.xml`)
- `dem.sh`, `cop2hgt.py` – dlaždice HGT 1″ z Copernicus GLO-30 (AWS Open Data) v zipu
- `vrstevnice.sh` – vrstevnice po 10 m (pyhgtmap, `tag-mapping-vrstevnice.xml`)
- `katalog.py` – složí `vrstvy.json` a `SHA256SUMS`

Data © přispěvatelé OpenStreetMap (ODbL). Výškový model: produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved.

`addfmreltags.pl` je převzatý ze skriptů Freemap Slovakia (https://github.com/FreemapSlovakia/mapsforge-freemap-hiking-style, GPL-3 nebo novější) a upravený.
