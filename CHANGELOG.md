# Změny

## 6.2.0

### Změny
- Přenosný GeoKuk: nic se neinstaluje, program si všechno ukládá do
  složky `data` vedle sebe: nastavení (`nastaveni.xml`), uživatelské mapy
  (`uzivatelske-mapy.properties`), cache map (`cache`), cesty, ikony
  (`ikony/moje`, `ikony/ostatni`), výlety (`vylety`), log a chybová
  hlášení (`log`). Rozhoduje jen to, odkud se spustí, složku jde
  přesunout i s daty. Nastavení už není v registru Windows, při prvním
  spuštění se odtud převezme. Volba „Ukládat nastavení k programu“
  odpadla.
- Pro Windows je ke stažení `GeoKuk-windows.zip` s přibalenou Javou:
  poprvé se spustí `GeoKuk.cmd`, Soubor > Vytvořit zástupce přidá
  GeoKuk do nabídky Start nebo na plochu. Spouštěč `start.jar`
  nainstaluje staženou novou verzi a zvolí paměť (polovina paměti
  počítače, 1 až 3 GB), změnit ji jde v Soubor > Paměť programu.
  Spouštěč `geokuk.cmd` odpadl.
- V Umístění souborů zůstaly k nastavení jen složky s daty jiných
  programů (keše z GPX, GeoGet, GSAK, výstupy rendru); ostatní ukazuje
  záložka Program.
- GeoKuk upozorní, když do své složky nemůže zapisovat (třeba v Program
  Files), nebo když je ve složce synchronizované přes OneDrive, Dropbox
  nebo Google Disk.
- Když nová verze potřebuje novější Javu, než je přibalená, nebo je
  k dispozici zip s novější Javou, GeoKuk nabídne stažení nového zipu.
- Nápověda > Nabízet testovací verze (beta) zapíná beta kanál; soubor
  `beta` vedle `geokuk.jar` se převezme.
- Dálkové ovládání: stav, posun mapy, výběr keše, podklad a přenačtení
  jdou bez tokenu na portu 48321 (výchozí); požadavky z webového
  prohlížeče program odmítá. Soubor `.geokuk\ovladani.properties`
  v domovské složce odpadl.

## 6.1.0

### Změny
- Mapy > Zobrazit všechny keše nastaví mapu tak, aby byly vidět všechny
  zobrazené keše. Po prvním načtení dat se to stane samo, když ve výřezu
  žádná keš není.
- Instalace s beta kanálem (soubor `beta` vedle `geokuk.jar`) má verzi
  trvale vpravo v menu i u ostré verze. Po odebrání souboru `beta`
  nabídne kontrola aktualizací poslední vydanou verzi, i když je starší.
- Uživatelské mapy: vlastní mapové podklady ze souboru
  `uzivatelske-mapy.properties` vedle `geokuk.jar`, v menu Mapy ve
  skupině „Uživatelské mapy“.
- Přidání atribucí pro mapy: Mapy.cz, OpenStreetMap a Freemap Slovakia,
  zobrazují se vpravo dole v mapě.
- „Zadat problém“ a nové tlačítko „Nahlásit na GitHubu“ v „Informace
  pro hlášení chyby“ otevřou nové issue s předvyplněnými informacemi
  o programu.
- Program zapisuje log do `%TEMP%\geokuk\geokuk.log`. Cesta k němu
  a jeho posledních 20 řádků jsou v „Informace pro hlášení chyby“.
- „Informace pro hlášení chyby“ obsahují i servisní hodnoty ze servisního
  okna.
- „Informace pro hlášení chyby“ zaznamenávají posledních 100 událostí:
  spuštěné položky menu s cestou v menu, klávesovou zkratkou a stavem
  přepínače, otevřená a zavřená okna s textem hlášek, souhrnně práci
  s mapou (podklad, měřítko, počet posunů, bez polohy) a načítání keší.
  Události se zapisují i do logu, z něhož hlášení ukazuje posledních
  50 řádků.
- Program si bere až 3 GB paměti, když ji Java přijme; jinak 2 GB nebo 1 GB.
- Dálkové ovládání: Soubor > Dálkové ovládání (nebo parametr
  `--ovladani[=port]`) povolí jiným programům na tomto počítači ovládat
  Geokuk přes HTTP – přesunout mapu, přepnout podklad, vybrat keš, znovu
  načíst keše a zjistit stav. Port a přístupový token jsou v souboru
  `.geokuk\ovladani.properties` v domovské složce uživatele.

