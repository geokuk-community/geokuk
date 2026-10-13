package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.*;

/**
 * Výběr vrstev tématu offline mapy uživatelem: jen odchylky od výběru autora tématu, takže vrstvy z nové verze tématu se zapnou podle autora.
 * Textová podoba pro nastavení: {@code styl|+vrstva,-vrstva}, prázdný styl = styl autora.
 */
public final class VolbaVrstev {

	public static final VolbaVrstev AUTOR = new VolbaVrstev(null, Collections.emptyMap());

	/** Zvolený styl, null = výchozí styl autora. */
	private final String styl;
	/** Vrstvy zapnuté (true) nebo vypnuté (false) jinak, než je má autor. */
	private final SortedMap<String, Boolean> zmeny;

	private VolbaVrstev(final String styl, final Map<String, Boolean> zmeny) {
		this.styl = styl == null || styl.isEmpty() ? null : styl;
		this.zmeny = Collections.unmodifiableSortedMap(new TreeMap<>(zmeny));
	}

	public String getStyl() {
		return styl;
	}

	public boolean isAutor() {
		return styl == null && zmeny.isEmpty();
	}

	/** Zvolený styl; výchozí styl autora se neukládá jako odchylka. */
	public VolbaVrstev seStylem(final String novy, final String vychoziAutora) {
		return new VolbaVrstev(Objects.equals(novy, vychoziAutora) ? null : novy, zmeny);
	}

	/** Zapnutí vrstvy; stav, jaký má autor, se neukládá jako odchylka. */
	public VolbaVrstev sVrstvou(final String vrstva, final boolean zapnuta, final boolean zapnutaAutorem) {
		final Map<String, Boolean> nove = new TreeMap<>(zmeny);
		if (zapnuta == zapnutaAutorem) {
			nove.remove(vrstva);
		} else {
			nove.put(vrstva, zapnuta);
		}
		return new VolbaVrstev(styl, nove);
	}

	/** Je vrstva zapnutá: podle volby uživatele, jinak podle autora. */
	public boolean jeZapnuta(final String vrstva, final boolean zapnutaAutorem) {
		final Boolean z = zmeny.get(vrstva);
		return z == null ? zapnutaAutorem : z;
	}

	public String naText() {
		final StringBuilder sb = new StringBuilder(styl == null ? "" : styl).append('|');
		boolean prvni = true;
		for (final Map.Entry<String, Boolean> e : zmeny.entrySet()) {
			if (!prvni) {
				sb.append(',');
			}
			prvni = false;
			sb.append(e.getValue() ? '+' : '-').append(e.getKey());
		}
		return sb.toString();
	}

	/** Volba z nastavení; prázdný nebo nečitelný text je výběr autora. */
	public static VolbaVrstev zTextu(final String text) {
		if (text == null || text.indexOf('|') < 0) {
			return AUTOR;
		}
		final int i = text.indexOf('|');
		final Map<String, Boolean> zmeny = new TreeMap<>();
		for (final String z : text.substring(i + 1).split(",")) {
			if (z.length() > 1 && (z.charAt(0) == '+' || z.charAt(0) == '-')) {
				zmeny.put(z.substring(1), z.charAt(0) == '+');
			}
		}
		return new VolbaVrstev(text.substring(0, i), zmeny);
	}

	@Override
	public boolean equals(final Object o) {
		return o instanceof VolbaVrstev && Objects.equals(styl, ((VolbaVrstev) o).styl) && zmeny.equals(((VolbaVrstev) o).zmeny);
	}

	@Override
	public int hashCode() {
		return Objects.hash(styl, zmeny);
	}

	@Override
	public String toString() {
		return naText();
	}
}
