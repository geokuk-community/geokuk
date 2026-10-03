package cz.geokuk.core.program;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import cz.geokuk.core.napoveda.VerzeJavy;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;
import cz.geokuk.start.Start;

/** Upozornění na nevhodné umístění přenosného programu a na starou přibalenou Javu, ukazují se po zobrazení hlavního okna. */
public final class KontrolaUmisteni {

	static final String DOPORUCENE_UMISTENI = "C:\\GeoKuk";

	private static final Pattern SYNCHRONIZOVANA = Pattern.compile("(?i)onedrive.*|dropbox|google ?drive|my drive|můj disk|icloud ?drive.*");
	private static final String UPOZORNENO_SYNC_value = "upozornenoSynchronizovana";
	private static final String UPOZORNENO_JAVA_value = "upozornenoJava";
	private static final String UPOZORNENO_STARA_DATA_value = "upozornenoStaraData";
	private static final Pattern SOUBOR_S_KESEMI = Pattern.compile("(?i).*\\.(gpx|geokuk|zip)");

	private KontrolaUmisteni() {}

	public static void zkontroluj() {
		if (!lzeZapsat(FConst.DATA_DIR)) {
			Dlg.error("Do složky " + FConst.DATA_DIR + " nelze zapisovat, nastavení, cache map ani data se neuloží.\n"
					+ "GeoKuk si všechno ukládá do složky, ze které je spuštěný. Přesuňte celou složku s programem\n"
					+ "na místo, kam smíte zapisovat, třeba do " + DOPORUCENE_UMISTENI + " (ne do Program Files).");
			return;
		}
		pripravSlozky();
		final MyPreferences pref = MyPreferences.current().node(FPref.VSEOBECNE_node);
		final String sluzba = synchronizovanaSluzba(FConst.KOREN, System.getenv());
		if (sluzba != null && !FConst.KOREN.getPath().equals(pref.get(UPOZORNENO_SYNC_value, null))) {
			pref.put(UPOZORNENO_SYNC_value, FConst.KOREN.getPath());
			Dlg.upozorneni("GeoKuk je ve složce, kterou synchronizuje " + sluzba + ":\n" + FConst.KOREN + "\n\n"
					+ "Cache map a nastavení se mění při každém spuštění, synchronizace je bude stále nahrávat\n"
					+ "a soubory, které program právě používá, může poškodit. Ukončete GeoKuk a přesuňte celou složku\n"
					+ "s programem mimo synchronizovanou složku, třeba do " + DOPORUCENE_UMISTENI + ".");
		}
		final Filex kesDir = MyPreferences.current().node(FPref.UMISTENI_SOUBORU_node).getFilex(FPref.KES_DIR_value, KesoidUmisteniSouboru.KES_DIR);
		final String staraData = staraData(new File(FConst.HOME_DIR, "geokuk"), FConst.DATA_DIR, kesDir == null ? null : kesDir.getEffectiveFile());
		if (staraData != null && !pref.getBoolean(UPOZORNENO_STARA_DATA_value, false)) {
			pref.putBoolean(UPOZORNENO_STARA_DATA_value, true);
			Dlg.info(staraData, "Data ze starší verze");
		}
		final String doporucena = VerzeJavy.vlastni().getProperty("doporucena");
		if (VerzeJavy.jePribalena() && VerzeJavy.jeStarsi(VerzeJavy.aktualni(), doporucena) && !doporucena.equals(pref.get(UPOZORNENO_JAVA_value, null))) {
			pref.put(UPOZORNENO_JAVA_value, doporucena);
			Dlg.info("Je k dispozici nový zip s programem GeoKuk s novější Javou (" + doporucena + ", přibalená je " + VerzeJavy.aktualni() + ").\n"
					+ "Program funguje dál a aktualizuje se i se stávající Javou. Novou Javu získáte, když nový zip\n"
					+ "stáhnete z " + FConst.WEB_PAGE_URL + "/releases/latest a rozbalíte přes složku s programem.\n"
					+ "Data a nastavení zůstanou.", "Nová Java");
		}
	}

	/** Prázdné složky, kam uživatel dává vlastní soubory, ať je najde. */
	private static void pripravSlozky() {
		for (final File slozka : Arrays.asList(KesoidUmisteniSouboru.KES_DIR.getFile(), KesoidUmisteniSouboru.IMAGE_MY_DIR.getFile(),
				KesoidUmisteniSouboru.IMAGE_3RDPARTY_DIR.getFile())) {
			slozka.mkdirs();
		}
	}