### Opravy
- Databáze GeoGetu nebo GSAKu, do které program dlouho zapisuje, se
  načte po skončení zápisu; do té doby zůstanou zobrazené dříve načtené
  keše. Poškozenou databázi nebo soubor, který není databáze, program
  ohlásí česky, místo aby ho tiše přeskočil.
- Keše se načtou i z databáze starší verze GeoGetu nebo GSAKu, které
  chybí některé sloupce nebo tabulky, i když jsou poškozené jen tagy
  nebo popisy, a i keš GeoGetu bez autora. Vadný waypoint se přeskočí,
  ostatní se načtou.
- Otevření filtru (F2) nad daty, ve kterých nejsou nalezené, archivované
  nebo jiné skryté keše, z filtru nevyhodí jejich skrytí.
- Server, který posílá dlaždici extrémně pomalu, nezablokuje stahování
  mapy; stažení jedné dlaždice má celkový limit 60 s.
- Hromadné stahování dlaždic ukazuje průběh, počet chyb a konec, jde
  zastavit a samo přestane, když server mapy stahování omezí
  (HTTP 429 nebo 503).
- Dlaždice, kterou se nepodařilo stáhnout (výpadek sítě), se po chvíli
  zkusí stáhnout znovu a místo technického výpisu ukazuje srozumitelný
  důvod.
- Chybějící nebo neúplná vybraná sada ikon (smazaná složka, chybí
  `skla.txt`, vypnutá složka vlastních ikon) se nahradí sadou Standard;
  dřív se kvůli ní nenačetly žádné keše.
- Uživatelská mapa s `{s}` nebo jinou neznámou proměnnou v adrese se
  při startu ohlásí jako chybná. Dlaždice se stáhnou i ze serveru,
  který adresu `http://` přesměruje na `https://`.
- Složka cest a složky ikon uvnitř datové složky se neprocházejí jako
  zdroj keší; uložení cesty už nepřenačte všechna data a body cest se
  neukazují jako keše.
- Cesty: plán trasy (`<rte>`) z GPX se načte jako cesta, soubor bez
  trasy se ohlásí. Vadný soubor při importu více souborů nezahodí cesty
  z ostatních. Nepovedené uložení nezmění soubor, ke kterému cesta patří.
- Když nejde uložit výlet (`lovim.ggt`, `tedne.ggt`), program to ohlásí
  a zkusí ho uložit znovu při další změně.
- Databáze GSAKu s nestandardním řazením nezastaví načítání ostatních
  dat dialogem, ohlásí se v přehledu problémů. Okno nečeká, než doběhne
  procházení velké datové složky.
- Dialogy se vejdou nad hlavní panel Windows. Okno uložené na větším
  nebo odpojeném monitoru se při startu posune a zmenší, aby bylo celé
  vidět.
- Zoom na obdélník (Shift + tažení), na keš, cestu, cesty a výlet
  vybere správné měřítko; dřív mapu oddálil o čtyři měřítka.
- Aktivní datová složka GeoGetu nebo GSAKu bez databází se ohlásí.
  Uložení v Umístění souborů nezakládá složky u neaktivních položek.
  Nápověda z okna O programu otevře hlavní stránku nápovědy.
- „Stáhnout nejnovější verzi“ mimo Windows otevře stránku nabízené
  verze, u bety tedy betu, ne poslední ostrou verzi.
- GPX: keše ve jmenném prostoru Groundspeak `cache/1/0/2` (GPX 1.1) se
  načtou jako keše. Keš s nesmyslnými souřadnicemi (NaN) nebo výškou
  s čárkou nezahodí zbytek souboru. Keš bez nápovědy nepřebírá typ
  a název z logů a travel bugů.
- User-Agent stahování map neobsahuje adresu webu.
- Alt+I otevře menu Skin i s vybranou keší a neoznačí ji jako ignorovanou.
- GPX soubor nemůže při načtení číst jiné soubory ani volat adresy
  na internetu (externí entity a DTD se nenačítají).
- Cesty: GPX, který nevytvořil Geokuk (tracklog z GPS, Locusu), Uložit
  nepřepíše a nabídne Uložit jako, aby se neztratily výšky, časy a body.
- Výlet (`lovim.ggt`, `tedne.ggt`): kódy keší, které zrovna nejsou
  v načtených datech, zůstanou v souboru; změny se zapisují v pořadí
  a při Konci se dopíšou.
- Useknutá dlaždice ve formátu JPEG se nepřijme ani nevezme z cache
  a stáhne se znovu.
- Při plném disku nebo nezapisovatelné složce TEMP se ikony a mapa
  načtou. Když nejde uložit nastavení, Konec nabídne ukončení bez uložení.
