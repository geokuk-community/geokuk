# Slovník pojmů a jmenné konvence Geokuku

Geokuk má vlastní terminologii. Část pojmů je převzatá z geocachingu, část vznikla při vývoji a v běžné češtině nemá ekvivalent (kešoid, kachle, sklivec, genotyp waypointu…). Tento dokument je určen **vývojářům i AI asistentům**, kteří na Geokuku pracují. Vysvětluje:

1. pravidla, podle kterých se v Geokuku pojmenovává (čeština × angličtina),
2. jmenné konvence a zkratky, na které narazíte v kódu,
3. slovník doménových pojmů s odkazy na třídy,
4. obecný technický kód,
5. balíky, jejich skutečný obsah a původní architektonický záměr,
6. místa, kde se pojmy pletou nebo kde kód pravidla nedodržuje.

Při psaní nového kódu, komentářů, issues i commitů používejte pojmy přesně v tomto významu. Když zavádíte nový pojem, doplňte ho sem.

Dokument vznikl pro GeoKuk 5.2.0 a byl upraven pro stav forku geokuk-community k commitu `c4b81fc3` (11/2026).

---

## 1. Pravidla pojmenování

### Doména česky, obecný kód anglicky

| Druh kódu | Jazyk | Příklad |
|---|---|---|
| **Doména**: vše, co se týká geocachingu, map, keší, výletů a cest | **česky** | `Kesoid`, `Cesta`, `Usek`, `Vylet`, `Kachle`, `Genotyp`, `Alela` |
| **Obecný technický kód**: tak obecný, že by šel bez úprav použít v úplně jiné aplikaci | **anglicky** | `EventManager`, `BoundingRect`, `ExceptionDumper`, `DirScanner` |
| **Cizí knihovny** | anglicky (jak jsou) | Swing, Guava, SQLite JDBC, SQLJet, mapsforge |
| **Názvy z cizího formátu nebo standardu** (GPX, Groundspeak, GeoGet, WGS84, UTM) | v originále, ale **jen ve vrstvě, která formát čte nebo zapisuje** | `GpxWpt`, `Groundspeak.difficulty`, `Wgs`, `Utm` |

Jazyk názvu tak sám říká, kam kód patří. Česká třída je doména Geokuku. Anglická třída je obecný nástroj, který o geocachingu nic neví a nesmí na doméně záviset.

Doplňující pravidla:
- **Identifikátory bez diakritiky** (`Usek`, `Ukladanec`, `obtiznost`). Komentáře, javadoc a texty UI s diakritikou.
- **Komentáře a javadoc česky.** Starší anglické javadocy pocházejí od přispěvatelů a nemusí se přepisovat.
- **Doménový model je česky i tam, kde data přicházejí z cizího formátu.** Parser GPX čte `difficulty` a `terrain`, keš je má uložené jako obtížnost a terén.
- **Jeden pojem, jeden název.** Synonyma v kódu nepoužívat. Pozor zvlášť na kachle × dlaždice (kapitola 7).
- **Nový doménový pojem** se tvoří česky. Klidně jako neologismus (kapitola 2.3), hlavně ať je jednoznačný. Pak ho doplňte do tohoto slovníku.

### Turboware (tw)

Obecnému technickému kódu se říká **turboware**, zkráceně **tw**. Je to kód nezávislý na doméně, který jde beze změny použít v jiné aplikaci. Část obecného kódu Geokuku pochází z dřívější tw knihovny, proto na ni ještě odkazují některé komentáře v `util/*` (`@see cz.tconsult.tw…`). Označení tw není závazné. Platí jen pravidlo, že obecný kód je anglicky a nezávisí na doméně.

V Geokuku je obecný kód hlavně v balících `util/*` a `framework`.

### Jak důsledně Geokuk pravidla dodržuje

Kód vznikal od roku 2009 a podílelo se na něm víc lidí, proto pravidla nejsou dodržena všude. Při úpravách je dobré nesoulad postupně odstraňovat, ne ho rozšiřovat.

| Oblast | Dodržení | Odchylky |
|---|---|---|
| `util/lang` | vysoké | žádné |
| `util/exception`, `util/process` | částečné | česky `OtevreniSlozky`. `FExceptionDumper` a `MyExceptionHandler` importují `core`, `BrowserOpener` a `JErrorDialog` `framework` |
| `util/file` | částečné | neprůhledné zkratky `KeFile`, `LamUrl`. Česky: `Soubory`, `BezpecnyZapis`, `TextovySoubor`, `Resourcy`. `Filex` importuje `core.program.FConst` |
| `util/gui` | částečné | česky: `JBarvovyDvojSlider`, `MenuStrujce`, `MouseKonecListener`, `ERoh` a většina tříd z verzí 6.x (`BublinkaHodnoty`, `PopisekNaMape`, `ZkraceniTextu`, `ZalamovaciLayout`…) |
| `util/index2d` (quadtree) | smíšené | `Ctverecnik`, `Splitenec`, `Indexator` vedle `Node`, `Sheet`, `BoundingRect` |
| `util/pocitadla` | česky | celý balík, přitom jde o obecné diagnostické metriky |
| `util/procak` | česky | `Procak`, `ProcakDispatcher`: obecný řetěz zpracovatelů |
| `framework` | smíšené | česky: `JPrekryvnik`, `JSlidePozadi`, `Onoff`, `Prefe`, `Dlg`, `FKurzory`, `SouborovePreferences`, `RozmisteniPrekryvu`. **Doména ve frameworku**: `PodkladMapSpecificModel0` (mapový podklad), `JPresCelePrekryvnik` (závisí na `core.coord` a `core.napoveda`), `JSlide0` a `MySlideListener` (závisí na `plugins.kesoid.Wpt`) |
| `api/mapicon` | vysoké | `Imagant` (česky), navíc importuje `plugins.kesoid.Repaintanger` |
| doména: cesty, výlety, kachle, genetika, mřížky, popisky | vysoké | |
| doména: kód z verzí 6.x (challenge, offline mapy, správa map, zdroje keší, dálkové ovládání) | vysoké | česky jsou i obecné pomocné třídy (`JsonCteni`, `OmezenaFronta`, `PrioritniFronta`); slovo „dlaždice“ ve významu kachle (kapitola 7) |
| doména: druhy kešoidů `plugins/kesoid/kind/*` | nízké | anglicky `Waymark`, `Munzee`, `Photo`, `SimpleWaypoint`, `CzechGeodeticPoint`; balíky `kind`, `data`, `detail`, `mvc`, `mapicon` |
| doména: render | smíšené | `RenderModel`, `RenderSettings`, `RenderParams` vedle `Rendrovadlo`, `EStavRendrovani` |
| doména: nastavení pluginů | smíšené | `KruhySettings`, `PopiskySettings`, `ObsazenostSettings` |
| doména: entita `Kesoid`, `Kes`, `Wpt` | smíšené | anglicky `author`, `hidden`, `hint`, `difficulty`, `terrain`, `size`, `status` vedle `vztah`, `hodnoceni`, `znamka`, `nazev`. Existuje `getName()` i `getNazev()` |
| doména: výčty | smíšené | `EKesSize`, `EKesStatus`, `EKesType` anglicky, `EKesVztah` česky s hodnotou `NORMAL` |

Vlastnosti keše jsou v Geokuku pojmenované dvakrát: anglicky v entitě `Kes` (z GPX) a česky v genetice (`GEN_obtiznost`, `GEN_teren`, `GEN_velikost`). Podle pravidla patří do doménového modelu česká jména.

---

## 2. Jmenné konvence v kódu

### 2.1 Prefixy a přípony tříd

| Konvence | Význam | Příklad |
|---|---|---|
| prefix `J` | Swing komponenta | `JKachlovnik`, `JKesDetail` |
| prefix `F` | statická utilita, fasáda nebo konstanty | `FConst`, `FPref`, `FGeoKonvertor` |
| prefix `E` | enum (`EKaType` už výčtem není, je to třída s vestavěnými a uživatelskými podklady) | `EKesVztah`, `EVylet` |
| prefix `I` | rozhraní (jen část rozhraní ho má) | `IAtom`, `IImportBuilder` |
| prefix `A` | **dvojznačný**: potomek `Atom` (`ASada`), nebo hodnotový typ z tw (`ATimestamp`, `AString0`) | |
| prefix `X` / `Y` | výjimka runtime / checked | `XChainRupture`, `YNejdeTo` |
| přípona `0` | abstraktní předek | `Model0`, `Action0`, `Hledac0`, `Misto0` |
| přípona `Action`, `Event`, `Model`, `SwingWorker`, `Plugin`, `PopiskyDef` | role třídy | `VyletAnoAction`, `PoziceChangedEvent` |

Kód z verzí 6.x prefixy `E`, `I`, `X` a `Y` většinou nepoužívá (`TypZdroje`, `TypUseku`, `ChallengeUloziste`, `ChallengeChyba`, `OfflineMapaChyba`). Prefix `J` a přípony `0`, `Action`, `Event` a `Model` drží.

### 2.2 Proměnné a pole

| Konvence | Význam | Příklad |
|---|---|---|
| prefix `a` | parametr metody | `aEvent` |
| prefix `i` | instanční pole (jen ve starším kódu) | `iExceptionId` |
| `xx`, `yy` | souřadnice v **Mou** (na rozdíl od pixelových `x`, `y`) | `Mou.xx` |
| `mou…`, `wgs…` | hodnota v daném souřadnicovém systému | `mouJZ`, `moustred`, `mouNaPixl` |
| `moumer` | měřítko v bitech (viz Moumer) | |
| `soord`, `roord`, `moord` | Coord slidu, renderu, výřezu | `getSoord()` |
| světové strany | rohy a směry: `sz`, `sv`, `jz`, `jv` (severozápad…) | `KaLoc` (JZ roh), `ERoh.JV` |
| `PresCele` × `DoRohu` | hlavní mapa přes celé okno × miniatura v rohu | `JKachlovnikPresCele`, `JKachlovnikDoRohu` |
| `curta` | aktuální (vybraná) cesta | `CestyModel.curta` |

Prefixy `xx`, `mou…`, `soord` jsou **Apps Hungarian**: neříkají datový typ (ten je často stejný, `int` nebo `Coord`), ale význam hodnoty. Chrání tak před chybou typu „k Mou jsem přičetl pixely“. Při psaní nového kódu je dodržujte.

