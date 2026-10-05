package cz.geokuk.plugins.kesoid.mapicon;

import static org.junit.Assert.assertEquals;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.KachloDownloader;
import cz.geokuk.util.pocitadla.*;

/** Servisní okno a hlášení chyby musí počitadla od sebe rozlišit. */
public class PocitadlaSkelTest {

	private static long pocet(final String jmeno) {
		synchronized (SpravcePocitadel.getPocitadla()) {
			return SpravcePocitadel.getPocitadla().stream().filter(p -> p.getName().equals(jmeno)).count();
		}
	}

	@Test
	public void kazdeSkloMaVlastniJmenaPocitadel() {
		final Sklo a = new Sklo("testA");
		final Sklo b = new Sklo("testB");
		assertEquals(1, pocet("Imagant - počet (testA)"));
		assertEquals(1, pocet("Imagant - počet (testB)"));
		assertEquals(1, pocet("Zdrojové obrázky - počet (testA)"));
		assertEquals(1, pocet("Zdrojové obrázky - počet (testB)"));
		assertEquals(Arrays.asList("testA", "testB"), Arrays.asList(a, b).stream().map(Sklo::getName).collect(Collectors.toList()));
	}

	@Test
	public void stazeneDlazdiceJednoPocitadlo() {
		final List<KachloDownloader> dva = Arrays.asList(new KachloDownloader(), new KachloDownloader());
		assertEquals(1, pocet("Downloadlé dlaždice"));
		assertEquals(2, dva.size());
	}
}
