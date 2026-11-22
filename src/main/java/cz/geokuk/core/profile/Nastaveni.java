package cz.geokuk.core.profile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.SouborovePreferences;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.file.BezpecnyZapis;
import cz.geokuk.util.file.Soubory;
import lombok.extern.slf4j.Slf4j;

/**
 * Nastavení programu v souboru {@link FConst#NASTAVENI_FILE} v datové složce. Při prvním spuštění se jednou převezme nastavení starší verze: ze
 * souboru vedle programu, nebo z Java Preferences (ve Windows registr). Cesty ke složkám a souborům se nepřebírají, platí výchozí složky
 * u programu. Výjimkou jsou datové složky GeoGetu a GSAKu, které patří jiným programům, a obsah výletů (lovim.ggt a tedne.ggt), který se
 * zkopíruje do složky výletů v datové složce.
 */
@Slf4j
public final class Nastaveni {

	private static final long DOCASNY_MAX_STARI_MS = 60_000;
	private static final String UZEL = "geokuk";
	/** Uzly a klíče s cestami starší verze; mířily do původní složky dat, tak se nepřebírají. */
	private static final String UMISTENI_SOUBORU = "umisteniSouboru";
	private static final String VYLET = "vylet";
	private static final String AKTUALNI_SOUBOR = "aktualniSoubor";
	/** Značka v novém nastavení, odkud a s jakým výsledkem se převzalo nastavení starší verze. */
	static final String ZNACKA_PREVODU = UZEL + "/prevod";
	private static final int VERZE_PREVODU = 1;
	static final String VAROVANI_PREVODU = "Nastavení starší verze se nepodařilo převzít, program začíná s výchozím nastavením. Podrobnosti jsou v logu.";
	/** Složky jiných programů; nemíří do původní složky dat, tak se převezmou. */
	private static final List<String> SLOZKY_JINYCH_PROGRAMU = Arrays.asList("geogetDataDir", "gsakDataDir");
	private static final String PRIPONA_AKTIVNI = "_active";
	private static final String PRIPONA_RELATIVNE = "_relativeToProgram";
	/** Klíče výletů ve starším nastavení a jména jejich souborů; výchozí umístění starší verze bylo ve složce geokuk v domovské složce. */
	private static final String[][] VYLETY = { { "anoGgtFile", "lovim.ggt" }, { "neGgtFile", "tedne.ggt" } };
	private static final String STARA_SLOZKA_DAT = "geokuk";
	/** Složka výletů v datové složce, stejná jako KesoidUmisteniSouboru.VYLETY_DIR. */
	private static final String SLOZKA_VYLETU = "vylety";
	/** Výchozí profil nastavení, viz MyPreferences.current(). */
	private static final String PROFIL = "current";
	private static final Map<String, String> JMENA_SLOZEK = new HashMap<>();
	static {
		JMENA_SLOZEK.put("geogetDataDir", "GeoGet");
		JMENA_SLOZEK.put("gsakDataDir", "GSAK");
	}

	/** Kopie obsahu souboru, v testech podvržitelná. */
	interface Kopie {
		void kopiruj(Path zdroj, OutputStream out) throws IOException;
	}

	static Kopie kopie = Files::copy;

	private static SouborovePreferences koren;
	private static String varovani;

	public static synchronized SouborovePreferences koren() {
		if (koren == null) {
			koren = otevri(FConst.NASTAVENI_FILE, FConst.STARE_NASTAVENI_FILE, true, FConst.HOME_DIR);
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
		return otevri(soubor, stary, zRegistru, null);
	}

	/**
	 * @param domov
	 *            domovská složka, ve které měla starší verze výchozí složku dat s výlety; null, když se výlety z výchozího umístění nepřebírají
	 */
	static SouborovePreferences otevri(final File soubor, final File stary, final boolean zRegistru, final File domov) {
		return otevri(soubor, stary, zRegistru ? Preferences::userRoot : null, domov);
	}

	/**
	 * @param registr
	 *            kořen Java Preferences, ze kterého se převezme nastavení starší verze; null, když se z nich nepřebírá. Volá se, jen když se
	 *            opravdu převádí: ve Windows {@link Preferences#userRoot()} založí klíč v registru.
	 */
	static SouborovePreferences otevri(final File soubor, final File stary, final Supplier<Preferences> registr, final File domov) {
		uklidDocasne(soubor);
		if (soubor.isFile() && soubor.length() == 0) {
			// Prázdný soubor (výpadek proudu, antivir) nemá co zachraňovat, platí jako chybějící.
			smaz(soubor);
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
		final SouborovePreferences nove = prevezmiStare(soubor, stary, registr, domov);
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
				smaz(f);
			}
		}
	}

