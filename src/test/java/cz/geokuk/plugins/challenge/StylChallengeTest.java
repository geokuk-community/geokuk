package cz.geokuk.plugins.challenge;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class StylChallengeTest {

	@Test
	public void kazdyTypMaSvouBarvuBezZelenePerven() {
		final Set<Color> barvy = new HashSet<>();
		for (final Challenge.Typ t : Challenge.Typ.values()) {
			final Challenge c = new Challenge("x", t, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC);
			final Color b = StylChallenge.barva(c);
			Assert.assertTrue(t + " různé", barvy.add(b));
			Assert.assertTrue(t + " není zelená ani červená (stav sektoru)", !(b.getGreen() > b.getRed() + 40 && b.getGreen() > b.getBlue() + 40) && !(b.getRed() > b.getGreen() + 90 && b.getRed() > b.getBlue() + 90 && b.getGreen() < 100));
		}
		Assert.assertEquals(Challenge.Typ.values().length, barvy.size());
	}
}
