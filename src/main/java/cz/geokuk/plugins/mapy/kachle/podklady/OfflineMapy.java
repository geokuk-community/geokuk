package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Offline mapa: všechny soubory .map ve složce vykreslené jedním tématem. Složku průběžně sleduje, takže nově zkopírovaná nebo vyměněná mapa se projeví bez
 * restartu.
 */
@Slf4j
public class OfflineMapy {

	private static final long KONTROLA_SLOZKY_NS = TimeUnit.SECONDS.toNanos(2);

	private final Runnable priZmene;

	/** Jak často se nejvýš dívá do složky, jestli se mapy nezměnily. */
	long kontrolaSlozkyNs = KONTROLA_SLOZKY_NS;

	private File slozka;
	private TemaOfflineMapy tema = TemaOfflineMapy.VYCHOZI;

	private OfflineRenderer renderer;
	/** Téma se drží i přes změnu map, jeho načtení trvá u velkých témat sekundy. */
	private OfflineRenderer.NacteneTema nacteneTema;
	private IOException chyba;
	/** Soubory, jejich velikosti a časy, ze kterých je renderer nebo chyba; null = zatím nic. */
	private String otiskSlozky;
	private long posledniKontrola;

	/**
	 * @param priZmene
	 *            zavolá se, když se změní vykreslování (mapy nebo téma), aby se zahodily dlaždice v paměti
	 */
	public OfflineMapy(final Runnable priZmene) {
		this.priZmene = priZmene;
	}

	public synchronized void nastav(final File slozka, final TemaOfflineMapy tema) {
		if (Objects.equals(this.slozka, slozka) && this.tema.equals(tema)) {
			return;
		}
		this.slozka = slozka;
		this.tema = tema;
		otiskSlozky = null;
	}

	public synchronized File getSlozka() {
		return slozka;
	}

	public synchronized TemaOfflineMapy getTema() {
		return tema;
	}

	/** Proč se nepoužilo zvolené téma, null když se použilo nebo mapa zatím není otevřená. */
	public synchronized String getChybaTematu() {
		return renderer == null ? null : renderer.getChybaTematu();
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
		final long ted = System.nanoTime();
		if (otiskSlozky == null || ted - posledniKontrola >= kontrolaSlozkyNs) {
			posledniKontrola = ted;
			final List<File> mapy = mapyVeSlozce(slozka);
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
		final StringBuilder sb = new StringBuilder(tema.otisk()).append('\n');
		for (final File f : mapy) {
			sb.append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n');
		}
		return sb.toString();
	}

	private void otevri(final List<File> mapy) {
		zavriRenderer();
		if (mapy.isEmpty()) {
			chyba = new OfflineMapaChyba("Ve složce " + slozka + " nejsou offline mapy (soubory .map).", "ve složce nejsou soubory .map", null);
		} else {
			try {
				final long start = System.nanoTime();
				renderer = OfflineRenderer.otevri(mapy, nacteneTema());
				chyba = null;
				log.info("Offline mapa otevřena za {} ms: {}, téma {}, klíč {}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), mapy, tema, renderer.getKlic());
			} catch (final IOException e) {
				chyba = e;
				log.warn("Offline mapu nejde otevřít: {}", e.getMessage());
			}
		}
		priZmene.run();
	}

	private OfflineRenderer.NacteneTema nacteneTema() throws IOException {
		if (nacteneTema != null && nacteneTema.otiskPozadovaneho.equals(tema.otisk())) {
			return nacteneTema;
		}
		uvolniTema();
		nacteneTema = OfflineRenderer.nactiTema(tema);
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
