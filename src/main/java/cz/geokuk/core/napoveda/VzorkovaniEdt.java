package cz.geokuk.core.napoveda;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import cz.geokuk.core.program.UmisteniProgramu;
import lombok.extern.slf4j.Slf4j;

/**
 * Diagnostika startu: v prvních {@link #TRVANI_MS} ms po zapnutí měření EDT vzorkuje zásobník EDT a na konci zapíše souhrn do logu a do souboru
 * vzorkovani-edt.txt ve složce logů. Zapíná se systémovou vlastností {@value #VLASTNOST}; bez ní se nic neděje.
 */
@Slf4j
final class VzorkovaniEdt {

	static final String VLASTNOST = "geokuk.vzorkovaniEdt";
	static final long VYCHOZI_PERIODA_MS = 50;
	static final long NEJMENSI_PERIODA_MS = 10;
	static final long TRVANI_MS = 8000;
	static final String SOUBOR = "vzorkovani-edt.txt";
	private static final int RAMCU_V_ZASOBNIKU = 12;
	private static final int POLOZEK = 15;
	private static final String BEZ_GEOKUKU = "(bez cz.geokuk)";

	private static final String[] KATEGORIE = { "org.mapsforge", "sqlite / SqlJet", "java.util.zip / jar", "sun.font", "javax.swing", "ClassLoader.defineClass" };

	private final Map<String, Integer> baliky = new HashMap<>();
	private final Map<String, Integer> prvniRamce = new HashMap<>();
	private final Map<String, Integer> zasobniky = new HashMap<>();
	private final int[] vKategorii = new int[KATEGORIE.length];
	private int vzorku;
	private int necinnych;
	private int zaneprazdnenych;
	private long periodaMs = VYCHOZI_PERIODA_MS;

	/** Perioda vzorkování z hodnoty vlastnosti; záporné číslo = vypnuto. „true“ nebo cokoli jiného než číslo dává výchozí periodu. */
	static long periodaZVlastnosti(final String hodnota) {
		if (hodnota == null) {
			return -1;
		}
		final String h = hodnota.trim();
		if (h.isEmpty() || "false".equalsIgnoreCase(h) || "0".equals(h)) {
			return -1;
		}
		try {
			return Math.max(NEJMENSI_PERIODA_MS, Long.parseLong(h));
		} catch (final NumberFormatException e) {
			return VYCHOZI_PERIODA_MS;
		}
	}

	/** Spustí vzorkování na vlastním vlákně, pokud je zapnuté vlastností; jinak nic nedělá. */
	static void spustPokudZapnuto(final Supplier<Thread> edt) {
		final long perioda = periodaZVlastnosti(System.getProperty(VLASTNOST));
		if (perioda < 0) {
			return;
		}
		final Thread vlakno = new Thread(() -> {
			final VzorkovaniEdt vzorkovani = vzorkuj(edt, perioda, TRVANI_MS);
			final String souhrn = vzorkovani.souhrn();
			log.info("{}", souhrn);
			try {
				Files.write(new File(UmisteniProgramu.log(), SOUBOR).toPath(), souhrn.getBytes(StandardCharsets.UTF_8));
			} catch (final IOException | RuntimeException e) {
				log.warn("Souhrn vzorkování EDT se nepodařilo zapsat: {}", e.toString());
			}
		}, "Vzorkování EDT");
		vlakno.setDaemon(true);
		vlakno.start();
	}

