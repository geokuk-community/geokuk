package cz.geokuk.core.program;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

import cz.geokuk.core.napoveda.VerzeJavy;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.start.Start;

/** Upozornění na nevhodné umístění přenosného programu a na starou přibalenou Javu, ukazují se po zobrazení hlavního okna. */
public final class KontrolaUmisteni {

	static final String DOPORUCENE_UMISTENI = "C:\\GeoKuk";

	private static final Pattern SYNCHRONIZOVANA = Pattern.compile("(?i)onedrive.*|dropbox|google ?drive|my drive|můj disk|icloud ?drive.*");
	private static final String UPOZORNENO_SYNC_value = "upozornenoSynchronizovana";
	private static final String UPOZORNENO_JAVA_value = "upozornenoJava";

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
