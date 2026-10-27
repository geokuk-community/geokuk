package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import cz.geokuk.core.napoveda.Vykon;
import cz.geokuk.core.program.UmisteniProgramu;
import lombok.extern.slf4j.Slf4j;

/**
 * Diagnostika získávání dlaždic, zapíná se systémovou vlastností {@value #VLASTNOST}. Pro každé změněné zobrazení mapy (start, posun, zoom, návrat) měří čas do
 * první a do poslední dlaždice, pro každou dlaždici čas od požadavku podle zdroje (paměť, disk, vykreslení, web) a fáze (fronta, zámek cache, čtení,
 * dekódování, vykreslení), zápisy do cache a pauzy sběru odpadu. Souhrn s percentily se zapisuje do logu a do souboru {@value #SOUBOR} ve složce logů.
 */
@Slf4j
public final class DiagnostikaDlazdic {

	public static final String VLASTNOST = "geokuk.diagnostikaDlazdic";
	public static final String SOUBOR = "diagnostika-dlazdic.txt";

	private static final int NEJVIC_VZORKU = 200_000;
	private static final int NEJVIC_POHLEDU = 5000;
	private static final int POHLEDU_V_SOUHRNU = 10;
	private static final long OBDOBI_ZAPISU_S = 30;
	private static final long OBDOBI_SBERU_ODPADU_MS = 25;
	private static final int HISTORIE_POHLEDU = 6;
	private static final String ZDROJ_NEZNAMY = "?";

	/** Co uživatel s mapou udělal. */
	public enum Scenar {
		START("start"), POSUN("posun"), ZOOM_1("zoom ±1"), ZOOM_2("zoom ±2"), ZOOM_3_A_VIC("zoom ±3 a víc"), NAVRAT("návrat na dřívější místo"), PREDVYKRESLENI("bez zobrazení (předvykreslení okolí)");

		final String popis;

		Scenar(final String popis) {
			this.popis = popis;
		}
	}

	private static volatile boolean zapnuto;
	private static final Map<String, Serie> SERIE = new ConcurrentHashMap<>();
	private static final Map<String, StavKachlovniku> STAVY = new ConcurrentHashMap<>();
	private static final List<Pohled> POHLEDY = new ArrayList<>();
	private static final List<long[]> ODPAD = new ArrayList<>();
	private static final List<String> SOUBORY = new ArrayList<>();
	private static final ThreadLocal<Cteni> CTENI = new ThreadLocal<>();
	private static volatile long zmena;
	private static long zapsanaZmena = -1;
	private static long odpadCasMs;
	private static long odpadPocet;

	static {
		if (jeZapnuto(System.getProperty(VLASTNOST))) {
			zapni();
		}
	}

	private DiagnostikaDlazdic() {}

	static boolean jeZapnuto(final String hodnota) {
		return hodnota != null && !hodnota.trim().isEmpty() && !"false".equalsIgnoreCase(hodnota.trim()) && !"0".equals(hodnota.trim());
	}

	public static boolean zapnuto() {
		return zapnuto;
	}

	/** Zapne diagnostiku, spustí sledování sběru odpadu a pravidelný zápis souhrnu. Opakované volání nic nedělá. */
	static synchronized void zapni() {
		if (zapnuto) {
			return;
		}
		zapnuto = true;
		final ScheduledExecutorService vlakno = Executors.newSingleThreadScheduledExecutor(r -> {
			final Thread t = new Thread(r, "Diagnostika dlaždic");
			t.setDaemon(true);
			return t;
		});
		vlakno.scheduleAtFixedRate(DiagnostikaDlazdic::zmerOdpad, OBDOBI_SBERU_ODPADU_MS, OBDOBI_SBERU_ODPADU_MS, TimeUnit.MILLISECONDS);
		vlakno.scheduleAtFixedRate(DiagnostikaDlazdic::zapisKdyzSeZmenilo, OBDOBI_ZAPISU_S, OBDOBI_ZAPISU_S, TimeUnit.SECONDS);
		Runtime.getRuntime().addShutdownHook(new Thread(DiagnostikaDlazdic::zapis, "Zápis diagnostiky dlaždic"));
		log.info("Diagnostika dlaždic zapnuta, souhrn se zapisuje do {} každých {} s a při ukončení.", SOUBOR, OBDOBI_ZAPISU_S);
	}

