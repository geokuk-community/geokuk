package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cz.geokuk.core.napoveda.Diagnostika;
import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.file.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 */
@Slf4j
public class MultiNacitac {


	// TODO: Doporučuji přejmenovat na GEOKUK_ROOTDIR_DEF resp. GEOGET_ROOTDIR_DEF. Už dávno nejde jen o jméno souboru/složky. [2016-04-09, Bohusz]
	private static final Root.Def FILE_NAME_REGEX_GEOKUK_DIR = new Root.Def(Integer.MAX_VALUE, Pattern.compile("(?i).*\\.(geokuk|gpx|zip|jpg|raw|tif)"), null);
	private static final Root.Def FILE_NAME_REGEX_GEOGET_DIR = new Root.Def(1, Pattern.compile("(?i).*\\.db3"), Pattern.compile("(?i).*\\.[0-9]{8}\\.db3"));
	private static final Root.Def GSAK_ROOTDIR_DEF = new Root.Def(2, Pattern.compile("sqlite.db3"), null);
	private static final Root.Def OPENSAK_ROOTDIR_DEF = new Root.Def(1, Pattern.compile("(?i).*\\.db"), null);

	private final DirScanner ds;
	private final RegistrStavuZdroju registr;

	private volatile File geogetDir;
	private volatile File gsakDir;
	private volatile File opensakDir;
	private final Set<File> ohlasenePrazdne = Collections.synchronizedSet(new HashSet<>());

	/** Databáze, které při posledním načítání zamykal jiný program; znovu se načítá, až je pustí. */
	private volatile Set<File> zamcene = Collections.emptySet();
	private List<KeFile> posledniSeznam;
	/** Zdroje, jejichž keše jsou v naposledy vráceném (zobrazeném) výsledku. */
	private Set<File> zobrazene = Collections.emptySet();
	/**
	 * Databáze, které jiný program v tomto běhu zamkl, s otiskem souboru z jejich posledního načtení. Import může běžet v několika transakcích a mezi nimi se mohla načíst
	 * rozpracovaná; změnu po dokončení zápisu sken sám nepozná.
	 */
	private final Map<File, String> sledovane = new HashMap<>();
	/** Waypointy zobrazených databází; keše databáze, kterou jiný program zamkne, se z nich převezmou do dalšího výsledku. */
	private Map<File, List<Wpt>> zobrazeneWpty = Collections.emptyMap();
	private InformaceOZdrojich zobrazeneInformace;
	private Genom zobrazenyGenom;

	private final List<Nacitac0> nacitace = new ArrayList<>();
	private final KesoidModel kesoidModel;

	// private static final String CACHE_SUFFIX = ".cache.serialized";

