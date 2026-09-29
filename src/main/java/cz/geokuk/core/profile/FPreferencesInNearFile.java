/**
 *
 */
package cz.geokuk.core.profile;

import java.io.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.file.BezpecnyZapis;

/**
 * @author Martin Veverka
 *
 */
public final class FPreferencesInNearFile {

	private static boolean ukladatDoSouboru = false;
	private static String varovani;

	public static void deleteAndSwitchOff() {
		FConst.PREFERENCES_FILE.delete();
		ukladatDoSouboru = false;
	}

	/**
	 * @return the ukladatDoSouboru
	 */
	public static boolean isUkladatDoSouboru() {
		return ukladatDoSouboru;
	}

	/**
	 * Nový soubor dotáhne do preferences, pokud
	 */
	public static void loadNearToProgramIfNewer() {
		final long lastModifiedSoubor = FConst.PREFERENCES_FILE.lastModified();
		final long lastModifiedFromPreferences = MyPreferences.root().getLong("lastModified", 0);
		if (lastModifiedSoubor > 0) {
			if (lastModifiedSoubor > lastModifiedFromPreferences) {
				loadNearToProgram();
			}
			ukladatDoSouboru = true;
		}
	}

	public static File saveNearToProgramAndSwitchOn() {
		final File file = saveNearToProgram();
		ukladatDoSouboru = true;
		return file;
	}

	public static void saveNearToProgramIfShould() {
		if (ukladatDoSouboru) {
			saveNearToProgram();
		}
	}

	/** Varování pro uživatele, jednou po zobrazení hlavního okna. */
	public static String prevzitVarovani() {
		final String v = varovani;
		varovani = null;
		return v;
	}

	private static void loadNearToProgram() {
		varovani = nacti(FConst.PREFERENCES_FILE);
		if (varovani == null) {
			updateLastModified();
			System.out.printf("FPreferencesInNearFile: Nactena vesera nastaveni do souboru \"%s\"\n", FConst.PREFERENCES_FILE);
		}
	}

	/**
	 * Načte nastavení ze souboru. Poškozený soubor nesmí bránit spuštění, proto
	 * se odloží stranou a vrátí se varování pro uživatele; jinak null.
	 */
	static String nacti(final File soubor) {
		try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(soubor))) {
			Preferences.importPreferences(bis);
			return null;
		} catch (final Exception e) {
			final File vadne = new File(soubor.getPath() + ".vadne");
			vadne.delete();
			final boolean odlozeno = soubor.renameTo(vadne);
			final String hlaska = "Nastavení ze souboru " + soubor + " nelze načíst, program pokračuje s výchozím nastavením."
					+ (odlozeno ? "\nPůvodní soubor je uložený jako " + vadne + "." : "");
			FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, hlaska);
			return hlaska;
		}
	}

	private static File saveNearToProgram() {
		try {
			BezpecnyZapis.zapis(FConst.PREFERENCES_FILE, out -> {
				try {
					MyPreferences.root().exportSubtree(out);
				} catch (final BackingStoreException e) {
					throw new IOException(e);
				}
			});
			updateLastModified();
			ukladatDoSouboru = true;
			System.out.printf("FPreferencesInNearFile: Ulozena vesera nastaveni do souboru \"%s\"\n", FConst.PREFERENCES_FILE);
		} catch (final Exception e) {
			throw new RuntimeException("Problem while saving preferences to \"" + FConst.PREFERENCES_FILE + "\"", e);
		}
		return FConst.PREFERENCES_FILE;
	}

	private static void updateLastModified() {
		final long lastModified = FConst.PREFERENCES_FILE.lastModified();
		MyPreferences.root().putLong("lastModified", lastModified);
	}

	private FPreferencesInNearFile() {}

}