	/** Jen pro testy. */
	static synchronized void vynuluj() {
		SERIE.clear();
		STAVY.clear();
		SOUBORY.clear();
		synchronized (POHLEDY) {
			POHLEDY.clear();
		}
		synchronized (ODPAD) {
			ODPAD.clear();
			odpadCasMs = 0;
			odpadPocet = 0;
		}
		zmena++;
	}

	/** Jen pro testy. */
	static void zapniProTesty(final boolean hodnota) {
		zapnuto = hodnota;
	}

	// ------------------------------------------------------------------------------------------------- série

	/** Naměřené hodnoty jedné veličiny, na konci se z nich počítají percentily. */
	static final class Serie {
		private final String jednotka;
		private long[] hodnoty = new long[256];
		private int pocet;
		private long celkem;

		Serie(final String jednotka) {
			this.jednotka = jednotka;
		}

		synchronized void pridej(final long hodnota) {
			celkem++;
			if (pocet < NEJVIC_VZORKU) {
				if (pocet == hodnoty.length) {
					hodnoty = Arrays.copyOf(hodnoty, pocet * 2);
				}
				hodnoty[pocet++] = hodnota;
			}
		}

		synchronized long[] serazene() {
			final long[] kopie = Arrays.copyOf(hodnoty, pocet);
			Arrays.sort(kopie);
			return kopie;
		}

		synchronized long celkem() {
			return celkem;
		}

		String popis() {
			final long[] s = serazene();
			if (s.length == 0) {
				return "bez záznamu";
			}
			return String.format(Locale.ROOT, "n=%d, p50 %s, p95 %s, p99 %s, max %s", celkem(), hodnota(percentil(s, 50)), hodnota(percentil(s, 95)), hodnota(percentil(s, 99)), hodnota(s[s.length - 1]));
		}

		private String hodnota(final long v) {
			return "ks".equals(jednotka) ? Long.toString(v) : String.format(Locale.ROOT, "%.1f ms", v / 1e6);
		}
	}

	/** Hodnota na dané pořadí v seřazeném poli (nejbližší vyšší pořadí). */
	static long percentil(final long[] serazene, final int procent) {
		if (serazene.length == 0) {
			return 0;
		}
		final int index = (int) Math.ceil(serazene.length * procent / 100.0) - 1;
		return serazene[Math.max(0, Math.min(serazene.length - 1, index))];
	}

	private static Serie serie(final String klic, final String jednotka) {
		return SERIE.computeIfAbsent(klic, k -> new Serie(jednotka));
	}

	private static void zaznamenejNs(final String klic, final long ns) {
		serie(klic, "ms").pridej(ns);
		zmena++;
	}

	private static void zaznamenejKs(final String klic, final long ks) {
		serie(klic, "ks").pridej(ks);
	}

	// ------------------------------------------------------------------------------------------------- zobrazení mapy

	/** Jedno zobrazení mapy: nové dlaždice, které se po změně výřezu musí získat. */
	public static final class Pohled {
		final String kachlovnik;
		final Scenar scenar;
		final long zacatekNs;
		final long zacatekMs;
		final int pozadovanych;
		private int hotovych;
		private int chyb;
		private long prvniNs = -1;
		private long kompletniNs = -1;
		private long nejdelsiNs;
		private boolean preruseno;
		private long odpadMs;
		private final Map<String, Integer> zdroje = new TreeMap<>();

		Pohled(final String kachlovnik, final Scenar scenar, final int pozadovanych, final long zacatekNs, final long zacatekMs) {
			this.kachlovnik = kachlovnik;
			this.scenar = scenar;
			this.pozadovanych = pozadovanych;
			this.zacatekNs = zacatekNs;
			this.zacatekMs = zacatekMs;
		}

