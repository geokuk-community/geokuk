#!/usr/bin/env python3
"""Variační koeficient čítačů z opakovaných běhů MerStart: citace.py SOUBOR_BEHU...

Každý soubor má řádky klíč=hodnota. Vypíše tabulku: průměr, směrodatná odchylka, VK (%), min a max.
"""
import statistics
import sys


def cti(soubor):
    d = {}
    with open(soubor, encoding="utf-8") as f:
        for r in f:
            if "=" in r and not r.startswith(("#", " ")):
                k, v = r.rstrip("\n").split("=", 1)
                try:
                    d[k] = float(v)
                except ValueError:
                    pass
    return d


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    behy = [cti(s) for s in sys.argv[1:]]
    klice = sorted(set().union(*[b.keys() for b in behy]))
    print("%-62s %12s %10s %7s %12s %12s" % ("čítač (%d běhů)" % len(behy), "průměr", "sm.odch.", "VK %", "min", "max"))
    for k in klice:
        h = [b.get(k, 0.0) for b in behy]
        if k.startswith("top.") or max(h) == 0:
            continue
        m = statistics.mean(h)
        s = statistics.stdev(h) if len(h) > 1 else 0.0
        print("%-62s %12.1f %10.1f %7.2f %12.1f %12.1f" % (k, m, s, 100 * s / m if m else 0, min(h), max(h)))
    print("## Nejčastější horní metody (počet vzorků JFR, součet přes běhy)")
    soucet = {}
    for b in behy:
        for k, v in b.items():
            if k.startswith("top."):
                soucet[k[7:]] = soucet.get(k[7:], 0) + v
    for k, v in sorted(soucet.items(), key=lambda x: -x[1])[:15]:
        print("%8d  %s" % (v, k))


if __name__ == "__main__":
    main()
