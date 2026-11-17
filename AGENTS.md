# AGENTS.md

Pokyny pro AI asistenty (a lidi), kteří pracují na kódu Geokuku.

Dokument vznikl pro GeoKuk 5.2.0 a byl upraven pro stav forku geokuk-community k commitu `c4b81fc3` (11/2026).

## Než začneš

**Přečti `doc/SLOVNIK.md`.** Geokuk má vlastní terminologii (kešoid, kachle × dlaždice, Mou, moumer, genotyp, alela, sklivec, bousek…) a vlastní jmenné konvence. Bez slovníku kód špatně pochopíš i pojmenuješ. Pojmy používej přesně v jeho významu, nové pojmy do něj doplň.

**Přečti `CONTRIBUTING.md`.** Platí pro lidi i asistenty: větev od aktuálního `main`, jeden pull request = jedna věc, oprava chyby s testem, který bez opravy neprojde, texty, komentáře a popisy commitů česky s diakritikou, návrh řádku do `CHANGELOG.md`. Při rozporu s tímto dokumentem má přednost `CONTRIBUTING.md`.

## O projektu

Geokuk je desktopová aplikace (Java, Swing) pro plánování geocachingových výletů nad mapami. Načítá keše a další kešoidy (GPX, databáze GeoGetu, GSAKu a OpenSAKu, fotky s GPS), zobrazuje je na online i offline mapových podkladech, filtruje, třídí do výletu, umožňuje kreslit cesty a plánovat challenge. Uživatelské rozhraní i doména jsou česky.

## Sestavení a spuštění

Vyžaduje JDK 21. Maven se stáhne sám přes Maven Wrapper (`./mvnw`, ve Windows `.\mvnw.cmd`).

```sh
./mvnw -B clean verify                    # překlad + testy + SpotBugs
java -Xmx2g -jar target/geokuk-6.5.0.jar  # spuštění
```

- Překládá se s `--release 8`, program má běžet na Javě 8 a novější. API z novější Javy proto nepoužívej.
- Varování překladače sestavení shodí (`failOnWarning`), stejně jako nový nález SpotBugs (výjimky a základ jsou ve složce `spotbugs/`) a nezachycená výjimka v testu.
- Testy: JUnit 4, Truth a Mockito, `src/test/java`. Testovací data v `testData/`. Testy běží nad datovou složkou `target/test-data` (vlastnost `geokuk.data`).
- Smoke test celého programu (`./mvnw -B -P smoke verify`) potřebuje displej, viz `README.md`, oddíl Build.
- Kód používá **Lombok** (`@Data`, `@Slf4j`…). IDE potřebuje Lombok plugin.
- Program si vše ukládá do složky `data` vedle jaru (při spuštění z `target/` tedy do `target/data`, kterou `clean` smaže). Vydává se jako `geokuk.jar`, spouštěč `start.jar` a zip pro Windows s Javou.
- Po každé změně spusť `./mvnw -B clean verify`. Swing UI testy pokrývají málo, změny v UI popiš, jak je ručně ověřit.

## Struktura

Zdroje jsou v `src/main/java/cz/geokuk`:

| Balík | Obsah |
|---|---|
| `core/program` | hlavní okno, menu (`Menu`), akce (`Akce`), složení aplikace (`Inicializator`), konstanty (`FConst`, `FPref`), umístění programu a dat (`UmisteniProgramu`) |
| `core/coord`, `core/coordinates` | souřadnice (Mou, Wgs, UTM), zobrazení (`Coord`), pozice, výřez |
| `core/render` | export mapy (obrázek, KMZ, OziExplorer, tisk) |
| `core/napoveda` | nápověda, kontrola a stažení aktualizací, informace pro hlášení chyby, měření výkonu |
| `core/ovladani` | dálkové ovládání přes HTTP |
| `core/*` ostatní | hledání, online/offline, soubor nastavení (`profile`), vzhled |
| `plugins/kesoid` | kešoidy, waypointy, import a zdroje keší (`importek`), genetika (`genetika`), ikony (`mapicon`), filtr, druhy kešoidů (`kind/*`) |
| `plugins/mapy` | mapové podklady, kachle, cache dlaždic, offline mapy (mapsforge), uživatelské mapy, offliner, správce offline map (`sprava`), průvodce nastavením map (`pruvodce`) |
| `plugins/cesty`, `plugins/vylety` | cesty (včetně vedení úseků po cestách, `cesty/routovani`) a výlety |
| `plugins/challenge` | plánovač challenge (360°, matice D/T) |
| `plugins/*` ostatní | popisky, kruhy, obsazenost, mřížky, geocoding, domov a pojmenované body (`refbody`) |
| `framework` | vlastní infrastruktura: DI, události, modely, nastavení |
| `util/*` | obecný technický kód (turboware) |
| `api/mapicon` | kreslení ikon |
| `start` | spouštěč přenosné verze (`start.jar`), nesmí používat žádné jiné třídy programu |

