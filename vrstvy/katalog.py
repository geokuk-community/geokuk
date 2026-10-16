#!/usr/bin/env python3
"""Složí vrstvy.json a SHA256SUMS z *.meta.json (zapisuje meta.sh).
Použití: katalog.py slozka_s_meta region nazev jih,západ,sever,východ [predpona_url]
nazev souboru = <region s / nahrazeným _>.<typ>.<přípona>; soubory[].cesta = lokální cesta ve složce vrstev (<region>.<typ>.<přípona>)"""
import glob, json, os, sys
from datetime import datetime, timezone

ZDROJ = 'https://github.com/geokuk-community/geokuk-maps/releases/download/'

COPERNICUS = ('produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018 '
              'provided under COPERNICUS by the European Union and ESA; all rights reserved. The organisations in charge of the '
              'Copernicus programme by law or by delegation do not incur any liability for any use of the Copernicus WorldDEM-30.')

TYPY = {
    'trasy': dict(nazev='Turistické, cyklo a MTB trasy – %s', format='map', licence='ODbL 1.0',
                  atribuce='© přispěvatelé OpenStreetMap', tema='trasy-kct'),
    'vrstevnice': dict(nazev='Vrstevnice – %s (10 m)', format='map', licence='Copernicus COP-DEM-GLO-30-F',
                       atribuce='Vrstevnice: Copernicus DEM GLO-30 © DLR/Airbus, ESA', atribuceDlouha=COPERNICUS,
                       pozn='Copernicus je DSM (povrch včetně budov a lesa).'),
    'dem': dict(nazev='Stínovaný reliéf – %s (HGT 1″)', format='hgt-zip', licence='Copernicus COP-DEM-GLO-30-F',
                atribuce='Reliéf: Copernicus DEM GLO-30 © DLR/Airbus, ESA', atribuceDlouha=COPERNICUS),
}


def main():
    slozka, region, nazev, bbox = sys.argv[1:5]
    predpona = sys.argv[5] if len(sys.argv) > 5 else ''
    jih, zapad, sever, vychod = (float(x) for x in bbox.split(','))
    vrstvy = []
    for cesta in sorted(glob.glob(os.path.join(slozka, '*.meta.json'))):
        with open(cesta, encoding='utf-8') as f:
            m = json.load(f)
        sablona = TYPY[m['typ']]
        zaklad = os.path.basename(cesta)[:-len('.%s.meta.json' % m['typ'])]
        pokryti = {'bbox': [jih, zapad, sever, vychod]}
        if os.path.exists(os.path.join(slozka, zaklad + '.poly')):
            pokryti['poly'] = predpona + zaklad + '.poly'
        v = {'id': '%s/%s' % (m['typ'], region), 'typ': m['typ'], 'region': region, 'nazev': sablona['nazev'] % nazev,
             'format': sablona['format'],
             'soubory': [{'nazev': m['nazev'], 'cesta': '%s.%s.%s' % (region, m['typ'], m['nazev'].rsplit('.', 1)[1]),
                          'url': predpona + m['nazev'], 'velikost': m['velikost'],
                          'sha256': m['sha256'], 'datumSouboru': m['vyrobeno']}],
             'velikost': m['velikost'], 'datumDat': m['datumDat'], 'vyrobeno': m['vyrobeno'], 'zdrojDat': m['zdrojDat']}
        if 'zoom' in m:
            v['zoom'] = m['zoom']
        if 'krokVrstevnic' in m:
            v['krokVrstevnic'] = m['krokVrstevnic']
        v['pokryti'] = pokryti
        for k in ('licence', 'atribuce', 'atribuceDlouha', 'tema', 'pozn'):
            if k in sablona:
                v[k] = sablona[k]
        v['licenceText'] = m['licenceText']
        vrstvy.append(v)
    out = {'format': 1, 'vygenerovano': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'), 'zdroje': [ZDROJ], 'vrstvy': vrstvy}
    with open(os.path.join(slozka, 'vrstvy.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write('\n')
    with open(os.path.join(slozka, 'SHA256SUMS'), 'w', encoding='utf-8') as f:
        for v in vrstvy:
            for s in v['soubory']:
                f.write('%s  %s\n' % (s['sha256'], s['nazev']))


if __name__ == '__main__':
    main()
