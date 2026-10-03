# Změny

## 6.2.0

### Změny
- Přenosný GeoKuk: nic se neinstaluje, program si všechno ukládá do
  složky `data` vedle sebe: nastavení (`nastaveni.xml`), uživatelské mapy
  (`uzivatelske-mapy.properties`), cache map (`cache`), cesty, ikony
  (`ikony/moje`, `ikony/ostatni`), výlety (`vylety`), log a chybová
  hlášení (`log`) i dočasné soubory Javy (`tmp`). Rozhoduje jen to,
  odkud se spustí, složku jde přesunout i s daty. Nastavení už není
  v registru Windows, při prvním spuštění se odtud převezme. Volba
  „Ukládat nastavení k programu“ odpadla.
- Pro Windows je ke stažení `GeoKuk-windows.zip` s přibalenou Javou,
  program a Java jsou v podsložce `program`. Poprvé se spustí
  `GeoKuk-prvni-spusteni.cmd`, GeoKuk pak vedle vytvoří zástupce
  `GeoKuk.lnk`, který jde zkopírovat na plochu nebo připnout. Po
  přesunu složky se zástupce ve složce i jeho kopie na ploše
  a v nabídce Start opraví. Soubor > Vytvořit zástupce přidá
  GeoKuk do nabídky Start nebo na plochu. Spouštěč `geokuk.cmd` odpadl.
- Spouštěč `start.jar` nainstaluje staženou novou verzi a zvolí paměť
  (polovina paměti počítače, 1 až 3 GB), změnit ji jde v Soubor >
  Paměť programu. Po stažení nové verze GeoKuk nabídne restart: uloží
  se jako při Soubor > Konec a spustí se znovu už v nové verzi.
- GeoKuk upozorní, když do své složky nemůže zapisovat (třeba v Program
  Files), nebo když je ve složce synchronizované přes OneDrive, Dropbox
  nebo Google Disk. Ve složce bez práva zápisu se keše z GeoGetu
  a GSAKu načtou, mapa se zobrazí (dlaždice zůstanou jen v paměti),
  log a chybová hlášení jsou v `%TEMP%\GeoKuk\log` a dočasné soubory
  v dočasné složce systému.
- Když nová verze potřebuje novější Javu, než je přibalená, nebo je
  k dispozici zip s novější Javou, GeoKuk nabídne stažení nového zipu.
- Nápověda > Nabízet testovací verze (beta) zapíná beta kanál. Dokud
  je zapnutý, je verze vidět vpravo v menu. Po vypnutí nabídne ruční kontrola
  aktualizací přechod na poslední stabilní verzi.
- „Připomenout za týden“ (i zavření dialogu nové verze) odloží nabídku
  té verze o 7 dní. Novější verze se nabídne hned.
- „Stáhnout novou verzi“ na Linuxu a macOS novou verzi rovnou
  nainstaluje; kde to nejde, otevře stránku nabízené verze, u bety
  tedy betu.
- Na oddálené mapě (zoom 12 a menší) jsou keše barevné tečky podle typu:
  tradiční zelené, multi žluté, mystery, letterbox a wherigo modré,
  virtuální, webcam a earthcache bílé, eventy červené, ostatní objekty
  šedé. Nalezené jsou menší, vlastní mají tmavý obrys, neaktivní
  a archivované jsou světlejší. Velikost teček se řídí hustotou keší.
  Tečky se vykreslí i u statisíců keší, limit 30 000 keší na mapě pro ně
  neplatí. Kešoidy > Tečky při oddálení / Vždy ikony / Vždy tečky.
- Zvýrazňovací kruhy a popisky se nekreslí, když by se na mapě slily
  do jedné plochy; kruhy obsazenosti, když mají poloměr pod 3 pixely.
  Popisek keše pod myší se při posunu mapy schová a na zoomu 7 a menším
  se nezobrazuje.
- Mapy > Zobrazit všechny keše nastaví mapu tak, aby byly vidět všechny
  zobrazené keše. Po prvním načtení dat se to stane samo, když ve výřezu
  žádná keš není.
