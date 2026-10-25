#!/usr/bin/env python3
"""Čas do otevření okna GeoKuku: stará a nová verze střídavě, medián, poměr proti prahu.

  start.py POCET PRAH_PROCENT STARA.jar NOVA.jar

Každý běh má novou prázdnou datovou složku. Měří se doba od spuštění procesu
do řádku „Otevřeno okno: GeoKuk“ v logu. První dvojice běhů se zahazuje
(studená cache systému). Končí kódem 3, když je nová verze o víc než PRAH_PROCENT
pomalejší, a kódem 2, když okno nějaký běh neotevřel ani na druhý pokus. Jiný nenulový kód je chyba skriptu.
"""
import os
import shutil
import statistics
import subprocess
import sys
import tempfile
import time

OKNO = "Otevřeno okno: GeoKuk"
LIMIT_S = 90


def beh(jar):
    data = tempfile.mkdtemp(prefix="geokuk-start-")
    try:
        log = os.path.join(data, "data", "log", "geokuk.log")
        t0 = time.perf_counter()
        p = subprocess.Popen(["java", "-Xmx1g", "-Dgeokuk.data=" + os.path.join(data, "data"), "-Duser.home=" + data, "-jar", jar, "--ovladani=0"],
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            while time.perf_counter() - t0 < LIMIT_S:
                if p.poll() is not None:
                    return None
                try:
                    with open(log, encoding="utf-8", errors="replace") as f:
                        if OKNO in f.read():
                            return (time.perf_counter() - t0) * 1000
                except OSError:
                    pass  # log ještě nevznikl nebo ho program právě zapisuje, zkusí se znovu
                time.sleep(0.02)
            return None
        finally:
            p.terminate()
            try:
                p.wait(20)
            except subprocess.TimeoutExpired:
                p.kill()
    finally:
        shutil.rmtree(data, ignore_errors=True)


def main():
    # Windows má výchozí kódování konzole cp1252, české znaky by shodily výpis.
    sys.stdout.reconfigure(encoding="utf-8")
    pocet, prah = int(sys.argv[1]), float(sys.argv[2])
    jary = {"stará": sys.argv[3], "nová": sys.argv[4]}
    casy = {k: [] for k in jary}
    for i in range(pocet + 1):
        for k, jar in jary.items():
            ms = beh(jar)
            if ms is None:
                # Jednorázový výpadek (pomalý runner, zamčený soubor) neruší celé měření.
                print("%s: okno se neotevřelo, zkouším znovu" % k)
                ms = beh(jar)
            if ms is None:
                print("%s: okno se neotevřelo" % k)
                return 2
            if i > 0:
                casy[k].append(ms)
    med = {k: statistics.median(v) for k, v in casy.items()}
    for k, v in casy.items():
        print("%s: okno %.0f ms (medián z %d běhů: %s)" % (k, med[k], len(v), " ".join("%.0f" % x for x in v)))
    zmena = (med["nová"] / med["stará"] - 1) * 100
    print("nová proti staré: %+.1f %% (práh +%.0f %%)" % (zmena, prah))
    if zmena > prah:
        print("PRÁH PŘEKROČEN: start nové verze je pomalejší o víc než %.0f %%" % prah)
        return 3
    return 0


if __name__ == "__main__":
    sys.exit(main())
