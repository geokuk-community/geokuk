package cz.geokuk.core.coord;

import static org.junit.Assert.*;

import org.junit.Test;

import cz.geokuk.core.coordinates.*;

public class OpenStreetViewActionTest {

	private static final Mou MISTO = new Wgs(50.08, 14.42).toMou();

	@Test
	public void bezMistaAkceBerePozici() {
		final OpenStreetViewAction akce = new OpenStreetViewAction(null);
		akce.onEvent(new PoziceChangedEvent(new Poziceq(() -> MISTO)));
		assertEquals(MISTO.toWgs().lat, akce.misto().lat, 1e-9);
		assertEquals(MISTO.toWgs().lon, akce.misto().lon, 1e-9);
	}

	@Test
	public void bezMistaAPoziceNicNeotevre() {
		assertNull(new OpenStreetViewAction(null).misto());
	}
}