### 2.3 Neologismy

Geokuk tvoří názvy tříd jako česká slova s příponami **-ec, -adlo, -ník, -ant, -er, -ovač**. Jsou nestandardní, ale konzistentní a dobře zapamatovatelné.

| Přípona | Význam | Příklady |
|---|---|---|
| **-ec** | věc, se kterou se něco děje nebo která něčím je | Uchopenec (dá se uchopit), Nalezenec (výsledek hledání), Ukládanec (čeká na uložení), Splitenec (výsledek rozdělení), Sklivec (složení skel) |
| **-adlo** | nástroj, který něco dělá | Rendrovadlo, Malovadlo |
| **-ník** | kontejner nebo skladatel | Kachlovník (skládá kachle), Překryvník (skládá vrstvy), Čtverečník (uzel quadtree), Pozicovník |
| **-ant, -er, -ovač** | aktér, zpracovatel | Imagant, Kaputer, Kanceler, Repaintanger, Zperzistentňovač |
| složeniny | slepení dvou slov | Bousek (bod + úsek), Weikoid (waypoint + kešoid), Kepodr (kešoid + poddruh), Kaputer (kachle + computer), Repaintanger (repaint + rectangle) |

### 2.4 Pozůstatky refaktoringu

Některé podivné názvy jsou jen stopy přejmenování. Autor přejmenuje třídu a pak opravuje místa, kde překladač hlásí chybu. Například koncové `q` v `Poziceq` nic neznamená. Takové názvy lze při úpravách opravit.

---

## 3. Slovník doménových pojmů

Sloupec **V kódu** odkazuje na hlavní třídy a pole. Cesty balíků jsou relativní k `src/main/java/cz/geokuk`.

### 3.1 Souřadnice a zobrazení

| Pojem | Význam | V kódu |
|---|---|---|
| **Mou** | Vnitřní celočíselné souřadnice v Mercatorově projekci. 2^32 jednotek obepne rovník (cca 1 cm). Do Mou se převádí vše ostatní. | `Mou` (`xx`, `yy`) |
| **Moud** | Rozdíl (vektor) dvou Mou. | `Moud` |
| **Moumer** | Měřítko jako počet bitů: 0 = celá Země do 256 px, maximum 24. Odpovídá úrovni zoomu. | `moumer`, `Coord.MOUMER_0_BITS` |
| **Wgs** | Zeměpisné souřadnice WGS84 (lat, lon). Standard, název se ponechává. | `Wgs` |
| **Místo** | Cokoli s polohou na zemi v libovolném souřadnicovém systému. | `Misto0`, `Mouable` |
| **Uchopenec** | Místo, které jde uchopit myší a na které se dá přilepit pozice (waypoint, bod cesty). | `Uchopenec` |
| **Coord** | Neměnný popis zobrazení: moumer, střed (moustred), velikost v px, natočení. | `Coord` |
| **Soord / Roord / Moord** | Coord slidu (vrstvy mapového okna) / renderovaného výstupu / výřezu. | `soord`, `roord`, `moord` |
| **Natočení** | Úhel otočení mapy proti severu. V Mercatoru je vždy 0. Volba renderu „Srovnat do severu“ je proto bez účinku, pravděpodobně pozůstatek map v S-JTSK. | `natoceni`, `isSrovnatDoSeveru` |
| **Výřez** | Právě zobrazená část mapy. Ukládá se, aby se po startu obnovila. | `VyrezModel` |
| **Pozice** | Uživatelem označený bod, zobrazený jako **záměrný kříž**. Může být prázdná. Přilepí se na uchopence (waypoint má prioritu 40–50, bod cesty 20–22) a pamatuje si původní Mou. | `PoziceModel`, `Poziceq` |
| **Záměrný kříž** | Grafické znázornění pozice: na hlavní mapě kříž se dvěma kroužky, v detailu v rohu pevný kříž uprostřed. | `JPozicovnikSlide`, `JZamernyKriz`, `OdstranKrizAction` |
| **Referenční bod** | Odvozená hodnota: **pozice, a pokud není, střed výřezu**. Od něj se počítá vzdálenost v hledání, geocodingu a renderu. | `HlidacReferencnihoBodu`, `ReferencniBodSeZmenilEvent` |
| **Domov** | Trvale uložené domácí souřadnice („Jsem doma…“, Ctrl+D „Přesunout domů“). Od nich se počítá vzdálenost a azimut v detailu keše a může být středem challenge. | `RefbodyModel.hc`, `TadyJsemDomaAction`, `BezDomuAction` |
| **Pojmenovaný bod** | Bod ze souboru `geohome.ini` v datovém adresáři GeoGetu (řádek `lat lon název`, CP1250), skok přes Alt+1 až Alt+9. **Není to referenční bod**, i když to naznačuje název balíčku `refbody`. | `NaKonkretniBodAction`, `RefbodyModel` |
| **Mřížka** | Souřadnicová síť přes mapu: stupně (Dd, DdMm, DdMmSs) a UTM. Mřížky S-42 a JTSK nejsou dokončené: existují jen akce a modely, položky menu a slidy jsou zakomentované. | `plugins/mrizky`, `JMrizka*` |
| **Měřítkovník** | Grafické měřítko na mapě, v textech pro uživatele „měřítková lišta“. Je uprostřed dole. | `JMeritkoSlide`, `MeritkovnikAction` |

Vzdálenost a azimut se počítají ze tří různých míst:
- stavový řádek: od **pozice** k myši,
- hledání: od **referenčního bodu**,
- detail keše: od **domova**.

### 3.2 Mapové podklady a export

