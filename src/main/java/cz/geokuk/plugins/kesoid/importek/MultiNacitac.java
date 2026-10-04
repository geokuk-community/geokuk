package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cz.geokuk.core.napoveda.Diagnostika;
import cz.geokuk.plugins.kesoid.KesBag;
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

	private final DirScanner ds;

	private volatile File geogetDir;
	private volatile File gsakDir;
	private final Set<File> ohlasenePrazdne = Collections.synchronizedSet(new HashSet<>());

	/** Databáze, které při posledním načítání zamykal jiný program; znovu se načítá, až je pustí. */
	private volatile Set<File> zamcene = Collections.emptySet();
	private List<KeFile> posledniSeznam;

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
		this.kesoidModel = kesoidModel;
		this.ds = ds;
		nacitace.add(new NacitacGeokuk());
		nacitace.add(new NacitacGpx());
		nacitace.add(new NacitacImageMetadata());
		nacitace.add(new GeogetLoader());
		nacitace.add(new GsakDbLoader(kesoidModel::getGsakParametryNacitani));
	}

	public boolean jeZamcena(final File databaze) {
		return zamcene.contains(databaze);
	}

	public KesBag nacti(final Future<?> future, final Genom genom) throws IOException {
		List<KeFile> list = ds.coMamNacist();
		if (!zamcene.isEmpty()) {
			if (zamcene.stream().noneMatch(DatabazeJinehoProgramu::jeZamcena)) {
				ds.nulujLastScaned();
				list = ds.coMamNacist();
			} else if (list != null && bezZamcenych(list).equals(bezZamcenych(posledniSeznam))) {
				return null; // změnila se jen zamčená databáze, jiný program do ní pořád zapisuje
			}
		}
		if (list == null) {
			return null;
		}
		posledniSeznam = list;
		ohlasPrazdneSlozky(list);
		final File gsak = gsakDir;
		// Platí čitelnost z doby skenu, pozdější kontrola by mohla vidět složku, která se mezitím vrátila.
		final Set<File> nedostupne = ds.getNedostupne();
		kesoidModel.setNedostupnePriNacitani(nedostupne);
		// Dočasně nedostupná složka (síť, USB) neznamená, že databáze zmizely; známé zůstanou známé.
		if (gsak == null || !nedostupne.contains(gsak)) {
			kesoidModel.zaradGsakDatabaze(list.stream().filter(f -> GSAK_ROOTDIR_DEF.equals(f.root.def)).map(KeFile::getFile).collect(Collectors.toSet()), nedostupne);
		}
		final KesoidImportBuilder builder = new KesoidImportBuilder(genom, kesoidModel.getGccomNick(), kesoidModel.getProgressModel(), kesoidModel.getKesopidPluginManager());
		builder.init();
		final long start = System.currentTimeMillis();
		Diagnostika.zaznamenej("Načítání keší: " + popisSouboru(list));
		final List<String> vadne = new ArrayList<>();
		final Set<File> zamceneTed = new HashSet<>();
		for (final KeFile file : list) {
			log.debug("Nacitam: " + file);
			try {
				zpracujJedenFile(file, builder, future);
			} catch (final DatabazeJinehoProgramu.Zamcena e) {
				log.info(e.getMessage());
				zamceneTed.add(file.getFile());
			} catch (final Exception e) {
				// znovu se zkusí, až se soubory změní; jinak by se chyba opakovala každých pár vteřin
				FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Problém při čtení souboru " + file);
				vadne.add(file.getFile().getName());
			}
		}

		builder.done();
		final KesBag bag = builder.getKesBag();
		Diagnostika.zaznamenej("Načteno " + bag.getKesoidy().size() + " kešoidů, " + bag.getWpts().size() + " waypointů za " + (System.currentTimeMillis() - start) / 100 / 10.0 + " s"
				+ (vadne.isEmpty() ? "" : ", chyba v souborech " + vadne) + (zamceneTed.isEmpty() ? "" : ", zamčené " + jmena(zamceneTed)));
		zamcene = zamceneTed;
		// Dokud je některá databáze zamčená, zůstane zobrazené, co už je načtené.
		return zamceneTed.isEmpty() || kesoidModel.getVsechnyKesoidy() == null ? bag : null;
	}

	/** Aktivní složka GeoGetu nebo GSAKu bez databáze je skoro jistě špatně zadaná, uživatel by jinak jen koukal na prázdnou mapu. */
	private void ohlasPrazdneSlozky(final List<KeFile> list) {
		final Map<File, String> slozky = new LinkedHashMap<>();
		if (geogetDir != null) {
			slozky.put(geogetDir, "GeoGetu");
		}
		if (gsakDir != null) {
			slozky.put(gsakDir, "GSAKu");
		}
		for (final KeFile f : list) {
			slozky.remove(f.root.dir);
		}
		for (final Map.Entry<File, String> e : slozky.entrySet()) {
			if (ohlasenePrazdne.add(e.getKey())) {
				FExceptionDumper.dump(new IOException("V datové složce " + e.getValue() + " \"" + e.getKey() + "\" nejsou žádné databáze (.db3). Zkontrolujte složku v Soubor > Umístění souborů."),
						EExceptionSeverity.DISPLAY, "Prázdná datová složka");
			}
		}
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
		this.geogetDir = geogetDir;
		this.gsakDir = gsakDir;
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
