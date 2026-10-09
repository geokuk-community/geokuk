package cz.geokuk.core.napoveda;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.Locale;

/**
 * Měření výkonu kreslení: doba překreslení mapy, obsluhy událostí na EDT a získání dlaždic. Pro každou veličinu se drží posledních {@link #VELIKOST_OKNA}
 * hodnot; zápis nic nealokuje, medián a p95 se počítají až při čtení.
 */
public final class Vykon {

	public static final int VELIKOST_OKNA = 256;
	/** Událost na EDT delší než práh se počítá jako blokování. */
	public static final long PRAH_EDT_MS = 100;
	static final long OKNO_RYCHLOSTI_NS = 10_000_000_000L;

	public enum Velicina {
		PREKRESLENI("Překreslení mapy"), EDT("Událost na EDT"), DLAZDICE_ONLINE("Získání dlaždice online"), DLAZDICE_OFFLINE("Získání dlaždice offline"),
		VYKRESLENI_OFFLINE("Vykreslení offline dlaždice");

		final String popis;

		Velicina(final String popis) {
			this.popis = popis;
		}
	}

	/** Souhrn jedné veličiny v milisekundách. */
	public static final class Souhrn {
		public final long pocet;
		public final double median;
		public final double p95;
		public final double max;
		/** Počet záznamů za posledních 10 s. */
		public final int zaPoslednich10s;

		Souhrn(final long pocet, final double median, final double p95, final double max, final int zaPoslednich10s) {
			this.pocet = pocet;
			this.median = median;
			this.p95 = p95;
			this.max = max;
			this.zaPoslednich10s = zaPoslednich10s;
		}
	}

	static final class Okno {
		private final long[] trvani = new long[VELIKOST_OKNA];
		private final long[] kdy = new long[VELIKOST_OKNA];
		private int dalsi;
		private long pocet;
		private long max;

		synchronized void zapis(final long trvaniNs, final long tedNs) {
			trvani[dalsi] = trvaniNs;
			kdy[dalsi] = tedNs;
			dalsi = (dalsi + 1) % VELIKOST_OKNA;
			pocet++;
			if (trvaniNs > max) {
				max = trvaniNs;
			}
		}

		synchronized void vynuluj() {
			dalsi = 0;
			pocet = 0;
			max = 0;
		}

		Souhrn souhrn(final long tedNs) {
			final long[] serazene;
			final long celkem;
			final long nejdelsi;
			int nedavno = 0;
			synchronized (this) {
				celkem = pocet;
				nejdelsi = max;
				final int n = (int) Math.min(pocet, VELIKOST_OKNA);
				serazene = Arrays.copyOf(trvani, n);
				for (int i = 0; i < n; i++) {
					if (tedNs - kdy[i] <= OKNO_RYCHLOSTI_NS) {
						nedavno++;
					}
				}
			}
			Arrays.sort(serazene);
			return new Souhrn(celkem, ms(percentil(serazene, 50)), ms(percentil(serazene, 95)), ms(nejdelsi), nedavno);
		}
	}

	private static final Okno[] OKNA = new Okno[Velicina.values().length];
	private static volatile long edtNadPrahem;

	static {
		for (int i = 0; i < OKNA.length; i++) {
			OKNA[i] = new Okno();
		}
	}

	private Vykon() {}

	public static void zaznamenej(final Velicina velicina, final long trvaniNs) {
		OKNA[velicina.ordinal()].zapis(trvaniNs, System.nanoTime());
		if (velicina == Velicina.EDT && trvaniNs >= PRAH_EDT_MS * 1_000_000) {
			edtNadPrahem++;
		}
	}

	public static Souhrn souhrn(final Velicina velicina) {
		return OKNA[velicina.ordinal()].souhrn(System.nanoTime());
	}

	public static long edtNadPrahem() {
		return edtNadPrahem;
	}

