package cz.geokuk.testy;

import org.junit.Assert;
import org.junit.Test;

public class ZabranaZaseknutiTest {

	@Test
	public void vypisVlakenObsahujeHlavniVlakno() {
		final String vypis = ZabranaZaseknuti.vypisVlaken();
		Assert.assertTrue(vypis, vypis.contains("\"main\"") || vypis.contains("RUNNABLE"));
		Assert.assertTrue(vypis.contains("vypisVlaken"));
	}

	@Test
	public void tridyAwtSeInicializujiBezVyjimky() throws Exception {
		new ZabranaZaseknuti().testRunStarted(null);
		new ZabranaZaseknuti().testRunStarted(null);
	}
}
