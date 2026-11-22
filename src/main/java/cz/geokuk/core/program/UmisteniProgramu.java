package cz.geokuk.core.program;

import java.io.File;

import cz.geokuk.start.Start;

/**
 * Adresář programu a jeho datová složka. Všechno, co program zapisuje, je v datové složce vedle programu, takže rozhoduje jen to, odkud se spustí.
 * Třída nesmí logovat, používá ji i konfigurace logu.
 */
public final class UmisteniProgramu {

	/** Přepíše umístění datové složky, používají ho testy. */
	public static final String DATA_PROPERTY = "geokuk.data";

	/** Složka s {@code geokuk.jar}, při běhu mimo jar aktuální složka. */
	public static final File JAR_DIR;
	public static final boolean JAR_DIR_EXISTUJE;
	/** Složka, kterou uživatel vidí; u přenosného GeoKuku nad podsložkou program. */
	public static final File KOREN;
	public static final File DATA_DIR;
	/** Program běží z balíku {@code GeoKuk.app} pro macOS. */
	public static final boolean APLIKACE_MAC;
	private static final File LOG;

	static {
		final File umisteni = umisteniTrid();
		if (umisteni != null && umisteni.isFile()) {
			JAR_DIR = umisteni.getParentFile();
			JAR_DIR_EXISTUJE = true;
		} else {
			JAR_DIR = new File("").getAbsoluteFile();
			JAR_DIR_EXISTUJE = false;
		}
		KOREN = Start.koren(JAR_DIR);
		APLIKACE_MAC = Start.aplikaceMac(JAR_DIR) != null;
		final String data = System.getProperty(DATA_PROPERTY);
		DATA_DIR = data != null && !data.isEmpty() ? new File(data).getAbsoluteFile() : new File(KOREN, "data");
		LOG = log(DATA_DIR, new File(System.getProperty("user.home")));
	}

	/** Jak se k programu dostane nová Java nebo celý program znovu: zip pro Windows rozbalit, z dmg pro macOS přetáhnout aplikaci. */
	public static String jakNovyBalik() {
		return jakNovyBalik(APLIKACE_MAC);
	}

	static String jakNovyBalik(final boolean mac) {
		return mac ? "z něj přetáhněte GeoKuk.app přes stávající aplikaci" : "rozbalte ho přes stávající složku s programem";
	}

	/** Balík s programem a Javou ke stažení: {@code dmg} pro macOS, jinak {@code zip}. */
	public static String balik() {
		return APLIKACE_MAC ? "dmg" : "zip";
	}

	/** Složka logu a výpisů chyb. */
	public static File log() {
		return LOG;
	}

	/** Log patří do datové složky, když do ní nejde zapisovat, tak do složky .geokuk v domovské složce. */
	static File log(final File data, final File domov) {
		final File log = new File(data, "log");
		if (Start.lzeZapsat(log)) {
			return log;
		}
		return new File(new File(domov, ".geokuk"), "log");
	}

	private static File umisteniTrid() {
		try {
			return new File(UmisteniProgramu.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (final Exception e) {
			return null;
		}
	}

	private UmisteniProgramu() {}
}
