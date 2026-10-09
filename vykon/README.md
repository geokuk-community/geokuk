# Měření výkonu

Porovná dvě verze GeoKuku na stejných syntetických datech:

```sh
vykon/mer.sh                       # origin/main proti HEAD
vykon/mer.sh v6.1.0 moje-vetev     # libovolné dva commity, větve nebo tagy
CO=kresleni vykon/mer.sh           # jen část: db, hint, kresleni, program
N=20000 POPIS=5000 vykon/mer.sh    # menší databáze
```

Skript obě verze postaví ve vlastních worktree (`target/vykon`), přeloží
proti nim harness ze `src/` (co verze neumí, přeskočí), jednou vygeneruje
databáze a vypíše výsledek, který uloží i do `target/vykon/<stroj>-<datum>.txt`.
Při výchozích hodnotách mají databáze 2× 2 GB a celé měření trvá asi 20 minut.

Potřeba: JDK 21, Python 3, git, Bash (na Windows Git Bash), na Linuxu bez
displeje `xvfb-run`. Studené čtení (soubor vyhozený z cache systému) funguje
jen na Linuxu.

Na GitHubu měří workflow **Výkon** (Actions → Výkon → Run workflow) na
Linuxu i na Windows, výsledek je v souhrnu běhu a v artefaktech.

## Části

- `db`: `GeogetLoader` a `GsakDbLoader` načtou databázi z `gen.py`
  (N keší s náhodným popisem délky POPIS, popis před hintem). Čas
  a přečtené bajty (jen Linux).
- `hint`: dotažení hintu jedné keše z databáze.
- `kresleni`: `Malovadlo` s trasou 500 000 bodů (měřítka 8, 12, 15,
  nevybraná i vybraná), `JZvyraznovaciKruhySlide` s 25 000 waypointy,
  `EKesWptType.decode`. Kreslí do obrázku 1400×900 celý a s clipem jedné
  dlaždice, medián z opakování.
- `kesoidy` (jen na vyžádání, jen nová verze): `JKesoidySlide` překreslí
  okno 1400×900 s N kešemi ve výřezu, ikony na zoomu 14 (bez limitu
  waypointů) pro počty v `IKONY` a tečky na zoomu 10 pro počty v `TECKY`.
  Medián ze 7 překreslení, alokace na jedno překreslení a halda s daty.
- `paralelne` (jen na vyžádání, jen nová verze): `MerRozpad` přečte obě
  databáze do prázdného builderu naráz, každou ve vlastním vlákně, pro
  1 a 2 vlákna. Celkový čas, procesorový čas vláken a zrychlení proti
  jednomu vláknu. Na vlastních databázích: `MerRozpad geoget=… gsak=…
  opensak=… kola=3 vlakna=1,2,4` (typ=cesta lze opakovat). S `skupiny=1`
  místo měření času jen z kódů databází vypíše skupiny zdrojů sdílejících
  klíč jména a pro každou dvojici počet společných klíčů s ukázkou kódů.
  S `predehrat=1` čte zdroje postupně a druhé vlákno mezitím sekvenčně
  načte soubor dalšího zdroje do cache systému; s `predehrat=0` bez toho.
  Smysl má studeně, každý běh po restartu počítače.
  S `retezce=1` načte zdroje jako program a pro každé textové pole
  waypointů a kešoidů vypíše počet řetězců, různých hodnot, paměť teď
  a paměť při sdílení stejných textů.
- `offline` (jen na vyžádání, jen nová verze): `MerOffline` otevře mapy
  `.map` ze složky `MAPY` s tématem `TEMA` a pro každý zoom ze `ZOOMY`
  vykreslí 6×6 dlaždic kolem `MISTO`: první průchod v jednom vlákně, pak
  v počtech vláken z `VLAKNA` (výchozí 1,2) a načtení z cache dlaždic.
  Čas na dlaždici, stěna na dlaždici, odhad první
  obrazovky (40 dlaždic), velikost PNG a halda. Bez `MAPY` měří malou
  syntetickou mapu z testů.
- `program`: smoke testy `velkaData` (GPX s 50 000 keší) a
  `velkaDatabazeGeogetu` (200 000 keší) spustí celý program s oknem;
  vypíše čas do zobrazení okna, načtení keší, obsazenou paměť a nejdelší
  událost na EDT.

Harness sahá na balíčkově viditelné API a přes reflexi na pole tříd.
Když se ve verzi změní, uprav `src/`.
