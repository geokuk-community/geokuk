package cz.geokuk.util.lang;

import org.junit.Assert;
import org.junit.Test;

/** Zlomek sekundy v textu se doplní nebo zkrátí na milisekundy. */
public class ATimestampMilisekundyTest {

	private static long milis(final String zlomek) {
		return ATimestamp.fromUtc("2020-01-02 03:04:05" + zlomek + " UTC").asLong() % 1000;
	}

	@Test
	public void zlomekSekundy() {
		Assert.assertEquals(0, milis(""));
		Assert.assertEquals(500, milis(".5"));
		Assert.assertEquals(120, milis(".12"));
		Assert.assertEquals(123, milis(".123"));
		Assert.assertEquals(123, milis(".123456"));
		Assert.assertEquals(7, milis(".007"));
	}
}
