package cz.geokuk.core.program;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;

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
		LOG = log(DATA_DIR, new File(System.getProperty("java.io.tmpdir")));
	}

	/** Složka logu a výpisů chyb. */
	public static File log() {
		return LOG;
	}

	/**
	 * Log patří do datové složky, když do ní nejde zapisovat, tak do složky GeoKuk v dočasné složce systému. Ta může být
	 * sdílená (/tmp), proto se tam jde jen do složky, která patří nám a nezapisují do ní ostatní; jinak do domovské složky.
	 */
	static File log(final File data, final File docasna) {
		final File log = new File(data, "log");
		if (Start.lzeZapsat(log)) {
			return log;
		}
		final File vDocasne = new File(docasna, "GeoKuk");
		if (patriNam(vDocasne)) {
			return new File(vDocasne, "log");
		}
		return new File(new File(System.getProperty("user.home"), ".geokuk"), "log");
	}

	/** Složka je naše: není odkaz, vytvořil ji stejný uživatel, který ji teď používá, a nezapisují do ní ostatní (jen POSIX). */
	static boolean patriNam(final File slozka) {
		final Path adresar = slozka.toPath();
		try {
			if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
				return Start.lzeZapsat(slozka);
			}
			if (!Files.isDirectory(adresar, LinkOption.NOFOLLOW_LINKS)) {
				if (Files.exists(adresar, LinkOption.NOFOLLOW_LINKS)) {
					return false;
				}
				Files.createDirectories(adresar, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
			}
			final Path zkouska = Files.createTempFile(adresar, "vlastnik", ".tmp");
			final UserPrincipal ja;
			try {
				ja = Files.getOwner(zkouska, LinkOption.NOFOLLOW_LINKS);
			} finally {
				Files.deleteIfExists(zkouska);
			}
			return ja.equals(Files.getOwner(adresar, LinkOption.NOFOLLOW_LINKS))
					&& !Files.getPosixFilePermissions(adresar, LinkOption.NOFOLLOW_LINKS).contains(PosixFilePermission.OTHERS_WRITE);
		} catch (final IOException | RuntimeException e) {
			return false;
		}
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
