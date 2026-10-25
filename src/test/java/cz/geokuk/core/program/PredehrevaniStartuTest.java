package cz.geokuk.core.program;

import org.junit.Test;

public class PredehrevaniStartuTest {

	/** Předehřátí běží vedle EDT a nesmí vyhodit výjimku ani při opakování. */
	@Test
	public void predehrejDobehneOpakovane() {
		PredehrevaniStartu.predehrej();
		PredehrevaniStartu.predehrej();
	}
}