**Názvy `core`, `plugins` a `api` popisují původní záměr, ne skutečnost.** Geokuk měl být malá obecná aplikace nad mapami a vše v `plugins`, včetně samotných kešoidů, mělo jít přidávat a vyměňovat. Refaktoring se nedotáhl: `core` dnes závisí na pluginech, `plugins/kesoid` je centrum s cykly k dalším modulům a skutečnými pluginy jsou jen druhy kešoidů v `plugins/kesoid/kind/*`. Co který balík doopravdy obsahuje: `doc/SLOVNIK.md`, kapitola 5.

## Kam dávat nový kód

- **Obecný kód** (nezávislý na geocachingu) do `util`, anglicky, bez importu z `core`, `plugins` a `framework`.
- **Nový druh kešoidu** jako plugin do `plugins/kesoid/kind/<druh>` (rozhraní `KesoidPlugin`), podle vzoru existujících druhů, a zapsat ho do seznamu v `KesoidPluginManager`.
- **Nová funkce** pokud možno jako samostatný modul v `plugins`, který závisí na jádru a na `plugins/kesoid`, ale ne naopak. Vzorem je `plugins/challenge`.
- **Nevytvářet nové závislosti** `core` → `plugins`, `framework` / `util` / `api` → doména, ani **nové cykly** mezi moduly. Kód tím nemá vzdalovat aplikaci od záměru vyměnitelných pluginů.
- **Do `plugins/kesoid/mvc` nic nepřidávat**, je to odkladiště. Novou třídu dej do balíku, kam věcně patří.
- Přesuny a přejmenování existujících balíků dělej jen jako samostatnou, předem domluvenou změnu. Rozbíjejí rozpracované větve ostatních.
- Větší změnu nebo novou funkci nejdřív navrhni v issue (`CONTRIBUTING.md`). Pro nový mapový podklad platí podmínky v `CONTRIBUTING.md`, oddíl Mapové podklady.

## Architektura v kostce

- **Skládání aplikace:** `core/program/Inicializator` registruje modely, akce a pluginy do `framework/BeanBag`. Závislosti se injektují reflexí přes veřejné metody `inject(Typ)`. Nový model nebo akci je nutné v `Inicializator` (resp. `Akce`) zaregistrovat.
- **Události:** `framework/EventManager`. Odběratel má veřejnou metodu `onEvent(KonkretniEvent)`, shoda je podle přesné třídy události. Nový odběratel hned dostane poslední vyslanou událost daného typu. Události se vysílají jen z vlákna Swingu (EDT).
- **Modely** dědí z `framework/Model0`, změnu oznamují událostí (`fire`). UI na ni reaguje, nevolá model opakovaně.
- **Dlouhé operace** běží ve `SwingWorker` (`MySwingWorker0`) nebo na vlastním vlákně, výsledek se do modelu předává na EDT.
- **Nastavení** se ukládá přes `framework/MyPreferences` do souboru `data/nastaveni.xml` (`core/profile/Nastaveni`, `framework/SouborovePreferences`), ne do registru Windows. Názvy klíčů jsou v `core/program/FPref`.
- **Data uživatele** jsou ve složce `data` vedle programu (`FConst.DATA_DIR`), složku jde přesunout i s programem. Cesty uvnitř složky programu se do nastavení ukládají se značkou `${GeoKuk}` (`MyPreferences.ZNACKA_KORENE`).
- **Druhy kešoidů** jsou pluginy (`plugins/kesoid/kind/*`, rozhraní `KesoidPlugin`), jejich seznam je v `KesoidPluginManager`.
- **Vlastnosti waypointů a ikony** řeší genetika: geny, alely, genotyp, fenotyp (viz slovník, kapitola 3.4 a 3.5).
- **Kontrola aktualizací** se ptá GitHub Releases (`FConst.LATEST_RELEASE_API_URL`), novou verzi instaluje spouštěč `start.jar`.

