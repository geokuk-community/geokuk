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
		final String data = System.getProperty(DATA_PROPERTY);
		DATA_DIR = data != null && !data.isEmpty() ? new File(data).getAbsoluteFile() : new File(KOREN, "data");
		LOG = log(DATA_DIR, new File(System.getProperty("user.home")));
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
