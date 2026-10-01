package cz.geokuk.core.program;

import java.io.File;

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
	public static final File DATA_DIR;

	static {
		final File umisteni = umisteniTrid();
		if (umisteni != null && umisteni.isFile()) {
			JAR_DIR = umisteni.getParentFile();
			JAR_DIR_EXISTUJE = true;
		} else {
			JAR_DIR = new File("").getAbsoluteFile();
			JAR_DIR_EXISTUJE = false;
		}
		final String data = System.getProperty(DATA_PROPERTY);
		DATA_DIR = data != null && !data.isEmpty() ? new File(data).getAbsoluteFile() : new File(JAR_DIR, "data");
	}

	public static File log() {
		return new File(DATA_DIR, "log");
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