		synchronized void hotova(final String zdroj, final long celkemNs, final boolean chyba, final long tedNs) {
			hotovych++;
			if (chyba) {
				chyb++;
			}
			zdroje.merge(zdroj, 1, Integer::sum);
			nejdelsiNs = Math.max(nejdelsiNs, celkemNs);
			final long odZacatku = tedNs - zacatekNs;
			if (prvniNs < 0) {
				prvniNs = odZacatku;
				zaznamenejNs("zobrazení " + kachlovnik + " / " + scenar.popis + ": do první dlaždice", odZacatku);
			}
			if (hotovych == pozadovanych && !preruseno) {
				kompletniNs = odZacatku;
				odpadMs = pauzyOdpadu(zacatekMs, System.currentTimeMillis());
				zaznamenejNs("zobrazení " + kachlovnik + " / " + scenar.popis + ": do poslední dlaždice", odZacatku);
				synchronized (POHLEDY) {
					if (POHLEDY.size() < NEJVIC_POHLEDU) {
						POHLEDY.add(this);
					}
				}
			}
		}

		synchronized void prerus() {
			if (hotovych < pozadovanych && !preruseno) {
				preruseno = true;
				zaznamenejKs("zobrazení " + kachlovnik + " / " + scenar.popis + ": přerušeno před dokončením (dlaždic chybělo)", pozadovanych - hotovych);
				zmena++;
			}
		}

		synchronized String popis() {
			return String.format(Locale.ROOT, "%s / %s: %d dlaždic, první za %.0f ms, poslední za %.0f ms, nejdelší dlaždice %.0f ms, zdroje %s, pauzy GC %d ms", kachlovnik, scenar.popis, pozadovanych,
					prvniNs / 1e6, kompletniNs / 1e6, nejdelsiNs / 1e6, zdroje, odpadMs);
		}

		synchronized long kompletniNs() {
			return kompletniNs;
		}
	}

	/** Co si kachlovník pamatuje o předchozích zobrazeních, aby šlo poznat, co uživatel udělal. */
	private static final class StavKachlovniku {
		int moumer = Integer.MIN_VALUE;
		long stredX;
		long stredY;
		/** Předchozí zobrazení od nejnovějšího: měřítko, střed x a y. */
		final Deque<long[]> historie = new ArrayDeque<>();
		Pohled posledni;
	}

	/**
	 * Rozpozná, co uživatel s mapou udělal: poprvé je to start, stejné měřítko je posun, jinak zoom podle rozdílu úrovní; návrat je zobrazení stejného měřítka
	 * a místa jako některé z několika předchozích, ne však bezprostředně předchozí.
	 *
	 * @param historie
	 *            předchozí zobrazení od nejnovějšího: měřítko, střed x a y
	 */
	static Scenar urci(final int predchoziMoumer, final Deque<long[]> historie, final int moumer, final long stredX, final long stredY, final long sirka, final long vyska) {
		if (predchoziMoumer == Integer.MIN_VALUE) {
			return Scenar.START;
		}
		if (predchoziMoumer != moumer) {
			int poradi = 0;
			for (final long[] h : historie) {
				if (poradi++ >= 1 && h[0] == moumer && Math.abs(h[1] - stredX) < sirka / 2 && Math.abs(h[2] - stredY) < vyska / 2) {
					return Scenar.NAVRAT;
				}
			}
		}
		switch (Math.abs(moumer - predchoziMoumer)) {
		case 0:
			return Scenar.POSUN;
		case 1:
			return Scenar.ZOOM_1;
		case 2:
			return Scenar.ZOOM_2;
		default:
			return Scenar.ZOOM_3_A_VIC;
		}
	}