- Uživatelské mapy: vlastní mapové podklady ze souboru
  `data/uzivatelske-mapy.properties`, v menu Mapy ve skupině
  „Uživatelské mapy“.
- Mapy mají atribuci (Mapy.cz, OpenStreetMap, Freemap Slovakia) vpravo
  dole v mapě.
- Hledání adresy (Jít > Adresa) a návrh názvu souboru u rendru podle
  místa používají Nominatim nad daty OpenStreetMap. Hledá se klávesou
  Enter nebo tlačítkem Hledat, souřadnice v poli najdou adresu místa.
  Položka „Geocoding“ v kontextovém menu mapy je nahrazena položkou
  „Na OpenStreetMap...“, která místo otevře v prohlížeči.
- Hlášení chyb: „Zadat problém“ i „Informace pro hlášení chyby“ nejdřív
  ukážou informace o programu, tlačítko „Nahlásit na GitHubu“ pak otevře
  nové issue, kde jsou předvyplněné. „Informace pro hlášení chyby“ obsahují cestu
  k logu a jeho posledních 50 řádků, servisní hodnoty ze servisního okna
  a posledních 100 událostí: spuštěné položky menu s cestou v menu,
  klávesovou zkratkou a stavem přepínače, otevřená a zavřená okna
  s textem hlášek, souhrnně práci s mapou (podklad, měřítko, počet
  posunů, bez polohy) a načítání keší.
- Dálkové ovládání: Soubor > Dálkové ovládání (nebo parametr
  `--ovladani[=port]`, výchozí port 48321) povolí jiným programům na
  tomto počítači ovládat GeoKuk přes HTTP – přesunout mapu, přepnout
  podklad, vybrat keš, znovu načíst keše a zjistit stav. Požadavky
  z webového prohlížeče program odmítá.
- V Umístění souborů zůstaly k nastavení jen složky s daty jiných
  programů (keše z GPX, GeoGet, GSAK, výstupy rendru); ostatní ukazuje
  záložka Program.

### Opravy
- Nová verze se stahuje jen jednou najednou a po stažení se ověří
  soubor na disku, takže se nenainstaluje neúplný program. Když nejde
  zjistit, jakou Javu nová verze potřebuje, neinstaluje se.
- Když spouštěč nemůže nainstalovat staženou verzi (soubor drží jiný
  program), spustí dosavadní verzi a novou zkusí nainstalovat příště.
- Tlačítka dialogů (Ano, Ne, Zrušit) a dialog pro výběr souboru jsou
  česky. Položka menu Kešoidy > Filtr... má správný název.
- Přehled problémů ukazuje u každého problému, co se dělo a proč to
  selhalo; sloupce se jmenují Č. a Hlášení. Stavový řádek bez načtených
  zdrojů neukazuje čas a bez vybrané pozice ukazuje pomlčku.
- Dialog nové verze má titulek „Nová verze programu“ a čitelný text
  a při souběhu automatické a ruční kontroly aktualizací se ukáže jen
  jednou.
- Načtení keší z databáze GeoGetu nebo GSAKu nečte popisy keší, takže
  nemusí projít celou databázi včetně dlouhých listingů. Hint se načte
  až po kliknutí na Hint v detailu keše. Načítání keší potřebuje zhruba
  polovinu paměti, 40 tisíc keší se vejde do 128 MB. Hodnocení, známka,
  BestOf, favority, nadmořská výška a vlastní hodnoty z databáze GeoGetu
  a GSAK se přenesou ke keším.
- Databáze GeoGetu nebo GSAKu, do které ten program právě zapisuje
  (třeba import), se načte po dokončení zápisu; do té doby zůstanou
  zobrazené dříve načtené keše. Poškozenou databázi nebo soubor, který
  není databáze, program ohlásí česky.
- Databáze GeoGetu a GSAKu se otevírají jen pro čtení, GeoKuk v jejich
  složce nic nezaloží ani nezmění.
- Keše se načtou i z databáze starší verze GeoGetu nebo GSAKu, které
  chybí některé sloupce nebo tabulky, i když jsou poškozené jen tagy
  nebo popisy, a i keš GeoGetu bez autora. Vadný waypoint nebo záznam
  se přeskočí, ostatní keše se načtou.