| Pojem | Význam | V kódu |
|---|---|---|
| **Kachle** | Čtvercový obrázek mapy (256 px) v pevné mřížce podle moumeru: stažený z mapového serveru, nebo vykreslený z offline mapy. Získává se, ukládá do cache, zobrazuje. Podle původního slovníku **to není dlaždice**; texty UI a kód z verzí 6.x jí ale říkají „dlaždice“ (kapitola 7, bod 1). | `Ka`, `JKachle`, `plugins/mapy/kachle` |
| **Lokace kachle** | Moumer a souřadnice kachle v mřížce. | `KaLoc` |
| **Podklad** | Mapový zdroj kachlí, který uživatel volí v menu Mapy (OSM, mapy.cz, ortofoto, offline mapa…). Od 11/2019 je podklad totéž co typ kachle. Skládání vrstev (dekorace, popisky) bylo tehdy zrušeno. Podklady jsou vestavěné (konstanty v kódu) a uživatelské. | `EKaType`, `MapyModel.podklad`, `PodkladAction` |
| **Id podkladu** | Jméno podkladu (`BASE_M`, `OFFLINE_MF`…, u uživatelské mapy `user-` a označení). Je zároveň klíčem v cache dlaždic, ukládá se jako vybraný podklad a podle něj se ukládá nastavení podle podkladu, takže jeho přejmenování osiří uložené kachle i nastavení. | `EKaType.name()`, `Ka.typToString()` |
| **Uživatelská mapa** | Vlastní podklad uživatele: soubor `<označení>.mapa` ve složce `data/mapy` s vlastnostmi `nazev`, `url` (vzor s `{z}`, `{x}`, `{y}`), `zkratka`, `atribuce`, `hromadne`, `hlavicka.*`… V menu Mapy je ve skupině „Uživatelské mapy“. Ukázky jsou v `priklady/mapy`. JSON s definicemi podkladů z verze 5.2.0 fork nemá. | `UzivatelskeMapy`, `UzivatelskyUrlBuilder`, `EKaType.isUzivatelska()` |
| **Překryv** | Průhledné kachle kreslené přes podklad: „ČÚZK – Stínovaný reliéf“ (online, WMS) a „Turistické a cyklotrasy“ (z offline map). Není to podklad. | `EKaType.RELIEF_CUZK`, `EKaType.TRASY`, `JKachlovnikReliefu`, `JKachlovnikTras`, `ReliefCuzkAction`, `PrekryvTrasAction` |
| **Atribuce** | Řádek se zdroji map vpravo dole v mapě; co se nevejde, je za ikonou ⓘ. | `AtribuceMapy`, `EKaType.getAtribuce()` |
| **Offline mapa** | Podklad (klávesa V) vykreslovaný v počítači z vektorových map `.map` (mapsforge) ve složce `data/offline-mapy`, bez internetu. Všechny mapy ve složce se kreslí naráz a složka se sleduje za běhu. Vykreslené kachle se ukládají do cache dlaždic. | `EKaType.OFFLINE_MF`, `OfflineMapy`, `OfflineRenderer`, `OfflineMapaChangedEvent` |
| **Téma offline mapy** | Vzhled offline mapy (menu Mapy > Téma offline mapy): vestavěné (GeoKuk, GeoKuk – detail, Mapsforge (původní)), nebo soubor uživatele `.xml` či `.zip` ve složce offline map. | `TemaOfflineMapy`, `JTemaOfflineMapyMenu` |
| **Vrstva tématu** | Přepínatelná část tématu offline mapy; téma může mít víc stylů a v každém vrstvy (Mapy > Téma offline mapy > Vrstvy tématu). Ukládají se jen odchylky od výběru autora tématu. | `VrstvyTematu`, `VolbaVrstev` |
| **Vrstva k mapě** | Data stahovaná k offline mapě: turistické a cyklotrasy, vrstevnice, výškový model pro stínování terénu. | `KatalogovaVrstva`, `KatalogVrstev`, `SouboryVrstev`, `StinovaniTerenu` |
| **Přehledová mapa** | Hrubá mapa světa `prehled-svet-ne.map` (Natural Earth) přibalená v zipu; dává data jen tam, kde není podrobná mapa. | `PrehledovaMapa`, `SouborMapy` |
| **Správce offline map** | Popup (menu Mapy > Správce offline map…, blok Mapy ve stavovém řádku) se staženými mapami, mapami pro zobrazené místo a mapami ke stažení i s vrstvami. Obrys mapy se ukazuje v mapě. | `plugins/mapy/sprava`, `SpravaMapModel`, `JMapyPopup`, `JBlokMap`, `OfflineMapyAction`, `JObrysMapSlide` |
| **Katalog map** | Seznam offline map zemí a regionů ke stažení (vestavěný `katalog-map.json`) a katalog vrstev (`layers.json` z projektu geokuk-maps). | `KatalogMap`, `KatalogovaMapa`, `MatriceMap`, `StahovacMapy`, `StahovacVrstev` |
| **Průvodce nastavením map** | Dialog (Soubor > Průvodce nastavením map, při první instalaci sám): země, podklad, vrstvy, shrnutí; stáhne, co chybí. | `plugins/mapy/pruvodce`, `JPruvodceMap`, `PruvodceMapAction` |
| **Nastavení podle podkladu** | Nastavení kreseb nad mapou ukládané zvlášť pro každý podklad (např. jiné barvy popisků na letecké a turistické mapě). | `PodkladMapSpecificModel0` (potomci `KruhyModel`, `PopiskyModel`, `ObsazenostModel`) |
| **Kachlovník** | Vrstva skládající kachle do zobrazení. | `JKachlovnik*` |
| **Kaputer** | Výpočty vztahu kachlí a zobrazení (kachle + computer). | `Kaputer` |
| **Získávač kachlí** | Získání kachle z paměti, disku, sítě nebo vykreslením offline mapy. | `KachleZiskavac`, `KachloDownloader` |
| **Záznam cache kachle** | Položka paměťové cache získávače: řídí získání jedné kachle a eviduje příjemce, kteří na ni čekají. | `KachleZiskavac.Kachlice` (vnitřní třída) |
| **Cache dlaždic** | Disková cache kachlí: databáze `tiles.sqlite` ve složce `data/cache`. Klíčem je lokace kachle a typ (id podkladu, u offline mapy klíč z tématu, měřítka, písma a otisků souborů map). Soubor > Optimalizace cache dlaždic… ji zhutní a odstraní zastaralé kachle offline map. | `KachleDBManager`, `CacheDb`, `OblastniKlic`, `UklidOfflineCache`, `OptimalizaceDatabaze` |
| **Priorita kachle** | `KACHLE` (zobrazení na mapě) × `STAHOVANI` (offliner, render). Stahování se brzdí, dokud čekají kachle k zobrazení. Kachle se navíc získávají od středu výřezu ven. | `Priority`, `PrioritniFronta` |
| **Náhled dlaždice** | Výřez už hotové kachle z jiného moumeru, který se ukáže, dokud se kachle získává. | `NahledDlazdice` |
| **Předvykreslení** | Vykreslení kachlí offline mapy do cache předem: těsně za okrajem výřezu a v nečinnosti pro oddálená měřítka. | `PredvykresleniOkoli`, `PredvykresleniPrehledu` |
| **První načítání mapy** | Stav po spuštění, kdy je v mapě logo a kresby nad mapou se nekreslí, dokud není načteno 40 % kachlí výřezu. | `PrvniNacitaniMapy`, `LogoNacitani`, `JIndikatorNacitaniSlide` |
| **Kanceler** | Zrušení požadavku na kachli, která mezitím opustila výřez. | `Kanceler` |
| **Zástupná kachle** | Obrázek zobrazený místo chybějící kachle (offline, chyba stažení). | `KachloDownloader.EPraznyObrazek` |
| **Ukládanec** | Kachle ve frontě na uložení na disk. | `Ukladanec` |
| **Offliner** | Hromadné stažení kachlí oblasti pro offline použití (Mapy > Postahovat dlaždice…). U podkladů, jejichž provozovatel to zakazuje, je vypnutý. | `JKachleOflinerDialog`, `KachleOflinerAction`, `DavkaStahovani`, `EKaType.SERVERY_BEZ_HROMADNEHO_STAHOVANI` |
| **Online/offline režim** | Zda smí aplikace na síť. Řídí stahování kachlí, geocoding i kontrolu aktualizací. S offline mapou nesouvisí. | `OnofflineModel` |
| **Render** | Export výřezu mapy do souboru: obrázek, KMZ (Google Earth), OziExplorer, tisk. | `core/render` |
| **Rendrovadlo** | Jádro renderování do obrázku. | `Rendrovadlo` |
| **Měřítko renderu** | Moumer, ve kterém se renderuje. Liší se od moumeru výřezu a určuje velikost výstupu. | `renderedMoumer` |
| **Ořezávací obdélník** | Rámeček oblasti renderu zobrazený na mapě během přípravy renderu. | `JRenderSlide` |
| **Dlaždice** | Díl, na který se rozdělí renderovaný výstup kvůli limitu velikosti (KMZ). Podle původního slovníku **to není kachle**; ve forku se tak ale běžně říká i kachli (kapitola 7, bod 1). | `DlazdicovaMetrika`, `kmzMaxDlazdiceX` |
| **Kalibrační bod** | Dvojice pixel ↔ WGS zapisovaná do `.map` souboru pro OziExplorer. | `JKalibrBoduSpinner`, `RenderSettings` |
| **Papírové měřítko** | Poměr 1:N pro tisk, z něj se počítá DPI. | `RenderSettings.papiroveMeritko` |
| **Papírová metrika** | Volba formátu papíru (A0…) a orientace pro tisk, odvozená z papírového měřítka. | `PapirovaMetrika` |

### 3.3 Kešoidy

| Pojem | Význam | V kódu |
|---|---|---|
| **Kešoid** | Zobecněná keš: cokoli, co se hledá nebo zobrazuje jako objekt s waypointy. | `Kesoid` |
| **Druh kešoidu** | Keš (`gc`), waymark (`wm`), munzee (`mz`), fotka (`pic`), český geodetický bod (`gb`, CGP), obecný waypoint (`wp`). | `EKesoidKind`, `GEN_druhKesoidu`, `plugins/kesoid/kind/*` |
| **Kešoidový plugin** | Podpora jednoho druhu kešoidu: načítání, ikony, popisky, detail. Seznam pluginů je zapsaný v `KesoidPluginManager`. | `KesoidPlugin`, `KesoidPluginManager` |
| **Waypoint** | Bod s polohou patřící ke kešoidu. Mezinárodní pojem geocachingu. | `Wpt` |
| **Hlavní × první waypoint** | **První waypoint** jsou úvodní (listingové) souřadnice keše. **Hlavní waypoint** je u keše final, pokud má neprázdné souřadnice, jinak první waypoint. Ostatní waypointy jsou **vedlejší**. | `getMainWpt`, `getFirstWpt`, `Kes.setMainWpt` |
| **Ručně přidaný waypoint** | Vedlejší waypoint přidaný uživatelem v GeoGetu (podle flagu GeoGet rozšíření). V tooltipu označen „+“. | `Wpt.rucnePridany` |
| **Nutný k luštění** | Vlastnost waypointu: hlavní, otázka nebo stage multiny. Stojí na ní volba filtru „Jen do terénu“. | `Wpt.nutnyKLusteni()` |
| **Pořadí kreslení** | Pořadí vrstev waypointů: ostatní, vedlejší waypoint keše, první, final (final nahoře). | `Wpt.EZOrder` |
| **Weikoid** | Společný předek kešoidu a waypointu (waypoint + kešoid). | `Weikoid0` |
| **Keš** | Geocachingová keš: typ, velikost, obtížnost, terén, hodnocení, známka, favority, BestOf. | `Kes` |
| **Vztah** | Vztah uživatele ke kešoidu: nehledané, nalezené, vlastní, neexistující, captured, deployed (munzee). | `EKesVztah` |
| **Stav** | Aktivní, deaktivovaná, archivovaná. | `EKesStatus` |
| **Hodnocení, BestOf, favority, známka** | Hodnocení z geocaching.cz a geocaching.com, přichází přes GeoGet. | `Kes`, `Gpxg` |
| **Nick** | Přezdívka uživatele na geocaching.com, určuje vlastní keše. | `GccomNick` |
| **Zdroj (zdroj keší)** | Soubor nebo databáze, odkud byly kešoidy načteny. Zobrazují se v tabulce zdrojů (stavový řádek, okno Přehled zdrojů) a dají se vypnout. Keš, která je ve více zdrojích, se zobrazí ze zdroje s nejnovějšími daty. | `InformaceOZdroji`, `KeFile`, `JTabulkaZdroju`, `InformaceoZdrojichAction` |
| **Typ zdroje** | GPX, GeoGet, GSAK, OpenSAK podle datové složky, ze které zdroj pochází. Typ jde celý zapnout a vypnout (Ctrl+1 až Ctrl+4, menu Kešoidy > Zdroje keší, přepínače ve stavovém řádku). | `TypZdroje`, `PrepniZdrojAction`, `JPrepinaceZdroju`, `ZkratkyZdroju` |
| **Stav zdroje** | Čeká na načtení, načítá se, čeká na zápis (databázi drží jiný program), načteno, chyba čtení, vypnuto. | `StavZdroje`, `RegistrStavuZdroju`, `StavZdrojuEvent` |
| **Kořen zdrojů** | Místo, odkud se zdroje skenují. Jsou čtyři: složka s GPX (rekurzivně: geokuk, gpx, zip, jpg…), GeoGet (`*.db3`), GSAK (`sqlite.db3`), OpenSAK (`*.db`). | `Root`, `Root.Def`, `MultiNacitac` |
| **Blokovaný zdroj** | Zdroj, který uživatel vyřadil z načítání, i s celým podstromem. | `KesoidModel.blokovaneZdroje` |
| **Načítat až po vybrání** | Volba zdrojů GeoGet, GSAK a OpenSAK: nová databáze se nenačte, dokud ji uživatel nezapne v tabulce zdrojů. | `FPref.NOVE_DATABAZE_HNED_value` v uzlech `GSAK_node`, `OPENSAK_node`, `GEOGET_node` |
| **Detekce zdrojů** | Při prvním spuštění (složka zdroje v nastavení chybí) hledání datové složky GeoGetu, GSAKu a OpenSAKu v obvyklých umístěních a v jejich souborech nastavení; nalezený zdroj se zapne jen s poslední použitou databází a s volbou Načítat až po vybrání. | `DetekceZdroju`, `KesoidModel.detekujZdroje` |
| **Načítač** | Importér jednoho formátu: GPX, DB GeoGetu, DB GSAKu, DB OpenSAKu, metadata fotek, vlastní formát Geokuku. | `Nacitac*`, `GeogetLoader`, `GsakDbLoader`, `OpensakDbLoader` |
| **Procák** | Zpracovatel jednoho GPX waypointu do kešoidu. Procáky jednotlivých pluginů jsou řetězené. | `Procak`, `*GpxWptProcak`, `ProcakDispatcher` |
| **GeoGet rozšíření** | Data z `<gpxg:GeogetExtension>` a z databází GeoGetu a GSAKu: nalezení, flag ručního přidání, hodnocení, BestOf, favority, známka, výška, kraj, okres, uživatelské tagy `geokuk_*`. | `Gpxg` |
| **Kepodr** (kešoid poddruh) | Ve skutečnosti **druh popisky**: určuje šablonu a dostupné zástupky popisku na mapě. Deklaruje ho plugin. Hodnoty `kes`, `kesadwpt`, `waymark`, `munzee`, `cgp`, `photo`, `simplewaypoint`. Popisky se kreslí jen u hlavních waypointů, takže `kesadwpt` se neuplatní a prakticky kepodr = druh kešoidu. | `Kepodr`, `KesoidPlugin.getKepodrs()`, `PopiskyPatterns` |
| **Bag** | Množina všech načtených kešoidů s prostorovým indexem. | `KesBag` |
| **Kód kešoidu** | Označení kešoidu (GC…, OC…, WM…) vytažené z textu nebo odkazu (coord.info, geocaching.com, Opencaching, Waymarking). Používá ho okno Najdi keš, dálkové ovládání a volba „Přejít na kešoid ze schránky“. | `KodKesoidu`, `HledejKesAction`, `PrejdiNaKesoidZeSchrankyAction`, `SledovaniSchranky` |
| **Zobrazování kešoidů** | Vypínač F5 (Kešoidy > Zobrazovat kešoidy): skryje a ukáže kešoidy včetně kruhů a popisků. | `KesoidyOnoffAction`, `KesoidOnoffEvent` |
| **Tečky** | Kreslení keší na oddálené mapě jako barevných teček podle typu místo ikon. Volby Tečky při oddálení / Vždy ikony / Vždy tečky. | `Tecky`, `EZobrazeniKesi`, `ZobrazeniKesiAction` |
| **Limity kreslení** | Nejvyšší počet waypointů ve výřezu kreslených jako ikony a jako tečky (Kešoidy > Limity kreslení…). Nad limitem teček se nekreslí nic. | `LimityKresleni`, `JLimityKresleniDialog`, `PrekrocenLimitWaypointuVeVyrezuEvent` |

