#!/usr/bin/env python3
"""Vygeneruje tag-mapping-trasy.xml: všechny kombinace klíčů fmrel* a hodnot, které vyrábí addfmreltags.pl."""
import sys

SYMBOLY = ['cave', 'edu', 'education', 'interesting_object', 'local', 'major', 'none', 'peak', 'roundtrip',
           'ruin', 'shelter', 'spring', 'stripe', 'yes']
BARVY = ['red', 'blue', 'green', 'yellow', 'black', 'white', 'default']
KLICE = []
for sit in ('', 'local', 'unknown'):
    KLICE += ['fmrelhiking%s%s' % (sit, b) for b in BARVY]
KLICE += ['fmrelhiking%s' % b for b in BARVY]
for typ in ('bicycle', 'mtb', 'horse', 'ski'):
    KLICE += ['fmrel%s%s' % (typ, b) for b in BARVY]
KLICE = list(dict.fromkeys(KLICE))
HLAVICKA = '''<?xml version="1.0" encoding="UTF-8"?>
<!-- Mapování tagů pro mapsforge-map-writer. Klíče fmrel* vyrábí skript addfmreltags.pl převzatý z Freemap Slovakia;
     struktura mapování vychází z tag-mapping-fm.xml projektu mapsforge-freemap-hiking-style (https://github.com/FreemapSlovakia/mapsforge-freemap-hiking-style).
     Soubor vzniká skriptem gen-tag-mapping-trasy.py. -->
<tag-mapping xmlns="http://mapsforge.org/tag-mapping" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://mapsforge.org/tag-mapping tag-mapping.xsd" default-zoom-appear="16" profile-name="default-profile">
	<ways>
'''
with open(sys.argv[1] if len(sys.argv) > 1 else 'tag-mapping-trasy.xml', 'w', encoding='utf-8') as f:
    f.write(HLAVICKA)
    for k in KLICE:
        for v in SYMBOLY:
            f.write('\t\t<osm-tag key="%s" value="%s" zoom-appear="11" />\n' % (k, v))
    for v in ('edu', 'education'):
        f.write('\t\t<osm-tag key="fmreleducation" value="%s" zoom-appear="11" />\n' % v)
    f.write('\t</ways>\n</tag-mapping>\n')
