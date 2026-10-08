package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.framework.Progressor;
import cz.geokuk.framework.ProgressorInputStream;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.lang.FString;

/**
 * @author Martin Veverka
 *
 */
public abstract class Nacitac0 {

	protected static final String PREFIX_USERDEFINOANYCH_GENU = "geokuk_";
	static Pattern osetriCislo = Pattern.compile("[^0-9]");

	private static final ThreadLocal<String> POPIS_PRUBEHU = new ThreadLocal<>();

	/** Průběhy založené ve volajícím vláknu ukážou místo plné cesty krátký popis zdroje; null popis zruší. */
	static void setPopisPrubehu(final String popis) {
		if (popis == null) {
			POPIS_PRUBEHU.remove();
		} else {
			POPIS_PRUBEHU.set(popis);
		}
	}

	/** Průběh čtení zdroje: ve stavovém řádku krátký popis, plná cesta v bublině. */
	protected static Progressor zahajPrubeh(final ProgressModel aProgressModel, final int max, final String cesta) {
		return aProgressModel.start(max, popisPrubehu(cesta), cesta);
	}

	private static String popisPrubehu(final String cesta) {
		final String popis = POPIS_PRUBEHU.get();
		return popis != null ? popis : cesta;
	}

	/** Volná halda v bajtech, v testu jde podvrhnout. */
	static LongSupplier volnaPamet = () -> {
		System.gc();
		final Runtime r = Runtime.getRuntime();
		return r.maxMemory() - (r.totalMemory() - r.freeMemory());
	};

	/** Selhala jen obří alokace kvůli vadnému souboru; když je halda opravdu plná, import má selhat celý. */
	static boolean jeMaloPameti() {
		return volnaPamet.getAsLong() < Runtime.getRuntime().maxMemory() / 4;
	}

	protected String intern(final String aString) {
		return FString.intern(aString);
	}

	protected abstract void nacti(File file, IImportBuilder builder, Future<?> future, ProgressModel aProgressModel) throws IOException;

	protected abstract void nacti(ZipFile zipFile, ZipEntry zipEntry, IImportBuilder builder, Future<?> future, ProgressModel aProgressModel) throws IOException;

	protected final void nactiBezVyjimky(final File file, final IImportBuilder builder, final Future<?> future, final ProgressModel aProgressModel) {
		try {
			try {
				nacti(file, builder, future, aProgressModel);
			} catch (final IOException e) {
				final String popis = DatabazeJinehoProgramu.popisChyby(file, e);
				throw new RuntimeException(popis != null ? popis : "Chyba při čtení \"" + file + "\"", e);
			}
		} catch (final Exception e) {
			if (DatabazeJinehoProgramu.jeZamcena(e)) {
				throw new DatabazeJinehoProgramu.Zamcena(file, e);
			}
			FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Problém při načítání keší, ostatní soubory se načtou");
		} catch (final StackOverflowError | OutOfMemoryError e) {
			// Poškozený soubor (třeba EXIF fotky) nesmí ukončit načítání ostatních.
			if (e instanceof OutOfMemoryError && jeMaloPameti()) {
				throw e;
			}
			FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Soubor \"" + file + "\" je asi poškozený, ostatní soubory se načtou");
		}
	}

	protected final void nactiBezVyjimky(final ZipFile zipFile, final ZipEntry zipEntry, final IImportBuilder builder, final Future<?> future, final ProgressModel aProgressModel) {
		try {
			try {
				nacti(zipFile, zipEntry, builder, future, aProgressModel);
			} catch (final IOException e) {
				throw new RuntimeException("Chyba při čtení \"" + zipEntry + "\"", e);
			}
		} catch (final Exception e) {
			FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Problém při načítání keší, ostatní soubory se načtou");
		} catch (final StackOverflowError | OutOfMemoryError e) {
			if (e instanceof OutOfMemoryError && jeMaloPameti()) {
				throw e;
			}
			FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Soubor \"" + zipEntry + "\" je asi poškozený, ostatní soubory se načtou");
		}
	}

	/**
	 * @param aString
	 * @return
	 */
	protected int parseCislo(String s) {
		s = osetriCislo.matcher(s).replaceAll("").trim();
		if (s.length() == 0) {
			return 0;
		}
		try {
			return Integer.parseInt(s);
		} catch (final NumberFormatException e) {
			FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, "Neplatné číslo \"" + s + "\" v hodnocení nebo BestOf");
			return 0; // je to španě, vrátíme nuli
		}
	}

	protected InputStream wrapByProgressor(final InputStream istm, final String sourceName, final ProgressModel aProgressModel) {
		return new BufferedInputStream(new ProgressorInputStream(aProgressModel, popisPrubehu(sourceName), sourceName, istm));
	}

	abstract boolean umiNacist(File file);

	/** Jména waypointů zdroje bez čtení ostatních údajů, aby šel předem poznat překryv s jinými zdroji; null, když to načítač neumí. */
	Collection<String> jmenaPredem(final File file) throws SQLException {
		return null;
	}

	abstract boolean umiNacist(ZipEntry zipEntry);
}
