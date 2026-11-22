"""Pozadí okna dmg pro macOS: šipka od GeoKuk.app ke složce Aplikace a rada k prvnímu spuštění.

Spouští se na macOS (písmo SF z /System/Library/Fonts), potřebuje Pillow:
    python3 -I grafika/generator/dmg-pozadi.py distribuce/macos
Vytvoří dmg-pozadi.png (640 × 440) a dmg-pozadi@2x.png (1280 × 880). Rozmístění ikon v okně je
v distribuce/macos/dmg-nastaveni.py, souřadnice tady musí odpovídat.
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFont

SIRKA, VYSKA = 640, 440
# Středy ikon GeoKuk.app a Aplikace, stejné jako v dmg-nastaveni.py.
APLIKACE_X, CIL_X, IKONY_Y = 160, 480, 170
VELIKOST_IKONY = 96

ZELENA = (0x23, 0x9C, 0x2B)
TEXT = (0x1E, 0x21, 0x24)
TEXT_SLABY = (0x55, 0x5B, 0x60)
POZADI_NAHORE = (0xF8, 0xF9, 0xF7)
POZADI_DOLE = (0xE6, 0xEB, 0xE4)

PISMO = "/System/Library/Fonts/SFNS.ttf"


def pismo(velikost, tucne, meritko):
    f = ImageFont.truetype(PISMO, velikost * meritko)
    try:
        f.set_variation_by_name("Semibold" if tucne else "Regular")
    except (OSError, ValueError):
        pass
    return f


def text_na_stred(kresba, y, text, velikost, barva, meritko, tucne=False):
    f = pismo(velikost, tucne, meritko)
    sirka = kresba.textlength(text, font=f)
    kresba.text(((SIRKA * meritko - sirka) / 2, y * meritko), text, font=f, fill=barva)


def kresli(meritko):
    s = meritko
    obr = Image.new("RGB", (SIRKA * s, VYSKA * s))
    kresba = ImageDraw.Draw(obr)
    for y in range(VYSKA * s):
        t = y / (VYSKA * s - 1)
        barva = tuple(round(a + (b - a) * t) for a, b in zip(POZADI_NAHORE, POZADI_DOLE))
        kresba.line([(0, y), (SIRKA * s, y)], fill=barva)

    text_na_stred(kresba, 26, "Přetáhněte GeoKuk do složky Aplikace", 20, TEXT, s, tucne=True)
    text_na_stred(kresba, 56, "nebo do vlastní složky jako přenosnou verzi (data budou vedle aplikace)", 12, TEXT_SLABY, s)

    # Šipka mezi ikonami.
    zacatek = APLIKACE_X + VELIKOST_IKONY / 2 + 22
    konec = CIL_X - VELIKOST_IKONY / 2 - 22
    tloustka = 6
    hrot = 16
    kresba.line([(zacatek * s, IKONY_Y * s), ((konec - hrot) * s, IKONY_Y * s)], fill=ZELENA, width=tloustka * s)
    kresba.polygon(
        [(konec * s, IKONY_Y * s), ((konec - hrot * 1.4) * s, (IKONY_Y - hrot) * s), ((konec - hrot * 1.4) * s, (IKONY_Y + hrot) * s)],
        fill=ZELENA,
    )

    text_na_stred(kresba, 272, "Při prvním spuštění macOS GeoKuk zablokuje, protože není podepsaný u Applu.", 12, TEXT_SLABY, s)
    text_na_stred(kresba, 290, "Povolte ho v Nastavení systému › Soukromí a zabezpečení › Přesto otevřít.", 12, TEXT_SLABY, s)
    text_na_stred(kresba, 308, "Podrobnosti jsou v CTIMNE.txt.", 12, TEXT_SLABY, s)
    return obr


def main():
    cil = sys.argv[1] if len(sys.argv) > 1 else "."
    kresli(1).save(os.path.join(cil, "dmg-pozadi.png"), optimize=True)
    kresli(2).save(os.path.join(cil, "dmg-pozadi@2x.png"), optimize=True)


if __name__ == "__main__":
    main()