	/**
	 * Začátek nového zobrazení v kachlovníku; volá se z EDT, když se po změně výřezu zadávají nové dlaždice. Vrací null, když nic nového není (a diagnostika je vypnutá).
	 */
	public static Pohled novyPohled(final String kachlovnik, final int moumer, final long stredX, final long stredY, final long sirka, final long vyska, final int novych) {
		if (!zapnuto) {
			return null;
		}
		final StavKachlovniku stav = STAVY.computeIfAbsent(String.valueOf(kachlovnik), k -> new StavKachlovniku());
		synchronized (stav) {
			final Scenar scenar = urci(stav.moumer, stav.historie, moumer, stredX, stredY, sirka, vyska);
			if (stav.moumer != Integer.MIN_VALUE) {
				stav.historie.addFirst(new long[] { stav.moumer, stav.stredX, stav.stredY });
				while (stav.historie.size() > HISTORIE_POHLEDU) {
					stav.historie.removeLast();
				}
			}
			stav.moumer = moumer;
			stav.stredX = stredX;
			stav.stredY = stredY;
			if (stav.posledni != null) {
				stav.posledni.prerus();
				stav.posledni = null;
			}
			if (novych <= 0) {
				return null;
			}
			final Pohled pohled = new Pohled(String.valueOf(kachlovnik), scenar, novych, System.nanoTime(), System.currentTimeMillis());
			stav.posledni = pohled;
			return pohled;
		}
	}

	// ------------------------------------------------------------------------------------------------- dlaždice

	/** Dlaždice došla příjemci. {@code zadanoNs} je čas jejího požadavku. */
	public static void dlazdiceHotova(final Pohled pohled, final KachloStav stav, final long zadanoNs) {
		if (!zapnuto) {
			return;
		}
		final long ted = System.nanoTime();
		final String zdroj = stav.getZdroj() == null ? ZDROJ_NEZNAMY : stav.getZdroj();
		final boolean chyba = stav.getThr() != null;
		final Scenar scenar = pohled == null ? Scenar.PREDVYKRESLENI : pohled.scenar;
		zaznamenejNs("dlaždice " + scenar.popis + " / " + zdroj + (chyba ? " (chyba)" : ""), ted - zadanoNs);
		if (pohled != null) {
			pohled.hotova(zdroj, ted - zadanoNs, chyba, ted);
		}
	}

	/** Časy jedné dlaždice v exekutorech disku, vykreslování a webu; patří dlaždici v paměťové cache. */
	public static final class Faze {
		volatile long diskZadano;
		volatile long diskStart;
		volatile long diskKonec;
		volatile long renderZadano;
		volatile long renderStart;
		volatile long renderKonec;
		volatile long webZadano;
		volatile long webStart;
		volatile long webKonec;

		public void diskZadano(final int frontaPred, final int bezi) {
			diskZadano = System.nanoTime();
			zaznamenejKs("fronta čtení z disku při zadání: čeká", frontaPred);
			zaznamenejKs("fronta čtení z disku při zadání: běží vláken", bezi);
		}

		public void diskStart() {
			diskStart = System.nanoTime();
		}

		public void diskKonec() {
			diskKonec = System.nanoTime();
		}

		public void renderZadano(final int frontaPred, final int bezi) {
			renderZadano = System.nanoTime();
			zaznamenejKs("fronta vykreslování při zadání: čeká", frontaPred);
			zaznamenejKs("fronta vykreslování při zadání: běží vláken", bezi);
		}

		public void renderStart() {
			renderStart = System.nanoTime();
		}

		public void renderKonec() {
			renderKonec = System.nanoTime();
		}

		public void webZadano(final int frontaPred) {
			webZadano = System.nanoTime();
			zaznamenejKs("fronta stahování při zadání: čeká", frontaPred);
		}

		public void webStart() {
			webStart = System.nanoTime();
		}

		public void webKonec() {
			webKonec = System.nanoTime();
		}
	}

	/** Zapíše fáze dlaždice, jakmile je získaná; jednou za dlaždici bez ohledu na počet příjemců. */
	public static void faze(final Faze f, final String zdroj) {
		if (!zapnuto || f == null) {
			return;
		}
		if (f.diskStart > 0 && f.diskZadano > 0) {
			zaznamenejNs("disk: čekání ve frontě před čtením", f.diskStart - f.diskZadano);
		}
		if (f.diskKonec > 0 && f.diskStart > 0) {
			zaznamenejNs("disk: čtení z cache celkem (zámek, čtení, dekódování)" + (zdroj.equals("DISK") ? ", zásah" : ", minutí"), f.diskKonec - f.diskStart);
		}
		if (f.renderStart > 0 && f.renderZadano > 0) {
			zaznamenejNs("vykreslení: čekání ve frontě", f.renderStart - f.renderZadano);
		}
		if (f.renderKonec > 0 && f.renderStart > 0) {
			zaznamenejNs("vykreslení offline mapy", f.renderKonec - f.renderStart);
		}
		if (f.webStart > 0 && f.webZadano > 0) {
			zaznamenejNs("web: čekání ve frontě", f.webStart - f.webZadano);
		}
		if (f.webKonec > 0 && f.webStart > 0) {
			zaznamenejNs("web: stažení", f.webKonec - f.webStart);
		}
	}

