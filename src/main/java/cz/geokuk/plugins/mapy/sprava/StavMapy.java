package cz.geokuk.plugins.mapy.sprava;

import java.time.Instant;

/** Stav jedné katalogové mapy: na disku, na serveru a rozpracované stahování. Neměnný. */
public final class StavMapy {

	public enum Mistni {
		NEMAME, MAME, POSKOZENA
	}

	public enum Server {
		/** Zatím nekontrolováno. */
		NEZNAMY, AKTUALNI, NOVEJSI, NEDOSTUPNY, CHYBI
	}

	public enum Faze {
		NIC, KONTROLUJE, STAHUJE, OVERUJE, HOTOVO, ZRUSENO, CHYBA
	}

	private final KatalogovaMapa mapa;
	private final Mistni mistni;
	private final Server server;
	private final Faze faze;
	private final long stazeno;
	private final long celkem;
	private final boolean rozpracovano;
	private final Instant datumDatMistni;
	private final long velikostMistni;
	private final Instant datumNaServeru;
	private final long velikostNaServeru;
	private final String etag;
	private final String lastModified;
	private final Instant zkontrolovano;
	private final String chyba;

	private StavMapy(final KatalogovaMapa mapa, final Mistni mistni, final Server server, final Faze faze, final long stazeno, final long celkem,
			final boolean rozpracovano, final Instant datumDatMistni, final long velikostMistni, final Instant datumNaServeru, final long velikostNaServeru,
			final String etag, final String lastModified, final Instant zkontrolovano, final String chyba) {
		this.mapa = mapa;
		this.mistni = mistni;
		this.server = server;
		this.faze = faze;
		this.stazeno = stazeno;
		this.celkem = celkem;
		this.rozpracovano = rozpracovano;
		this.datumDatMistni = datumDatMistni;
		this.velikostMistni = velikostMistni;
		this.datumNaServeru = datumNaServeru;
		this.velikostNaServeru = velikostNaServeru;
		this.etag = etag;
		this.lastModified = lastModified;
		this.zkontrolovano = zkontrolovano;
		this.chyba = chyba;
	}

	public static StavMapy novy(final KatalogovaMapa mapa) {
		return new StavMapy(mapa, Mistni.NEMAME, Server.NEZNAMY, Faze.NIC, 0, -1, false, null, -1, null, -1, null, null, null, null);
	}

	StavMapy sMistni(final Mistni m, final Instant datumDat, final long velikost, final boolean rozpr) {
		return new StavMapy(mapa, m, server, faze, stazeno, celkem, rozpr, datumDat, velikost, datumNaServeru, velikostNaServeru, etag, lastModified,
				zkontrolovano, chyba);
	}

	StavMapy sFazi(final Faze f, final String ch) {
		return new StavMapy(mapa, mistni, server, f, stazeno, celkem, rozpracovano, datumDatMistni, velikostMistni, datumNaServeru, velikostNaServeru, etag,
				lastModified, zkontrolovano, ch);
	}

	StavMapy sPrubehem(final long st, final long cel, final boolean rozpr) {
		return new StavMapy(mapa, mistni, server, faze, st, cel, rozpr, datumDatMistni, velikostMistni, datumNaServeru, velikostNaServeru, etag, lastModified,
				zkontrolovano, chyba);
	}

	StavMapy sServerem(final Server s, final Instant datum, final long velikost, final String et, final String lm, final Instant kdy) {
		return new StavMapy(mapa, mistni, s, faze, stazeno, celkem, rozpracovano, datumDatMistni, velikostMistni, datum, velikost, et, lm, kdy, chyba);
	}

	StavMapy sServerem(final Server s) {
		return new StavMapy(mapa, mistni, s, faze, stazeno, celkem, rozpracovano, datumDatMistni, velikostMistni, datumNaServeru, velikostNaServeru, etag,
				lastModified, zkontrolovano, chyba);
	}

	/** Je server novější než mapa na disku: jiná velikost a ne starší než data na disku. */
	Server porovnej() {
		if (mistni != Mistni.MAME || velikostNaServeru < 0) {
			return server;
		}
		if (velikostNaServeru == velikostMistni) {
			return Server.AKTUALNI;
		}
		if (datumNaServeru != null && datumDatMistni != null && !datumNaServeru.isAfter(datumDatMistni)) {
			return Server.AKTUALNI;
		}
		return Server.NOVEJSI;
	}

	public KatalogovaMapa getMapa() {
		return mapa;
	}

	public Mistni getMistni() {
		return mistni;
	}

	public Server getServer() {
		return server;
	}

	public Faze getFaze() {
		return faze;
	}

	/** Bajtů na disku včetně dřív rozpracovaných. */
	public long getStazeno() {
		return stazeno;
	}

	/** Celková velikost, -1 když není známá. */
	public long getCelkem() {
		return celkem;
	}

	/** Na disku je rozpracované stahování, které lze dokončit. */
	public boolean isRozpracovano() {
		return rozpracovano;
	}

	/** Datum vytvoření mapy na disku podle její hlavičky; null když ji nemáme. */
	public Instant getDatumDatMistni() {
		return datumDatMistni;
	}

	/** Velikost mapy na disku, -1 když ji nemáme. */
	public long getVelikostMistni() {
		return velikostMistni;
	}

	/** Last-Modified ze serveru, null když nebyl dotázán. */
	public Instant getDatumNaServeru() {
		return datumNaServeru;
	}

	public long getVelikostNaServeru() {
		return velikostNaServeru;
	}

	String getEtag() {
		return etag;
	}

	String getLastModified() {
		return lastModified;
	}

	/** Kdy se naposledy ptal server, null = nikdy. */
	public Instant getZkontrolovano() {
		return zkontrolovano;
	}

	/** Chyba pro uživatele, null bez chyby. */
	public String getChyba() {
		return chyba;
	}

	public boolean isBezi() {
		return faze == Faze.KONTROLUJE || faze == Faze.STAHUJE || faze == Faze.OVERUJE;
	}

	@Override
	public String toString() {
		return mapa + " " + mistni + "/" + server + "/" + faze + " " + stazeno + "/" + celkem + (chyba == null ? "" : " " + chyba);
	}
}