	/**
	 * Verze 6.0.0 měla keše, cesty, výlety a ikony ve složce {@code geokuk} v domovské složce. Vrátí, co z toho je potřeba zkopírovat do složky
	 * data a do složky s kešemi, nebo null, když nic.
	 */
	static String staraData(final File stara, final File data, final File kesDir) {
		if (!stara.isDirectory()) {
			return null;
		}
		final List<String> co = new ArrayList<>();
		if (kesDir != null && maSouborySKesemi(stara, data, true) && !maSouborySKesemi(kesDir, null, false)) {
			co.add("soubory s kešemi (GPX, .geokuk, zip) ze složky " + stara + " do " + kesDir);
		}
		pridej(co, "cesty", new File(stara, "cesty"), new File(data, "cesty"));
		pridej(co, "vlastní ikony", new File(stara, "imagesMy"), new File(data, "ikony/moje"));
		pridej(co, "ikony ostatních", new File(stara, "images3rdParty"), new File(data, "ikony/ostatni"));
		final File vylety = new File(data, "vylety");
		for (final String ggt : Arrays.asList("lovim.ggt", "tedne.ggt")) {
			final File zdroj = new File(stara, ggt);
			if (zdroj.length() > 0 && !new File(vylety, ggt).isFile()) {
				co.add("výlet " + zdroj + " do " + vylety);
			}
		}
		if (co.isEmpty()) {
			return null;
		}
		return "Ve složce " + stara + " jsou data ze starší verze GeoKuku.\nChcete-li je používat i v této verzi, zkopírujte:\n• " + String.join("\n• ", co)
				+ "\nPak GeoKuk spusťte znovu.";
	}

	private static void pridej(final List<String> co, final String popis, final File zdroj, final File cil) {
		if (maSoubory(zdroj) && !maSoubory(cil)) {
			co.add(popis + " ze složky " + zdroj + " do " + cil);
		}
	}

	private static boolean maSoubory(final File slozka) {
		final File[] soubory = slozka.listFiles();
		return soubory != null && soubory.length > 0;
	}

	/** Soubory s kešemi ve složce i v podsložkách, bez složky data (a složky s programem); u staré složky navíc bez cest a ikon. */
	private static boolean maSouborySKesemi(final File slozka, final File data, final boolean stara) {
		final File[] soubory = slozka.listFiles();
		if (soubory == null) {
			return false;
		}
		for (final File f : soubory) {
			if (f.isFile() && SOUBOR_S_KESEMI.matcher(f.getName()).matches()) {
				return true;
			}
			final boolean vynechat = stara && Arrays.asList("cesty", "imagesMy", "images3rdParty").contains(f.getName()) || data != null && jeUvnitr(data, f);
			if (f.isDirectory() && !vynechat && maSouborySKesemi(f, data, false)) {
				return true;
			}
		}
		return false;
	}

	private static boolean jeUvnitr(final File soubor, final File slozka) {
		final File cil = slozka.getAbsoluteFile();
		for (File f = soubor.getAbsoluteFile(); f != null; f = f.getParentFile()) {
			if (f.equals(cil)) {
				return true;
			}
		}
		return false;
	}

	static boolean lzeZapsat(final File slozka) {
		return Start.lzeZapsat(slozka);
	}

	/** Jméno služby, která složku synchronizuje, nebo null. */
	static String synchronizovanaSluzba(final File slozka, final Map<String, String> prostredi) {
		final String cesta = slozka.getAbsolutePath();
		for (final String promenna : Arrays.asList("OneDrive", "OneDriveConsumer", "OneDriveCommercial")) {
			final String koren = prostredi.get(promenna);
			if (koren != null && !koren.isEmpty() && jePod(cesta, koren)) {
				return "OneDrive";
			}
		}
		for (File f = slozka.getAbsoluteFile(); f != null; f = f.getParentFile()) {
			final String jmeno = f.getName();
			if (SYNCHRONIZOVANA.matcher(jmeno).matches()) {
				final String male = jmeno.toLowerCase(Locale.ROOT);
				if (male.startsWith("onedrive")) {
					return "OneDrive";
				}
				if (male.startsWith("dropbox")) {
					return "Dropbox";
				}
				if (male.startsWith("icloud")) {
					return "iCloud";
				}
				return "Google Disk";
			}
		}
		return null;
	}

	private static boolean jePod(final String cesta, final String promenna) {
		final String koren = new File(promenna).getAbsolutePath();
		final String k = koren.endsWith(File.separator) ? koren : koren + File.separator;
		return cesta.equalsIgnoreCase(koren) || cesta.toLowerCase(Locale.ROOT).startsWith(k.toLowerCase(Locale.ROOT));
	}
}
