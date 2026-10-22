"""sestav-tema.py JAR VYSTUP
Ze společného zdroje vzhledu GeoKuk (zdroj/geokuk-spolecne.xml + bloky dat) vyrobí:
  VYSTUP/geokuk/GeoKuk.xml, GeoKuk_detail.xml (+ symbols/)  vestavěná témata, trasy z vrstvy GeoKuku (fmrel*), zdroje jar: z programu
  VYSTUP/trasy.xml                                          překryv Turistické a cyklotrasy (podklady/trasy.xml v programu)
  VYSTUP/GeoKuk-tema.zip                                    téma pro aplikace s mapami mapsforge (aDrake, Locus, OruxMaps),
                                                            trasy z tagů osmc_*, všechny zdroje relativně file:
Symboly jar:symbols a jar:patterns se berou z JAR (geokuk.jar, složka assets/), vlastní symboly z resources programu."""
import os, re, subprocess, sys, tempfile, zipfile
D = os.path.dirname(os.path.abspath(__file__))
Z = os.path.join(D, 'zdroj')
KOREN = os.path.join(D, '..', '..')
SYMBOLY = os.path.join(KOREN, 'src', 'main', 'resources', 'cz', 'geokuk', 'plugins', 'mapy', 'kachle', 'podklady', 'geokuk')
jar, vystup = sys.argv[1], sys.argv[2]

def bajty(p):
    with open(p, 'rb') as f:
        return f.read()

def zapis(p, data):
    with open(p, 'wb') as f:
        f.write(data)

def cti(jm):
    return bajty(os.path.join(Z, jm)).decode('utf-8')

spolecne = cti('geokuk-spolecne.xml')
for m in ('#DATA-TRASY#', '#DATA-NAZVY#'):
    assert '<!--%s-->' % m in spolecne, m

ZNACKY = ('    <!--#TOURIST#-->', '    <!--#/TOURIST#-->')
useky_tourist = re.findall(re.escape(ZNACKY[0]) + r'\n(.*?)\n' + re.escape(ZNACKY[1]), spolecne, re.S)
assert useky_tourist, 'chybí úseky tourist'
spolecne = '\n'.join(l for l in spolecne.split('\n') if l not in ZNACKY)

def sestav(trasy, nazvy):
    return spolecne.replace('    <!--#DATA-TRASY#-->', trasy.rstrip('\n')).replace('    <!--#DATA-NAZVY#-->', nazvy.rstrip('\n'))


IKONA_VYSKA = 20      # jednotná výška ikon bodů (px); šířka podle poměru stran SVG
IKONA_VYJIMKY = {'informace.svg': 18}   # ikony s jinou výškou

def velikost_ikon(xml):
    """Ikony bodů (<symbol>) dostanou jednotnou výšku; šířka se řídí poměrem stran."""
    def f(m):
        t = m.group(0)
        if 'symbol-percent' in t:
            return t
        t = re.sub(r'\s+symbol-(width|height)="[^"]*"', '', t)
        v = next((h for n, h in IKONA_VYJIMKY.items() if n in t), IKONA_VYSKA)
        return t.replace('<symbol', '<symbol symbol-height="%d"' % v, 1)
    return re.sub(r'<symbol(?=[\s/>])[^>]*>', f, xml)

geokuk = sestav(cti('data-geokuk-trasy.xml'), cti('data-geokuk-nazvy.xml'))
android = sestav(cti('data-osmc-trasy.xml'), '')
def vrstevnice_drive(xml, o=1):
    """Pro telefon (bez stínování): pravidla vrstevnic začínají o jeden zoom dřív."""
    m = re.search(r'<!--#VRSTEVNICE#-->(.*?)<!--#/VRSTEVNICE#-->', xml, re.S)
    blok = re.sub(r'zoom-(min|max)="(\d+)"', lambda k: 'zoom-%s="%d"' % (k.group(1), int(k.group(2)) - o), m.group(1))
    return xml[:m.start(1)] + blok + xml[m.end(1):]


geokuk = velikost_ikon(geokuk)
android = vrstevnice_drive(velikost_ikon(android))
modul_detail = velikost_ikon(cti('modul-detail.xml'))
KONEC = '</rendertheme>'
def s_detailem(xml):
    i = xml.rindex(KONEC)
    return xml[:i] + modul_detail.rstrip(chr(10)) + chr(10) + xml[i:]
geokuk_detail = s_detailem(geokuk)
android_detail = s_detailem(android)

