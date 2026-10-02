"""Databáze GeoGetu a GSAKu pro měření načítání: N keší s dlouhým popisem.

python3 vykon/gen.py SLOZKA [N] [DELKA_POPISU]
Popis je náhodný (nekomprimovatelný) a v řádku před hintem, jako nejhorší
případ, kdy čtení hintu znamená přečíst celý popis.
"""
import sqlite3, os, zlib, random, sys
SLOZKA = sys.argv[1]
N = int(sys.argv[2]) if len(sys.argv) > 2 else 100_000
L = int(sys.argv[3]) if len(sys.argv) > 3 else 20_000
def geoget(path):
    if os.path.exists(path): os.remove(path)
    c=sqlite3.connect(path); s=c.cursor()
    s.execute("CREATE TABLE geocache (id TEXT PRIMARY KEY, x REAL, y REAL, name TEXT, author TEXT, cachetype TEXT, cachesize TEXT, difficulty TEXT, terrain TEXT, cachestatus INTEGER, gs_ownerid INTEGER, dthidden INTEGER, country TEXT, state TEXT, dtfound INTEGER)")
    s.execute("CREATE TABLE geolist (id TEXT PRIMARY KEY, longdesc BLOB, shortdesc BLOB, hint TEXT)")
    s.execute("CREATE TABLE waypoint (id TEXT, x REAL, y REAL, prefixid TEXT, wpttype TEXT, name TEXT)")
    s.execute("CREATE TABLE geotag (id TEXT, ptrkat INTEGER, ptrvalue INTEGER)")
    s.execute("CREATE TABLE geotagcategory (key INTEGER, value TEXT)")
    s.execute("CREATE TABLE geotagvalue (key INTEGER, value TEXT)")
    r=random.Random(1)
    for i in range(N):
        kod="GC%05X"%(0x10000+i)
        s.execute("INSERT INTO geocache VALUES (?,?,?,?,?,?,?,?,?,0,1,20200101,'CZ','Praha',0)",(kod,49+r.random()*2,13+r.random()*5,"Keš %d"%i,"autor","Traditional Cache","Regular","2","3"))
        s.execute("INSERT INTO geolist VALUES (?,?,?,?)",(kod,os.urandom(L),zlib.compress(("Krátký popis %d"%i).encode()),"Hint %d"%i))
    c.commit(); c.close()
def gsak(path):
    if os.path.exists(path): os.remove(path)
    c=sqlite3.connect(path); s=c.cursor()
    for t in ["Attributes", "CacheImages", "Corrected", "Filter", "Ignore", "LogImages", "LogMemo", "Logs"]:
        s.execute("CREATE TABLE %s (Code TEXT)"%t)
    s.execute("CREATE TABLE Caches (Code TEXT PRIMARY KEY, Name TEXT, PlacedBy TEXT, Archived INTEGER, CacheType TEXT, Container TEXT, County TEXT, Country TEXT, Difficulty TEXT, FoundByMeDate TEXT, Latitude REAL, Longitude REAL, OwnerId INTEGER, OwnerName TEXT, PlacedDate TEXT, State TEXT, TempDisabled INTEGER, Terrain TEXT, LatOriginal REAL, LonOriginal REAL, Elevation INTEGER, FavPoints INTEGER)")
    s.execute("CREATE TABLE CacheMemo (Code TEXT PRIMARY KEY, LongDescription TEXT, ShortDescription TEXT, Url TEXT, Hints TEXT)")
    s.execute("CREATE TABLE Waypoints (cParent TEXT, cCode TEXT, cPrefix TEXT, cName TEXT, cType TEXT, cLat REAL, cLon REAL, cByuser INTEGER, cDate TEXT, cFlag INTEGER, sB1 INTEGER)")
    s.execute("CREATE TABLE Custom (Code TEXT)")
    r=random.Random(1)
    for i in range(N):
        kod="GC%05X"%(0x10000+i)
        s.execute("INSERT INTO Caches VALUES (?,?,'autor',0,'T','Regular','','CZ','2','',?,?,1,'autor','2020-01-01','Praha',0,'3',0,0,0,0)",(kod,"Keš %d"%i,49+r.random()*2,13+r.random()*5))
        s.execute("INSERT INTO CacheMemo VALUES (?,?,?,?,?)",(kod,os.urandom(L//2).hex(),"Krátký popis %d"%i,"","Hint %d"%i))
    c.commit(); c.close()
os.makedirs(SLOZKA, exist_ok=True)
geoget(os.path.join(SLOZKA, "geoget.db3")); gsak(os.path.join(SLOZKA, "gsak.db3"))
