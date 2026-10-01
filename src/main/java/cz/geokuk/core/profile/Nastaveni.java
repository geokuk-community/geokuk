package cz.geokuk.core.profile;

import java.io.File;
import java.io.IOException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.SouborovePreferences;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import lombok.extern.slf4j.Slf4j;

/**
 * Nastavení programu v souboru {@link FConst#NASTAVENI_FILE} v datové složce. Při prvním spuštění se jednou převezme nastavení starší verze: ze
 * souboru vedle programu, nebo z Java Preferences (ve Windows registr).
 */
@Slf4j
public final class Nastaveni {

	private static final String UZEL = "geokuk";

	private static SouborovePreferences koren;
	private static String varovani;

	public static synchronized SouborovePreferences koren() {
		if (koren == null) {
			koren = otevri(FConst.NASTAVENI_FILE, FConst.STARE_NASTAVENI_FILE, true);
			koren.ulozitPriUkonceni();
		}
		return koren;
	}

	/** Varování pro uživatele, jednou po zobrazení hlavního okna. */
	public static synchronized String prevzitVarovani() {
		final String v = varovani;
		varovani = null;
		return v;
	}

	/**
	 * Otevře nastavení v souboru. Poškozený soubor nesmí bránit spuštění, proto se odloží stranou a program pokračuje s výchozím nastavením.
	 */
	static SouborovePreferences otevri(final File soubor, final File stary, final boolean zRegistru) {
		if (soubor.isFile()) {
			try {
				return SouborovePreferences.nacti(soubor);
			} catch (final IOException e) {
				final File vadne = new File(soubor.getPath() + ".vadne");
				vadne.delete();
				final boolean odlozeno = soubor.renameTo(vadne);
				varovani = "Nastavení ze souboru " + soubor + " nelze načíst, program pokračuje s výchozím nastavením."
						+ (odlozeno ? "\nPůvodní soubor je uložený jako " + vadne + "." : "");
				FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, varovani);
				return SouborovePreferences.prazdne(soubor);
			}
		}
		final SouborovePreferences nove = prevezmiStare(soubor, stary, zRegistru);
		try {
			nove.ulozHned();
		} catch (final IOException e) {
			// ohlásí kontrola zapisovatelnosti datové složky
		}
		return nove;
	}

	private static SouborovePreferences prevezmiStare(final File soubor, final File stary, final boolean zRegistru) {
		if (stary != null && stary.isFile()) {
			try {
				final SouborovePreferences nacteno = SouborovePreferences.nacti(stary);
				final SouborovePreferences nove = SouborovePreferences.prazdne(soubor);
				((SouborovePreferences) nove.node(UZEL)).zkopirujZ(nacteno.node(UZEL));
				log.info("Převzato nastavení ze souboru {}", stary);
				return nove;
			} catch (final IOException | BackingStoreException | RuntimeException e) {
				log.warn("Nastavení ze souboru {} nelze převzít", stary, e);
			}
		}
		final SouborovePreferences nove = SouborovePreferences.prazdne(soubor);
		if (zRegistru) {
			try {
				final Preferences registr = Preferences.userRoot();
				if (registr.nodeExists(UZEL)) {
					((SouborovePreferences) nove.node(UZEL)).zkopirujZ(registr.node(UZEL));
					log.info("Převzato nastavení z Java Preferences");
				}
			} catch (final BackingStoreException | RuntimeException e) {
				log.warn("Nastavení z Java Preferences nelze převzít", e);
			}
		}
		return nove;
	}

	private Nastaveni() {}
}