	/** Dlaždice za sekundu za posledních 10 s, online i offline dohromady. */
	public static double dlazdicZaSekundu() {
		final long ted = System.nanoTime();
		return (OKNA[Velicina.DLAZDICE_ONLINE.ordinal()].souhrn(ted).zaPoslednich10s + OKNA[Velicina.DLAZDICE_OFFLINE.ordinal()].souhrn(ted).zaPoslednich10s)
				/ (OKNO_RYCHLOSTI_NS / 1e9);
	}

	public static void vynuluj() {
		for (final Okno okno : OKNA) {
			okno.vynuluj();
		}
		edtNadPrahem = 0;
	}

	static long percentil(final long[] serazene, final int procent) {
		if (serazene.length == 0) {
			return 0;
		}
		final int index = (int) Math.ceil(serazene.length * procent / 100.0) - 1;
		return serazene[Math.max(0, Math.min(serazene.length - 1, index))];
	}

	private static double ms(final long ns) {
		return ns / 1e6;
	}

	/** Oddíl pro Informace pro hlášení chyby. */
	public static String text() {
		final StringBuilder sb = new StringBuilder("\nVýkon kreslení (posledních " + VELIKOST_OKNA + " záznamů):\n");
		for (final Velicina v : Velicina.values()) {
			final Souhrn s = souhrn(v);
			sb.append("  ").append(v.popis).append(": ");
			if (s.pocet == 0) {
				sb.append("bez záznamu\n");
				continue;
			}
			sb.append(String.format(Locale.ROOT, "medián %.1f ms, p95 %.1f ms, max %.1f ms (celkem %d)", s.median, s.p95, s.max, s.pocet));
			if (v == Velicina.EDT) {
				sb.append(", nad ").append(PRAH_EDT_MS).append(" ms: ").append(edtNadPrahem).append('×');
			}
			sb.append('\n');
		}
		sb.append(String.format(Locale.ROOT, "  Dlaždic za sekundu (posledních 10 s): %.1f%n", dlazdicZaSekundu()));
		return sb.toString();
	}

	/** Stejná čísla pro dálkové ovládání. */
	public static String json() {
		final StringBuilder sb = new StringBuilder("{");
		for (final Velicina v : Velicina.values()) {
			final Souhrn s = souhrn(v);
			sb.append('"').append(v.name().toLowerCase(Locale.ROOT)).append("\":")
					.append(String.format(Locale.ROOT, "{\"pocet\":%d,\"medianMs\":%.3f,\"p95Ms\":%.3f,\"maxMs\":%.3f}", s.pocet, s.median, s.p95, s.max)).append(',');
		}
		sb.append("\"edtNadPrahem\":").append(edtNadPrahem).append(',');
		sb.append(String.format(Locale.ROOT, "\"dlazdicZaSekundu\":%.2f}", dlazdicZaSekundu()));
		return sb.toString();
	}

	/** Fronta událostí, která měří dobu obsluhy každé události; výjimky propouští beze změny. */
	static final class MericiFronta extends EventQueue {
		private int hloubka;
		private boolean[] maVnoreni = new boolean[8];

		@Override
		protected void dispatchEvent(final AWTEvent event) {
			if (hloubka > 0) {
				maVnoreni[hloubka - 1] = true;
			}
			if (hloubka == maVnoreni.length) {
				maVnoreni = Arrays.copyOf(maVnoreni, hloubka * 2);
			}
			maVnoreni[hloubka] = false;
			hloubka++;
			final long start = System.nanoTime();
			try {
				super.dispatchEvent(event);
			} finally {
				hloubka--;
				// Událost s vnořenou smyčkou (modální dialog) EDT neblokovala.
				if (!maVnoreni[hloubka]) {
					zaznamenej(Velicina.EDT, System.nanoTime() - start);
				}
			}
		}
	}

	/** Začne měřit události na EDT. */
	public static void merEdt() {
		Toolkit.getDefaultToolkit().getSystemEventQueue().push(new MericiFronta());
	}
}
