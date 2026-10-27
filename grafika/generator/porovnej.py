"""Porovná vygenerované soubory se soubory v repozitáři (md5 a podíl shodných pixelů).
python3 -I porovnej.py <koren-repa> [vystup]"""
import hashlib
import io
import os
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

koren = sys.argv[1]
vystup = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'vystup')
dvojice = [(f'geokuk{s}.png', f'src/main/resources/img/geokuk{s}.png') for s in (16, 32, 48, 64, 128, 256)]
dvojice += [('geokuk-logo.png', 'src/main/resources/img/geokuk-logo.png'),
            ('geokuk400.jpg', 'src/main/resources/img/geokuk400.jpg'),
            ('geokuk.ico', 'distribuce/windows/geokuk.ico')]


def obrazy(cesta):
    if cesta.endswith('.ico'):
        d = Path(cesta).read_bytes()
        n = struct.unpack('<H', d[4:6])[0]
        vysledek = []
        for i in range(n):
            w = d[6 + 16 * i] or 256
            im = Image.open(cesta)
            im.size = (w, w)
            vysledek.append(np.array(im.convert('RGBA')).astype(int))
        return vysledek
    return [np.array(Image.open(cesta).convert('RGBA')).astype(int)]


chyba = False
for novy, puvodni in dvojice:
    a, b = os.path.join(vystup, novy), os.path.join(koren, puvodni)
    if not os.path.exists(b):
        print(f'{puvodni}: v repu chybí')
        chyba = True
        continue
    stejne_md5 = hashlib.md5(Path(a).read_bytes()).digest() == hashlib.md5(Path(b).read_bytes()).digest()
    shoda = min((np.abs(x - y).max(axis=2) <= 8).mean() if x.shape == y.shape else 0 for x, y in zip(obrazy(a), obrazy(b)))
    ok = stejne_md5 or shoda >= 0.998
    chyba |= not ok
    print(f'{puvodni}: {"md5 shodné" if stejne_md5 else "md5 se liší"}, shodných pixelů {shoda * 100:.2f} % {"OK" if ok else "ROZDÍL"}')
sys.exit(1 if chyba else 0)
