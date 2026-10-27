package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;

import lombok.Data;

@Data
public class KachloStav {
	private final Image img;
	private final Throwable thr;
	/** Odkud dlaždice přišla (paměť, disk, vykreslení, web); jen pro diagnostiku. */
	private String zdroj;

	public KachloStav(final Image img) {
		this.img = img;
		this.thr = null;
	}

	public KachloStav(final Throwable thr) {
		this.img = null;
		this.thr = thr;
	}

}