	/**
	 * Checks whether the given file is a ZIP file. Copied from http://www.java2s.com/Code/Java/File-Input-Output/DeterminewhetherafileisaZIPFile.htm
	 */
	private static boolean isZipFile(final File fileToTest) {
		if (fileToTest.isDirectory()) {
			return false;
		}
		if (fileToTest.length() < 4) {
			return false;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(fileToTest)))) {
			final int test = in.readInt();
			return test == 0x504b0304;
		} catch (final IOException e) {
			throw new IllegalArgumentException("The file " + fileToTest + " cannot be checked!", e);
		}
	}

	public MultiNacitac(final KesoidModel kesoidModel) {
		this(kesoidModel, new DirScanner());
	}

	MultiNacitac(final KesoidModel kesoidModel, final DirScanner ds) {
		this(kesoidModel, ds, new RegistrStavuZdroju());
	}

	/** Registr si může předat model, který stav zdrojů rozesílá. */
	MultiNacitac(final KesoidModel kesoidModel, final DirScanner ds, final RegistrStavuZdroju registr) {
		this.registr = registr;
		this.kesoidModel = kesoidModel;
		this.ds = ds;
		nacitace.add(new NacitacGeokuk());
		nacitace.add(new NacitacGpx());
		nacitace.add(new NacitacImageMetadata());
		nacitace.add(new GeogetLoader());
		nacitace.add(new GsakDbLoader(kesoidModel::getGsakParametryNacitani));
		nacitace.add(new OpensakDbLoader());
	}

	public RegistrStavuZdroju getRegistr() {
		return registr;
	}

	static TypZdroje typ(final KeFile f) {
		final Root.Def def = f.root.def;
		if (GSAK_ROOTDIR_DEF.equals(def)) {
			return TypZdroje.GSAK;
		}
		if (OPENSAK_ROOTDIR_DEF.equals(def)) {
			return TypZdroje.OPENSAK;
		}
		return FILE_NAME_REGEX_GEOGET_DIR.equals(def) ? TypZdroje.GEOGET : TypZdroje.GPX;
	}

	/** Velikost souboru i jeho WAL, kde se databáze zapisuje. */
	private static long velikostNaDisku(final File soubor) {
		return soubor.length() + new File(soubor.getPath() + "-wal").length();
	}

	private void prepisRegistr(final List<KeFile> list) {
		final Map<File, KeFile> poSouboru = new LinkedHashMap<>();
		for (final KeFile f : list) {
			poSouboru.put(f.getFile(), f);
		}
		registr.prepis(new ArrayList<>(poSouboru.keySet()), f -> typ(poSouboru.get(f)), f -> poSouboru.get(f).getRelativePath().toString(), kesoidModel::maSeNacist, MultiNacitac::velikostNaDisku);
	}

	public boolean jeZamcena(final File databaze) {
		return zamcene.contains(databaze);
	}

	/** Synchronizované: zrušené načítání může ještě doběhnout, když už začíná další. */
	public synchronized KesBag nacti(final Future<?> future, final Genom genom) throws IOException {
		DatabazeJinehoProgramu.setNacitani(future);
		try {
			return nactiZmeny(future, genom);
		} finally {
			DatabazeJinehoProgramu.setNacitani(null);
		}
	}

	private KesBag nactiZmeny(final Future<?> future, final Genom genom) throws IOException {
		List<KeFile> list = ds.coMamNacist();
		if (!zamcene.isEmpty()) {
			if (!zamcene.stream().allMatch(DatabazeJinehoProgramu::jeZamcena)) {
				ds.nulujLastScaned();
				list = ds.coMamNacist();
			} else if (list != null && bezZamcenych(list).equals(bezZamcenych(posledniSeznam))) {
				return null; // změnila se jen zamčená databáze, jiný program do ní pořád zapisuje
			}
		}
		if (list == null && zmenilaSeSledovana()) {
			ds.nulujLastScaned();
			list = ds.coMamNacist();
		}
		if (list == null) {
			return null;
		}
		posledniSeznam = list;
		prepisRegistr(list);
		ohlasPrazdneSlozky(list);
		final File gsak = gsakDir;
		// Platí čitelnost z doby skenu, pozdější kontrola by mohla vidět složku, která se mezitím vrátila.
		final Set<File> nedostupne = ds.getNedostupne();
		kesoidModel.setNedostupnePriNacitani(nedostupne);
		// Dočasně nedostupná složka (síť, USB) neznamená, že databáze zmizely; známé zůstanou známé.
		if (gsak == null || !nedostupne.contains(gsak)) {
			kesoidModel.zaradGsakDatabaze(databaze(list, GSAK_ROOTDIR_DEF), nedostupne);
		}
		final File opensak = opensakDir;
		if (opensak == null || !nedostupne.contains(opensak)) {
			kesoidModel.zaradOpensakDatabaze(databaze(list, OPENSAK_ROOTDIR_DEF), nedostupne);
		}
		if (kesoidModel.getVsechnyKesoidy() == null) {
			kesoidModel.setNacitaneZdroje(predbezneZdroje(list));
		}
		final KesoidImportBuilder builder = new KesoidImportBuilder(genom, kesoidModel.getGccomNick(), kesoidModel.getProgressModel(), kesoidModel.getKesopidPluginManager());
		builder.init();
		builder.setSledovaneZdroje(f -> !FILE_NAME_REGEX_GEOKUK_DIR.equals(f.root.def));
		final long start = System.currentTimeMillis();
		Diagnostika.zaznamenej("Načítání keší: " + popisSouboru(list));
		final List<String> vadne = new ArrayList<>();
		final Set<File> zamceneTed = new HashSet<>();
		final Map<File, String> otiskyPredCtenim = new HashMap<>();
		for (final KeFile f : list) {
			if (sledovane.containsKey(f.getFile())) {
				otiskyPredCtenim.put(f.getFile(), otisk(f.getFile()));
			}
		}
		final Set<File> prevzate = new HashSet<>();
		boolean nelzePrevzit = false;
		for (final KeFile file : list) {
			if (future != null && future.isCancelled()) {
				break;
			}
			log.debug("Nacitam: " + file);
			final File soubor = file.getFile();
			try {
				if (kesoidModel.maSeNacist(soubor)) {
					registr.zacina(soubor);
					ProgressModel.setSledovacPostupu(procent -> registr.postup(soubor, procent));
				}
				zpracujJedenFile(file, builder, future);
				if (kesoidModel.maSeNacist(soubor) && !(future != null && future.isCancelled())) {
					final int[] pocty = builder.getPoctyCurrent();
					registr.hotovo(soubor, pocty[0], pocty[1]);
				}
			} catch (final DatabazeJinehoProgramu.Zamcena e) {
				registr.cekaNaZapis(soubor);
				log.info(e.getMessage());
				zamceneTed.add(file.getFile());
				if (zobrazene.contains(file.getFile())) {
					// Zamčená uprostřed čtení by měla v builderu část nových keší, ty se se starými míchat nesmí.
					if (genom == zobrazenyGenom && !builder.maWaypointyZe(file.getFile())) {
						builder.prevezmi(file, zobrazeneWpty.getOrDefault(file.getFile(), Collections.emptyList()), zobrazeneInformace == null ? null : zobrazeneInformace.get(file));
						prevzate.add(file.getFile());
					} else {
						nelzePrevzit = true;
					}
				}
			} catch (final Exception e) {
				// znovu se zkusí, až se soubory změní; jinak by se chyba opakovala každých pár vteřin
				FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Problém při čtení souboru " + file);
				vadne.add(file.getFile().getName());
				registr.chyba(soubor, popisChyby(e));
			} finally {
				ProgressModel.setSledovacPostupu(null);
			}
		}

		if (future != null && future.isCancelled()) {
			ds.nulujLastScaned();
			return null;
		}
		builder.done();
		final KesBag bag = builder.getKesBag();
		Diagnostika.zaznamenej("Načteno " + bag.getKesoidy().size() + " kešoidů, " + bag.getWpts().size() + " waypointů za " + (System.currentTimeMillis() - start) / 100 / 10.0 + " s"
				+ (vadne.isEmpty() ? "" : ", chyba v souborech " + vadne) + (zamceneTed.isEmpty() ? "" : ", zamčené " + jmena(zamceneTed)));
		zamcene = zamceneTed;
		kesoidModel.setZamceneDatabaze(jmena(zamceneTed));
		for (final Map.Entry<File, String> e : otiskyPredCtenim.entrySet()) {
			sledovane.put(e.getKey(), e.getValue());
		}
		// Zamčená databáze se načte po uvolnění zámku, otisk se jí zapíše až po přečtení.
		for (final File f : zamceneTed) {
			sledovane.put(f, "");
		}
		// Keše ze zamčené databáze, které nešly převzít, zůstanou zobrazené se vším ostatním, dokud ji jiný program nepustí.
		if (nelzePrevzit && kesoidModel.getVsechnyKesoidy() != null) {
			return null;
		}
		final Set<File> nactene = new HashSet<>();
		for (final KeFile f : list) {
			if (!zamceneTed.contains(f.getFile()) || prevzate.contains(f.getFile())) {
				nactene.add(f.getFile());
			}
		}
		zobrazene = nactene;
		zobrazeneWpty = builder.getWptyPodleZdroje();
		zobrazeneInformace = bag.getInformaceOZdrojich();
		zobrazenyGenom = genom;
		return bag;
	}

	/** Sledovaná databáze se od načtení změnila a jiný program ji už nedrží. */
	private boolean zmenilaSeSledovana() {
		final Set<File> naposledyNactene = new HashSet<>();
		if (posledniSeznam != null) {
			for (final KeFile f : posledniSeznam) {
				naposledyNactene.add(f.getFile());
			}
		}
		// Databáze, která už není ve zdrojích, se nesleduje; chybějící zůstává, až se vrátí.
		sledovane.keySet().removeIf(f -> !naposledyNactene.contains(f) && f.exists());
		for (final Map.Entry<File, String> e : sledovane.entrySet()) {
			// Chybějící databázi (odpojený disk) najde sken, až se vrátí, a sledování pokračuje.
			if (e.getKey().exists() && !zamcene.contains(e.getKey()) && !otisk(e.getKey()).equals(e.getValue()) && !DatabazeJinehoProgramu.jeZamcena(e.getKey())) {
				return true;
			}
		}
		return false;
	}

	/** Čas a velikost databáze i jejího WAL, zápis se projeví aspoň v jednom z nich. */
	private static String otisk(final File databaze) {
		final File wal = new File(databaze.getPath() + "-wal");
		return databaze.lastModified() + ":" + databaze.length() + ":" + wal.lastModified() + ":" + wal.length();
	}

	/** Zdroje, které se právě načítají, aby šly v Přehledu zdrojů vypnout dřív, než se načtou. */
	private static InformaceOZdrojich predbezneZdroje(final List<KeFile> list) {
		final InformaceOZdrojich.Builder zdroje = InformaceOZdrojich.builder();
		for (final KeFile f : list) {
			zdroje.add(f, true);
		}
		return zdroje.done();
	}

	private static Set<File> databaze(final List<KeFile> list, final Root.Def def) {
		return list.stream().filter(f -> def.equals(f.root.def)).map(KeFile::getFile).collect(Collectors.toSet());
	}

	/** Aktivní složka GeoGetu, GSAKu nebo OpenSAKu bez databáze je skoro jistě špatně zadaná, uživatel by jinak jen koukal na prázdnou mapu. */
	private void ohlasPrazdneSlozky(final List<KeFile> list) {
		final Map<File, String[]> slozky = new LinkedHashMap<>();
		if (geogetDir != null) {
			slozky.put(geogetDir, new String[] { "GeoGetu", ".db3" });
		}
		if (gsakDir != null) {
			slozky.put(gsakDir, new String[] { "GSAKu", ".db3" });
		}
		if (opensakDir != null) {
			slozky.put(opensakDir, new String[] { "OpenSAKu", ".db" });
		}
		for (final KeFile f : list) {
			slozky.remove(f.root.dir);
		}
		for (final Map.Entry<File, String[]> e : slozky.entrySet()) {
			if (ohlasenePrazdne.add(e.getKey())) {
				FExceptionDumper.dump(new IOException("V datové složce " + e.getValue()[0] + " \"" + e.getKey() + "\" nejsou žádné databáze (" + e.getValue()[1]
						+ "). Zkontrolujte složku v Soubor > Umístění souborů."), EExceptionSeverity.DISPLAY, "Prázdná datová složka");
			}
		}
	}

	private static String popisChyby(final Exception e) {
		final String zprava = e.getMessage();
		if (zprava == null || zprava.isEmpty()) {
			return "Soubor se nepodařilo přečíst.";
		}
		final int konec = zprava.indexOf('\n');
		return konec < 0 ? zprava : zprava.substring(0, konec);
	}

	private List<KeFile> bezZamcenych(final List<KeFile> seznam) {
		if (seznam == null) {
			return null;
		}
		final List<KeFile> vysledek = new ArrayList<>();
		for (final KeFile f : seznam) {
			if (!zamcene.contains(f.getFile())) {
				vysledek.add(f);
			}
		}
		Collections.sort(vysledek, Comparator.comparing(f -> f.getFile().getPath()));
		return vysledek;
	}

	private static List<String> jmena(final Set<File> soubory) {
		final List<String> vysledek = new ArrayList<>();
		for (final File f : soubory) {
			vysledek.add(DatabazeJinehoProgramu.jmeno(f));
		}
		Collections.sort(vysledek);
		return vysledek;
	}

	public static boolean jeCitelnaSlozka(final File slozka) {
		return slozka.isDirectory() && slozka.list() != null;
	}

	/** Počet souborů podle přípony, bez cest (hlášení je veřejné). */
	private static String popisSouboru(final List<KeFile> soubory) {
		final Map<String, Integer> podlePripony = new TreeMap<>();
		for (final KeFile f : soubory) {
			final String jmeno = f.getFile().getName().toLowerCase(Locale.ROOT);
			podlePripony.merge(jmeno.contains(".") ? jmeno.substring(jmeno.lastIndexOf('.') + 1) : "bez přípony", 1, Integer::sum);
		}
		return soubory.size() + " souborů " + podlePripony;
	}

	// TODO Proč jsou tu ty File parametry, když máme k dispozici kesoidModel, odkud se jejich hodnoty vždy berou? [2016-04-09, Bohusz]
	public void setRootDirs(final boolean prenacti, final File kesDir, final File geogetDir, final File gsakDir, final Set<File> vynechane) {
		setRootDirs(prenacti, kesDir, geogetDir, gsakDir, null, vynechane);
	}

	public void setRootDirs(final boolean prenacti, final File kesDir, final File geogetDir, final File gsakDir, final File opensakDir, final Set<File> vynechane) {
		this.geogetDir = geogetDir;
		this.gsakDir = gsakDir;
		this.opensakDir = opensakDir;
		final List<Root> roots = new ArrayList<>();
		if (kesDir != null) {
			roots.add(new Root(kesDir, FILE_NAME_REGEX_GEOKUK_DIR, vynechane));
		}
		if (geogetDir != null) {
			roots.add(new Root(geogetDir, FILE_NAME_REGEX_GEOGET_DIR));
		}
		if (gsakDir != null) {
			roots.add(new Root(gsakDir, GSAK_ROOTDIR_DEF));
		}
		if (opensakDir != null) {
			roots.add(new Root(opensakDir, OPENSAK_ROOTDIR_DEF));
		}
		ds.seRootDirs(prenacti, roots.toArray(new Root[roots.size()]));
	}

	/**
	 * @param kefile
	 * @param builder
	 * @param future
	 * @throws IOException
	 */
	/** Cizí soubor s naší příponou jen přeskočíme, není to chyba uživatele. Databázi, kterou nejde přečíst, ale ohlásíme. */
	private boolean umiNacist(final Nacitac0 nacitac, final File file) {
		try {
			return nacitac.umiNacist(file);
		} catch (final Exception e) {
			if (DatabazeJinehoProgramu.jeZamcena(e)) {
				throw new DatabazeJinehoProgramu.Zamcena(file, e);
			}
			final String popis = DatabazeJinehoProgramu.popisChyby(file, e);
			if (popis != null) {
				throw new RuntimeException(popis, e);
			}
			log.warn("Soubor {} nelze rozpoznat, přeskakuji: {}", file, e.toString());
			return false;
		}
	}

	private void zpracujJedenFile(final KeFile kefile, final KesoidImportBuilder builder, final Future<?> future) throws IOException {
		final File file = kefile.getFile();
		if (isZipFile(file)) {
			try (ZipFile zipFile = new ZipFile(file)) {
				final boolean nacitat = kesoidModel.maSeNacist(kefile);
				for (final Enumeration<? extends ZipEntry> en = zipFile.entries(); en.hasMoreElements();) {
					final ZipEntry entry = en.nextElement();
					for (final Nacitac0 nacitac : nacitace) {
						builder.setCurrentlyLoading(kefile, nacitat);
						if (nacitat && nacitac.umiNacist(entry)) {
							nacitac.nactiBezVyjimky(zipFile, entry, builder, future, kesoidModel.getProgressModel());
						}
					}
				}
			}
		} else {
			for (final Nacitac0 nacitac : nacitace) {
				final boolean nacitat = kesoidModel.maSeNacist(kefile);
				builder.setCurrentlyLoading(kefile, nacitat);
				if (nacitat && umiNacist(nacitac, file)) {
					nacitac.nactiBezVyjimky(file, builder, future, kesoidModel.getProgressModel());
				}
			}
		}
	}
}
