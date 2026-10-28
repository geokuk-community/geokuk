#!/usr/bin/env python3
"""Porovná schéma OpenSAKu s tím, co GeoKuk čte, a vypíše zprávu v Markdownu.

Použití: opensak_zmeny.py <pouzite.txt> <ulozene.txt> <aktualni.txt>
  pouzite.txt  řádky „tabulka sloupec“, které načítání čte (src/test/resources/opensak-pouzite-sloupce.txt)
  ulozene.txt  schéma, se kterým je GeoKuk ověřený (výstup opensak_schema.py)
  aktualni.txt schéma ze zkoumané větve OpenSAKu (výstup opensak_schema.py)
Návratový kód 1 jen když v aktuálním schématu chybí sloupec nebo tabulka, které GeoKuk čte; přidané sloupce a vyšší verze jsou jen informace.
"""
import sys
from pathlib import Path


def nacti(soubor):
    radky = [r.strip() for r in Path(soubor).read_text(encoding="utf-8").splitlines() if r.strip()]
    verze = None
    if radky and radky[0].startswith("verze "):
        verze = int(radky[0].split()[1])
        radky = radky[1:]
    return verze, set(radky)


def main():
    _, pouzite = nacti(sys.argv[1])
    verze_ulozena, ulozene = nacti(sys.argv[2])
    verze_aktualni, aktualni = nacti(sys.argv[3])
    chybi = sorted(pouzite - aktualni)
    pridane = sorted(aktualni - ulozene)
    zmizele = sorted((ulozene - aktualni) - pouzite)
    if chybi:
        tabulky_aktualni = {r.split()[0] for r in aktualni}
        print("**Chybí sloupce, které GeoKuk čte:**")
        print()
        for radek in chybi:
            tabulka, sloupec = radek.split()
            print("- `%s.%s`%s" % (tabulka, sloupec, "" if tabulka in tabulky_aktualni else " (tabulka chybí)"))
        print()
    print("Verze schématu: %s (ověřená %s)." % (verze_aktualni, verze_ulozena))
    if pridane:
        print("Přidané sloupce (GeoKuk je nečte): %s." % ", ".join(r.replace(" ", ".") for r in pridane))
    if zmizele:
        print("Zmizelé sloupce, které GeoKuk nečte: %s." % ", ".join(r.replace(" ", ".") for r in zmizele))
    sys.exit(1 if chybi else 0)


if __name__ == "__main__":
    main()
