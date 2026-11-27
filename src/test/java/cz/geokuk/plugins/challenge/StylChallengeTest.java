package cz.geokuk.plugins.challenge;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

public class StylChallengeTest {

	private static double jas(final Color c) {
		final double[] v = { c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0 };
		for (int i = 0; i < 3; i++) {
			v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
		}
		return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
	}

	private static double kontrast(final Color a, final Color b) {
		final double x = jas(a);
		final double y = jas(b);
		return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
	}

	private static Challenge challenge(final Challenge.Typ t) {
		return new Challenge("x", t, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC);
	}

	@Test
	public void kazdyTypMaSvouBarvu() {
		final Set<Color> barvy = new HashSet<>();
		for (final Challenge.Typ t : Challenge.Typ.values()) {
			Assert.assertTrue(t + " různé", barvy.add(StylChallenge.barva(challenge(t))));
		}
	}

	/** Bílý text na barvě (chip, štítek) a barva na světlé mapě i s lemem (značky); stejné v Ocean, Steel i Charcoal, protože štítek má vlastní podklad. */
	@Test
	public void barvaMaDostatecnyKontrastKBilemuTextuAKSvetleMape() {
		final Color mapa = new Color(0xF1EEE8);
		final Color les = new Color(0xCDE2B8);
		for (final Challenge.Typ t : Challenge.Typ.values()) {
			final Color b = StylChallenge.barva(challenge(t));
			Assert.assertTrue(t + " bílý text " + kontrast(b, Color.WHITE), kontrast(b, Color.WHITE) >= 4.5);
			Assert.assertTrue(t + " mapa " + kontrast(b, mapa), kontrast(b, mapa) >= 4.0);
			Assert.assertTrue(t + " les " + kontrast(b, les), kontrast(b, les) >= 3.0);
		}
	}

	@Test
	public void barvaNeniZelenaAniCervenaVyhrazenaProStavSektoru() {
		for (final Challenge.Typ t : Challenge.Typ.values()) {
			final Color b = StylChallenge.barva(challenge(t));
			Assert.assertFalse(t + " zelená", b.getGreen() > b.getRed() + 40 && b.getGreen() > b.getBlue() + 40);
			Assert.assertFalse(t + " červená", b.getRed() > b.getGreen() + 90 && b.getRed() > b.getBlue() + 90 && b.getGreen() < 100);
		}
	}
}