### 3.4 Genetika

Vlastnosti waypointů a jejich zobrazení popisuje Geokuk pomocí **genetické metafory**. Kdo zná základy genetiky, odvodí si vztahy mezi pojmy sám.

| Pojem | Význam | V kódu |
|---|---|---|
| **Genom** | Registr všech genů, alel a druhů. | `Genom` |
| **Gen** | Jedna vlastnost waypointu: sym (typ), postavení (hlavní × vedlejší), postavení k myši, druh, vztah, stav, výlet, cesty, velikost, obtížnost, terén… | `Gen` |
| **Alela** | Hodnota genu. Každý gen má **výchozí alelu**. Kvalifikované jméno má tvar `alela:gen` (`fnd:vztah`). | `Alela` |
| **Druh** | Určuje, které geny daný druh kešoidu má a na kterých lokusech. | `Druh` |
| **Lokus** | Pozice genu v DNA daného druhu. | `Druh.lokusy` |
| **Genotyp** | Neměnná, internovaná (sdílená) kombinace alel jednoho waypointu. | `Genotyp` |
| **DNA** | Pole alel genotypu indexované lokusem. | `Genotyp.dna` |
| **Fenotyp** | Projev genotypu navenek, tedy ikona. **Nefenotypované alely** se na ikoně neprojeví. | `jmenaNefenotypovanychAlel`, `JFenotypIkonyDialog` |
| **Uživatelské geny** | Geny a alely vzniklé za běhu z tagů `geokuk_*` GeoGetu. | `Nacitac0.PREFIX_USERDEFINOANYCH_GENU` |
| **Gen `vylet`, gen `cesty`** | Geny, kterými se výlet a cesty promítají do ikony: `lovime`, `ignoru`, výchozí `nevime` / `nacestejsou`, výchozí `mimocesticu`. | `Genom.GEN_vylet`, `GEN_cesty` |
| **Grupa** | Skupina alel symbolového genu podle prefixu názvu souboru ikony (`gc!`, `gcawp!`). **Zastaralé** („Grupy nechceme, použijeme druhy“), přesto ji používá toolbar a dialogy výběru ikon. Pro nový kód použijte druh. | `Grupa`, `GrupaSym` |
| **Zhasnutí** | **Úplné skrytí** waypointů s danou alelou, tj. přidání alely do množiny nechtěných alel filtru. Vstupy: menu „Zhasni“, ovladače alel na toolbaru, dialog Filtr (F2). | `ZhasniKeseUrciteAlelyAction`, `jmenaNechtenychAlel`, `Gen.vypsatelnyVeZhasinaci` |
| **Ovladače alel** | Zaškrtávátka zhasnutí na toolbaru. Seznam alel je pevný. | `JToolbarOvladaceAlel` |
| **Filtr** | Nechtěné alely + prahy (hodnocení, BestOf, favority, výlet, „jen do terénu“, „jen final“). Plánovač challenge k němu přidává „jen kandidáti“. | `KesoidFilter`, `FilterDefinition` |

| Genetika | Obecný IT pojem |
|---|---|
| gen | atribut, vlastnost |
| alela | hodnota výčtu |
| druh | schéma, typ |
| lokus | index v poli |
| genotyp | internovaná n-tice hodnot (flyweight) |
| fenotyp | zobrazení dat |

### 3.5 Ikony

Ikona waypointu je fenotyp: skládá se z vrstev podle alel genotypu.

| Pojem | Význam | V kódu |
|---|---|---|
| **Sada ikon** | Pojmenovaná sada grafiky ikon (adresář s definicemi). | `Sada`, `ASada` |
| **Sklo** | Jedna vrstva, ze kterých se skládá ikona (obrysy, čtverce, typy, zešednutí…). Definice ve `skla.txt`. | `Sklo`, `skloNormal`, `skloZvyrazneni` |
| **Vrstva skla** | Vyhledávací struktura ikon jednoho skla podle alel. | `Vrstva` |
| **Icondef** | Definice obrázku pro kombinaci alel. Alely jsou v názvu souboru. | `IconDef`, `IconDefNacitac` |
| **Sklivec** | Výsledná složená ikona jednoho genotypu (obrázky přes všechna skla). Cache genotyp → sklivec. | `Sklivec` |
| **Imagant** | Obrázek s posunem vůči středu. | `Imagant` |
| **Zvýraznění** | Barevný čtverec pod ikonou keše podle pravidel z alel (Kešoidy > Zvýraznění: předvolby Terén 4,5 a 5, Obtížnost 4,5 a 5, nebo vlastní pravidla). Není to zvýrazňovací kruh (kapitola 7). | `Zvyrazneni`, `JMenuZvyrazneni`, `JZvyrazneniDialog`, `ZvyrazneniEvent` |
| **Zvětšení ikon** | Násobek velikosti ikon keší na mapě (1× až 5×, Soubor > Zobrazení programu). | `ZvetseniIkonEvent`, `FPref.ZVETSENI_IKON_value` |
| **Repaintanger** | Repaint + rectangle: **maximální** okraje, o které libovolná ikona sady přesahuje bod waypointu. Hodnota jen roste. Rozšiřuje oblast k překreslení i výběr waypointů při kreslení. | `Repaintanger` |

### 3.6 Výlet a cesty

**Výlet a cesty jsou dvě různé věci.** Výlet je dlouhodobé třídění kešoidů. Cesty jsou konkrétní trasy přes body. Ve verzi 3.0 se sloučily, verze 3.3.0 je znovu oddělila.