- Databáze GSAKu s nestandardním řazením nezastaví načítání ostatních
  dat dialogem, ohlásí se v přehledu problémů. Okno nečeká, než doběhne
  procházení velké datové složky.
- GSAK: prázdné vlastní hodnoty se nezobrazují jako tagy.
- GSAK: kód keše z vlastních hodnot se nebere jako tag. Databáze GSAKu
  s vlastními hodnotami tak zabírá výrazně méně paměti.
- Aktivní datová složka GeoGetu nebo GSAKu bez databází se ohlásí.
  Uložení v Umístění souborů nezakládá složky u neaktivních položek.
- Dálkové ovládání jde spustit i ze složky, do které nejde zapisovat
  (třeba Program Files). Když spustit nejde, například kvůli obsazenému
  portu, hlášení řekne proč.
- GPX: keše ve jmenném prostoru Groundspeak `cache/1/0/2` (GPX 1.1) se
  načtou jako keše. Keš s nesmyslnými souřadnicemi (NaN), výškou
  s čárkou, bez názvu nebo s nesmyslnou obtížností či terénem nezahodí
  zbytek souboru. Keš bez nápovědy nepřebírá typ a název z logů
  a travel bugů.
- GPX soubor nemůže při načtení číst jiné soubory ani volat adresy
  na internetu (externí entity a DTD se nenačítají).
- Načtené soubory GPX, `.geokuk` a fotky nezůstávají ve Windows zamčené.
- Chybný soubor v datové složce už nezpůsobí opakované načítání dat
  a chybová hlášení dokola; nečitelná nebo zacyklená složka se přeskočí.
  Složka cest a složky ikon uvnitř datové složky se neprocházejí jako
  zdroj keší.
- Chybějící nebo neúplná vybraná sada ikon (smazaná složka, chybí
  `skla.txt`, vypnutá složka vlastních ikon) se nahradí sadou Standard;
  dřív se kvůli ní nenačetly žádné keše.
- Pruh „Indexování“ při načítání keší ukazuje průběh.
- Zavřené hledání keší už nedrží v paměti keše z předchozího načtení.
- Hledání keší s regulárním výrazem: `\D`, `\W`, `\S` a `\Q…\E` fungují
  podle očekávání.
- Otevření filtru (F2) nad daty, ve kterých nejsou nalezené, archivované
  nebo jiné skryté keše, z filtru nevyhodí jejich skrytí. Keše bez
  hodnocení, BestOf nebo favoritů filtr podle těchto prahů neskryje.
- Kešoidy > Implicitní výběr (Alt+F2) vrátí filtr i zobrazené typy keší
  na výchozí hodnoty. Kešoidy > Jednotkové kruhy (Alt+F8) jsou v menu.
- Stahování mapových dlaždic má časový limit (celkem 60 s na dlaždici),
  takže pomalý nebo neodpovídající server nezastaví mapu. Neúplně
  stažená nebo useknutá dlaždice se nepoužije, nevezme z cache a stáhne
  se znovu; dlaždici, kterou server pošle celou, program nezahodí.
- Dlaždice, kterou se nepodařilo stáhnout (výpadek sítě), se po 30
  sekundách zkusí stáhnout znovu, ne při každém překreslení mapy,
  a místo technického výpisu ukazuje srozumitelný důvod. Bez připojení
  k internetu se chyby stahování hlásí do logu souhrnně.
- Hromadné stahování dlaždic ukazuje průběh, počet chyb a konec, jde
  zastavit a samo přestane, když server mapy stahování omezí
  (HTTP 429 nebo 503).
- Stažené dlaždice se uloží do diskové cache i při rychlém posouvání
  a zoomování a během souvislého čtení z cache, dlaždice z posledních
  sekund i při ukončení programu.
  Zdravá cache se neodkládá jako poškozená a opakovaná chyba čtení
  z cache se do logu hlásí souhrnně.
- Při rychlém posouvání mapy se nezobrazí cizí dlaždice a souběžný
  zápis cache map nepoškodí.
