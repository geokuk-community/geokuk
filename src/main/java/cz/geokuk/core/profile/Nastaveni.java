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
 * souboru vedle programu, nebo z Java Preferences (ve Windows registr). Cesty ke složkám a souborům se nepřebírají, platí výchozí složky
 * u programu.
 */
@Slf4j
public final class Nastaveni {

	private static final long DOCASNY_MAX_STARI_MS = 60_000;
	private static final String UZEL = "geokuk";
	/** Uzly a klíče s cestami starší verze; mířily do původní složky dat, tak se nepřebírají. */
	private static final String UMISTENI_SOUBORU = "umisteniSouboru";
	private static final String VYLET = "vylet";
	private static final String AKTUALNI_SOUBOR = "aktualniSoubor";

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
		uklidDocasne(soubor);
		if (soubor.isFile() && soubor.length() == 0) {
			// Prázdný soubor (výpadek proudu, antivir) nemá co zachraňovat, platí jako chybějící.
			soubor.delete();
		}
		if (soubor.isFile()) {
			try {
				return SouborovePreferences.nacti(soubor);
			} catch (final IOException e) {
				final File vadne = new File(soubor.getPath() + ".vadne");
				final boolean odlozeno = odloz(soubor, vadne);
				varovani = "Nastavení ze souboru " + soubor + " nelze načíst, program pokračuje s výchozím nastavením."
						+ (odlozeno ? "\nPůvodní soubor je uložený jako " + vadne + "."
								: "\nPůvodní soubor zůstal beze změny, změny nastavení se při tomto spuštění neuloží.");
				FExceptionDumper.dump(e, EExceptionSeverity.WORKARROUND, varovani);
				// Původní soubor, který nejde odložit, se nepřepíše; zkusí se znovu při příštím spuštění.
				return SouborovePreferences.prazdne(odlozeno ? soubor : new File(soubor.getPath() + ".nove"));
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

	/** Smaže dočasné soubory zápisu, které po pádu zůstaly vedle souboru nastavení; čerstvé nechá, mohl by je právě zapisovat jiný běh. */
	private static void uklidDocasne(final File soubor) {
		final File slozka = soubor.getAbsoluteFile().getParentFile();
		final String predpona = soubor.getName() + ".";
		final File[] soubory = slozka == null ? null : slozka.listFiles();
		if (soubory == null) {
			return;
		}
		final long hranice = System.currentTimeMillis() - DOCASNY_MAX_STARI_MS;
		for (final File f : soubory) {
			final String jmeno = f.getName();
			if (f.isFile() && jmeno.startsWith(predpona) && jmeno.endsWith(".tmp") && f.lastModified() < hranice) {
				f.delete();
			}
		}
	}

	/** Starší odložený soubor se smaže, až když jde soubor odsunout (antivirus ho může držet). */
	private static boolean odloz(final File soubor, final File vadne) {
		final File docasne = new File(soubor.getPath() + ".odkladani");
		docasne.delete();
		if (!soubor.renameTo(docasne)) {
			return false;
		}
		vadne.delete();
		if (docasne.renameTo(vadne)) {
			return true;
		}
		docasne.renameTo(soubor);
		return false;
	}

	private static SouborovePreferences prevezmiStare(final File soubor, final File stary, final boolean zRegistru) {
		if (stary != null && stary.isFile()) {
			try {
				final SouborovePreferences nacteno = SouborovePreferences.nacti(stary);
				final SouborovePreferences nove = SouborovePreferences.prazdne(soubor);
				prevezmi(nove, nacteno.node(UZEL));
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
					prevezmi(nove, registr.node(UZEL));
					log.info("Převzato nastavení z Java Preferences");
				}
			} catch (final BackingStoreException | RuntimeException e) {
				log.warn("Nastavení z Java Preferences nelze převzít", e);
			}
		}
		return nove;
	}

	static void prevezmi(final SouborovePreferences nove, final Preferences zdroj) throws BackingStoreException {
		final SouborovePreferences uzel = (SouborovePreferences) nove.node(UZEL);
		uzel.zkopirujZ(zdroj);
		vynechCesty(uzel);
	}

	private static void vynechCesty(final Preferences uzel) throws BackingStoreException {
		if (VYLET.equals(uzel.name())) {
			uzel.remove(AKTUALNI_SOUBOR);
		}
		for (final String dite : uzel.childrenNames()) {
			if (UMISTENI_SOUBORU.equals(dite)) {
				uzel.node(dite).removeNode();
			} else {
				vynechCesty(uzel.node(dite));
			}
		}
	}

	private Nastaveni() {}
}
