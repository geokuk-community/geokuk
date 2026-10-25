#!/usr/bin/env python3
"""Rozpad startu: mediány časových značek 'Start: ...' z logu ze N běhů jaru."""
import os, re, shutil, statistics, subprocess, sys, tempfile, time

OKNO = "Otevřeno okno: GeoKuk"
sys.stdout.reconfigure(encoding="utf-8")
jar, n = sys.argv[1], int(sys.argv[2])
vysledky = {}
poradi = []
for i in range(n + 1):
    d = tempfile.mkdtemp(prefix="geokuk-rozpad-")
    log = os.path.join(d, "data", "log", "geokuk.log")
    t0 = time.perf_counter()
    p = subprocess.Popen(["java", "-Xmx1g", "-Dgeokuk.data=" + os.path.join(d, "data"), "-Duser.home=" + d, "-jar", jar, "--ovladani=0"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    okno = None
    while time.perf_counter() - t0 < 90:
        try:
            with open(log, encoding="utf-8", errors="replace") as f:
                if OKNO in f.read():
                    okno = (time.perf_counter() - t0) * 1000
                    break
        except OSError:
            pass
        time.sleep(0.02)
    time.sleep(2)  # dokončí se i zbytek startu
    p.terminate()
    try:
        p.wait(20)
    except subprocess.TimeoutExpired:
        p.kill()
    try:
        text = open(log, encoding="utf-8", errors="replace").read()
    except OSError:
        text = ""
    shutil.rmtree(d, ignore_errors=True)
    if i == 0:
        continue
    if okno:
        vysledky.setdefault("okno viditelné od spuštění procesu", []).append(okno)
    for m in re.finditer(r"Start: (.+?) za (\d+) ms", text):
        k = m.group(1)
        if k not in poradi:
            poradi.append(k)
        vysledky.setdefault(k, []).append(int(m.group(2)))
print("Medián z %d běhů, ms od začátku startu (Diagnostika), rozdíl proti předchozí značce" % n)
pred = 0
for k in poradi:
    v = vysledky.get(k)
    if not v:
        continue
    med = statistics.median(v)
    print("%-32s %6.0f  (+%4.0f)   min %d max %d" % (k, med, med - pred, min(v), max(v)))
    pred = med
v = vysledky.get("okno viditelné od spuštění procesu")
if v:
    print("%-32s %6.0f   (od spuštění procesu, min %.0f max %.0f)" % ("okno viditelné", statistics.median(v), min(v), max(v)))
