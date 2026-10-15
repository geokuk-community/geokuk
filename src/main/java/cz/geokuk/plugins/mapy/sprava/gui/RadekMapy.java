package cz.geokuk.plugins.mapy.sprava.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Jeden řádek popupu Mapy: mapa z katalogu nebo soubor uživatele ve složce offline map. */
public final class RadekMapy {

	/** Stav mapy, jak ho ukazuje jedna ikona. */
	public enum Stav {
		/** Mapa je ve složce a není známá novější. */
		AKTUALNI,
		/** Na serveru je novější verze. */
		NOVEJSI,
		/** Soubor je poškozený nebo stažení skončilo chybou. */
		CHYBA,
		/** Stahuje se, ověřuje nebo kontroluje. */
		STAHUJE,
		/** Mapa z katalogu, která ve složce není. */
		NEMAME
	}

	private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy", new Locale("cs")).withZone(ZoneId.systemDefault());

	final String id;
	final String nazev;
	/** Země u regionů (Německo), jinak null. */
	final String skupina;
	final Stav stav;
	/** Soubor uživatele (není v katalogu): jen se zobrazí. */
	final boolean vlastni;
	/** Pokrývá střed zobrazené mapy. */
	final boolean proZobrazeneMisto;
	/** Velikost v bajtech, -1 neznámá; u nestažené mapy přibližná. */
	final long velikost;
	final long stazeno;
	final Instant datumDat;
	final Instant datumNaServeru;
	final boolean rozpracovano;
	final String zdroj;
	final String licence;
	final String chyba;

	private RadekMapy(final Builder b) {
		id = b.id;
		nazev = b.nazev;
		skupina = b.skupina;
		stav = b.stav;
		vlastni = b.vlastni;
		proZobrazeneMisto = b.proZobrazeneMisto;
		velikost = b.velikost;
		stazeno = b.stazeno;
		datumDat = b.datumDat;
		datumNaServeru = b.datumNaServeru;
		rozpracovano = b.rozpracovano;
		zdroj = b.zdroj;
		licence = b.licence;
		chyba = b.chyba;
	}

	public static Builder katalogova(final String id, final String nazev, final Stav stav) {
		final Builder b = new Builder();
		b.id = id;
		b.nazev = nazev;
		b.stav = stav;
		return b;
	}

	public static Builder vlastni(final String nazevSouboru) {
		final Builder b = new Builder();
		b.nazev = nazevSouboru;
		b.stav = Stav.AKTUALNI;
		b.vlastni = true;
		return b;
	}

	public String getId() {
		return id;
	}

	public String getNazev() {
		return nazev;
	}

	public Stav getStav() {
		return stav;
	}

	public boolean isVlastni() {
		return vlastni;
	}

	/** Je ve složce map (i poškozená); jinak je k stažení. */
	public boolean isVeSlozce() {
		return vlastni || stav == Stav.AKTUALNI || stav == Stav.NOVEJSI || stav == Stav.CHYBA && datumDat != null;
	}

	/** Krátký popis stavu za názvem. */
	String popis() {
		switch (stav) {
		case STAHUJE:
			return velikost > 0 ? String.format(new Locale("cs"), "%d %% z %s", procenta(), velikost(velikost)) : "Stahuje se " + velikost(stazeno);
		case NOVEJSI:
			return datumNaServeru != null ? "Novější data z " + DATUM.format(datumNaServeru) : "Novější verze ke stažení";
		case CHYBA:
			return chyba != null ? chyba : "Poškozená mapa";
		case NEMAME:
			return velikost > 0 ? (rozpracovano ? "Staženo " + velikost(stazeno) + " z " : "") + "~" + velikost(velikost) : "";
		case AKTUALNI:
		default:
			final StringBuilder sb = new StringBuilder();
			if (velikost > 0) {
				sb.append(velikost(velikost));
			}
			if (datumDat != null) {
				sb.append(sb.length() > 0 ? " · " : "").append("data ").append(DATUM.format(datumDat));
			}
			return sb.toString();
		}
	}

	int procenta() {
		return velikost > 0 ? (int) Math.min(100, stazeno * 100 / velikost) : 0;
	}

	/** Tooltip: velikost, datum dat, zdroj, licence. */
	String tooltip() {
		final StringBuilder sb = new StringBuilder("<html><b>").append(html(nazev)).append("</b>");
		if (velikost > 0) {
			sb.append("<br>Velikost: ").append(stav == Stav.NEMAME ? "~" : "").append(velikost(velikost));
		}
		if (datumDat != null) {
			sb.append("<br>Data z: ").append(DATUM.format(datumDat));
		}
		if (datumNaServeru != null && stav == Stav.NOVEJSI) {
			sb.append("<br>Na serveru: ").append(DATUM.format(datumNaServeru));
		}
		sb.append("<br>Zdroj: ").append(vlastni ? "vlastní soubor ve složce offline map" : html(zdroj));
		if (licence != null) {
			sb.append("<br>Licence: ").append(html(licence));
		}
		if (chyba != null) {
			sb.append("<br><font color='#C62828'>").append(html(chyba)).append("</font>");
		}
		return sb.toString();
	}

	static String velikost(final long b) {
		if (b >= 1_000_000_000L) {
			return String.format(new Locale("cs"), "%.1f GB", b / 1e9);
		}
		return String.format(new Locale("cs"), "%d MB", Math.max(1, Math.round(b / 1e6)));
	}

	private static String html(final String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static final class Builder {
		private String id;
		private String nazev;
		private String skupina;
		private Stav stav;
		private boolean vlastni;
		private boolean proZobrazeneMisto;
		private long velikost = -1;
		private long stazeno;
		private Instant datumDat;
		private Instant datumNaServeru;
		private boolean rozpracovano;
		private String zdroj;
		private String licence;
		private String chyba;

		public Builder skupina(final String s) {
			skupina = s;
			return this;
		}

		public Builder proZobrazeneMisto(final boolean b) {
			proZobrazeneMisto = b;
			return this;
		}

		public Builder velikost(final long b) {
			velikost = b;
			return this;
		}

		public Builder stazeno(final long b) {
			stazeno = b;
			return this;
		}

		public Builder datumDat(final Instant d) {
			datumDat = d;
			return this;
		}

		public Builder datumNaServeru(final Instant d) {
			datumNaServeru = d;
			return this;
		}

		public Builder rozpracovano(final boolean b) {
			rozpracovano = b;
			return this;
		}

		public Builder zdroj(final String z) {
			zdroj = z;
			return this;
		}

		public Builder licence(final String l) {
			licence = l;
			return this;
		}

		public Builder chyba(final String c) {
			chyba = c;
			return this;
		}

		public RadekMapy build() {
			if (chyba != null && stav == Stav.AKTUALNI) {
				stav = Stav.CHYBA;
			}
			return new RadekMapy(this);
		}
	}
}
