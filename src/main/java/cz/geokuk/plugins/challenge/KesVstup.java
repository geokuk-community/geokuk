package cz.geokuk.plugins.challenge;

import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.Wpt;

/** Přizpůsobení kešoidu z modelu programu pro výpočet challenge. */
public final class KesVstup implements VstupKes {

	private final Kesoid kesoid;
	private final Wpt hlavni;

	private KesVstup(final Kesoid kesoid, final Wpt hlavni) {
		this.kesoid = kesoid;
		this.hlavni = hlavni;
	}

	/** Null, když kešoid nemá hlavní waypoint se souřadnicemi. */
	public static KesVstup z(final Kesoid kesoid) {
		final Wpt w = kesoid.getMainWpt();
		return w == null || w.hasEmptyCoords() ? null : new KesVstup(kesoid, w);
	}

	@Override
	public String id() {
		return kesoid.getIdentifier();
	}

	@Override
	public double lat() {
		return hlavni.getWgs().lat;
	}

	@Override
	public double lon() {
		return hlavni.getWgs().lon;
	}

	@Override
	public boolean nalezena() {
		return kesoid.getVztah() == EKesVztah.FOUND;
	}

	@Override
	public boolean lab() {
		return symbolObsahuje("lab");
	}

	@Override
	public boolean locationless() {
		return symbolObsahuje("locationless");
	}

	@Override
	public StavLogu logNaGc() {
		return StavLogu.NEZNAMY;
	}

	private boolean symbolObsahuje(final String text) {
		final String sym = hlavni.getSym();
		return sym != null && sym.toLowerCase(java.util.Locale.ROOT).contains(text);
	}
}
