package cz.geokuk.plugins.challenge;

/** Nenalezená keš, která by zaplnila chybějící políčko challenge. */
public final class KandidatPolicka {

	private final String id;
	private final double lat;
	private final double lon;
	private final String stitek;
	private final Integer dniDo;

	KandidatPolicka(final String id, final double lat, final double lon, final String stitek, final Integer dniDo) {
		this.id = id;
		this.lat = lat;
		this.lon = lon;
		this.stitek = stitek;
		this.dniDo = dniDo;
	}

	public String id() {
		return id;
	}

	public double lat() {
		return lat;
	}

	public double lon() {
		return lon;
	}

	/** Štítek políčka, které keš zaplní, například „D4/T1“. */
	public String stitek() {
		return stitek;
	}

	/** Za kolik dní nastane datum keše (0 = dnes); null u challenge bez času. */
	public Integer dniDo() {
		return dniDo;
	}
}