	// ------------------------------------------------------------------------------------------------- čtení a zápis cache

	/** Časy jednoho čtení z databáze cache v jednom vlákně. */
	public static final class Cteni {
		long spojeniNs;
		long zamekNs;
		long dotazNs;
		long dekodovaniNs;
	}

	/** Začátek čtení dlaždice z cache v tomto vlákně; vrací null, když je diagnostika vypnutá. */
	public static Cteni zacniCteni() {
		if (!zapnuto) {
			return null;
		}
		final Cteni c = new Cteni();
		CTENI.set(c);
		return c;
	}

	public static void skonciCteni(final Cteni c) {
		if (c == null) {
			return;
		}
		CTENI.remove();
		zaznamenejNs("cache: získání spojení s databází", c.spojeniNs);
		zaznamenejNs("cache: čekání na zámek čtení (zápis ho drží)", c.zamekNs);
		zaznamenejNs("cache: dotaz a čtení bajtů", c.dotazNs);
		zaznamenejNs("cache: dekódování obrázku", c.dekodovaniNs);
	}

	public static void spojeni(final long ns) {
		final Cteni c = CTENI.get();
		if (c != null) {
			c.spojeniNs += ns;
		}
	}

	public static void zamekCteni(final long ns) {
		final Cteni c = CTENI.get();
		if (c != null) {
			c.zamekNs += ns;
		}
	}

	public static void dotaz(final long ns) {
		final Cteni c = CTENI.get();
		if (c != null) {
			c.dotazNs += ns;
		}
	}

	public static void dekodovani(final long ns) {
		final Cteni c = CTENI.get();
		if (c != null) {
			c.dekodovaniNs += ns;
		}
	}

	/** Zápis dávky dlaždic: čekání na zámek zápisu (čtení se zastaví), trvání transakce a velikost dávky. */
	public static void zapisDavky(final long cekaniNs, final long trvaniNs, final int dlazdic) {
		if (!zapnuto) {
			return;
		}
		zaznamenejNs("zápis dávky: čekání na zámek zápisu", cekaniNs);
		zaznamenejNs("zápis dávky: trvání transakce (čtení cache stojí)", trvaniNs);
		zaznamenejKs("zápis dávky: počet dlaždic", dlazdic);
	}

	/** Doba, po kterou EDT sestavuje dlaždice kachlovníku po změně výřezu. */
	public static void initKachlovniku(final String kachlovnik, final long trvaniNs, final int dlazdic) {
		if (!zapnuto) {
			return;
		}
		zaznamenejNs("EDT: sestavení dlaždic kachlovníku " + kachlovnik, trvaniNs);
		zaznamenejKs("EDT: dlaždic v kachlovníku " + kachlovnik, dlazdic);
	}

	/** Stav souboru cache při otevření. */
	public static void soubor(final String popis) {
		if (!zapnuto) {
			return;
		}
		synchronized (SOUBORY) {
			if (!SOUBORY.contains(popis)) {
				SOUBORY.add(popis);
			}
		}
	}

	// ------------------------------------------------------------------------------------------------- sběr odpadu

	/** Čas sběru odpadu se čte po 25 ms; přírůstek se přičte k času, kdy byl zjištěn. */
	private static void zmerOdpad() {
		try {
			long cas = 0;
			long pocet = 0;
			for (final GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
				cas += Math.max(0, bean.getCollectionTime());
				pocet += Math.max(0, bean.getCollectionCount());
			}
			synchronized (ODPAD) {
				if (cas > odpadCasMs) {
					zaznamenejOdpad(System.currentTimeMillis(), cas - odpadCasMs);
				}
				odpadCasMs = cas;
				odpadPocet = pocet;
			}
		} catch (final RuntimeException e) {
			log.debug("Čas sběru odpadu nejde zjistit: {}", e.toString());
		}
	}

