# Změny

## 6.1.0

### Změny
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

### Opravy
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
  s verzí a odkazem na web.
- OpenStreetMap se načítá přes https a nejde ji hromadně stahovat
  do cache.

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
