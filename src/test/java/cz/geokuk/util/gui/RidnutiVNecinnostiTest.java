package cz.geokuk.util.gui;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

public class RidnutiVNecinnostiTest {

	private static final long SEKUNDA = TimeUnit.SECONDS.toNanos(1);

	private long cas = 1000 * SEKUNDA;
	private boolean aktivni;
	private final RidnutiVNecinnosti r = new RidnutiVNecinnosti(30, TimeUnit.SECONDS, () -> aktivni, () -> cas);

	@Test
	public void neaktivniKontrolujeJenJednouZaInterval() {
		Assert.assertTrue("první kontrola vždy", r.ted());
		cas += 10 * SEKUNDA;
		Assert.assertFalse(r.ted());
		cas += 19 * SEKUNDA;
		Assert.assertFalse(r.ted());
		cas += SEKUNDA;
		Assert.assertTrue(r.ted());
	}

	@Test
	public void aktivniKontrolujeKazdyTik() {
		aktivni = true;
		Assert.assertTrue(r.ted());
		cas += SEKUNDA;
		Assert.assertTrue(r.ted());
		aktivni = false;
		cas += SEKUNDA;
		Assert.assertFalse(r.ted());
		aktivni = true;
		Assert.assertTrue("po návratu do programu hned", r.ted());
	}

	@Test
	public void prvniKontrolaProbehneIPriNizkemCasu() {
		final RidnutiVNecinnosti hned = new RidnutiVNecinnosti(30, TimeUnit.SECONDS, () -> false, () -> -5 * SEKUNDA);
		Assert.assertTrue("nanoTime může začínat kdekoli", hned.ted());
	}
}
