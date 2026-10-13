package cz.geokuk.plugins.cesty.routovani;

import java.util.*;

import cz.geokuk.core.coordinates.Wgs;

/** Výsledek hledání trasy po cestách mezi dvěma body. */
public final class Trasa {

	public enum Stav {
		/** Trasa nalezena, {@link #getBody()} vede ze startu do cíle. */
		NALEZENO,
		/** Start nebo cíl není na žádné offline mapě s cestami. */
		BEZ_MAPY,
		/** Na mapě mezi body cesta pro pěší nevede. */
		NENALEZENO,
		/** Body jsou od sebe dál, než se po cestách hledá. */
		PRILIS_DALEKO,
		/** Hledání bylo zrušeno. */
		ZRUSENO
	}

	private final Stav stav;
	private final List<Wgs> body;
	private final double delka;

	private Trasa(final Stav stav, final List<Wgs> body) {
		this.stav = stav;
		this.body = Collections.unmodifiableList(body);
		double d = 0;
		for (int i = 1; i < body.size(); i++) {
			d += body.get(i - 1).vzdalenost(body.get(i));
		}
		delka = d;
	}

	static Trasa nalezena(final List<Wgs> body) {
		return new Trasa(Stav.NALEZENO, body);
	}

	static Trasa bez(final Stav stav) {
		return new Trasa(stav, Collections.emptyList());
	}

	public Stav getStav() {
		return stav;
	}

	public boolean isNalezena() {
		return stav == Stav.NALEZENO;
	}

	/** Body trasy včetně startu a cíle; prázdné, když trasa nalezena není. */
	public List<Wgs> getBody() {
		return body;
	}

	/** Délka trasy v metrech, 0 když trasa nalezena není. */
	public double getDelka() {
		return delka;
	}

	@Override
	public String toString() {
		return stav + (isNalezena() ? String.format(Locale.ROOT, " %.0f m, %d bodů", delka, body.size()) : "");
	}
}