hlavicka_android = ('<!-- GeoKuk: téma GeoKuku pro aplikace s mapami mapsforge (aDrake, Locus, OruxMaps). Odvozeno z tématu GeoKuk '
                    '(GNU LGPL 3 nebo novější, původně mapsforge default.xml). Stínování terénu a vrstvy GeoKuku v této variantě nejsou; '
                    'turistické a cyklotrasy z tagů osmc_* jsou součástí stylu. -->')
android = android.replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n' + hlavicka_android, 1)
android = android.replace('src="jar:', 'src="file:')
android_detail = android_detail.replace('src="jar:', 'src="file:')

zj = zipfile.ZipFile(jar)  # otevřený do konce běhu skriptu
assety = {n for n in zj.namelist() if n.startswith(('assets/symbols/', 'assets/patterns/')) and not n.endswith('/')}

def zdroje(xml):
    return sorted(set(re.findall(r'src="(jar|file):([^"]+)"', xml)))

def obsah(cesta):
    """Vlastní symbol z resources, jinak symbol z jaru."""
    p = os.path.join(SYMBOLY, cesta)
    if os.path.isfile(p):
        return bajty(p)
    if 'assets/' + cesta in assety:
        return zj.read('assets/' + cesta)
    raise SystemExit('chybí zdroj %s' % cesta)

os.makedirs(os.path.join(vystup, 'geokuk'), exist_ok=True)
zapis(os.path.join(vystup, 'geokuk', 'GeoKuk.xml'), geokuk.encode('utf-8'))
zapis(os.path.join(vystup, 'geokuk', 'GeoKuk_detail.xml'), geokuk_detail.encode('utf-8'))
for typ, c in zdroje(geokuk_detail):
    if typ == 'file':   # vlastní symboly vedle tématu
        d = os.path.join(vystup, 'geokuk', c); os.makedirs(os.path.dirname(d), exist_ok=True); zapis(d, obsah(c))

# Zip se stálým časem souborů, aby stejný zdroj dal stejný zip.
zip_cesta = os.path.join(vystup, 'GeoKuk-tema.zip')
soubory = {'GeoKuk.xml': android.encode('utf-8'), 'GeoKuk_detail.xml': android_detail.encode('utf-8'),
           'README.txt': bajty(os.path.join(D, 'zip', 'README.txt')),
           'LICENSE-GPL-3.0.txt': bajty(os.path.join(KOREN, 'LICENSE')),
           'LICENSE-LGPL-3.0.txt': bajty(os.path.join(KOREN, 'licence', 'LGPL-3.0.txt'))}
for typ, c in zdroje(android + android_detail):
    soubory[c] = obsah(c)
with zipfile.ZipFile(zip_cesta, 'w', zipfile.ZIP_DEFLATED) as z:
    for jm in sorted(soubory):
        z.writestr(zipfile.ZipInfo(jm, (1980, 1, 1, 0, 0, 0)), soubory[jm], zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(zip_cesta) as z:
    v_zipu = set(z.namelist())
    for jm in ('GeoKuk.xml', 'GeoKuk_detail.xml'):
        chybi = [c for _, c in zdroje(z.read(jm).decode('utf-8')) if c not in v_zipu]
        assert not chybi, '%s: v zipu chybí %s' % (jm, chybi)

# Překryv tras: trasy z tagů osmc_*, pak data GeoKuku, prvky vrstvy tourist a jména tras; symboly vestavěného tématu ze složky geokuk/.
with tempfile.TemporaryDirectory() as d:
    subprocess.check_call([sys.executable, '-I', os.path.join(D, 'generuj-trasy.py'), os.path.join(d, 't.xml')])
    trasy = bajty(os.path.join(d, 't.xml')).decode('utf-8')
konec = '</rendertheme>\n'
assert trasy.endswith(konec)
trasy = (trasy[:-len(konec)] + '<!-- Trasy, skály a body ze stažené vrstvy tourist, stejně jako v tématu GeoKuk. -->\n'
         + cti('data-geokuk-trasy.xml').rstrip('\n') + '\n' + '\n'.join(useky_tourist) + '\n' + cti('data-geokuk-nazvy.xml').rstrip('\n') + '\n' + konec)
trasy = trasy.replace('src="file:symbols/', 'src="file:geokuk/symbols/')
zapis(os.path.join(vystup, 'trasy.xml'), trasy.encode('utf-8'))
print('GeoKuk.xml %d B; GeoKuk-tema.zip %d B, %d souborů' % (len(geokuk.encode('utf-8')), os.path.getsize(zip_cesta), len(v_zipu)))
