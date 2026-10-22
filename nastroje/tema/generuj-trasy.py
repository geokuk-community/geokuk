#!/usr/bin/env python3
"""Generuje téma Turistické a cyklotrasy (src/main/resources/.../podklady/trasy.xml).
Spuštění: python3 generuj-trasy.py <výstup.xml>"""
import sys

BARVY = [("red", "#E00000"), ("blue", "#1060E0"), ("green", "#00A020"), ("yellow", "#F0C000"), ("orange", "#F07000"),
         ("purple", "#9030C0"), ("brown", "#8B4513"), ("black", "#202020"), ("white", "#FFFFFF")]
JINA_BARVA = "#707070"
# Posun čáry podle pořadí souběžné trasy (násobek kroku), střídavě na obě strany.
PORADI = [("ord_1", 0), ("ord_2", 1), ("ord_3", -1), ("ord_4", 2), ("ord_5", -2), ("ord_6", 3), ("ord_7", -3), ("ord_8", 4), ("ord_9", -4)]
# (zoom-min, zoom-max, šířka čáry, šířka lemu, krok posunu), šířky v bodech obrazovky bez škálování zoomem.
PASMA = [(10, 12, 1.6, 3.0, 2.2), (13, 14, 2.4, 4.2, 3.0), (15, 22, 3.2, 5.4, 4.0)]
LEM = "#B0FFFFFF"
CYKLO = [("ncn|icn", "#C000B0", 1.0), ("~|lcn|rcn", "#E040D0", 0.8)]


def f(x):
    return ("%.1f" % x).rstrip("0").rstrip(".")


def caru(barva, sirka, lem, dy):
    return ('<line stroke="%s" stroke-width="%s" stroke-linecap="round" scale="none" dy="%s"/>'
            '<line stroke="%s" stroke-width="%s" stroke-linecap="round" scale="none" dy="%s"/>' % (LEM, f(lem), f(dy), barva, f(sirka), f(dy)))


def main(vystup):
    r = ['<?xml version="1.0" encoding="UTF-8"?>',
         '<!-- Turistické a cyklotrasy z tagů osmc_* v mapovém souboru přes libovolný podklad. Součást GeoKuku, licence GNU GPL 3 nebo novější. -->',
         '<rendertheme xmlns="http://mapsforge.org/renderTheme" version="5" map-background="#00000000" map-background-outside="#00000000">']
    for zmin, zmax, sirka, lem, krok in PASMA:
        r.append('<rule e="way" k="route" v="bicycle|mtb" zoom-min="%d" zoom-max="%d">' % (zmin, zmax))
        for site, barva, nasobek in CYKLO:
            s = sirka * nasobek
            r.append(' <rule e="way" k="network" v="%s"><line stroke="%s" stroke-width="%s" stroke-dasharray="%s,%s" stroke-linecap="butt" scale="none" dy="%s"/></rule>'
                     % (site, barva, f(s), f(4 * s), f(2 * s), f(-2.5 * krok)))
        r.append('</rule>')
        r.append('<rule e="way" k="route" v="hiking|foot" zoom-min="%d" zoom-max="%d">' % (zmin, zmax))
        for poradi, posun in PORADI + [("~", 0)]:
            dy = posun * krok
            r.append(' <rule e="way" k="osmc_order" v="%s">' % poradi)
            for jmeno, barva in BARVY:
                r.append('  <rule e="way" k="osmc_color" v="%s">%s</rule>' % (jmeno, caru(barva, sirka, lem, dy)))
            # Trasa bez pořadí i barvy je v datech jen nosič symbolů značky; šedá čára by zakryla barevnou trasu ve stejné stopě.
            if poradi != "~":
                r.append('  <rule e="way" k="osmc_color" v="%s">%s</rule>' % ("~|none", caru(JINA_BARVA, sirka, lem, dy)))
            r.append(' </rule>')
        r.append('</rule>')
    r.append('</rendertheme>')
    with open(vystup, "w", encoding="utf-8", newline="\n") as o:
        o.write("\n".join(r) + "\n")


main(sys.argv[1])
