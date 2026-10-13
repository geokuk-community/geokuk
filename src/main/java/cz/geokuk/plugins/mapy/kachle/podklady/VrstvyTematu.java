package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.*;

import org.mapsforge.map.rendertheme.XmlRenderThemeStyleLayer;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;

/** Nabídka vrstev tématu offline mapy pro menu: styly (vybírá se jeden) a v každém přepínatelné vrstvy. */
public final class VrstvyTematu {

	/** Styl nebo vrstva s názvem pro menu. */
	public static final class Polozka {
		private final String id;
		private final String nazev;
		private final boolean zapnutaAutorem;

		Polozka(final String id, final String nazev, final boolean zapnutaAutorem) {
			this.id = id;
			this.nazev = nazev;
			this.zapnutaAutorem = zapnutaAutorem;
		}

		public String getId() {
			return id;
		}

		public String getNazev() {
			return nazev;
		}

		public boolean isZapnutaAutorem() {
			return zapnutaAutorem;
		}
	}

	private final TemaOfflineMapy tema;
	private final String vychoziStyl;
	private final List<Polozka> styly = new ArrayList<>();
	private final Map<String, List<Polozka>> vrstvy = new HashMap<>();

	VrstvyTematu(final TemaOfflineMapy tema, final XmlRenderThemeStyleMenu menu) {
		this.tema = tema;
		vychoziStyl = menu.getDefaultValue();
		for (final XmlRenderThemeStyleLayer styl : menu.getLayers().values()) {
			if (!styl.isVisible()) {
				continue;
			}
			styly.add(new Polozka(styl.getId(), nazev(styl, menu.getDefaultLanguage()), true));
			final List<Polozka> overlaye = new ArrayList<>();
			for (final XmlRenderThemeStyleLayer o : styl.getOverlays()) {
				overlaye.add(new Polozka(o.getId(), nazev(o, menu.getDefaultLanguage()), o.isEnabled()));
			}
			vrstvy.put(styl.getId(), Collections.unmodifiableList(overlaye));
		}
	}

	/** Název česky, jinak anglicky, jinak ve výchozím jazyce tématu, jinak id. */
	static String nazev(final XmlRenderThemeStyleLayer vrstva, final String vychoziJazyk) {
		final Map<String, String> nazvy = vrstva.getTitles();
		for (final String jazyk : new String[] { "cs", "en", vychoziJazyk }) {
			final String n = jazyk == null ? null : nazvy.get(jazyk);
			if (n != null && !n.trim().isEmpty()) {
				return n.trim();
			}
		}
		return vrstva.getId();
	}

	/** Téma, ze kterého je nabídka (s výběrem vrstev, se kterým bylo načtené). */
	public TemaOfflineMapy getTema() {
		return tema;
	}

	public String getVychoziStyl() {
		return vychoziStyl;
	}

	public List<Polozka> getStyly() {
		return Collections.unmodifiableList(styly);
	}

	/** Přepínatelné vrstvy stylu. */
	public List<Polozka> getVrstvy(final String styl) {
		final List<Polozka> v = vrstvy.get(styl);
		return v == null ? Collections.emptyList() : v;
	}

	/** Styl podle volby, když ho téma nabízí, jinak výchozí styl autora. */
	public String zvolenyStyl(final VolbaVrstev volba) {
		return volba.getStyl() != null && vrstvy.containsKey(volba.getStyl()) ? volba.getStyl() : vychoziStyl;
	}
}