- Ve filtru se stav aktivních keší jmenuje „Aktivní“.
- Oddíl Výstup v dialogu Rendrování se vejde i s dlouhou výstupní složkou:
  složka se zkrátí uprostřed, celá je v bublině nápovědy.
- Poškozená cache mapových dlaždic se odloží stranou (jen jednou a ta
  poškozená) a založí znovu. Když cache nejde použít (třeba odpojený
  disk), program na to jednou upozorní.
- Mapové dlaždice v paměti mají strop, při dlouhé práci s mapou paměť
  neroste.
- Uživatelská mapa s `{s}` nebo jinou neznámou proměnnou v adrese se
  při startu ohlásí jako chybná. Dlaždice se stáhnou i ze serveru,
  který adresu `http://` přesměruje na `https://`. Uživatelská mapa
  nemůže převzít klávesovou zkratku programu (třeba F3 nebo Ctrl+S),
  program ji s hláškou vynechá.
- Stahování mapových dlaždic se všem serverům představuje jako GeoKuk
  s verzí, bez adresy webu. OpenStreetMap se načítá přes https a nejde
  ji hromadně stahovat do cache.
- Zvýrazňovací kruhy, popisky a obsazenost se po dorazení dlaždice
  kreslí jen v jejím místě, ne v celém okně. Kreslení obsazenosti
  nezdržuje zjišťování typu waypointu.
- Chyba při výpočtu ikon keší nezastaví jejich kreslení a do přehledu
  problémů se zapíše jen jednou.
- Toolbar s přepínači ikon má stálou výšku, mapa se po načtení keší
  (třeba Munzee nebo databáze) neposouvá.
- Zoom na obdélník (Shift + tažení), na keš, cestu, cesty a výlet
  vybere správné měřítko; dřív mapu oddálil o čtyři měřítka.
- Při oddálení, kdy se svět v okně opakuje, se nevykreslí všechny keše
  najednou; výřez se v takovém měřítku bere jako celý svět. Při měřítku
  v tisících kilometrů nepřetékají souřadnice, takže tažení mapy,
  přiblížení a zoom do obdélníku míří tam, kam mají.
- Čáry UTM mřížky jsou na správném místě i u okraje zóny, převod z UTM
  na zeměpisné souřadnice byl až o 7 metrů posunutý k jihu. Převod na
  UTM určuje správně pásmo, souřadnice na jižní polokouli se převádějí
  zpět správně.
- Souřadnice ve stupních a minutách (i vteřinách v mřížce a rendru) se
  zaokrouhlují správně, místo například 49°60.000 se ukáže 50°00.000.
- Souřadnice zadané mimo rozsah (šířka nad 90°, délka nad 180°, minuty
  nebo vteřiny od 60) program odmítne, místo aby přeskočil jinam.
- Dialog rendru ukazuje rozměr terénu ve skutečných kilometrech, ne
  desetinásobek. Měřítko „1 : 0“ pro tisk a uložení mapy se neuplatní
  ani neuloží.
- Kalibrační body v souboru `.map` pro OziExplorer mají polokouli S a W,
  rendr západně od Greenwiche nebo na jižní polokouli sedí na mapě.
  Rendr pro OziExplorer ohlásí, když se soubor `.map` nepodaří zapsat
  (třeba na plný disk), a nenechá po sobě neúplné soubory.
- Rendr mapy nepřichází o dlaždice, které si najednou vyžádalo víc míst.
- Dlouhý záznam trasy (statisíce bodů) se kreslí rychleji: úseky mimo
  překreslovanou oblast a kratší než pixel se vynechají a body vybrané
  trasy těsně u sebe mají jednu značku.
- Cesty: plán trasy (`<rte>`) z GPX se načte jako cesta, soubor bez
  trasy se ohlásí. Vadný soubor při importu více souborů nezahodí cesty
  z ostatních. GPX, který nevytvořil GeoKuk (tracklog z GPS, Locusu),
  Uložit nepřepíše a nabídne Uložit jako, aby se neztratily výšky, časy
  a body. Cesta s názvem obsahujícím `&`, `<` nebo `%` se uloží a jde
  znovu otevřít. Nepovedené uložení nezmění soubor, ke kterému cesta
  patří. Uložení cesty nepřenačte všechna data a body cest se
  neukazují jako keše.
