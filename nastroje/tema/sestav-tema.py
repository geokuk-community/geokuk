"""sestav-tema.py JAR VYSTUP [--zvys-verzi]
Ze společného zdroje vzhledu GeoKuk (zdroj/geokuk-spolecne.xml + bloky dat) vyrobí:
  VYSTUP/geokuk/GeoKuk.xml, GeoKuk_detail.xml (+ symbols/)  vestavěná témata, trasy z vrstvy GeoKuku (fmrel*), zdroje jar: z programu
  VYSTUP/trasy.xml                                          překryv Turistické a cyklotrasy (podklady/trasy.xml v programu)
  VYSTUP/GeoKuk-tema.zip                                    téma pro aplikace s mapami mapsforge (c:geo, aDrake) k mapám GeoKuku:
                                                            základ z download.mapsforge.org a vrstvy GeoKuku (fmrel*), zdroje file:
  VYSTUP/GeoKuk-tema-PAWS.zip                               totéž pro mapy s trasami v tagech osmc_*, zdroje file:
Symboly jar:symbols a jar:patterns se berou z JAR (geokuk.jar, složka assets/), vlastní symboly z resources programu.
Oba zipy mají společnou verzi z VERZE. Když se obsah zipů liší od otisku ve VERZE, skript skončí chybou;
--zvys-verzi nastaví verzi o jednu vyšší než na origin/main a zapíše nový otisk."""
import hashlib, os, re, subprocess, sys, tempfile, zipfile
D = os.path.dirname(os.path.abspath(__file__))
Z = os.path.join(D, 'zdroj')
KOREN = os.path.join(D, '..', '..')
SYMBOLY = os.path.join(KOREN, 'src', 'main', 'resources', 'cz', 'geokuk', 'plugins', 'mapy', 'kachle', 'podklady', 'geokuk')
argumenty = [a for a in sys.argv[1:] if a != '--zvys-verzi']
zvys_verzi = len(argumenty) < len(sys.argv) - 1
jar, vystup = argumenty
SOUBOR_VERZE = os.path.join(D, 'VERZE')
ZASTUPCE_VERZE = '#VERZE#'

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


IKONA_VYSKA = 16      # jednotná výška ikon bodů (px); šířka podle poměru stran SVG
IKONA_VYJIMKY = {'informace.svg': 15}   # ikony s jinou výškou

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
vrstvy = geokuk
paws = sestav(cti('data-osmc-trasy.xml'), '')
def vrstevnice_drive(xml, o=1):
    """Pro telefon (bez stínování): pravidla vrstevnic začínají o jeden zoom dřív."""
    m = re.search(r'<!--#VRSTEVNICE#-->(.*?)<!--#/VRSTEVNICE#-->', xml, re.S)
    blok = re.sub(r'zoom-(min|max)="(\d+)"', lambda k: 'zoom-%s="%d"' % (k.group(1), int(k.group(2)) - o), m.group(1))
    return xml[:m.start(1)] + blok + xml[m.end(1):]


geokuk = velikost_ikon(geokuk)
vrstvy = vrstevnice_drive(velikost_ikon(vrstvy))
paws = vrstevnice_drive(velikost_ikon(paws))
modul_detail = velikost_ikon(cti('modul-detail.xml'))
KONEC = '</rendertheme>'
def s_detailem(xml):
    i = xml.rindex(KONEC)
    return xml[:i] + modul_detail.rstrip(chr(10)) + chr(10) + xml[i:]
geokuk_detail = s_detailem(geokuk)

PUVOD = ('Odvozeno z tématu GeoKuk (GNU LGPL 3 nebo novější, původně mapsforge default.xml). '
         'Stínování terénu kreslí aplikace, která ho umí a má výšková data.')
HLAVICKY = {
    'vrstvy': 'GeoKuk: téma GeoKuku pro aplikace s mapami mapsforge (c:geo, aDrake) k mapám GeoKuku: základní mapa '
              'z download.mapsforge.org a vrstvy GeoKuku (turistické a cyklotrasy, body, vrstevnice). ' + PUVOD,
    'paws': 'GeoKuk: téma GeoKuku pro aplikace s mapami mapsforge (c:geo, aDrake) k mapám, které mají turistické '
            'a cyklotrasy v tagech osmc_*. ' + PUVOD,
}

def pro_aplikace(xml, varianta):
    """Téma pro aplikace: hlavička s původem a všechny zdroje relativně vedle tématu."""
    hlavicka = '%s Verze tématu: %s.' % (HLAVICKY[varianta], ZASTUPCE_VERZE)
    xml = xml.replace('<?xml version="1.0" encoding="UTF-8"?>', '<?xml version="1.0" encoding="UTF-8"?>\n<!-- %s -->' % hlavicka, 1)
    return xml.replace('src="jar:', 'src="file:')