	private static void smaz(final File soubor) {
		try {
			Files.deleteIfExists(soubor.toPath());
		} catch (final IOException e) {
			log.warn("Soubor {} nelze smazat", soubor, e);
		}
	}

	/** Starší odložený soubor se smaže, až když jde soubor odsunout (antivirus ho může držet). */
	private static boolean odloz(final File soubor, final File vadne) {
		final File docasne = new File(soubor.getPath() + ".odkladani");
		Soubory.smaz(docasne);
		if (!soubor.renameTo(docasne)) {
			return false;
		}
		Soubory.smaz(vadne);
		if (docasne.renameTo(vadne)) {
			return true;
		}
		if (!docasne.renameTo(soubor)) {
			log.warn("Soubor {} nejde vrátit z {}", soubor, docasne);
		}
		return false;
	}

	/**
	 * Nové nastavení se sestaví celé z jednoho zdroje. Převod běží v paměti a do nastavení se souborem se přenese až po úspěchu, aby se
	 * neuložil jen částečně převzatý stav; teprve potom se ze stejného zdroje zkopírují výlety.
	 */
	private static SouborovePreferences prevezmiStare(final File soubor, final File stary, final Supplier<Preferences> registr, final File domov) {
		final File slozkaVyletu = new File(soubor.getAbsoluteFile().getParentFile(), SLOZKA_VYLETU);
		boolean chyba = false;
		if (stary != null && stary.isFile()) {
			try {
				final Prevod prevod = new Prevod(slozkaVyletu, domov);
				final SouborovePreferences nacteno = SouborovePreferences.nacti(stary);
				return prevzato(soubor, prevedVPameti(nacteno.node(UZEL), prevod), "soubor", stary.toString(), prevod);
			} catch (final IOException | BackingStoreException | RuntimeException e) {
				log.warn("Nastavení ze souboru {} nelze převzít", stary, e);
				chyba = true;
			}
		}
		if (registr != null) {
			try {
				final Preferences koren = registr.get();
				if (koren.nodeExists(UZEL)) {
					final Prevod prevod = new Prevod(slozkaVyletu, domov);
					return prevzato(soubor, prevedVPameti(koren.node(UZEL), prevod), "registr", "Java Preferences", prevod);
				}
			} catch (final BackingStoreException | RuntimeException e) {
				log.warn("Nastavení z Java Preferences nelze převzít", e);
				chyba = true;
			}
		}
		final SouborovePreferences nove = SouborovePreferences.prazdne(soubor);
		if (chyba) {
			// Značka zůstane i ve výchozím nastavení, aby se stejné selhání neopakovalo při každém spuštění.
			znacka(nove, "zadny", "chyba");
			varovani = VAROVANI_PREVODU;
			log.warn("Převod nastavení starší verze: nic nepřevzato, program začíná s výchozím nastavením");
		}
		return nove;
	}

	private static SouborovePreferences prevedVPameti(final Preferences zdroj, final Prevod prevod) throws BackingStoreException {
		final SouborovePreferences prevedeno = SouborovePreferences.vPameti();
		prevezmi(prevedeno, zdroj, prevod);
		return prevedeno;
	}

	private static SouborovePreferences prevzato(final File soubor, final SouborovePreferences prevedeno, final String zdroj, final String popis,
			final Prevod prevod) throws BackingStoreException {
		final SouborovePreferences nove = SouborovePreferences.prazdne(soubor);
		nove.zkopirujZ(prevedeno);
		zkopirujVylety(prevod);
		final int klicu = pocetKlicu(nove.node(UZEL));
		znacka(nove, zdroj, "ok");
		final Preferences znacka = nove.node(ZNACKA_PREVODU);
		znacka.put("prevzato", String.join("; ", prevod.prevzato));
		znacka.put("neprevzato", String.join("; ", prevod.neprevzato));
		log.info("Převod nastavení starší verze z {}: převzato {} hodnot; převzato: {}; nepřevzato: {}", popis, klicu, prevod.prevzato, prevod.neprevzato);
		return nove;
	}

