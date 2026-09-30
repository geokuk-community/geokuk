package cz.geokuk.plugins.kesoid.hledani;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.Test;

import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.importek.ImportKesiTestPristup;

public class HledacTest {

	private static List<String> najdi(final String vzorek, final boolean regularni, final String... kody) throws Exception {
		final String[] wpt = new String[kody.length];
		for (int i = 0; i < kody.length; i++) {
			wpt[i] = ImportKesiTestPristup.kesBezHodnoceni(kody[i]);
		}
		final KesBag bag = ImportKesiTestPristup.importuj(wpt);
		final HledaciPodminka p = new HledaciPodminka();
		p.setVzorek(vzorek);
		p.setRegularniVyraz(regularni);
		final List<String> nalezene = new ArrayList<>();
		for (final Nalezenec n : new Hledac(bag).hledej(p)) {
			nalezene.add(n.getKes().getIdentifier());
		}
		Collections.sort(nalezene);
		return nalezene;
	}

	@Test
	public void podleKodu() throws Exception {
		assertEquals(Arrays.asList("GC1234"), najdi("gc12", false, "GC1234", "GC9999"));
	}

	@Test
	public void podleNazvuBezDiakritiky() throws Exception {
		// Název keše z importu je „Keš GCxxxx“.
		assertEquals(Arrays.asList("GC1234", "GC9999"), najdi("kes", false, "GC1234", "GC9999"));
	}

	@Test
	public void regularniVyrazSVelkymiTridami() throws Exception {
		// \D je nečíslice; malé \d by našlo i kódy s číslicí na tom místě.
		assertEquals(Arrays.asList("GCABCD"), najdi("^GC\\D\\D\\D\\D$", true, "GC1234", "GCABCD"));
	}
}
