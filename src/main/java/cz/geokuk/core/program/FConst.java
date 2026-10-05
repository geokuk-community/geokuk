package cz.geokuk.core.program;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.jar.Manifest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FConst {

	private static final String NOT_VERSION_I_AM_IN_DEVELOP = "develop";


	public static final boolean ZAKAZAT_PRIPRAVOVANOU_FUNKCIONALITU = false;

	public static final boolean JAR_DIR_EXISTUJE;

	public static final String VERSION;

	public static final boolean I_AM_IN_DEVELOPMENT_ENVIRONMENT;

	public static final File JAR_DIR;

	/** Složka, kterou uživatel vidí; u přenosného GeoKuku nad podsložkou program. */
	public static final File KOREN = UmisteniProgramu.KOREN;

	/** Nastavení, cache, data a logy programu. */
	public static final File DATA_DIR = UmisteniProgramu.DATA_DIR;

	public static final File NASTAVENI_FILE = new File(DATA_DIR, "nastaveni.xml");

	/** Soubor, do kterého staré verze ukládaly nastavení k programu na přání uživatele. */
	public static final File STARE_NASTAVENI_FILE = new File(UmisteniProgramu.JAR_DIR, "geokuk-preferences.xml");

	/** Za adresu se doplní číslo verze. */
	public static final String RELEASE_TAG_URL = "https://github.com/geokuk-community/geokuk/releases/tag/v";
	public static final String LATEST_RELEASE_API_URL = "https://api.github.com/repos/geokuk-community/geokuk/releases/latest";
	public static final String RELEASES_API_URL = "https://api.github.com/repos/geokuk-community/geokuk/releases";
	public static final String RELEASE_DOWNLOAD_URL = "https://github.com/geokuk-community/geokuk/releases/download/";
	public static final String POST_PROBLEM_URL = "https://github.com/geokuk-community/geokuk/issues/new";

	public static final String WEB_PAGE_URL = "https://github.com/geokuk-community/geokuk";

	public static final String WEB_PAGE_WIKI = "http://wiki.geocaching.cz/wiki/Geokuk";

	public static final int MAX_POC_WPT_NA_MAPE = 30000;
	public static final int MAX_POC_TECEK_NA_MAPE = 200_000;

	public static final File HOME_DIR = new File(System.getProperty("user.home"));

	static {
		JAR_DIR = UmisteniProgramu.JAR_DIR;
		JAR_DIR_EXISTUJE = UmisteniProgramu.JAR_DIR_EXISTUJE;
		log.debug("Jar dir: {}", JAR_DIR);
		// versionproperties.
		String version;
		boolean iamindevelopmentenvi;
		try {
			version = atributManifestu("Geokuk-Version");
			iamindevelopmentenvi = false;
		} catch (final IllegalArgumentException e) {
			version = NOT_VERSION_I_AM_IN_DEVELOP;
			iamindevelopmentenvi = true;
		}
		VERSION = version;
		I_AM_IN_DEVELOPMENT_ENVIRONMENT = iamindevelopmentenvi;

	}

	public static final String NL = System.getProperty("line.separator");

	public static void logInit() {
		log.info("GEOKUK " + VERSION);
		log.info("JAR_DIR = " + JAR_DIR);
		log.info("JAR_DIR_EXISTUJE = " + JAR_DIR_EXISTUJE);
		log.info("KOREN = " + KOREN);
		log.info("DATA_DIR = " + DATA_DIR);
		log.info("HOME_DIR = " + HOME_DIR);
		log.info("WEB_PAGE_URL = " + WEB_PAGE_URL);
	}


	/** Atribut z manifestu jaru, který ho má; chybí-li všude, IllegalArgumentException. */
	public static String atributManifestu(final String jmeno) {
		try {
			for (final URL url : Collections.list(FConst.class.getClassLoader().getResources("META-INF/MANIFEST.MF"))) {
				try (InputStream is = url.openStream()) {
					final String hodnota = new Manifest(is).getMainAttributes().getValue(jmeno);
					if (hodnota != null) {
						return hodnota;
					}
				}
			}
		} catch (final IOException e) {
			throw new IllegalArgumentException(jmeno, e);
		}
		throw new IllegalArgumentException(jmeno);
	}
}
