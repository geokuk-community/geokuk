package cz.geokuk.plugins.challenge;

import cz.geokuk.plugins.kesoid.EKesStatus;
import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

/** Přizpůsobení kešoidu z modelu programu pro výpočet challenge. */
public final class KesVstup implements VstupKes {

	private final Kesoid kesoid;
	private final Wpt hlavni;

	private KesVstup(final Kesoid kesoid, final Wpt hlavni) {
		this.kesoid = kesoid;
		this.hlavni = hlavni;
	}

	/** Pro challenge se počítají jen keše z gc.com (kód GC…); jiné kešoidy (OC, waymarky, munzee) a kešoidy bez souřadnic dávají null. */
	public static KesVstup z(final Kesoid kesoid) {
		if (!(kesoid instanceof Kes) || kesoid.getIdentifier() == null || !kesoid.getIdentifier().startsWith("GC")) {
			return null;
		}
		final Wpt w = kesoid.getMainWpt();
		return w == null || w.hasEmptyCoords() ? null : new KesVstup(kesoid, w);
	}

	/** Keše z gc.com ze seznamu kešoidů, vytvářené až při průchodu; kešoidy, které se nepočítají, se přeskočí. */
	public static java.util.Collection<VstupKes> zKesoidu(final java.util.List<? extends Kesoid> kesoidy) {
		return new java.util.AbstractCollection<VstupKes>() {
			@Override
			public java.util.Iterator<VstupKes> iterator() {
				final java.util.Iterator<? extends Kesoid> it = kesoidy.iterator();
				return new java.util.Iterator<VstupKes>() {
					private VstupKes dalsi;

					@Override
					public boolean hasNext() {
						while (dalsi == null && it.hasNext()) {
							dalsi = z(it.next());
						}
						return dalsi != null;
					}

					@Override
					public VstupKes next() {
						if (!hasNext()) {
							throw new java.util.NoSuchElementException();
						}
						final VstupKes v = dalsi;
						dalsi = null;
						return v;
					}
				};
			}

			@Override
			public int size() {
				return kesoidy.size();
			}
		};
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
	public boolean vlastni() {
		return kesoid.getVztah() == EKesVztah.OWN;
	}

	@Override
	public boolean neaktivni() {
		return kesoid.getStatus() != EKesStatus.ACTIVE;
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