- Ukládání cest, nastavení a výletů zapíše soubor celý, nebo nechá
  původní beze změny; chybu zápisu (plný disk, disk jen pro čtení)
  program ohlásí a cesty zůstanou rozpracované, ne ztracené. Nastavení
  se ukládá i po neočekávané chybě při zápisu.
- Výlet (`lovim.ggt`, `tedne.ggt`): kódy keší, které zrovna nejsou
  v načtených datech, zůstanou v souboru; změny se zapisují v pořadí
  a při Konci se dopíšou. Když výlet nejde uložit, program to ohlásí
  a zkusí ho uložit znovu při další změně.
- Chyba při kopírování odkazů výletu nebo cesty do schránky se ohlásí.
- Program nastartuje i s poškozeným nastavením nebo s nesmyslnou
  uloženou hodnotou; poškozený soubor odloží stranou a upozorní na to.
  Dlouhé nastavení (seznamy souborů a podobně) po zkrácení nebo smazání
  nenechává zbytky, které se dřív přilepily k nové hodnotě.
- Při plném disku nebo nezapisovatelné složce TEMP se ikony a mapa
  načtou. Když nejde uložit nastavení, Konec nabídne ukončení bez
  uložení.
- Chybová hlášení (`data/log/chyby`) si drží jen posledních dvacet spuštění,
  starší se při startu smažou. Chyba při práci na pozadí je v přehledu
  problémů česky a s příčinou.
- Dialogy se vejdou nad hlavní panel Windows. Okno uložené na větším
  nebo odpojeném monitoru se při startu posune a zmenší, aby bylo celé
  vidět.
- Servisní okno (Soubor > Servis) má titulek, tlačítka Zavřít
  a Nápověda, zavírá se klávesou Esc, otevírá se uprostřed hlavního okna
  a ukazuje hodnoty hned po otevření; po zavření se hodnoty přestanou
  měřit.
- Dialog Nastavení popisků se vejde na obrazovku: barva písma a podkladu
  jsou na kartách a pole pro vzorky popisků jsou dost široká.
- „Listing do Geogetu“ (F3) funguje i u keší, které mají jen běžný odkaz
  na listing; do schránky se vloží ten. Klávesa F3 patří jen této akci.
- Menu Cesty má podtržené C a Skin I; položky v menu Výlety, Cesty
  a Kešoidy mají různá podtržená písmena, která jsou v jejich textu.
  Alt+I otevře menu Skin i s vybranou keší a neoznačí ji jako
  ignorovanou.
- Písmeno napsané v otevřeném menu nebo do pole na liště (Hodnocení,
  BestOf, Favorit) nepřepne mapový podklad. Po výběru v seznamu Výlet
  na liště ovládají šipky a PageUp/PageDown dál mapu.
- Když nejde otevřít prohlížeč (nápověda, web), program ukáže adresu
  a nabídne ji zkopírovat. Nápověda z okna O programu otevře hlavní
  stránku nápovědy.
- O programu uvádí licenci GNU GPL v3.

### Odstraněno
- Vrstva „Open cyclo“ (Thunderforest). Lze ji přidat jako uživatelskou
  mapu s vlastním klíčem, příklad je v `priklady/uzivatelske-mapy.properties`.
- Položka nápovědy „Zprávy uživatelům“, která neměla odkud zprávy brát.
- Vrstva „Letecká Google“, náhled vlevo dole ukazuje leteckou mapu
  Mapy.cz.

### Vývoj
- Aktualizace knihoven se známými zranitelnostmi: guava 33.4.8,
  sqlite-jdbc 3.53.4.0, metadata-extractor 2.21.0, logback 1.3.16,
  v testech junit 4.13.2.
- Popis vydání na GitHubu se bere z tohoto souboru. Soubory vydání mají
  ověřitelný původ (`gh attestation verify`).
- Program se vydává jako `geokuk.jar` a zip pro Windows s Javou,
  konfigurace Launch4j je odstraněná.
- Smoke test celého programu nad falešným mapovým serverem (workflow
  Smoke, každou noc na `main`) a test kolizí klávesových zkratek.

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
