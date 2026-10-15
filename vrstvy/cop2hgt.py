#!/usr/bin/env python3
"""Copernicus GLO-30 (AWS Open Data) -> HGT 1" (3601x3601, int16 BE) pro oblast.
Použití: cop2hgt.py jih,západ,sever,východ vystupni_slozka pracovni_slozka
Vyžaduje numpy a GDAL (gdalbuildvrt, gdalwarp)."""
import math, os, subprocess, sys, urllib.error, urllib.request
from concurrent.futures import ThreadPoolExecutor
import numpy as np

URL = 'https://copernicus-dem-30m.s3.amazonaws.com/Copernicus_DSM_COG_10_{n}_00_{e}_00_DEM/Copernicus_DSM_COG_10_{n}_00_{e}_00_DEM.tif'


def jmeno(lat, lon):
    return '%s%02d' % ('N' if lat >= 0 else 'S', abs(lat)), '%s%03d' % ('E' if lon >= 0 else 'W', abs(lon))


def stahni(args):
    lat, lon, slozka = args
    n, e = jmeno(lat, lon)
    cil = os.path.join(slozka, n + e + '.tif')
    if os.path.exists(cil):
        return cil
    for pokus in range(5):
        try:
            with urllib.request.urlopen(URL.format(n=n, e=e), timeout=120) as r, open(cil + '.cast', 'wb') as f:
                while True:
                    blok = r.read(1 << 20)
                    if not blok:
                        break
                    f.write(blok)
            os.replace(cil + '.cast', cil)
            return cil
        except urllib.error.HTTPError as ex:
            if ex.code in (403, 404):
                return None
        except OSError:
            pass
    raise RuntimeError('stažení %s%s selhalo' % (n, e))


def main():
    jih, zapad, sever, vychod = (float(x) for x in sys.argv[1].split(','))
    vystup, prace = sys.argv[2], sys.argv[3]
    os.makedirs(vystup, exist_ok=True)
    zdroj = os.path.join(prace, 'cop')
    os.makedirs(zdroj, exist_ok=True)
    lat0, lat1 = math.floor(jih), math.ceil(sever) - 1
    lon0, lon1 = math.floor(zapad), math.ceil(vychod) - 1
    cile = [(a, o) for a in range(lat0, lat1 + 1) for o in range(lon0, lon1 + 1)]
    kandidati = [(a, o, zdroj) for a in range(lat0 - 1, lat1 + 2) for o in range(lon0 - 1, lon1 + 2)]
    with ThreadPoolExecutor(8) as ex:
        tify = [t for t in ex.map(stahni, kandidati) if t]
    print('dlaždic Copernicu', len(tify), 'z', len(kandidati), flush=True)
    vrt = os.path.join(prace, 'cop.vrt')
    seznam = os.path.join(prace, 'cop.txt')
    with open(seznam, 'w') as f:
        f.write('\n'.join(tify))
    subprocess.run(['gdalbuildvrt', '-q', '-input_file_list', seznam, vrt], check=True)
    pul = 0.5 / 3600
    zapsano = 0
    for a, o in cile:
        n, e = jmeno(a, o)
        raw = os.path.join(prace, 'tile.raw')
        subprocess.run(['gdalwarp', '-q', '-overwrite', '-te', str(o - pul), str(a - pul), str(o + 1 + pul), str(a + 1 + pul),
                        '-ts', '3601', '3601', '-r', 'bilinear', '-ot', 'Float32', '-of', 'ENVI', vrt, raw], check=True)
        v = np.fromfile(raw, dtype='<f4').reshape(3601, 3601)
        v = np.nan_to_num(v, nan=0.0)
        if not (v > 0).any():
            continue
        np.rint(v).astype('>i2').tofile(os.path.join(vystup, n + e + '.hgt'))
        zapsano += 1
        for p in (raw, raw + '.hdr', raw + '.aux.xml'):
            if os.path.exists(p):
                os.remove(p)
    print('HGT dlaždic', zapsano, flush=True)


if __name__ == '__main__':
    main()