	private static void znacka(final SouborovePreferences nove, final String zdroj, final String vysledek) {
		final Preferences znacka = nove.node(ZNACKA_PREVODU);
		znacka.putInt("verze", VERZE_PREVODU);
		znacka.put("zdroj", zdroj);
		znacka.put("vysledek", vysledek);
		znacka.put("datum", LocalDate.now().toString());
	}

	private static int pocetKlicu(final Preferences uzel) throws BackingStoreException {
		int pocet = uzel.keys().length;
		for (final String dite : uzel.childrenNames()) {
			pocet += pocetKlicu(uzel.node(dite));
		}
		return pocet;
	}

	/** Kam se při převodu kopírují výlety a kde byla výchozí složka dat starší verze. */
	static final class Prevod {
		final File slozkaVyletu;
		final File domov;
		/** Souhrn pro uživatele, bez cest. */
		final List<String> prevzato = new ArrayList<>();
		final List<String> neprevzato = new ArrayList<>();
		/** Soubory výletů ze zdroje, ze kterého se převzalo nastavení; jméno v nové složce výletů → původní soubor. */
		final Map<String, File> vylety = new HashMap<>();

		Prevod(final File slozkaVyletu, final File domov) {
			this.slozkaVyletu = slozkaVyletu;
			this.domov = domov;
		}
	}

	static void prevezmi(final SouborovePreferences nove, final Preferences zdroj, final Prevod prevod) throws BackingStoreException {
		final SouborovePreferences uzel = (SouborovePreferences) nove.node(UZEL);
		uzel.zkopirujZ(zdroj);
		// Výlety se berou z výchozího profilu, se kterým běží i tato verze (jiný profil -Dprefroot se nepoužívá); uzel se jen čte, aby v registru nevznikl prázdný.
		final String umisteniProfilu = PROFIL + "/" + UMISTENI_SOUBORU;
		najdiVylety(zdroj.nodeExists(umisteniProfilu) ? zdroj.node(umisteniProfilu) : null, prevod);
		vynechCesty(uzel, prevod);
	}

	private static void vynechCesty(final Preferences uzel, final Prevod prevod) throws BackingStoreException {
		if (VYLET.equals(uzel.name())) {
			uzel.remove(AKTUALNI_SOUBOR);
		}
		for (final String dite : uzel.childrenNames()) {
			if (UMISTENI_SOUBORU.equals(dite)) {
				ponechJenSlozkyJinychProgramu(uzel.node(dite), PROFIL.equals(uzel.name()) ? prevod : null);
			} else {
				vynechCesty(uzel.node(dite), prevod);
			}
		}
	}

	/**
	 * Ponechá jen absolutní cesty ke složkám jiných programů. Relativní cesta se ve starší verzi počítala od jiné složky programu, tak se
	 * nepřebírá. Složka, která neexistuje nebo je prázdná, se nepřebírá a najde ji detekce zdrojů; starší verze měla zapnutou výchozí složku
	 * GSAKu pro Windows a sama ji vytvořila i na počítačích, kde GSAK není. Síťová složka se při startu nezkoumá, mohla by start zdržet;
	 * převezme se, jak byla.
	 * Uzel bez ponechaných klíčů se smaže.
	 *
	 * @param prevod
	 *            kam zapsat souhrn převzatých složek; null u jiného než výchozího profilu
	 */
	private static void ponechJenSlozkyJinychProgramu(final Preferences umisteni, final Prevod prevod) throws BackingStoreException {
		for (final String klic : umisteni.keys()) {
			if (!jePrebiranaSlozka(umisteni, klic)) {
				umisteni.remove(klic);
			}
		}
		for (final String slozka : SLOZKY_JINYCH_PROGRAMU) {
			final String cesta = umisteni.get(slozka, null);
			if (cesta != null && !jeSitova(cesta) && jePrazdnaNeboNeni(new File(cesta))) {
				umisteni.remove(slozka);
				umisteni.remove(slozka + PRIPONA_AKTIVNI);
				umisteni.remove(slozka + PRIPONA_RELATIVNE);
				if (prevod != null) {
					prevod.neprevzato.add(JMENA_SLOZEK.get(slozka) + " (složka neexistuje nebo je prázdná)");
				}
				continue;
			}
			if (cesta != null && prevod != null) {
				prevod.prevzato.add(JMENA_SLOZEK.get(slozka) + (umisteni.getBoolean(slozka + PRIPONA_AKTIVNI, true) ? "" : " (vypnuto)"));
			}
		}
		if (umisteni.keys().length == 0 && umisteni.childrenNames().length == 0) {
			umisteni.removeNode();
		}
	}

