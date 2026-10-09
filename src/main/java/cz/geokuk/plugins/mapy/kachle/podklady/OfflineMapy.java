package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Offline mapa: všechny soubory .map ve složce vykreslené jedním tématem. Složku průběžně sleduje, takže nově zkopírovaná nebo vyměněná mapa se projeví bez
 * restartu.
 */
@Slf4j
public class OfflineMapy {

	private static final long KONTROLA_SLOZKY_NS = TimeUnit.SECONDS.toNanos(2);

	private final Runnable priZmene;
	/** Zavolá se po každém otevření map s klíčem dlaždic a souborem symbolů (null = neukládají se). */
	private volatile BiConsumer<String, File> priOtevreni = (klic, symboly) -> {};

	/** Jak často se nejvýš dívá do složky, jestli se mapy nezměnily. */
	long kontrolaSlozkyNs = KONTROLA_SLOZKY_NS;

	// Nastavení se mění z EDT bez zámku; vlákno, které právě otevírá mapy pod zámkem, by EDT zdrželo.
	private volatile File slozka;
	/** Kam se ukládají vykreslené symboly témat, null = nikam. */
	private volatile File slozkaSymbolu;
	private volatile TemaOfflineMapy tema = TemaOfflineMapy.VYCHOZI;
	/** Měřítko displeje (1 = 100 %), podle něj se kreslí větší dlaždice. */
	private volatile double meritko = 1;
	/** Násobek velikosti písma a značek na mapě (1 = 100 %). */
	private volatile double pismo = 1;

	/** Nastavení, podle kterého jsou otevřené mapy; mění se jen v {@link #pouzij()}. */
	private File slozkaOtevrena;
	private TemaOfflineMapy temaOtevrene;
	private double meritkoOtevrene;
	private double pismoOtevrene;

	private volatile OfflineRenderer renderer;
	/** Téma se drží i přes změnu map, jeho načtení trvá u velkých témat sekundy. */
	private OfflineRenderer.NacteneTema nacteneTema;
	private IOException chyba;
	/** Soubory, jejich velikosti a časy, ze kterých je renderer nebo chyba; null = zatím nic. */
	private String otiskSlozky;
	private long posledniKontrola;

	/**
	 * @param priZmene
	 *            zavolá se, když se změní vykreslování (mapy nebo téma, ne při prvním otevření), aby se zahodily dlaždice v paměti
	 */
	public OfflineMapy(final Runnable priZmene) {
		this.priZmene = priZmene;
	}

	public void nastav(final File slozka, final TemaOfflineMapy tema) {
		this.slozka = slozka;
		this.tema = tema;
	}

	/** Nastaví měřítko displeje, zaokrouhlené na čtvrtiny v rozsahu 1–3; při změně se mapy vykreslí znovu. */
	public void nastavMeritko(final double meritkoDispleje) {
		meritko = zaokrouhliMeritko(meritkoDispleje);
	}

	static double zaokrouhliMeritko(final double meritkoDispleje) {
		if (Double.isNaN(meritkoDispleje)) {
			return 1;
		}
		return Math.max(1, Math.min(3, Math.round(meritkoDispleje * 4) / 4.0));
	}

	/** Nastaví velikost písma a značek (1 = 100 %), zaokrouhlenou na 5 % v rozsahu 80–150 %; mapy se vykreslí znovu. */
	public void nastavPismo(final double velikost) {
		pismo = zaokrouhliPismo(velikost);
	}

	public static double zaokrouhliPismo(final double velikost) {
		if (Double.isNaN(velikost)) {
			return 1;
		}
		return Math.max(0.8, Math.min(1.5, Math.round(velikost * 20) / 20.0));
	}

	public double getMeritko() {
		return meritko;
	}

	public void setPriOtevreni(final BiConsumer<String, File> priOtevreni) {
		this.priOtevreni = priOtevreni;
	}

	public void setSlozkaSymbolu(final File slozkaSymbolu) {
		this.slozkaSymbolu = slozkaSymbolu;
	}

	public File getSlozka() {
		return slozka;
	}

	public TemaOfflineMapy getTema() {
		return tema;
	}

	/** Proč se nepoužilo zvolené téma, null když se použilo nebo mapa zatím není otevřená. */
	public String getChybaTematu() {
		final OfflineRenderer r = renderer;
		return r == null ? null : r.getChybaTematu();
	}

