package cz.geokuk.plugins.kesoid;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Které zapnuté vrstvy kruhů se právě nekreslí a proč; z toho vzniká štítek ve stavovém řádku. Neměnný. */
public final class StavVrstev {

	/** Od tohoto přiblížení se Obsazenost 161 m kreslí (menší kruh než 3 px se nekreslí). */
	public static final int NEJNIZSI_ZOOM_OBSAZENOSTI = 11;
	public static final String TEXT_STITKU = "Kruhy skryté";

	/** Skrytá vrstva: důvod a limit, o který šlo (u limitu počtu). */
	public static final class Skryta {
		private final DuvodNekresleni duvod;
		private final int limit;

		Skryta(final DuvodNekresleni duvod, final int limit) {
			this.duvod = duvod;
			this.limit = limit;
		}

		public DuvodNekresleni getDuvod() {
			return duvod;
		}

		public int getLimit() {
			return limit;
		}

		@Override
		public boolean equals(final Object o) {
			if (!(o instanceof Skryta)) {
				return false;
			}
			final Skryta s = (Skryta) o;
			return duvod == s.duvod && limit == s.limit;
		}

		@Override
		public int hashCode() {
			return Objects.hash(duvod, limit);
		}
	}

	private static final StavVrstev PRAZDNY = new StavVrstev(Collections.emptyMap());

	private final Map<VrstvaNadMapou, Skryta> skryte;

	private StavVrstev(final Map<VrstvaNadMapou, Skryta> skryte) {
		this.skryte = skryte;
	}

	public static StavVrstev prazdny() {
		return PRAZDNY;
	}

	/** Nový stav, ve kterém vrstva má daný důvod; {@code KRESLI} nebo null ji ze skrytých vyřadí. */
	public StavVrstev s(final VrstvaNadMapou vrstva, final DuvodNekresleni duvod, final int limit) {
		final Map<VrstvaNadMapou, Skryta> m = new EnumMap<>(VrstvaNadMapou.class);
		m.putAll(skryte);
		if (duvod == null || duvod == DuvodNekresleni.KRESLI) {
			m.remove(vrstva);
		} else {
			m.put(vrstva, new Skryta(duvod, duvod == DuvodNekresleni.LIMIT_POCTU ? limit : 0));
		}
		return m.isEmpty() ? PRAZDNY : new StavVrstev(Collections.unmodifiableMap(m));
	}

	/** Je aspoň jedna zapnutá vrstva skrytá? */
	public boolean maSkryte() {
		return !skryte.isEmpty();
	}

	public Skryta get(final VrstvaNadMapou vrstva) {
		return skryte.get(vrstva);
	}

	public Map<VrstvaNadMapou, Skryta> getSkryte() {
		return skryte;
	}

	/** Text štítku ve stavovém řádku. */
	public String text() {
		return TEXT_STITKU;
	}

	/** Tooltip štítku: řádek pro každou skrytou vrstvu s důvodem. */
	public String tooltip() {
		final StringBuilder sb = new StringBuilder("<html>");
		boolean prvni = true;
		for (final Map.Entry<VrstvaNadMapou, Skryta> e : skryte.entrySet()) {
			if (!prvni) {
				sb.append("<br/>");
			}
			prvni = false;
			sb.append(e.getKey().getNazev()).append(": ").append(textDuvodu(e.getKey(), e.getValue().getDuvod(), e.getValue().getLimit()));
		}
		return sb.append("</html>").toString();
	}

	/** Důvod česky pro danou vrstvu. */
	public static String textDuvodu(final VrstvaNadMapou vrstva, final DuvodNekresleni duvod, final int limit) {
		switch (duvod) {
		case LIMIT_POCTU:
			return String.format(new Locale("cs"), "příliš mnoho bodů ve výřezu (limit %,d), přibližte mapu", limit);
		case PRILIS_HUSTE:
			return "příliš husté, přibližte mapu";
		case POLOMER_PRILIS_MALY:
			return vrstva == VrstvaNadMapou.OBSAZENOST ? "nezobrazuje se pod zoomem " + NEJNIZSI_ZOOM_OBSAZENOSTI + ", přibližte mapu" : "poloměr pod 3 px, přibližte mapu";
		default:
			return "";
		}
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof StavVrstev && skryte.equals(((StavVrstev) o).skryte);
	}

	@Override
	public int hashCode() {
		return skryte.hashCode();
	}

	@Override
	public String toString() {
		return "StavVrstev" + skryte.keySet();
	}
}