| Pojem | Význam | V kódu |
|---|---|---|
| **Výlet** | Rozdělení kešoidů na **ANO** (lovím), **NE** (ignoruji, vynechávám) a **NEVÍM** (nerozhodnuto, výchozí). | `Vylet`, `VyletModel` |
| **Pojmenovaný výlet** | Výletů může být víc, každý má jméno. Jeden je **aktivní**; s ním pracují akce, filtr a ikony. Seznam výletů je v popupu u jména výletu ve stavovém řádku (založit, aktivovat, přejmenovat, smazat). Bez souboru `vylety.txt` existuje jediný výlet „Výchozí“. | `VyletInfo`, `VyletyUlozisko`, `JVyletyPopup`, `JPopisekVyletu`, `VyletyZmenenyEvent` |
| **Stav ve výletu** | ANO / NE / NEVÍM jednoho kešoidu. Do ikony se promítá genem `vylet`. | `EVylet` |
| **Práh výletu** | Volba filtru: všechny / bez vynechaných / jen lovené. Používá tentýž enum `EVylet` (viz kapitola 7). | `FilterDefinition.prahVyletu` |
| **Seznam GGT** | Formát GeoGetu: prostý text, jeden kód kešoidu na řádek. | `Ggt`, `VyletPul` |
| **Půlka výletu** | Obsah jednoho souboru výletu: `lovim.ggt` = ANO, `tedne.ggt` = NE. NEVÍM se neukládá. Soubory jsou ve složce `data/vylety`; aktivní výlet je vždy v `lovim.ggt` a `tedne.ggt`, ostatní v `ulozene/<id>/`, seznam v `vylety.txt`. | `VyletPul`, `VyletyUlozisko` |
| **Cesta** | Lomená čára přes body. | `Cesta` |
| **Vybraná cesta** | Cesta, ke které se přidávají body a na kterou míří akce z menu. Může být žádná. | `CestyModel.curta` |
| **Panel cesty** | Panel vpravo nahoře v mapě s délkou vybrané cesty, počtem bodů a keší a vzdušnými úseky; přepíná se v něm vedení úseků. | `JPanelCesty`, `PanelCestyAction`, `PanelCestyEvent` |
| **Bod cesty** | Vrchol cesty. Může být navázán na waypoint. | `Bod` |
| **Připíchnutí bodu** | Navázání bodu cesty na waypoint: při kreslení přichycením, po načtení podle souřadnic. | `CestyZperzistentnovac.pripniNaWayponty` |
| **Úsek** | Spojnice dvou bodů cesty. Má typ: rovný, po cestě, nebo vzdušný. | `Usek`, `TypUseku`, `NastavTypUsekuAction` |
| **Úsek po cestě** | Úsek vedený po cestách a pěšinách z offline mapy (pěší trasa bez internetu). Kreslí se plnou čarou, rovný čárkovaně. Trasa se hledá na pozadí a do GPX se ukládá se všemi body. | `TypUseku.PO_CESTE`, `plugins/cesty/routovani`, `Routovac`, `TrasyUseku`, `Trasa` |
| **Vzdušný úsek** | Úsek, který se nepočítá do délky cesty, kreslí se tečkovaně a v GPX rozděluje cestu na samostatné `trkseg`. Představuje přesun, který se nejde. | `TypUseku.VZDUSNY`, `Usek.isVzdusny()`, `PrepniVzdusnostUseku` |
| **Segment** | Souvislá část cesty mezi vzdušnými úseky, v GPX `trkseg`. | `Bod.isNaHraniciSegmentu` |
| **Bousek** | Bod nebo úsek (bod + úsek), společný předek. | `Bousek0` |
| **Blízký bousek** | Bod nebo úsek do 7 px od kurzoru, na který míří myš a kontextové menu. | `JCestySlide.blizkyBousek` |
| **Krajový bod** | Počáteční nebo koncový bod cesty. | `Bod.isKrajovy` |
| **Uzavřená (kruhová) cesta** | Cesta, jejíž první a poslední bod leží na stejném místě. | `Cesta.isKruh` |
| **Startocíl** | Krajový bod uzavřené cesty, který je startem i cílem. | `Bod.isStartocil` |
| **Jednobodová cesta** | Cesta s jediným bodem. Maže se akcí „Promazat jednobodové cesty“. | `Cesta` |
| **Dokument cest** | GPX soubor se všemi cestami, výchozí složka `data/cesty`. V UI a kódu se místy nepřesně nazývá „výlet“. | `Doc` |
| **Zperzistentňovač** | Ukládání a načítání výletu a cest ze souborů. | `VyletovyZperzistentnovac`, `CestyZperzistentnovac` |

### 3.7 Mapové okno a kresby

| Pojem | Význam | V kódu |
|---|---|---|
| **Slide** | Průhledná vrstva mapového okna (kešoidy, cesty, popisky, mřížky, kachle…). | `JSlide0`, `JSingleSlide0` |
| **Překryvník** | Kontejner skládající slidy přes sebe. | `JPrekryvnik` |
| **Překryvník s rohama** | Kořen hlavního okna: mapa přes celou plochu a v rozích plovoucí panely (JZ detail v rohu, JV informace o keši, SV panel cesty). | `JSRohamaPrekryvnik`, `CornerLayoutManager` |
| **Rozmístění překryvů** | Jediné místo, kde se rozhoduje, kam na mapě smí který překryvný prvek (pruh plánovače, ukazatel výkonu, krátká zpráva, měřítko, panely v rozích). | `RozmisteniPrekryvu` |
| **Detail keše** | Panel vpravo dole s údaji kešoidu na pozici (kód, název, vzdálenost a azimut od domova, D/T…). Jiný modul do něj může doplnit řádek (doplněk detailu), plánovač 360° tak ukazuje sektor keše. | `JKesoidDetailContainer`, `JKesDetail`, `DoplnekDetailuKese`, `DoplnekDetailuModel` |
| **Detail v rohu** | Malá mapa vlevo dole s pevným moumerem 17 a ortofotem, centrovaná na pozici. Při kreslení cest sleduje kurzor. | `JDetailPrekryvnik`, `JKachlovnikDoRohu` |
| **Myšování** | Nejvyšší průhledný slide, který chytá myš a klávesy. | `JPresCeleMysovani`, `JDetailMysovani` |
| **Popisky** | Texty u waypointů podle šablony (Ctrl+F12). | `plugins/kesoidpopisky` |
| **Kruhy** | Zvýrazňovací kruhy kolem waypointů (F8), velikost v pixelech nebo v metrech. | `plugins/kesoidkruhy`, `KruhyOnoffAction`, `JZvyraznovaciKruhySlide` |
| **Kruhy v metrech** | Kruhy s pevným poloměrem v metrech (Alt+F8), výchozí 100 m; položka menu se jmenuje podle poloměru (Kruhy 161 m). V kódu „jednotkové kruhy“. | `JednotkoveKruhyAction`, `PolomerKruhu`, `KruhySettings` |
| **Stav vrstev kruhů** | Evidence, které zapnuté vrstvy kruhů (zvýrazňovací, v metrech, obsazenost) se nekreslí a proč; ve stavovém řádku štítek „Kruhy skryté“ nebo „Kruhy omezené“. | `StavVrstevModel`, `VrstvaNadMapou`, `DuvodNekresleni` |
| **Obsazenost** | Kruhy o poloměru 161 m kolem **obsazujících waypointů** (final, stage multiny, tradiční keš, ne archivované): oblasti obsazené kešemi. | `JObsazenost`, `Wpt.obsazujeOblast()` |
| **Malovadlo** | Kreslení cest. | `Malovadlo` |
| **Stavový řádek** | Souřadnice myši, počty kešoidů, vzdálenost a azimut od pozice, jméno aktivního výletu a počty v něm, soubor a počty cest, přepínače zdrojů keší, blok Mapy, štítky kruhů a plánovače. | `JStatusBar`, `JPrepinaceZdroju`, `JBlokMap`, `JPopisekVyletu` |
| **Krátká zpráva (toast)** | Zpráva uprostřed dole nad měřítkem, která se po klávesové zkratce na chvíli ukáže s názvem akce a stavem („Kruhy v metrech – Zapnuto, 100 m“). Myš ani klávesy nezachytává. | `JToast`, `ToastModel`, `ToastEvent`, `ZpravyZkratek` |

### 3.8 Hledání a ostatní

| Pojem | Význam | V kódu |
|---|---|---|
| **Hledač, nálezenec** | Hledání (kešoidů podle textu, míst přes geocoding). Nálezenec je výsledek se vzdáleností a azimutem od referenčního bodu. | `Hledac0`, `Nalezenec0` |
| **Geocoding** | Hledání adres a míst (Jít > Adresa…, Ctrl+M) přes Nominatim nad daty OpenStreetMap. | `plugins/geocoding`, `Nominatim` |
| **Otevření místa v mapě na webu** | Položky kontextového menu mapy: Mapy.com, Google Mapy, OpenStreetMap, ČÚZK Geoprohlížeč. | `OdkazyNaMapy`, `OtevritVMapeAction` |
| **Street View** | Otevření Google Street View v prohlížeči. Třída se jmenuje zavádějícím způsobem `OpenStreetViewAction`, s projektem OpenStreetView nesouvisí. | `OpenStreetViewAction` |
| **Umístění souborů** | Nastavitelné cesty k datům jiných programů a výstupům (keše z GPX, GeoGet, GSAK, OpenSAK, render, offline mapy). Ostatní složky jsou pevně ve složce `data`. | `*UmisteniSouboru`, `JUmisteniSouboruDialog`, `Filex` |
| **Profil nastavení** | Pojmenovaný podstrom nastavení vybraný parametrem `-Dprefroot`. Nedokončené. | `MyPreferences` |

### 3.9 Plánovač challenge

Plánovač pomáhá plnit challenge keše: z nálezů spočítá, co chybí, a z nenalezených keší nabídne ty, které by chybějící doplnily. Celý je v balíku `plugins/challenge`.

| Pojem | Význam | V kódu |
|---|---|---|
| **Challenge** | Definice úlohy jako data: název, typ, střed, oblast a pravidla. Zapnutá (aktivní) může být jen jedna. | `Challenge`, `ChallengeModel`, `ChallengeSeznam` |
| **Typ challenge** | **360°** (nález v každém ze 360 úhlových sektorů kolem středu), **matice D/T** (nález v každém z 81 políček obtížnost × terén), kalendář (nález v každém dni roku; v UI zatím vypnutý). | `Challenge.Typ`, `VypocetSektoru`, `VypocetMatice`, `VypocetKalendare` |
| **Šablona** | Přednastavená definice jen ke čtení (360° od domova, GC4PCQB, GC56R49, Matice D/T). Vlastní challenge vzniká jako kopie. | `Sablony`, `JChallengeDialog` |
| **Střed** | Bod, od kterého se u 360° měří azimut: domov, zadané souřadnice, nebo souřadnice keše podle kódu (listing). Není to střed výřezu. | `Challenge.Stred`, `Challenge.DruhStredu` |
| **Oblast** | Území, ve kterém se počítají nálezy a kandidáti (stát, sjednocení okresů). Hranice jsou ve zdrojích programu. | `Oblast`, `OblastPolygony`, `PoskytovatelOblasti`, `FormatOblasti`, `FormatRuian` |
| **Pravidla** | Podle čeho se počítá: Project-GC (koule, bez Lab keší), nebo Stator (elipsoid WGS84, včetně Lab keší). | `Challenge.ZdrojPravidel`, `PravidlaTypu`, `Geodezie` |
| **Sektor** | Jeden stupeň azimutu od středu (0 až 359). Je splněný, chybějící, nebo **nejistý** (azimut keše leží příliš blízko hranice stupně). | `VysledekSektoru`, `GeometrieSektoru` |
| **Políčko** | Jedna kombinace obtížnosti a terénu matice D/T („D3/T4“), případně den kalendáře. | `VysledekPolicek`, `JMaticeDT` |
| **Násobnost (Kolikrát splnit)** | Kolikrát se má každé políčko matice D/T splnit (1 až 9). | `ChallengeSeznam`, `VypocetMatice` |
| **Kandidát** | Nenalezená keš, která by doplnila chybějící sektor nebo políčko. | `VysledekSektoru.Kandidati`, `KandidatPolicka`, `VybranyKandidat` |
| **Jen kandidáti** | Filtr (F4), který na mapě nechá jen kandidáty; vybraný sektor nebo políčko jejich keše jen zvýrazní. | `FiltrKandidatu`, `ChallengeJenKandidatiAction` |
| **Lišta (pruh) plánovače** | Lišta vlevo nahoře na mapě: výběr challenge, odznak s počtem chybějících, tlačítka ◀ ▶, přepínače Jen kandidáti, Sektory a Štítky. Vypnutá se smrskne na úchyt. | `JPruhPlanovace`, `StavPruhu`, `ChallengeZapnoutAction` |
| **Odznak** | Tlačítko v liště s počtem chybějících sektorů nebo políček; otevírá popup plánovače. | `JPruhPlanovace`, `JPopupPlanovace` |
| **Prstenec sektorů** | Kresba na mapě u 360°: kružnice kolem středu se stavem sektorů, klín vybraného sektoru a čára ke kandidátovi. | `JPlanovacPodKeskami`, `TvarPlanovace` |
| **Štítek kandidáta** | Text u keše na mapě s číslem sektoru nebo políčkem D/T. | `StitekKese`, `JPlanovacNadKeskami`, `ZvyrazneniKese` |
| **Posun výběru, skok** | ◀ ▶ (Shift+← a Shift+→) posouvají výběr na předchozí a další chybějící sektor nebo políčko. Ctrl+Shift+šipka skočí na nejbližšího kandidáta ve směru šipky. | `PosunVyberu`, `ChallengeDalsiAction`, `ChallengePredchoziAction`, `SkokVeSmeru`, `ChallengeSkokAction` |
| **Předpočet** | Výpočet polohy keší vůči středu a oblasti na pozadí, uložený do souboru v podsložce `challenge` složky cache. | `Predpocet`, `SouborPredpoctu`, `StavPredpoctu`, `PlanovacChallenge` |

