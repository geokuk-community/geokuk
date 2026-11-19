"""Z vykreslených PNG udělá soubory pro program: ikony okna, geokuk.ico, logo načítání a úvodní obrázek.
python3 -I dokonci.py <mezivysledky> <vystup>"""
import io
import os
import struct
import sys

import numpy as np
from PIL import Image

ZELENA = (0x23, 0x9C, 0x2B)
POZADI_IKONY = (0x2A, 0x2E, 0x32)  # RGB průhledných pixelů ikony (tmavá dlaždice), aby zmenšování nedělalo světlý lem

mezi, vystup = sys.argv[1], sys.argv[2]
os.makedirs(vystup, exist_ok=True)


def bmp(im):
    """Položka .ico jako 32bitová bitmapa s alfou a maskou (pro 16–48 px, kvůli starším nástrojům)."""
    a = np.array(im.convert('RGBA'))
    s = a.shape[0]
    hlavicka = struct.pack('<IiiHHIIiiII', 40, s, 2 * s, 1, 32, 0, 0, 0, 0, 0, 0)
    pixely = a[::-1][:, :, [2, 1, 0, 3]].tobytes()
    radek = ((s + 31) // 32) * 4
    maska = bytearray()
    for y in range(s - 1, -1, -1):
        r = bytearray(radek)
        for x in range(s):
            if a[y, x, 3] == 0:
                r[x // 8] |= 0x80 >> (x % 8)
        maska += r
    return hlavicka + pixely + bytes(maska)


def png(im):
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    return b.getvalue()


ikony = {}
for s in (16, 24, 32, 48, 64, 128, 256):
    a = np.array(Image.open(os.path.join(mezi, f'ikona-{s}.png')).convert('RGBA'))
    a[a[:, :, 3] == 0, :3] = POZADI_IKONY
    ikony[s] = Image.fromarray(a)
    ikony[s].save(os.path.join(vystup, f'geokuk{s}.png'), optimize=True)

polozky = [(s, (png if s > 48 else bmp)(ikony[s])) for s in (256, 128, 64, 48, 32, 24, 16)]
data = b''
adresar = b''
posun = 6 + 16 * len(polozky)
for s, blob in polozky:
    adresar += struct.pack('<BBBBHHII', s % 256, s % 256, 0, 0, 1, 32, len(blob), posun + len(data))
    data += blob
with open(os.path.join(vystup, 'geokuk.ico'), 'wb') as f:
    f.write(struct.pack('<HHH', 0, 1, len(polozky)) + adresar + data)

a = np.array(Image.open(os.path.join(mezi, 'logo-zelene-400.png')).convert('RGBA'))
a[:, :, :3] = ZELENA  # jedna barva, průhlednost jen v alfě
Image.fromarray(a).save(os.path.join(vystup, 'geokuk-logo.png'), optimize=True)

Image.open(os.path.join(mezi, 'splash-400.png')).convert('RGB').save(os.path.join(vystup, 'geokuk400.jpg'), quality=95)
print('hotovo:', ', '.join(sorted(f for f in os.listdir(vystup) if os.path.isfile(os.path.join(vystup, f)))))