	/** Soubory .map ve složce podle abecedy, velikost písmen v příponě nehraje roli. */
	static List<File> mapyVeSlozce(final File slozka) {
		final File[] soubory = slozka == null ? null : slozka.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".map"));
		if (soubory == null) {
			return Collections.emptyList();
		}
		final List<File> mapy = new ArrayList<>(Arrays.asList(soubory));
		mapy.sort(Comparator.comparing(File::getName));
		return mapy;
	}

	/**
	 * Renderer pro aktuální mapy a téma, už s {@link OfflineRenderer#zacni()}; volající musí zavolat {@link OfflineRenderer#skonci()}. Mapy otevírá při prvním
	 * použití a po změně ve složce, nevolat z EDT.
	 *
	 * @throws IOException
	 *             když ve složce není mapa nebo ji nejde otevřít
	 */
	synchronized OfflineRenderer pouzij() throws IOException {
		final File s = slozka;
		final TemaOfflineMapy t = tema;
		final double m = meritko;
		final double pi = pismo;
		if (!Objects.equals(s, slozkaOtevrena) || !t.equals(temaOtevrene) || m != meritkoOtevrene || pi != pismoOtevrene) {
			slozkaOtevrena = s;
			temaOtevrene = t;
			meritkoOtevrene = m;
			pismoOtevrene = pi;
			otiskSlozky = null;
		}
		final long ted = System.nanoTime();
		if (otiskSlozky == null || ted - posledniKontrola >= kontrolaSlozkyNs) {
			posledniKontrola = ted;
			final List<File> mapy = mapyVeSlozce(slozkaOtevrena);
			final String otisk = otisk(mapy);
			if (!otisk.equals(otiskSlozky)) {
				otiskSlozky = otisk;
				otevri(mapy);
			}
		}
		if (chyba != null) {
			throw chyba;
		}
		if (!renderer.zacni()) {
			throw new IllegalStateException("Renderer offline mapy je zavřený");
		}
		return renderer;
	}

	private String otisk(final List<File> mapy) {
		final StringBuilder sb = new StringBuilder(temaOtevrene.otisk()).append('\n').append(meritkoOtevrene).append(' ').append(pismoOtevrene).append('\n');
		for (final File f : mapy) {
			sb.append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n');
		}
		return sb.toString();
	}

	private void otevri(final List<File> mapy) {
		// Při prvním otevření nejsou na obrazovce dlaždice, které by změna zneplatnila; ohlášení by zahodilo i rozdělaná vykreslení.
		final boolean zneplatnit = renderer != null || chyba != null;
		zavriRenderer();
		if (mapy.isEmpty()) {
			chyba = new OfflineMapaChyba("Ve složce " + slozkaOtevrena + " nejsou offline mapy (soubory .map).", "ve složce nejsou soubory .map", null);
		} else {
			try {
				final long start = System.nanoTime();
				renderer = OfflineRenderer.otevri(mapy, nacteneTema());
				chyba = null;
				log.info("Offline mapa otevřena za {} ms: {}, téma {}, klíč {}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), mapy, temaOtevrene, renderer.getKlic());
				priOtevreni.accept(renderer.getKlic(), renderer.getSouborSymbolu());
			} catch (final IOException e) {
				chyba = e;
				log.warn("Offline mapu nejde otevřít: {}", e.getMessage());
			}
		}
		if (zneplatnit) {
			priZmene.run();
		}
	}

	private OfflineRenderer.NacteneTema nacteneTema() throws IOException {
		if (nacteneTema != null && nacteneTema.otiskPozadovaneho.equals(temaOtevrene.otisk()) && nacteneTema.meritko == meritkoOtevrene && nacteneTema.pismo == pismoOtevrene) {
			return nacteneTema;
		}
		uvolniTema();
		nacteneTema = OfflineRenderer.nactiTema(temaOtevrene, slozkaSymbolu, meritkoOtevrene, pismoOtevrene);
		return nacteneTema;
	}

	private void uvolniTema() {
		if (nacteneTema != null) {
			nacteneTema.uvolni();
			nacteneTema = null;
		}
	}

	/** Otevře mapy a načte téma předem, aby první dlaždice nečekaly; chyby se ukážou až na dlaždicích. Nevolat z EDT. */
	public void predpriprav() {
		if (getSlozka() == null) {
			return; // složka ještě není nastavená, chyba „bez map“ by se zbytečně ohlásila
		}
		try {
			pouzij().skonci();
		} catch (final IOException | RuntimeException e) {
			log.debug("Offline mapu nejde připravit předem: {}", e.getMessage());
		}
	}

	private void zavriRenderer() {
		if (renderer != null) {
			renderer.zavri();
			renderer = null;
		}
	}

	/** Zavře mapy; při dalším použití se otevřou znovu. */
	public synchronized void zavri() {
		zavriRenderer();
		uvolniTema();
		otiskSlozky = null;
	}
}
