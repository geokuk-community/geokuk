package cz.geokuk.core.program;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PredehrevaniStartuTest {

	/** Předehřátí běží vedle EDT a nesmí vyhodit výjimku ani při opakování. */
	@Test
	public void predehrejDobehneOpakovane() {
		PredehrevaniStartu.predehrej();
		PredehrevaniStartu.predehrej();
	}

	@Test
	public void znakyObsahujiCislicePismenaADiakritiku() {
		for (final char znak : "09%aZáčěřšžůÝ".toCharArray()) {
			assertTrue("chybí " + znak, PredehrevaniStartu.ZNAKY.indexOf(znak) >= 0);
		}
	}
}
