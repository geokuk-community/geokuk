package cz.geokuk.plugins.kesoid;

import static cz.geokuk.plugins.kesoid.kind.kes.EKesDiffTerRating.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.kind.kes.EKesDiffTerRating;

/** Obtížnost a terén: čtení z GPX a zobrazení v ikonách a popiscích. */
public class EKesDiffTerRatingTest {

	@Test
	public void parseVsechHodnot() {
		final String[] texty = { "1", "1.5", "2", "2.5", "3", "3.5", "4", "4.5", "5" };
		for (int i = 0; i < texty.length; i++) {
			Assert.assertEquals(texty[i], EKesDiffTerRating.values()[i], EKesDiffTerRating.parse(texty[i]));
		}
		Assert.assertEquals(TWO, EKesDiffTerRating.parse("2.0"));
		Assert.assertEquals(FIVE, EKesDiffTerRating.parse(" 5 ".trim()));
	}

	@Test
	public void neplatneHodnotyJsouNezname() {
		for (final String text : new String[] { null, "", "abc", "0", "0.5", "5.5", "6", "-1" }) {
			Assert.assertEquals(String.valueOf(text), UNKNOWN, EKesDiffTerRating.parse(text));
		}
	}

	@Test
	public void cisloProZobrazeni() {
		Assert.assertEquals("1.0", ONE.toNumberString());
		Assert.assertEquals("2.5", TWO_HALF.toNumberString());
		Assert.assertEquals("5.0", FIVE.toNumberString());
		Assert.assertEquals("0", UNKNOWN.toNumberString());
	}

	@Test
	public void dvoumistneCislo() {
		final String[] ocekavane = { "10", "15", "20", "25", "30", "35", "40", "45", "50", "0" };
		for (final EKesDiffTerRating r : EKesDiffTerRating.values()) {
			Assert.assertEquals(r.name(), ocekavane[r.ordinal()], r.to2DigitNumberString());
		}
	}

	@Test
	public void jedenZnakProIkony() {
		Assert.assertEquals("1A2B3C4D5?", new String(new char[] { ONE.toSingleChar(), ONE_HALF.toSingleChar(), TWO.toSingleChar(), TWO_HALF.toSingleChar(), THREE.toSingleChar(),
				THREE_HALF.toSingleChar(), FOUR.toSingleChar(), FOUR_HALF.toSingleChar(), FIVE.toSingleChar(), UNKNOWN.toSingleChar() }));
	}

	@Test
	public void kazdaHodnotaSePrecteZeSvehoCisla() {
		for (final EKesDiffTerRating r : EKesDiffTerRating.values()) {
			if (r != UNKNOWN) {
				Assert.assertEquals(r, EKesDiffTerRating.parse(r.toNumberString()));
			}
		}
	}
}
