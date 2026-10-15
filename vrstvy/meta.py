#!/usr/bin/env python3
"""Zapíše SOUBOR.meta.json pro katalog.
Použití: meta.py typ soubor datumDat zdrojDat licence.txt [extra_json]"""
import hashlib, json, os, sys
from datetime import datetime, timezone

typ, soubor, datum, zdroj, licence = sys.argv[1:6]
extra = json.loads(sys.argv[6]) if len(sys.argv) > 6 else {}
h = hashlib.sha256()
with open(soubor, 'rb') as f:
    for blok in iter(lambda: f.read(1 << 20), b''):
        h.update(blok)
with open(licence, encoding='utf-8') as f:
    text = f.read().strip()
m = {'typ': typ, 'nazev': os.path.basename(soubor), 'velikost': os.path.getsize(soubor), 'sha256': h.hexdigest(),
     'datumDat': datum, 'zdrojDat': zdroj, 'vyrobeno': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'),
     'licenceText': text}
m.update(extra)
with open(os.path.splitext(soubor)[0] + '.meta.json', 'w', encoding='utf-8') as f:
    json.dump(m, f, ensure_ascii=False, indent=1)
    f.write('\n')