zipy = {'GeoKuk-tema.zip': ('vrstvy', vrstvy), 'GeoKuk-tema-PAWS.zip': ('paws', paws)}
PRO_MAPY = {'vrstvy': 'geokuk-maps', 'paws': 'osmc'}
pro_mapy = {jm: PRO_MAPY[v] for jm, (v, _) in zipy.items()}
zipy = {jm: (pro_aplikace(xml, v), pro_aplikace(s_detailem(xml), v)) for jm, (v, xml) in zipy.items()}
# Každý zip kreslí trasy jen z jedné sady tagů, aby se na mapě nekreslily dvakrát.
for jm, (zakl, det) in zipy.items():
    for xml in (zakl, det):
        ma_fmrel, ma_osmc = 'k="fmrel' in xml, 'osmc_' in xml
        assert (ma_fmrel, ma_osmc) == ((True, False) if jm == 'GeoKuk-tema.zip' else (False, True)), '%s: fmrel %s, osmc %s' % (jm, ma_fmrel, ma_osmc)

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

def soubory_zipu(zakl, det, pro):
    """Obsah zipu se zástupcem místo čísla verze."""
    readme = bajty(os.path.join(D, 'zip', 'README.txt')).decode('utf-8').replace('\n\n', '\n\nVerze tématu: %s\n\n' % ZASTUPCE_VERZE, 1)
    soubory = {'GeoKuk.xml': zakl, 'GeoKuk_detail.xml': det, 'README.txt': readme,
               'tema.json': '{"version": %s, "for": "%s"}\n' % (ZASTUPCE_VERZE, pro),
               'LICENSE-GPL-3.0.txt': bajty(os.path.join(KOREN, 'LICENSE')),
               'LICENSE-LGPL-3.0.txt': bajty(os.path.join(KOREN, 'licence', 'LGPL-3.0.txt'))}
    for typ, c in zdroje(zakl + det):
        soubory[c] = obsah(c)
    return {jm: d.encode('utf-8') if isinstance(d, str) else d for jm, d in soubory.items()}

def otisk(obsahy):
    """sha256 obsahu všech zipů se zástupcem místo verze: stejný zdroj dá stejný otisk."""
    h = hashlib.sha256()
    for jm in sorted(obsahy):
        for soubor in sorted(obsahy[jm]):
            d = obsahy[jm][soubor]
            h.update(('%s/%s %d\n' % (jm, soubor, len(d))).encode('utf-8') + d)
    return h.hexdigest()

def cti_verzi(text):
    hodnoty = dict(l.split('=', 1) for l in text.splitlines() if l and not l.startswith('#'))
    return int(hodnoty['verze']), hodnoty['otisk']

def verze_na_main():
    try:
        text = subprocess.check_output(['git', '-C', KOREN, 'show', 'origin/main:nastroje/tema/VERZE'], stderr=subprocess.DEVNULL)
    except (OSError, subprocess.CalledProcessError):
        return None
    return cti_verzi(text.decode('utf-8'))[0]

def zapis_zip(zip_cesta, soubory, verze):
    """Zip se stálým časem souborů, aby stejný zdroj dal stejný zip; vrací názvy souborů v zipu."""
    zastupce = ZASTUPCE_VERZE.encode('utf-8')
    soubory = {jm: d.replace(zastupce, str(verze).encode('utf-8')) if jm.endswith(('.xml', '.txt', '.json')) else d for jm, d in soubory.items()}
    with zipfile.ZipFile(zip_cesta, 'w', zipfile.ZIP_DEFLATED) as z:
        for jm in sorted(soubory):
            z.writestr(zipfile.ZipInfo(jm, (1980, 1, 1, 0, 0, 0)), soubory[jm], zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(zip_cesta) as z:
        v_zipu = set(z.namelist())
        for jm in ('GeoKuk.xml', 'GeoKuk_detail.xml'):
            chybi = [c for _, c in zdroje(z.read(jm).decode('utf-8')) if c not in v_zipu]
            assert not chybi, '%s: v zipu chybí %s' % (jm, chybi)
    return v_zipu

obsahy = {jm: soubory_zipu(zakl, det, pro_mapy[jm]) for jm, (zakl, det) in zipy.items()}
novy_otisk = otisk(obsahy)
verze, ulozeny_otisk = cti_verzi(bajty(SOUBOR_VERZE).decode('utf-8'))
if zvys_verzi and novy_otisk != ulozeny_otisk:
    na_main = verze_na_main()
    verze = (na_main if na_main is not None else verze) + 1
    zapis(SOUBOR_VERZE, ('# Verze tématu pro aplikace (GeoKuk-tema.zip, GeoKuk-tema-PAWS.zip) a otisk obsahu zipů, zapisuje\n'
                         '# python3 nastroje/tema/sestav-tema.py JAR VYSTUP --zvys-verzi\nverze=%d\notisk=%s\n' % (verze, novy_otisk)).encode('utf-8'))
    ulozeny_otisk = novy_otisk
for jm in zipy:
    zapis_zip(os.path.join(vystup, jm), obsahy[jm], verze)

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
print('GeoKuk.xml %d B; ' % len(geokuk.encode('utf-8'))
      + '; '.join('%s %d B' % (jm, os.path.getsize(os.path.join(vystup, jm))) for jm in sorted(zipy)) + '; verze tématu %d' % verze)
if novy_otisk != ulozeny_otisk:
    sys.exit('Téma se změnilo: zvyš verzi v nastroje/tema/VERZE a aktualizuj otisk (příkaz '
             'python3 nastroje/tema/sestav-tema.py target/geokuk.jar target/tema --zvys-verzi).')