### 3.10 Provoz programu

| Pojem | Význam | V kódu |
|---|---|---|
| **Přenosný GeoKuk** | Program se neinstaluje. Vše, co si ukládá, je ve složce `data` vedle něj a složku jde přesunout i s daty. Zip pro Windows má program a Javu v podsložce `program`. | `UmisteniProgramu`, `FConst.DATA_DIR`, `KontrolaUmisteni` |
| **Složka data** | `nastaveni.xml`, `mapy`, `mapy-priklady`, `cache`, `offline-mapy`, `gpx`, `cesty`, `ikony` (`moje`, `ostatni`), `vylety`, `render`, `log`. Data verze 5.x ve složce `~/geokuk` program nečte. | `FConst`, `KesoidUmisteniSouboru`, `KachleUmisteniSouboru`, `RenderUmisteniSouboru` |
| **Soubor nastavení** | `data/nastaveni.xml` ve formátu exportu Java Preferences. Při prvním spuštění se jednou převezme nastavení starší verze z registru nebo ze souboru `geokuk-preferences.xml`. | `Nastaveni`, `SouborovePreferences` |
| **Značka kořene** | `${GeoKuk}` na začátku uložené cesty: cesta uvnitř složky programu, která se při přesunu složky posune s ní. | `MyPreferences.ZNACKA_KORENE`, `Filex` |
| **Spouštěč** | `start.jar`: nainstaluje staženou novou verzi, zvolí paměť a spustí `geokuk.jar`. Nesmí používat žádné jiné třídy programu. | `start/Start`, `Cds`, `Restart`, `PametProgramuAction` |
| **Kontrola aktualizací** | Dotaz na GitHub Releases forku; nová verze se stáhne a nainstaluje při příštím spuštění. | `ZkontrolovatAktualizaceSwingWorker`, `StahnoutAktualizaciSwingWorker`, `FConst.LATEST_RELEASE_API_URL` |
| **Beta kanál** | Nabízení testovacích verzí (Nápověda > Nabízet testovací verze). | `BetaKanalAction` |
| **Informace pro hlášení chyby** | Text s verzí, prostředím, posledními událostmi a výkonem kreslení (Nápověda > Zadat problém); jde poslat do issue na GitHubu. V kódu „diagnostika“. | `Diagnostika`, `DiagnostikaAction`, `ZadatProblemAction`, `Vykon` |
| **Dálkové ovládání** | Ovládání běžícího programu přes HTTP jen z tohoto počítače (Soubor > Dálkové ovládání, parametr `--ovladani[=port]`, výchozí port 48321): `/stav`, `/pozice`, `/kes`, `/podklad`, `/popredi`, `/prenacti`, `/vykon`. | `core/ovladani`, `DalkoveOvladani`, `DalkoveOvladaniAction` |
| **Nápověda** | F1 a tlačítka Nápověda otevírají stránky uživatelské wiki na GitHubu. Nápověda > Co je nového ukazuje změny podle verzí. | `NapovedaAction`, `NapovedaWiki`, `CoJeNoveho` |

---

## 4. Obecný technický kód (tw)

| Třídy | Význam |
|---|---|
| `util/index2d`: `Indexator`, `Ctverecnik`, `Sheet`, `Splitenec` | Quadtree: index, vnitřní uzel se 4 potomky (jz, jv, sz, sv), list, výsledek rozdělení. |
| `util/pocitadla`: `Pocitadlo`, `PocitadloMalo`, `PocitadloNula`, `PocitadloRoste`, `PocitadloSystem` | Diagnostické metriky zobrazené v servisním okně. Varianty mají stejné chování a liší se jen skupinou: má být malé (počty objektů, únik paměti), má být nula (fronty), stále roste (kumulativní počty událostí), hodnoty JVM. |
| `util/procak`: `Procak`, `ProcakDispatcher` | Řetěz zpracovatelů s více koly. |
| `util/file`: `KeFile`, `Root`, `FileAndTime`, `DirScanner` | Soubor vztažený ke kořenu skenování (include/exclude, hloubka) s časem změny. „Ke“ pravděpodobně znamená kešoid, obsah je ale obecný. |
| `util/file`: `LamUrl`, `MultiFolder`, `KeyTree` | Last-Modified URL. Překryvný strom souborů z resources a složek na disku (ikony), přípona `.remove` položku odebere. |
| `framework/Atom` | Otevřený výčet: pojmenované hodnoty internované za běhu (`Atom.valueOf`). |
| `util/lang`: `ATimestamp`, `AString0`, `IAtom*` | Hodnotové typy převzaté z tw, v aplikaci se skoro nepoužívají. |
| `framework`: `BeanBag`, `Factory`, `BeanType`, `MultiInjection`, `AfterInjectInit`, `AfterEventReceiverRegistrationInit` | Vlastní DI kontejner nad reflexí: injekce přes metody `inject(X)`, inicializace ve čtyřech fázích. Aplikaci skládá `core/program/Inicializator`. |
| `framework`: `EventManager`, `Event0`, `Model0` | Publish/subscribe podle třídy události přes metody `onEvent(X)`. Odběratelé přes slabé reference. Nový odběratel hned dostane poslední událost. Událost se odesílá jen z EDT. |
| `framework`: `Onoff`, `OnoffEvent0`, `ToggleAction0` | Přepínač v modelu, který při změně vyšle událost. Akce se zaškrtnutím sdílená menu a tlačítkem. |
| `framework`: `MyPreferences`, `Prefe`, `@Preferenceble`, `SouborovePreferences` | Nastavení přes rozhraní Java Preferences, uložené v souboru `data/nastaveni.xml` (ne v registru Windows), struktury přes JavaBeans. Soubor otevírá `core/profile/Nastaveni`. Jména klíčů drží `core/program/FPref`. |
| `util/file`: `Filex`, `BezpecnyZapis`, `Soubory`, `TextovySoubor` | Cesta s příznakem, zda je relativní ke složce programu a zda je aktivní. Zápis souboru přes dočasný soubor a přejmenování. Čtení textu s rozpoznáním kódování. |
| `framework/Dlg` | Dialogy ano/ne, chyba, info, přepsat soubor. |
| `framework/NabidkyPoStartu` | Okna, která program otevře sám (nová verze, doporučení optimalizace, průvodce map, zástupce): po jednom, až není otevřené modální ani blokující okno, a po pauze 3 s. |
| `util/gui`: `CornerLayoutManager`, `ERoh` | Layout: podklad přes celou plochu a komponenty v rozích. |
| `util/exception`: `AExcId`, `FExceptionDumper`, `JErrorDialog` | Výjimky s identifikátorem, dump do složky `data/log/chyby`, dialog „Přehled problémů“. |
| `core/program/JServiceFrame` | Servisní okno s počítadly (menu „Servis…“). |
| `core/lookandfeel`, `LafSupport` | Volba vzhledu Swingu a tématu (menu „Skin“). |
| `plugins/mapy/sprava/JsonCteni` | Malá čtečka JSON (katalog map, popisy vydání). Knihovnu na JSON program nemá. |
| `src/test`: `cz.geokuk.testy`, `cz.geokuk.smoke` | Posluchači testů (nezachycené výjimky, zábrana zaseknutí) a smoke test celého programu nad falešným mapovým serverem. |

---

## 5. Balíky

### 5.1 Původní záměr: malé jádro a vyměnitelné pluginy

Geokuk měl být **obecná, relativně malá aplikace nad mapami**. Vše, co je v `plugins`, mělo jít přidávat a vyměňovat. Pluginem měly být i samotné keše (kešoidy), tedy jádro by o geocachingu vůbec nevědělo.

Refaktoring k tomuto cíli se **nepodařilo dotáhnout**. Názvy balíků (`core`, `plugins`, `api`) proto popisují záměr, ne skutečnost:

- **`core` závisí na pluginech.** `core/program` (hlavní okno, menu, `Inicializator`) importuje všech jedenáct modulů v `plugins`, `core/coord` čtyři, `core/render` tři, `core/ovladani` dva (`kesoid`, `mapy`) a `core/napoveda` jeden (`mapy`).
- **`plugins/kesoid` je centrum.** Závisí na něm všechny ostatní moduly kromě `mrizky` a on sám závisí na `cesty`, `vylety`, `kesoidpopisky` a `refbody`. S každým z nich tvoří cyklus. Na `mapy` už `plugins/kesoid` nezávisí (`mapy` na něm ano). Nový cyklus je mezi `mapy` a `challenge` (`JKachlovnikPresCele` čte `ChallengeModel`).
- **`framework`, `util` a `api` závisí na doméně** (`framework` 22 importy z `core` a `plugins`, `util` sedmi, `api/mapicon` na `plugins/kesoid`).

