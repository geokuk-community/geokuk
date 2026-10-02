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
- `program`: smoke testy `velkaData` (GPX s 50 000 keší) a
  `velkaDatabazeGeogetu` (200 000 keší) spustí celý program s oknem;
  vypíše čas do zobrazení okna, načtení keší, obsazenou paměť a nejdelší
  událost na EDT.

Harness sahá na balíčkově viditelné API a přes reflexi na pole tříd.
Když se ve verzi změní, uprav `src/`.
