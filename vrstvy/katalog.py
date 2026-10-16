#!/usr/bin/env python3
"""Builds layers.json and SHA256SUMS from *.meta.json (written by meta.py).
Usage: katalog.py meta_dir region name south,west,north,east [url_prefix]
File name = <region with / replaced by _>.<type>.<extension>; files[].path = <region>.<type>.<extension>."""
import glob, json, os, sys
from datetime import datetime, timezone

SOURCE = 'https://github.com/geokuk-community/geokuk-maps/releases/download/'

COPERNICUS = ('produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018 '
              'provided under COPERNICUS by the European Union and ESA; all rights reserved. The organisations in charge of the '
              'Copernicus programme by law or by delegation do not incur any liability for any use of the Copernicus WorldDEM-30.')

TYPES = {
    'routes': dict(name='Hiking, cycling and MTB routes – %s', format='map', license='ODbL 1.0',
                   attribution='© OpenStreetMap contributors', theme='routes'),
    'contours': dict(name='Contour lines – %s (10 m)', format='map', license='Copernicus COP-DEM-GLO-30-F',
                     attribution='Contours: Copernicus DEM GLO-30 © DLR/Airbus, ESA', attributionFull=COPERNICUS,
                     note='Copernicus DEM is a surface model (includes buildings and forest canopy).'),
    'dem': dict(name='Hillshade elevation data – %s (HGT 1")', format='hgt-zip', license='Copernicus COP-DEM-GLO-30-F',
                attribution='Elevation: Copernicus DEM GLO-30 © DLR/Airbus, ESA', attributionFull=COPERNICUS),
}


def main():
    folder, region, name, bbox = sys.argv[1:5]
    prefix = sys.argv[5] if len(sys.argv) > 5 else ''
    south, west, north, east = (float(x) for x in bbox.split(','))
    layers = []
    for path in sorted(glob.glob(os.path.join(folder, '*.meta.json'))):
        with open(path, encoding='utf-8') as f:
            m = json.load(f)
        t = TYPES[m['typ']]
        base = os.path.basename(path)[:-len('.%s.meta.json' % m['typ'])]
        coverage = {'bbox': [south, west, north, east]}
        if os.path.exists(os.path.join(folder, base + '.poly')):
            coverage['poly'] = prefix + base + '.poly'
        layer = {'id': '%s/%s' % (m['typ'], region), 'type': m['typ'], 'region': region, 'format': t['format'],
                 'name': t['name'] % name,
                 'files': [{'name': m['nazev'], 'path': '%s.%s.%s' % (region, m['typ'], m['nazev'].rsplit('.', 1)[1]),
                            'url': prefix + m['nazev'], 'size': m['velikost'], 'sha256': m['sha256'], 'date': m['vyrobeno']}],
                 'size': m['velikost'], 'dataDate': m['datumDat'], 'produced': m['vyrobeno'], 'dataSource': m['zdrojDat']}
        if 'zoom' in m:
            layer['zoom'] = m['zoom']
        if 'krokVrstevnic' in m:
            layer['contourInterval'] = m['krokVrstevnic']
        layer['coverage'] = coverage
        for k in ('license', 'attribution', 'attributionFull', 'theme', 'note'):
            if k in t:
                layer[k] = t[k]
        layer['licenseText'] = m['licenceText']
        layers.append(layer)
    out = {'version': 1, 'generated': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'), 'sources': [SOURCE], 'layers': layers}
    with open(os.path.join(folder, 'layers.json'), 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
        f.write('\n')
    with open(os.path.join(folder, 'SHA256SUMS'), 'w', encoding='utf-8') as f:
        for layer in layers:
            for s in layer['files']:
                f.write('%s  %s\n' % (s['sha256'], s['name']))


if __name__ == '__main__':
    main()
