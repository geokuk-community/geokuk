package cz.geokuk.plugins.cesty.routovani;

import java.util.*;

import org.mapsforge.core.model.Tag;

/** Které cesty z offline mapy jsou pro pěší a jak moc se jim vyhýbat. */
final class PeskyProfil {

	/** Koeficient délky podle typu cesty; 1 = nejvhodnější. Typy, které tu nejsou, pěší nepoužije. */
	private static final Map<String, Float> KOEFICIENTY = new HashMap<>();

	static {
		for (final String s : new String[] { "footway", "path", "track", "pedestrian", "steps", "living_street", "residential", "service", "unclassified", "bridleway", "cycleway",
				"road" }) {
			KOEFICIENTY.put(s, 1f);
		}
		for (final String s : new String[] { "tertiary", "tertiary_link" }) {
			KOEFICIENTY.put(s, 1.1f);
		}
		for (final String s : new String[] { "secondary", "secondary_link" }) {
			KOEFICIENTY.put(s, 1.3f);
		}
		for (final String s : new String[] { "primary", "primary_link" }) {
			KOEFICIENTY.put(s, 1.5f);
		}
		for (final String s : new String[] { "trunk", "trunk_link" }) {
			KOEFICIENTY.put(s, 2f);
		}
	}

	/** Nejmenší koeficient, podle něj se odhaduje zbývající vzdálenost. */
	static final float MIN_KOEFICIENT = 1f;

	private PeskyProfil() {}

	/** Koeficient délky cesty s danými tagy, 0 = pěší ji nepoužije. */
	static float koeficient(final List<Tag> tagy) {
		String highway = null;
		String access = null;
		String foot = null;
		for (final Tag t : tagy) {
			switch (t.key) {
			case "highway":
				highway = t.value;
				break;
			case "access":
				access = t.value;
				break;
			case "foot":
				foot = t.value;
				break;
			default:
				break;
			}
		}
		final Float k = highway == null ? null : KOEFICIENTY.get(highway);
		if (k == null || "no".equals(foot)) {
			return 0;
		}
		final boolean pesiPovoleni = "yes".equals(foot) || "designated".equals(foot) || "permissive".equals(foot);
		if (!pesiPovoleni && ("private".equals(access) || "no".equals(access))) {
			return 0;
		}
		return k;
	}
}
