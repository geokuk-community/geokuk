#!/usr/bin/env python3
"""Vypíše schéma databáze OpenSAKu ze zdrojáků: verzi a sloupce tabulek.

Použití: opensak_schema.py <kořen klonu OpenSAKu>
Výstup: řádek „verze N“ a pak seřazené řádky „tabulka sloupec“.
Typy se nevypisují, starší databáze je po migracích mají jiné než models.py.
"""
import ast
import re
import sys
from pathlib import Path


def sloupce(models):
    strom = ast.parse(models.read_text(encoding="utf-8"))
    for trida in strom.body:
        if not isinstance(trida, ast.ClassDef):
            continue
        tabulka = None
        jmena = []
        for prvek in trida.body:
            if isinstance(prvek, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "__tablename__" for t in prvek.targets):
                tabulka = prvek.value.value
            elif isinstance(prvek, ast.AnnAssign) and isinstance(prvek.value, ast.Call) \
                    and isinstance(prvek.value.func, ast.Name) and prvek.value.func.id == "mapped_column":
                argy = prvek.value.args
                if argy and isinstance(argy[0], ast.Constant) and isinstance(argy[0].value, str):
                    jmena.append(argy[0].value)
                else:
                    jmena.append(prvek.target.id)
        if tabulka:
            for jmeno in jmena:
                yield "%s %s" % (tabulka, jmeno)


def verze(database):
    m = re.search(r"^SCHEMA_VERSION\s*=\s*(\d+)", database.read_text(encoding="utf-8"), re.M)
    if not m:
        raise SystemExit("V database.py chybí SCHEMA_VERSION")
    return int(m.group(1))


def main():
    db = Path(sys.argv[1]) / "src" / "opensak" / "db"
    print("verze %d" % verze(db / "database.py"))
    for radek in sorted(sloupce(db / "models.py")):
        print(radek)


if __name__ == "__main__":
    main()