## Pravidla pojmenování

- **Doména česky** (bez diakritiky v identifikátorech): `Kesoid`, `Cesta`, `Usek`, `Kachle`, `obtiznost`.
- **Obecný technický kód anglicky** (turboware): kód, který by šel použít v jiné aplikaci, nesmí záviset na doméně.
- **Názvy z cizích formátů** (GPX, Groundspeak) jen ve vrstvě, která formát čte nebo zapisuje.
- **Jeden pojem, jeden název.** Pozor hlavně na **kachle** (obrázek mapy ze serveru) × **dlaždice** (díl renderovaného výstupu). Fork v textech UI a v novém kódu říká kachli „dlaždice“, viz slovník, kapitola 7, bod 1.
- Komentáře a texty UI česky s diakritikou, stejně tak popisy commitů (`CONTRIBUTING.md`). Texty UI hlídá test `TextyGuiTest`.
- Podrobnosti, konvence (`J`, `F`, `E`, přípona `0`, `xx`/`yy` pro Mou…) a místa, kde kód pravidla nedodržuje: `doc/SLOVNIK.md`, kapitoly 1 a 2.

## Na co si dát pozor

Tyto změny tiše rozbijí data nebo nastavení uživatelů. Dělej je jen záměrně a s převodem starých dat:

- **Klíče nastavení** v `FPref` a názvy vlastností tříd s `@Preferenceble`: přejmenování ztratí uložené nastavení. Totéž platí pro klíče definic challenge (`NastaveniChallenge`) a pro textové podoby ukládané do nastavení (`TemaOfflineMapy`, `VolbaVrstev`, `Zvyrazneni`).
- **Názvy konstant výčtů ukládaných do nastavení:** `MyPreferences.putEnum` ukládá `name()`, definice challenge také (`Challenge.Typ` a další).
- **Pořadí konstant `EVylet`:** do nastavení se ukládá `ordinal()` (práh výletu ve filtru).
- **Id podkladu** (`EKaType.name()`, u uživatelské mapy `user-` a název souboru `.mapa`): je klíčem v cache dlaždic (`data/cache/tiles.sqlite`), ukládá se jako vybraný podklad a podle něj se ukládá nastavení podle podkladu.
- **Klíč dlaždic offline mapy v cache** (`OblastniKlic`, `UklidOfflineCache`): změna zneplatní vykreslené dlaždice všem uživatelům.
- **Formát souborů ve složce `data`:** `nastaveni.xml`, výlety (`vylety/lovim.ggt`, `tedne.ggt`, `vylety.txt`, `ulozene/<id>/`), dokument cest (GPX v `cesty`), uživatelské mapy (`mapy/*.mapa`, názvy vlastností), struktura složky `offline-mapy` se staženými mapami a vrstvami.
- **Formáty cizích programů:** `geohome.ini` a seznamy GGT GeoGetu, schémata databází GeoGetu, GSAKu a OpenSAKu. Jsou to data uživatele a jiných programů.
- **Názvy souborů ikon** v sadách ikon: kódují alely, viz Icondef ve slovníku.
- **Dálkové ovládání** (`core/ovladani`, příkazy popsané v `README.md`) a parametr `--ovladani`: používají je jiné programy.
- **Názvy souborů vydání** (`geokuk.jar`, `start.jar`, `java.properties`, `GeoKuk-windows.zip`) a atributy manifestu (`Geokuk-Version`): podle nich se starší verze aktualizují.

Dál platí:
- Na zastaralý a nedokončený kód (slovník, kapitola 9) nenavazuj. Například **grupa** je zastaralá, místo ní použij druh.
- Neprovádět hromadné přeformátování ani přejmenování mimo rozsah úkolu. Rozbije historii a ztíží review.
- Když opravuješ překlep v názvu, kolizi pojmů nebo zastaralý kód, uprav i slovník.
- Cizí ani testovací klíče k mapovým službám do repozitáře nepatří (`CONTRIBUTING.md`). Zranitelnosti se nehlásí veřejně (`SECURITY.md`).