- Databáze GeoGetu a GSAKu se otevírají jen pro čtení, Geokuk v jejich
  složce nic nezaloží ani nezmění.
- O programu uvádí licenci GNU GPL v3.
- Při oddálení, kdy se svět v okně opakuje, se nevykreslí všechny keše
  najednou; výřez se v takovém měřítku bere jako celý svět.
- Při měřítku v tisících kilometrů nepřetékají souřadnice, takže tažení
  mapy, přiblížení na keš, cestu nebo výlet a zoom do vybraného obdélníku
  míří tam, kam mají.
- Opakovaná chyba při čtení dlaždic z diskové cache se do logu hlásí
  souhrnně, ne u každé dlaždice zvlášť.
- Disková cache dlaždic funguje i po rychlém posouvání mapy; dlaždice
  se zbytečně nestahují znovu a zdravá cache se neodkládá jako poškozená.
- Dlaždici, kterou server pošle celou, program nezahodí jako useknutou,
  takže se uloží do cache a nestahuje se dokola znovu.
- Okno „Soubor > Servis“ ukazuje hodnoty hned po otevření, i když se
  s mapou zrovna nepracuje. Po zavření okna se hodnoty přestanou měřit.
- „Listing do Geogetu“ (F3) funguje i u keší, které mají jen běžný odkaz
  na listing; do schránky se vloží ten.
- Klávesa F3 patří akci „Listing do Geogetu“; „Otevřít cesty (gpx)“ ji
  už nemá, dřív se obě hlásily o tutéž klávesu.
- Měřítko „1 : 0“ pro tisk a uložení mapy se neuplatní ani neuloží.
- Složka s hlášeními o chybách (`%TEMP%\geokuk\excrep`) si drží jen
  posledních dvacet spuštění, starší se při startu smažou.
- Vadný záznam v databázi GeoGetu nebo GSAKu se přeskočí a zbytek keší
  se načte; dřív kvůli němu zůstala databáze celá nenačtená.
- Chybný soubor v datové složce už nezpůsobí opakované načítání dat
  a chybová hlášení dokola; nečitelná nebo zacyklená složka se přeskočí.
- Stahování mapových dlaždic má časový limit, takže neodpovídající server
  nezastaví mapu, a neúplně stažená dlaždice se nepoužije.
- Program nastartuje i s poškozeným nastavením vedle programu nebo
  s nesmyslnou uloženou hodnotou; poškozený soubor odloží stranou
  a upozorní na to.
- Poškozená cache mapových dlaždic se založí znovu, místo aby se tiše
  přestala používat.
- Ukládání cest, nastavení a výletů zapíše soubor celý, nebo nechá původní
  beze změny; chybu zápisu (plný disk, disk jen pro čtení) program ohlásí
  a cesty zůstanou rozpracované, ne ztracené.
- Zavřené hledání keší už nedrží v paměti keše z předchozího načtení.
- Mapové dlaždice v paměti mají strop, při dlouhé práci s mapou paměť
  neroste.
- Keše bez hodnocení, BestOf nebo favoritů filtr podle těchto prahů
  neskryje.
- Cesta s názvem obsahujícím `&`, `<` nebo `%` se uloží a jde znovu
  otevřít.
- Dlouhé nastavení (seznamy souborů a podobně) po zkrácení nebo smazání
  nenechává zbytky, které se dřív přilepily k nové hodnotě.
- Převod na UTM určuje správně pásmo, souřadnice na jižní polokouli
  se převádějí zpět správně.
- Načítání keší potřebuje zhruba polovinu paměti, 40 tisíc keší se
  vejde do 128 MB. Hodnocení, známka, BestOf, favority, nadmořská výška
  a vlastní hodnoty z databáze GeoGetu a GSAK se přenesou ke keším.
- Stahování mapových dlaždic se všem serverům představuje jako Geokuk
  s verzí.
- OpenStreetMap se načítá přes https a nejde ji hromadně stahovat
  do cache.
- Keš bez názvu nebo s nesmyslnou obtížností či terénem nezpůsobí,
  že se nenačte celý soubor.
- Databáze GeoGetu nebo GSAKu, do které ten program právě zapisuje
  (třeba import), se načte po dokončení zápisu, místo aby se ohlásila
  chyba a keše z ní zmizely.
- Stažené mapové dlaždice se uloží do cache i při rychlém posouvání
  a zoomování a dlaždice z posledních sekund se uloží i při ukončení
  programu.
- Poškozenou cache mapových dlaždic odloží program jen jednou a odložený
  soubor je ten poškozený, ne nově založená cache.