	/** Vzorkuje vlákno z dodavatele po {@code periodaMs} po dobu {@code trvaniMs}; vlákno se čeká, dokud dodavatel nevrátí hodnotu. */
	static VzorkovaniEdt vzorkuj(final Supplier<Thread> edt, final long periodaMs, final long trvaniMs) {
		final VzorkovaniEdt v = new VzorkovaniEdt();
		v.periodaMs = periodaMs;
		final long konec = System.nanoTime() + trvaniMs * 1_000_000;
		while (System.nanoTime() < konec) {
			try {
				Thread.sleep(periodaMs);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			final Thread vlakno = edt.get();
			if (vlakno != null) {
				final StackTraceElement[] st = vlakno.getStackTrace();
				if (st.length > 0) {
					v.vzorek(st);
				}
			}
		}
		return v;
	}

	/** EDT čeká na další událost: ve smyčce událostí, bez rozdávané události. */
	static boolean necinny(final StackTraceElement[] st) {
		return obsahuje(st, e -> "java.awt.EventQueue".equals(e.getClassName()) && "getNextEvent".equals(e.getMethodName()));
	}

	void vzorek(final StackTraceElement[] st) {
		vzorku++;
		if (necinny(st)) {
			necinnych++;
			return;
		}
		zaneprazdnenych++;
		for (int i = 0; i < KATEGORIE.length; i++) {
			if (obsahuje(st, KATEGORIE_ZASAH.get(i))) {
				vKategorii[i]++;
			}
		}
		final StackTraceElement prvniGeokuk = prvniGeokuk(st);
		pricti(baliky, prvniGeokuk == null ? BEZ_GEOKUKU : balicek(prvniGeokuk.getClassName()));
		pricti(prvniRamce, prvniGeokuk == null ? BEZ_GEOKUKU : popis(prvniGeokuk));
		pricti(zasobniky, zasobnik(st, prvniGeokuk));
	}

	String souhrn() {
		final StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "Vzorkování EDT: %d vzorků po %d ms (prvních %d s po zapnutí měření EDT), čekajících na událost %d, zaneprázdněných %d%n", vzorku, periodaMs,
				TRVANI_MS / 1000, necinnych, zaneprazdnenych));
		sb.append("Podíl zaneprázdněných vzorků se zásobníkem obsahujícím:\n");
		for (int i = 0; i < KATEGORIE.length; i++) {
			sb.append(String.format(Locale.ROOT, "  %s: %d (%s)%n", KATEGORIE[i], vKategorii[i], procent(vKategorii[i], zaneprazdnenych)));
		}
		vypis(sb, "Zaneprázdněné vzorky podle balíčku prvního rámce cz.geokuk", baliky);
		vypis(sb, "Nejčastější první rámce cz.geokuk", prvniRamce);
		vypis(sb, "Nejčastější zásobníky (od vrcholu, " + RAMCU_V_ZASOBNIKU + " rámců a první rámec cz.geokuk)", zasobniky);
		return sb.toString();
	}

	private static final List<Predicate<StackTraceElement>> KATEGORIE_ZASAH = new ArrayList<>();

	static {
		KATEGORIE_ZASAH.add(e -> e.getClassName().startsWith("org.mapsforge"));
		KATEGORIE_ZASAH.add(e -> {
			final String t = e.getClassName().toLowerCase(Locale.ROOT);
			return t.contains("sqlite") || t.contains("sqljet") || t.startsWith("org.tmatesoft");
		});
		KATEGORIE_ZASAH.add(e -> e.getClassName().startsWith("java.util.zip") || e.getClassName().startsWith("java.util.jar") || e.getClassName().endsWith("URLClassPath"));
		KATEGORIE_ZASAH.add(e -> e.getClassName().startsWith("sun.font"));
		KATEGORIE_ZASAH.add(e -> e.getClassName().startsWith("javax.swing"));
		KATEGORIE_ZASAH.add(e -> e.getClassName().endsWith("ClassLoader") && e.getMethodName().startsWith("defineClass"));
	}

	private static boolean obsahuje(final StackTraceElement[] st, final Predicate<StackTraceElement> podminka) {
		for (final StackTraceElement e : st) {
			if (podminka.test(e)) {
				return true;
			}
		}
		return false;
	}

	private static StackTraceElement prvniGeokuk(final StackTraceElement[] st) {
		for (final StackTraceElement e : st) {
			if (e.getClassName().startsWith("cz.geokuk.")) {
				return e;
			}
		}
		return null;
	}

	/** Balíček do čtvrté úrovně, třeba cz.geokuk.plugins.mapy. */
	static String balicek(final String trida) {
		final String[] casti = trida.split("\\.");
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < casti.length - 1 && i < 4; i++) {
			sb.append(i == 0 ? "" : ".").append(casti[i]);
		}
		return sb.toString();
	}

	private static String popis(final StackTraceElement e) {
		final String trida = e.getClassName();
		return trida.substring(trida.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
	}

	private static String zasobnik(final StackTraceElement[] st, final StackTraceElement prvniGeokuk) {
		final List<String> ramce = new ArrayList<>();
		for (int i = 0; i < st.length && i < RAMCU_V_ZASOBNIKU; i++) {
			ramce.add(popis(st[i]));
		}
		if (prvniGeokuk != null && !ramce.contains(popis(prvniGeokuk))) {
			ramce.add("… " + popis(prvniGeokuk));
		}
		return String.join(" ← ", ramce);
	}

	private static void pricti(final Map<String, Integer> mapa, final String klic) {
		mapa.merge(klic, 1, Integer::sum);
	}

	private static String procent(final int cast, final int celek) {
		return celek == 0 ? "0 %" : String.format(Locale.ROOT, "%.1f %%", 100.0 * cast / celek);
	}

	private static void vypis(final StringBuilder sb, final String nadpis, final Map<String, Integer> mapa) {
		sb.append(nadpis).append(":\n");
		final List<Map.Entry<String, Integer>> polozky = new ArrayList<>(mapa.entrySet());
		polozky.sort((a, b) -> {
			final int podlePoctu = Integer.compare(b.getValue(), a.getValue());
			return podlePoctu != 0 ? podlePoctu : a.getKey().compareTo(b.getKey());
		});
		for (int i = 0; i < polozky.size() && i < POLOZEK; i++) {
			sb.append(String.format(Locale.ROOT, "  %d× %s%n", polozky.get(i).getValue(), polozky.get(i).getKey()));
		}
	}
}