Skutečně fungující pluginy jsou jen **druhy kešoidů** v `plugins/kesoid/kind/*`. Implementují `KesoidPlugin`. Do verze 5.2.0 se hledaly reflexí; fork knihovnu Reflections odstranil a pluginy jsou vyjmenované v `KesoidPluginManager`, nový druh se tedy zapisuje tam. Je to zmenšený model toho, jak měla vypadat celá aplikace.

Záměru nejblíž je z nových modulů `plugins/challenge`: závisí na `kesoid`, `mapy`, `refbody` a jádru, jeho výpočty (`Vypocet*`, `Vstup*`) na modelu programu nezávisí a zpět na něj sahá jen `core/program` a zmíněný `JKachlovnikPresCele`.

**Ostatní obsah `plugins` jsou moduly podle funkce**, ne vyměnitelné pluginy. Nový kód by ale neměl vzdálit aplikaci od původního záměru:

- nevytvářet nové závislosti `core` → `plugins`, `framework` → doména, `util` → doména,
- nevytvářet nové cykly mezi moduly,
- novou funkci, která jde udělat jako samostatný modul nad rozhraním, tak udělat.

### 5.2 Co který balík skutečně obsahuje

Cesty jsou relativní k `src/main/java/cz/geokuk`.

| Balík | Obsah | Poznámka |
|---|---|---|
| `core/program` | hlavní okno, menu (`Menu`), akce (`Akce`), složení aplikace (`Inicializator`), konstanty (`FConst`, `FPref`), stavový řádek, umístění programu a dat (`UmisteniProgramu`, `KontrolaUmisteni`), dialog Umístění souborů, paměť a zobrazení programu | importuje všechny moduly |
| `core/coordinates` | **souřadnicové systémy**: Mou, Wgs, UTM, Mercator, převody | téměř synonymum s `coord`, obsah jiný |
| `core/coord` | **zobrazení a pohyb po mapě**: `Coord`, pozice, výřez, zoom, posun, záměrný kříž; navíc mapové akce (`OpenStreetViewAction`, `NastavMapuCeskaAction`) | |
| `core/render` | render (obrázek, KMZ, OziExplorer, tisk) | anglický název doménového balíku |
| `core/hledani` | obecný rámec hledání (`Hledac0`, `Nalezenec0`) | konkrétní hledání kešoidů je v `plugins/kesoid/hledani` se stejnými názvy tříd bez `0` |
| `core/napoveda` | nápověda (wiki), O programu, Co je nového, kontrola a stažení aktualizací, beta kanál, informace pro hlášení chyby (`Diagnostika`), měření výkonu (`Vykon`) | zprávy uživatelům jsou zrušené |
| `core/onoffline` | online/offline režim | přepínač `OnlineModeAction` je ale v `plugins/mapy/kachle` |
| `core/ovladani` | dálkové ovládání přes HTTP | závisí na `plugins/kesoid` a `plugins/mapy` |
| `core/profile`, `core/lookandfeel` | soubor nastavení (`Nastaveni`), vzhled (Skin) | název `profile` zůstal po ukládání nastavení k programu |
| `framework` | infrastruktura: DI (`BeanBag`), události (`EventManager`), modely (`Model0`), nastavení, slidy a překryvníky | obsahuje i doménu (`PodkladMapSpecificModel0`) |
| `api/mapicon` | kreslení ikon (`Imagant`, `*Drawer`) | **není API**, závisí na `plugins/kesoid` |
| `img` | javový balík s `ImageLoader`, ne složka obrázků | obrázky jsou v resources |
| `util/*` | obecný technický kód (turboware) | `util/file`, `util/gui` a `util/exception` importují `core` |
| `start` | spouštěč přenosné verze (`Start`, `Cds`), balí se zvlášť do `start.jar` | nesmí používat žádné jiné třídy programu |
| `plugins/kesoid` | kešoidy, waypointy, `KesBag`, filtr (`KesoidFilter`, `FilterDefinition`), vykreslení (`JKesoidySlide`, `Tecky`), krátké zprávy (`JToast`), stav vrstev kruhů | centrum aplikace |
| `plugins/kesoid/kind/*` | **skutečné pluginy**: druhy kešoidů (`kes`, `waymark`, `munzee`, `cgp`, `photo`, `simplewaypoint`) | |
| `plugins/kesoid/importek` | načítače (GPX, GeoGet, GSAK, OpenSAK, fotky), typy a stavy zdrojů | **`importek`**: `import` je v Javě klíčové slovo |
| `plugins/kesoid/genetika` | genom, geny, alely, genotyp | |
| `plugins/kesoid/mapicon` | sady ikon, skla, sklivce, zvýraznění | |
| `plugins/kesoid/mvc` | **odkladiště** (54 souborů, ve verzi 5.2.0 jich bylo 36): akce, události, dialogy, `KesoidModel`, nick, parametry GSAKu, přepínače a tabulka zdrojů, limity kreslení, sledování schránky | sem nic nového nepřidávat |
| `plugins/kesoid/filtr` | dialog filtru | třídy filtru samotného jsou v kořeni `plugins/kesoid` |
| `plugins/kesoid/detail` | detail kešoidu | |
| `plugins/kesoid/detailroh` | **detail v rohu** (malá mapa) a záměrný kříž | s kešoidy nesouvisí, jen tu leží |
| `plugins/kesoid/data` | jediný soubor `EKesoidKind` | |
| `plugins/kesoid/hledani` | textové hledání kešoidů, hledání souřadnic | |
| `plugins/kesoidpopisky`, `kesoidkruhy`, `kesoidobsazenost` | popisky, zvýrazňovací kruhy, obsazenost | moduly nad kešoidy, pojmenované prefixem místo vnoření |
| `plugins/mapy` | podklady (`MapyModel`), kachle (`kachle/data`, `kachle/gui`, `kachle/podklady`), offliner (`stahovac`), umístění cache a offline map (`KachleUmisteniSouboru`) | `kachle/data` obsahuje i uživatelské mapy, `kachle/podklady` i cache dlaždic a vykreslování offline map (mapsforge) |
| `plugins/mapy/sprava` | správce offline map: katalog map a vrstev, stahování, stav (`SpravaMapModel`), popup a blok ve stavovém řádku (`sprava/gui`) | |
| `plugins/mapy/pruvodce` | průvodce nastavením map | |
| `plugins/cesty` | cesty: data (`data`), akce rozdělené podle objektu (`akce/bod`, `cesta`, `usek`, `doc`, `soubor`), ukládání, panel cesty | |
| `plugins/cesty/routovani` | vedení úseků po cestách z offline mapy (`Routovac`, `TrasyUseku`) | |
| `plugins/vylety` | výlet (ANO / NE / NEVÍM), pojmenované výlety | |
| `plugins/challenge` | plánovač challenge: definice, výpočty, lišta, popup a kresba na mapě | 84 souborů v jednom balíku, bez podbalíků |
| `plugins/mrizky` | souřadnicové mřížky | |
| `plugins/refbody` | **domov a pojmenované body** | ne referenční bod (ten je v `HlidacReferencnihoBodu`), viz kapitola 7 |
| `plugins/geocoding` | hledání adres (Nominatim), otevření místa v mapách na webu | |

### 5.3 Pojmenování balíků

- Doménové balíky jsou většinou česky (`cesty`, `vylety`, `mrizky`, `genetika`, `filtr`, `podklady`, `stahovac`, `detailroh`, `importek`, `sprava`, `pruvodce`, `routovani`, `ovladani`). Anglicky jsou `coord`, `coordinates`, `render`, `program`, `profile`, `geocoding`, `onoffline`, `start` a v kešoidech `kind`, `data`, `detail`, `mvc`, `mapicon`. `refbody` je napůl. `challenge` je převzatý pojem geocachingu.
- Organizace se mísí: někde podle vrstev uvnitř funkce (`cesty/akce` + `cesty/data`, `mapy/kachle/data|gui|podklady`), jinde ne.
- Pro nové balíky platí pravidla kapitoly 1: doménový balík česky, obecný anglicky.

## 6. Externí formáty a systémy

| Pojem | Význam |
|---|---|
| **GPX** | Standardní XML formát waypointů a tras. Geokuk ho čte (kešoidy) i zapisuje (dokument cest). |
| **Groundspeak** | Rozšíření GPX od geocaching.com s údaji o keši (`groundspeak:cache`). |
| **GeoGet** | Český program na správu keší. Geokuk čte jeho databázi (`*.db3`), GPX rozšíření `gpxg`, `geohome.ini`, zapisuje seznamy GGT a předává mu kešoidy přes schránku. |
| **GGT** | Seznam kódů kešoidů GeoGetu, jeden na řádek. |
| **GSAK** | Program na správu keší. Geokuk čte jeho databázi `sqlite.db3`. |
| **OpenSAK** | Program na správu keší. Geokuk čte jeho databáze `*.db` (schéma do verze 28). |
| **mapsforge** | Formát vektorových offline map (`.map`) a knihovna, která je vykresluje podle tématu (`.xml`, `.zip`). Mapy jsou z download.mapsforge.org, osm.paws.cz nebo OpenAndroMaps. |
| **Nominatim** | Služba hledání adres nad daty OpenStreetMap. |
| **WMS** | Mapová služba, ze které se bere překryv ČÚZK – Stínovaný reliéf. |
| **RÚIAN** | Registr, ze kterého jsou hranice oblastí pro challenge. |
| **Project-GC, Stator** | Dvoje pravidla počítání challenge, která plánovač umí. |
| **GitHub Releases** | Odtud se kontrolují a stahují nové verze programu. |
| **KMZ** | Zabalený KML pro Google Earth a navigace Garmin, výstup renderu. |
| **OziExplorer** | Program pro kalibrované mapy, výstup renderu (obrázek + `.map`). |
| **JTSK, S-42, UTM, MGRS** | Souřadnicové systémy pro zobrazení a zadávání souřadnic. |

---

## 7. Kolize a dvojznačnosti pojmů