- Když cache mapových dlaždic nejde použít (třeba odpojený disk), program
  na to jednou upozorní.
- Bez připojení k internetu se chyby stahování dlaždic hlásí do logu
  souhrnně a nezakládají výpis chyby u každé dlaždice.
- Menu Cesty má podtržené C a Skin I, dřív se o písmeno přetahovaly
  s Výlety a Nápovědou.
- Písmeno napsané v otevřeném menu nebo do pole na liště (Hodnocení,
  BestOf, Favorit) nepřepne mapový podklad.
- Uživatelská mapa nemůže převzít klávesovou zkratku programu (třeba F3
  nebo Ctrl+S), program ji s hláškou vynechá.
- Kešoidy > Implicitní výběr (Alt+F2) vrátí filtr i zobrazené typy keší
  na výchozí hodnoty.
- Kešoidy > Jednotkové kruhy (Alt+F8) jsou v menu.
- Položky v menu Výlety, Cesty a Kešoidy mají různá podtržená písmena,
  která jsou v jejich textu.
- Po výběru v seznamu Výlet na liště ovládají šipky a PageUp/PageDown
  dál mapu.
- Když nejde otevřít prohlížeč (nápověda, web), program ukáže adresu
  a nabídne ji zkopírovat.

### Odstraněno
- Vrstva „Open cyclo“ (Thunderforest). Lze ji přidat jako uživatelskou
  mapu s vlastním klíčem, příklad je v `priklady/uzivatelske-mapy.properties`.
- Položka nápovědy „Zprávy uživatelům“, která neměla odkud zprávy brát.
- Vrstva „Letecká Google“, náhled vlevo dole ukazuje leteckou mapu
  Mapy.cz.

### Vývoj
- Aktualizace knihoven se známými zranitelnostmi: guava 33.4.8,
  sqlite-jdbc 3.41.2.2, metadata-extractor 2.18.0, v testech logback
  1.2.13 a junit 4.13.1.
- Popis vydání na GitHubu se bere z tohoto souboru.
- Program se vydává jen jako jar, konfigurace Launch4j je odstraněná.
- Ručně spouštěný smoke test celého programu nad falešným mapovým
  serverem (workflow Smoke) a test kolizí klávesových zkratek.

## 6.0.0

Navazuje na verzi 5.1.1c Jiřího Bílka.

### Opravy
- Oddálení mapy na nejmenší měřítko už nevyhazuje chybové okno.
- Mapa při přibližování ke kurzoru neskáče jinam, ani na širokých
  monitorech.
- Mapu nejde oddálit víc, než kolik program spolehlivě zvládne.
- Mřížky se nezacyklí při přetočení mapy přes okraj světa.
- Program spuštěný z jaru pozná svou složku, takže funguje nastavení
  uložené u programu a cesty relativní k programu.

### Změny
- Nápověda → „Informace pro hlášení chyby“ ukáže verzi, prostředí,
  poslední události a chyby a umí je zkopírovat do schránky. Testovací
  a vývojová verze má číslo trvale vpravo v menu, kliknutím se otevře
  totéž.
- Ve Windows Geokuk vedle sebe vytvoří spouštěč `geokuk.cmd`, pokud
  chybí. Novou verzi Geokuk stáhne a spouštěč ji při dalším spuštění
  nainstaluje. Předchozí verze zůstane jako
  `geokuk.jar.bak`.
- Kontrola nové verze a stažení míří na GitHub Releases tohoto repa,
  „Zadat problém“ a webová stránka programu na jeho GitHub.
- V dialogu o nové verzi tlačítko „Připomenout za měsíc“ odloží další
  automatickou kontrolu o 30 dní.
- Ve zprávách uživatelům přibylo tlačítko „Označit vše jako přečtené“.
- Nová instalace má vypnuté datové složky GeoGet a GSAK, popisky keší
  a mřížky Dd a DdMmMmm. Uložené nastavení se nemění.

### Vývoj
- Build na JDK 21 přes Maven Wrapper, CI na GitHub Actions.
- Release obsahuje `geokuk.jar` a `geokuk.jar.sha256`. Verze v jaru
  se bere z tagu; tag s příponou (`v6.0.1-beta.1`) vydá testovací verzi
  jako pre-release. Geokuk ji nabídne, jen když vedle `geokuk.jar` leží
  soubor `beta`. Sestavení mimo tag mají verzi s příponou `-dev.<číslo
  běhu CI>` a v jaru je uložen commit.
- Odstraněn build přes Ant a NetBeans a zdroj webu geokuk.cz.
