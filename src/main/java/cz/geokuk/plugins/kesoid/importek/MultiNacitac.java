package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.util.*;
import java.util.concurrent.Future;
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

	/** Zamčení se ohlásí jednou, ne při každém dalším pokusu o načtení. */
	private volatile boolean hlasenoZamceni;

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
		this.kesoidModel = kesoidModel;
		ds = new DirScanner();
		nacitace.add(new NacitacGeokuk());
		nacitace.add(new NacitacGpx());
		nacitace.add(new NacitacImageMetadata());
		nacitace.add(new GeogetLoader());
		nacitace.add(new GsakDbLoader(kesoidModel::getGsakParametryNacitani));
	}

	public List<KeFile> gsakSoubory(final Filex aDataDir) {
		return ds.scan(new Root(aDataDir.getFile(), GSAK_ROOTDIR_DEF));
	}

	public KesBag nacti(final Future<?> future, final Genom genom) throws IOException {
		final List<KeFile> list = ds.coMamNacist();
		if (list == null) {
			return null;
		}
		ohlasPrazdneSlozky(list);
		final KesoidImportBuilder builder = new KesoidImportBuilder(genom, kesoidModel.getGccomNick(), kesoidModel.getProgressModel(), kesoidModel.getKesopidPluginManager());
		builder.init();
		final long start = System.currentTimeMillis();
		Diagnostika.zaznamenej("Načítání keší: " + popisSouboru(list));
		final List<String> vadne = new ArrayList<>();
		final List<String> zamcene = new ArrayList<>();
		for (final KeFile file : list) {
			log.debug("Nacitam: " + file);
			try {
				zpracujJedenFile(file, builder, future);
			} catch (final DatabazeJinehoProgramu.Zamcena e) {
				zamcene.add(file.getFile().getName());
				if (!hlasenoZamceni) {
					hlasenoZamceni = true;
					FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Zamčená databáze " + file);
				}
			} catch (final Exception e) {
				// znovu se zkusí, až se soubory změní; jinak by se chyba opakovala každých pár vteřin
				FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Problém při čtení souboru " + file);
				vadne.add(file.getFile().getName());
			}
		}

		builder.done();
		final KesBag bag = builder.getKesBag();
		Diagnostika.zaznamenej("Načteno " + bag.getKesoidy().size() + " kešoidů, " + bag.getWpts().size() + " waypointů za " + (System.currentTimeMillis() - start) / 100 / 10.0 + " s"
				+ (vadne.isEmpty() ? "" : ", chyba v souborech " + vadne) + (zamcene.isEmpty() ? "" : ", zamčené " + zamcene));

		if (zamcene.isEmpty()) {
			hlasenoZamceni = false;
			return bag;
		}
		// Zamčenou databázi zkusíme při příštím skenu znovu a do té doby necháme zobrazené, co už je načtené.
		ds.nulujLastScaned();
		return kesoidModel.getVsechnyKesoidy() == null ? bag : null;
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
