package cz.geokuk.plugins.kesoid.mvc;

import static org.junit.Assert.*;

import org.junit.Test;

public class ViditelnostSKesoidyTest {

	@Test
	public void bezUdalostiJeVidet() {
		assertTrue(new ViditelnostSKesoidy().isViditelne());
	}

	@Test
	public void viditelneJenKdyzJsouZapnuteKesoidyIVlastniVypinac() {
		final ViditelnostSKesoidy v = new ViditelnostSKesoidy();
		assertFalse(v.setKesoidy(false));
		assertFalse(v.setVlastni(true));
		assertTrue(v.setKesoidy(true));
		assertFalse(v.setVlastni(false));
		assertFalse(v.setKesoidy(false));
		assertFalse(v.isViditelne());
	}

	/** F5 zapnuto vrátí vrstvu přesně do stavu, který nastavil její vlastní vypínač, a ten se F5 nepřepisuje. */
	@Test
	public void zapnutiKesoiduVratiPredchoziStavVlastnihoVypinace() {
		final ViditelnostSKesoidy zapnute = new ViditelnostSKesoidy();
		zapnute.setVlastni(true);
		zapnute.setKesoidy(false);
		assertTrue("vlastní zapnuto, kešoidy znovu zapnuty: vidět", zapnute.setKesoidy(true));

		final ViditelnostSKesoidy vypnute = new ViditelnostSKesoidy();
		vypnute.setVlastni(false);
		vypnute.setKesoidy(false);
		assertFalse("vlastní vypnuto, kešoidy znovu zapnuty: dál nevidět", vypnute.setKesoidy(true));
	}

	@Test
	public void poradiPrichoduUdalostiPriStartuNerozhoduje() {
		final ViditelnostSKesoidy a = new ViditelnostSKesoidy();
		a.setKesoidy(false);
		a.setVlastni(true);
		final ViditelnostSKesoidy b = new ViditelnostSKesoidy();
		b.setVlastni(true);
		b.setKesoidy(false);
		assertEquals(a.isViditelne(), b.isViditelne());
		assertFalse(a.isViditelne());
	}
}
