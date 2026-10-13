package cz.geokuk.plugins.cesty.routovani;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.junit.Test;
import org.mapsforge.core.model.Tag;

public class PeskyProfilTest {

	private static float k(final String... klicHodnota) {
		final List<Tag> tagy = new ArrayList<>();
		for (int i = 0; i + 1 < klicHodnota.length; i += 2) {
			tagy.add(new Tag(klicHodnota[i], klicHodnota[i + 1]));
		}
		return PeskyProfil.koeficient(tagy);
	}

	@Test
	public void pesiCesty() {
		assertEquals(1f, k("highway", "path"), 0);
		assertEquals(1f, k("highway", "track", "tracktype", "grade3"), 0);
		assertEquals(1f, k("highway", "steps"), 0);
		assertEquals(1.5f, k("highway", "primary"), 0);
	}

	@Test
	public void nepouzitelne() {
		assertEquals(0f, k("highway", "motorway"), 0);
		assertEquals(0f, k("highway", "motorway_link"), 0);
		assertEquals(0f, k("highway", "construction"), 0);
		assertEquals(0f, k("waterway", "river"), 0);
		assertEquals(0f, k("highway", "footway", "foot", "no"), 0);
	}

	@Test
	public void soukromaJenSPovolenimProPesi() {
		assertEquals(0f, k("highway", "service", "access", "private"), 0);
		assertEquals(0f, k("highway", "track", "access", "no"), 0);
		assertEquals(1f, k("highway", "service", "access", "private", "foot", "yes"), 0);
		assertEquals(1f, k("highway", "track", "access", "no", "foot", "designated"), 0);
		assertEquals(1f, k("highway", "track", "access", "destination"), 0);
	}
}