	private static boolean jeSitova(final String cesta) {
		return cesta.startsWith("\\\\") || cesta.startsWith("//");
	}

	private static boolean jePrazdnaNeboNeni(final File slozka) {
		final String[] obsah = slozka.list();
		return obsah == null || obsah.length == 0;
	}

	private static boolean jePrebiranaSlozka(final Preferences umisteni, final String klic) {
		for (final String slozka : SLOZKY_JINYCH_PROGRAMU) {
			if (klic.equals(slozka) || klic.equals(slozka + PRIPONA_AKTIVNI) || klic.equals(slozka + PRIPONA_RELATIVNE)) {
				return jeAbsolutniCesta(umisteni, slozka);
			}
		}
		return false;
	}

	private static boolean jeAbsolutniCesta(final Preferences umisteni, final String klic) {
		final String cesta = umisteni.get(klic, null);
		// Starší verze použila absolutní cestu i se zaškrtnutým „relativně k programu“.
		return cesta != null && new File(cesta).isAbsolute();
	}

	/** Zapamatuje si soubory výletů starší verze; síťová cesta se při startu nezkoumá, mohla by start zdržet. */
	private static void najdiVylety(final Preferences umisteni, final Prevod prevod) {
		for (final String[] vylet : VYLETY) {
			final String cesta = umisteni == null ? null : umisteni.get(vylet[0], null);
			if (cesta != null && jeSitova(cesta)) {
				prevod.neprevzato.add("výlet " + vylet[1] + " (síťová cesta)");
				continue;
			}
			final File zdroj = staryVylet(umisteni, vylet[0], vylet[1], prevod.domov);
			if (zdroj != null) {
				prevod.vylety.put(vylet[1], zdroj);
			}
		}
	}

	/**
	 * Zkopíruje soubory výletů starší verze do složky výletů. Soubor, který už ve složce výletů je, se nepřepíše; původní soubor zůstane,
	 * může ho dál používat starší verze nebo GeoGet.
	 */
	private static void zkopirujVylety(final Prevod prevod) {
		for (final String[] vylet : VYLETY) {
			final File zdroj = prevod.vylety.get(vylet[1]);
			if (zdroj == null || !zdroj.isFile()) {
				continue;
			}
			final File cil = new File(prevod.slozkaVyletu, vylet[1]);
			if (cil.exists()) {
				continue;
			}
			try {
				// Po pádu uprostřed nezůstane useknutý výlet, který by příští převod už nepřepsal.
				BezpecnyZapis.zapis(cil, out -> kopie.kopiruj(zdroj.toPath(), out));
				prevod.prevzato.add("výlet " + vylet[1]);
				log.info("Převzat výlet {} do {}", zdroj, cil);
			} catch (final IOException | RuntimeException e) {
				prevod.neprevzato.add("výlet " + vylet[1]);
				log.warn("Výlet {} nelze převzít do {}", zdroj, cil, e);
			}
		}
	}

	/** Soubor výletu podle staršího nastavení; bez klíče platí výchozí umístění starší verze, vypnutý nebo relativní se nepřebírá. */
	private static File staryVylet(final Preferences umisteni, final String klic, final String jmeno, final File domov) {
		if (umisteni != null && !umisteni.getBoolean(klic + PRIPONA_AKTIVNI, true)) {
			return null;
		}
		final String cesta = umisteni == null ? null : umisteni.get(klic, null);
		if (cesta == null) {
			return domov == null ? null : new File(new File(domov, STARA_SLOZKA_DAT), jmeno);
		}
		return jeAbsolutniCesta(umisteni, klic) ? new File(cesta) : null;
	}

	private Nastaveni() {}
}