	/** Přírůstek času sběru odpadu zjištěný v daném čase. */
	static void zaznamenejOdpad(final long kdyMs, final long trvaniMs) {
		synchronized (ODPAD) {
			ODPAD.add(new long[] { kdyMs, trvaniMs });
		}
		zaznamenejNs("sběr odpadu: přírůstek času za 25 ms", trvaniMs * 1_000_000);
	}

	/** Součet přírůstků času sběru odpadu v daném úseku. */
	static long pauzyOdpadu(final long odMs, final long doMs) {
		long soucet = 0;
		synchronized (ODPAD) {
			for (final long[] o : ODPAD) {
				if (o[0] >= odMs && o[0] <= doMs) {
					soucet += o[1];
				}
			}
		}
		return soucet;
	}

	// ------------------------------------------------------------------------------------------------- souhrn

	static String souhrn() {
		final StringBuilder sb = new StringBuilder();
		final Runtime rt = Runtime.getRuntime();
		sb.append(String.format(Locale.ROOT, "Diagnostika dlaždic (jádra %d, nejvíc paměti %d MB, v použití %d MB)%n", rt.availableProcessors(), rt.maxMemory() >> 20, (rt.totalMemory() - rt.freeMemory()) >> 20));
		synchronized (SOUBORY) {
			for (final String s : SOUBORY) {
				sb.append(s).append('\n');
			}
		}
		synchronized (ODPAD) {
			sb.append(String.format(Locale.ROOT, "Sběr odpadu od zapnutí: %d sběrů, celkem %d ms%n", odpadPocet, odpadCasMs));
		}
		final List<String> klice = new ArrayList<>(SERIE.keySet());
		klice.sort(null);
		oddil(sb, "Zobrazení mapy (od změny výřezu po dlaždici)", klice, "zobrazení ");
		oddil(sb, "Dlaždice (od požadavku po doručení, podle scénáře a zdroje)", klice, "dlaždice ");
		oddil(sb, "Fáze získání dlaždice", klice, "disk: ", "vykreslení", "web: ", "fronta ");
		oddil(sb, "Cache tiles.sqlite: čtení a zápis", klice, "cache: ", "zápis dávky");
		oddil(sb, "Vlákno oken (EDT)", klice, "EDT: ");
		oddil(sb, "Sběr odpadu", klice, "sběr odpadu");
		final List<Pohled> pohledy;
		synchronized (POHLEDY) {
			pohledy = new ArrayList<>(POHLEDY);
		}
		pohledy.sort((a, b) -> Long.compare(b.kompletniNs(), a.kompletniNs()));
		sb.append("\nNejpomalejší zobrazení mapy:\n");
		for (int i = 0; i < pohledy.size() && i < POHLEDU_V_SOUHRNU; i++) {
			sb.append("  ").append(pohledy.get(i).popis()).append('\n');
		}
		sb.append('\n').append(Vykon.text());
		return sb.toString();
	}

	private static void oddil(final StringBuilder sb, final String nadpis, final List<String> klice, final String... predpony) {
		boolean nadpisZapsan = false;
		for (final String klic : klice) {
			for (final String predpona : predpony) {
				if (klic.startsWith(predpona)) {
					if (!nadpisZapsan) {
						sb.append('\n').append(nadpis).append(":\n");
						nadpisZapsan = true;
					}
					sb.append("  ").append(klic).append(": ").append(SERIE.get(klic).popis()).append('\n');
					break;
				}
			}
		}
	}

	private static void zapisKdyzSeZmenilo() {
		if (zmena != zapsanaZmena) {
			zapis();
		}
	}

	private static synchronized void zapis() {
		try {
			zapsanaZmena = zmena;
			final String souhrn = souhrn();
			Files.write(new File(UmisteniProgramu.log(), SOUBOR).toPath(), souhrn.getBytes(StandardCharsets.UTF_8));
		} catch (final IOException | RuntimeException e) {
			log.warn("Souhrn diagnostiky dlaždic se nepodařilo zapsat: {}", e.toString());
		}
	}
}