Na tato místa si dejte pozor. Při úpravách kódu nebo textů UI je dobré je sjednocovat.

1. **Kachle × dlaždice.** V češtině synonyma, v původním slovníku Geokuku dvě různé věci: kachle je obrázek mapy ze serveru, dlaždice díl renderovaného výstupu. Kód to nedodržoval už ve verzi 5.2.0: offliner („Postahovat dlaždice…“), počítadla („Downloadlé dlaždice“) a `KachleUrlBuilder` říkají *dlaždice* ve významu **kachle**. Fork v tom pokračuje a rozdíl dnes vypadá takto:
   - **Texty pro uživatele** (menu, dialogy, CHANGELOG, README) říkají kachli vždy „dlaždice“: „Postahovat dlaždice…“, „Optimalizace cache dlaždic…“, „dlaždice map“. Slovo kachle se v textech pro uživatele prakticky nevyskytuje, zůstalo v servisních počítadlech (`KachleZiskavac`, `JKachlovnik`) a v logu.
   - **Názvy tříd a balíků z původního kódu** drží kachli: `Ka`, `KaLoc`, `JKachle`, `JKachlovnik*`, `KachleZiskavac`, `KachloDownloader`, `KachleDBManager`, `KachleModel`, `plugins/mapy/kachle`.
   - **Kód z verzí 6.x** používá „dlaždice“ ve významu kachle v komentářích i identifikátorech: `NahledDlazdice`, `ZobrazeneDlazdice`, `Vykon.Velicina.DLAZDICE_ONLINE`, `OptimalizaceDatabaze` (`stare-dlazdice.txt`), komentáře v `plugins/mapy/kachle/podklady` a `kachle/gui`, v `plugins/mapy/sprava`, `plugins/cesty/routovani` a testech (`FalesnyDlazdicovyServer`). Slovo „dlaždice“ je ve zhruba 75 souborech hlavního kódu.
   - **Dlaždice ve významu původního slovníku** (díl renderu) zůstává v `core/render`: `DlazdicovaMetrika`, `DlazdicovaMetrikaXY`, `JNastavovacVelikostiDlazdic`, `kmzMaxDlazdiceX`.
   - U mapsforge se „dlaždice“ říká i jeho vlastním dlaždicím (`Tile`), které se s kachlí kryjí.

   Která z obou podob má platit, není rozhodnuto; tento bod jen zaznamenává stav.
2. **`EVylet` má dvě role:** stav kešoidu ve výletu a práh filtru. Jeho `toString()` vrací texty filtru, takže stav NE se zobrazí jako „Všechny“. Filtr porovnává `ordinal()`, který se ukládá do nastavení, proto se pořadí konstant nesmí měnit.
3. **Výlet × dokument cest.** Dokument cest se v kódu a místy v UI nazývá „výlet“ (`FPref.VYLET_node`, „Můj výlet.gpx“). Stavový řádek už říká „cesty“. Soubor `lovim.ggt` má dvě role: výlet ANO v `data/vylety/` a výchozí cíl exportu cest v adresáři GeoGetu.
4. **Referenční bod × pojmenovaný bod.** Referenční bod je „pozice, jinak střed výřezu“. Balíček s domovem a pojmenovanými body se ale jmenuje `refbody`. Tooltip pojmenovaného bodu už říká „pojmenovaný bod“.
5. **Zhasnout** u kešoidů znamená skrýt podle alely, u mřížek (`ZhasniVsechnyMrizkyAction`) vypnout vrstvu.
6. **Prefix `A`:** potomek `Atom` × hodnotový typ.
7. **`KesoidFilter.isFiltered()`** vrací `true`, když waypoint filtrem **projde** a zůstane zobrazen.
8. **Záměrný kříž.** Kód i UI používají „záměrný“, ne „zaměřovací“.
9. **Kepodr** slibuje obecné třídění waypointů („kešoid poddruh“), ale slouží jen k výběru šablony popisku.
10. **`DEFAULTNI_DOMACI_SOURADNICE`** je ve `VyrezModel` a používá ji i `refbody` a `challenge` jako znak „domov není nastaven“. Ve `VyrezModel` slouží jako výchozí střed výřezu.
11. **`OpenStreetViewAction`** otevírá Google Street View.
12. **`EKaType`** má prefix výčtu, ale je to třída: vedle vestavěných konstant vrací `values()` i uživatelské mapy načtené za běhu.
13. **Offline.** Tři různé věci: online/offline režim (`OnofflineModel`, smí se na síť), offline mapa (podklad z map `.map`) a offliner (hromadné stažení kachlí online podkladu do cache).
14. **Vrstva.** Slide mapového okna, vrstva tématu offline mapy (`VrstvyTematu`), vrstva stahovaná k offline mapě (trasy, vrstevnice, stínování; `KatalogovaVrstva`) a vrstva kruhů nad mapou (`VrstvaNadMapou`).
15. **Zvýraznění.** Zvýrazňovací kruhy (`plugins/kesoidkruhy`), zvýraznění keše barevným čtvercem (`Zvyrazneni`), zvýraznění sektoru nebo políčka v plánovači (`VyberZvyrazneni`, `ZvyrazneniKese`) a obrys mapy ve správci (`ZvyrazneniMapyEvent`).
16. **Zdroj.** Zdroj keší (soubor, databáze; `TypZdroje`), zdroje map v atribuci a v plánovači `ZdrojPravidel` a `ZdrojeNalezu`.
17. **Střed.** Střed výřezu (`moustred`) × střed challenge (`Challenge.Stred`).
18. **Téma.** Téma offline mapy (`TemaOfflineMapy`) × téma vzhledu Swingu (`core/lookandfeel`, menu Skin > Témata).
19. **Kruhy v metrech × jednotkové kruhy.** UI říká „Kruhy v metrech“ (a položku jmenuje podle poloměru), kód `JednotkoveKruhy`.
20. **Lišta × pruh plánovače.** UI a CHANGELOG říkají „lišta“, kód `JPruhPlanovace`, `StavPruhu`.
21. **Aktivní.** Aktivní výlet (`VyletModel`), aktivní (zapnutá) challenge (`ChallengeModel`) a aktivní složka v Umístění souborů (`Filex`).

---

## 8. Překlepy v názvech

Hodí se při hledání v kódu: když hledáte „Groundspeak“, konstanta se jmenuje `GROUNSPEAK_*`.

| Název v kódu | Správně |
|---|---|
| `SouboeCestaAction0` | SouborCestaAction0 |
| `OpenStreatMapUrlBuilder` | OpenStreetMapUrlBuilder |
| `JNastavovecMeritka` | JNastavovacMeritka |
| `InformaceoZdrojichAction` | InformaceOZdrojichAction |
| `XZaknclovanoRazeni` | XZkanclovanoRazeni |
| `XRelativizeDubleDot` | XRelativizeDoubleDot |
| `Kesoid.setVztahx`, `getVztahx` | setVztah, getVztah |
| `getFountTime` / `setFoundTime` | getFoundTime |
| `GROUNSPEAK_*` | GROUNDSPEAK_* |
| `Groundspeak.availaible` | available |
| `PreferencebleProperty` atd. | Preferenceable |
| `BeanBag.registerSigleton` | registerSingleton |
| `JKachleOflinerDialog`, `KachleOflinerAction` | Offliner |

---

## 9. Zastaralý a nedokončený kód

Na tento kód nenavazujte, nebo ho při úpravách dokončete či odstraňte.

- **Grupa** (`Grupa`, `GrupaSym`): označená jako zastaralá, má ji nahradit druh.
- **Kepodr a popisky**: přesun definic popisků do pluginů (12/2019) zůstal nedokončený. `PopiskyPatterns` má pro každý kepodr ručně psané pole a výchozí šablony pluginů se nepoužívají.
- **Mřížky S-42 a JTSK**: jen akce a modely.
- **Profily nastavení**: `PreferencesProfileChangedEvent` se nikdy nevysílá.
- **Mrtvé třídy a pole**: `Imagant.computeInsets`, `KesoidModel.jmenaAlelNaToolbaru` (ukládá se, nikdo nečte). `Gpxg.czkraj`/`czokres` se načítají, ale používají se jen do otisku zdroje (`KliceZdroje`).
- **Duplicity**: čtení formátu GGT je dvakrát (`Ggt` a `VyletPul`), předání kešoidů do GeoGetu přes schránku také dvakrát (`VyletPresClipboardDoGeogetuAction`, `CestyPresClipboardDoGeogetuAction`).
- **Challenge kalendář**: typ `Challenge.Typ.KALENDAR` a `VypocetKalendare` existují, v UI je ale vypnutý (`ChallengeModel.setKalendarVUi` volají jen testy). Volba `ZdrojeNalezu.VSECHNY` je zatím bez účinku.
- **Porušení vrstev**: `framework/PodkladMapSpecificModel0` a `JPresCelePrekryvnik` závisí na doméně, `api/mapicon/Imagant` na `plugins.kesoid`, a `KeFile`, `DirScanner`, `Root` leží v `util/file`, ačkoli je používá jen import kešoidů. Ve forku přibylo: `framework` → `core.napoveda` (`NapovedaAction`, `JPresCelePrekryvnik`) a `core.profile` (`MyPreferences`), `util/file/Filex` → `core.program.FConst`, `util/exception` → `core.napoveda.Diagnostika`, `core.program.UmisteniProgramu` a `PametProgramuAction`, `core/ovladani` a `core/napoveda` → `plugins`, `plugins/mapy` → `plugins/challenge` a `plugins.kesoid.mvc.KesoidModel`. Odkladiště `plugins/kesoid/mvc` narostlo z 36 na 54 souborů.

---

## 10. Jak slovník udržovat

- Nový doménový pojem doplňte do příslušné tabulky kapitoly 3: český název, význam z pohledu uživatele a hlavní třídy.
- Nový obecný kód (anglicky) doplňte do kapitoly 4 jen tehdy, když jeho název sám nestačí.
- Když opravíte kolizi, překlep nebo dokončíte zastaralý kód, upravte kapitoly 7 až 9.
- Význam pojmu ověřujte v kódu, ne podle názvu třídy. Několik názvů v Geokuku slibuje něco jiného, než skutečně dělá (Kepodr, `OpenStreetViewAction`, `isFiltered`).
