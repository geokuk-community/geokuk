package cz.geokuk.core.program;

import java.io.File;

import com.jcabi.manifests.Manifests;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FConst {

	private static final String NOT_VERSION_I_AM_IN_DEVELOP = "develop";


	public static final boolean ZAKAZAT_PRIPRAVOVANOU_FUNKCIONALITU = false;

	public static final boolean JAR_DIR_EXISTUJE;

	public static final String VERSION;

	public static final boolean I_AM_IN_DEVELOPMENT_ENVIRONMENT;

	public static final File JAR_DIR;

	public static final File PREFERENCES_FILE;

	public static final String LATEST_RELEASE_URL = "https://github.com/geokuk-community/geokuk/releases/latest";
	public static final String LATEST_RELEASE_API_URL = "https://api.github.com/repos/geokuk-community/geokuk/releases/latest";
	public static final String RELEASES_API_URL = "https://api.github.com/repos/geokuk-community/geokuk/releases";
	public static final String RELEASE_DOWNLOAD_URL = "https://github.com/geokuk-community/geokuk/releases/download/";
	public static final String MAPY_KONFIGURACE_URL = "https://raw.githubusercontent.com/geokuk-community/geokuk/main/src/main/resources/mapy.properties";
	public static final String POST_PROBLEM_URL = "https://github.com/geokuk-community/geokuk/issues/new";

	public static final String WEB_PAGE_URL = "https://github.com/geokuk-community/geokuk";

	public static final String WEB_PAGE_WIKI = "  http://wiki.geocaching.cz/wiki/Geokuk";

	public static final int MAX_POC_WPT_NA_MAPE = 30000;

	public static final File HOME_DIR = new File(System.getProperty("user.home"));

	static {
		final File umisteni = umisteniTrid();
		if (umisteni != null && umisteni.isFile()) { // je to z jaru
			JAR_DIR = umisteni.getParentFile();
			JAR_DIR_EXISTUJE = true;
		} else {
			JAR_DIR = new File("").getAbsoluteFile();
			JAR_DIR_EXISTUJE = false;
		}
		log.debug("Jar dir: {}", JAR_DIR);
		// versionproperties.
		String version;
		boolean iamindevelopmentenvi;
		try {
			version = Manifests.read("Geokuk-Version");
			iamindevelopmentenvi = false;
		} catch (final IllegalArgumentException e) {
			version = NOT_VERSION_I_AM_IN_DEVELOP;
			iamindevelopmentenvi = true;
		}
		VERSION = version;
		I_AM_IN_DEVELOPMENT_ENVIRONMENT = iamindevelopmentenvi;
		// preferenčník
		PREFERENCES_FILE = new File(JAR_DIR, "geokuk-preferences.xml");

	}

	public static final String NL = System.getProperty("line.separator");

	private static File umisteniTrid() {
		try {
			return new File(FConst.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (final Exception e) {
			log.warn("Nelze zjistit umístění programu.", e);
			return null;
		}
	}

	public static void logInit() {
		log.info("GEOKUK " + VERSION);
		log.info("JAR_DIR = " + JAR_DIR);
		log.info("JAR_DIR_EXISTUJE = " + JAR_DIR_EXISTUJE);
		log.info("HOME_DIR = " + HOME_DIR);
		log.info("WEB_PAGE_URL = " + WEB_PAGE_URL);
	}

}
