package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

public class LogoNacitaniTest {

	@Before
	public void obnovLogo() {
		LogoNacitani.obnovProTesty();
	}

	private static final int SIRKA = 800;
	private static final int VYSKA = 600;

	private static class Kachlovnik extends JKachlovnik {
		private static final long serialVersionUID = 1L;
		private final boolean logo;

		Kachlovnik(final boolean logo) {
			super("test", Priority.KACHLE);
			this.logo = logo;
			setSize(SIRKA, VYSKA);
		}

		@Override
		protected boolean kreslitLogoNacitani() {
			return logo;
		}
	}

	private static JKachle dlazdice() {
		final KaLoc loc = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);
		return new JKachle(null, new Ka(loc, EKaType.OFFLINE_MF));
	}

	private static Kachlovnik kachlovnikSDlazdici(final boolean logo, final JKachle dlazdice) {
		final Kachlovnik k = new Kachlovnik(logo);
		dlazdice.setLocation(272, 172);
		k.add(dlazdice);
		return k;
	}

	private static BufferedImage vykresliPozadi(final Kachlovnik k) {
		final BufferedImage img = new BufferedImage(SIRKA, VYSKA, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try {
			k.paintComponent(g);
		} finally {
			g.dispose();
		}
		return img;
	}

	private static boolean maBarevnyPixel(final BufferedImage img) {
		for (int y = 172; y < 428; y++) {
			for (int x = 272; x < 528; x++) {
				final int p = img.getRGB(x, y);
				if (p >>> 24 != 0 && (p & 0xFFFFFF) != 0xFFFFFF) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	public void cekajiciDlazdiceNekresliNicSama() {
		final JKachle d = dlazdice();
		d.setSize(256, 256);
		final BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try {
			d.paintComponent(g);
		} finally {
			g.dispose();
		}
		for (int y = 0; y < 256; y++) {
			for (int x = 0; x < 256; x++) {
				Assert.assertEquals(new Color(0, 0, 0, 0).getRGB(), img.getRGB(x, y));
			}
		}
	}

	@Test
	public void dlazdiceCekaDokudNemaObrazekNeboChybu() {
		final JKachle d = dlazdice();
		Assert.assertTrue(d.cekaNaObrazek());
		d.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
		Assert.assertFalse(d.cekaNaObrazek());
		final JKachle chybna = dlazdice();
		chybna.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		Assert.assertFalse(chybna.cekaNaObrazek());
	}

	@Test
	public void logoSeKresliJenPriCekani() {
		final JKachle d = dlazdice();
		final Kachlovnik k = kachlovnikSDlazdici(true, d);
		Assert.assertTrue(maBarevnyPixel(vykresliPozadi(k)));
		d.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
		Assert.assertFalse(maBarevnyPixel(vykresliPozadi(k)));
	}

	@Test
	public void logoSeNekresliPriChybe() {
		final JKachle d = dlazdice();
		final Kachlovnik k = kachlovnikSDlazdici(true, d);
		d.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		Assert.assertFalse(maBarevnyPixel(vykresliPozadi(k)));
	}

	@Test
	public void logoSeNekresliVKachlovnikuBezLoga() {
		final Kachlovnik k = kachlovnikSDlazdici(false, dlazdice());
		Assert.assertFalse(maBarevnyPixel(vykresliPozadi(k)));
	}

	@Test
	public void velikostLogaSeridiOknem() {
		Assert.assertEquals(240, LogoNacitani.velikost(800, 600, 1));
		Assert.assertEquals(480, LogoNacitani.velikost(800, 600, 2));
		Assert.assertEquals(0, LogoNacitani.velikost(100, 100, 1));
	}

	private static void vymenDlazdici(final Kachlovnik k, final JKachle stara, final JKachle nova) {
		k.remove(stara);
		nova.setLocation(272, 172);
		k.add(nova);
	}

	private static void nacti(final JKachle d) {
		d.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
	}

	@Test
	public void logoSeNekresliPoPrvnimUplnemNacteniVyrezu() {
		final JKachle d = dlazdice();
		final Kachlovnik k = kachlovnikSDlazdici(true, d);
		Assert.assertTrue("před načtením ano", maBarevnyPixel(vykresliPozadi(k)));
		nacti(d);
		Assert.assertFalse(maBarevnyPixel(vykresliPozadi(k)));
		final JKachle dalsi = dlazdice();
		vymenDlazdici(k, d, dalsi);
		Assert.assertFalse("při dalším čekání na dlaždice už ne", maBarevnyPixel(vykresliPozadi(k)));
	}

	@Test
	public void logoSeNekresliPoPrvniChybeDlazdice() {
		final JKachle d = dlazdice();
		final Kachlovnik k = kachlovnikSDlazdici(true, d);
		d.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		vykresliPozadi(k);
		final JKachle dalsi = dlazdice();
		vymenDlazdici(k, d, dalsi);
		Assert.assertFalse(maBarevnyPixel(vykresliPozadi(k)));
	}

	@Test
	public void logoSeNekresliPoLimitu() {
		final long[] cas = { 5_000 };
		LogoNacitani.hodiny = () -> cas[0];
		final Kachlovnik k = kachlovnikSDlazdici(true, dlazdice());
		Assert.assertTrue(maBarevnyPixel(vykresliPozadi(k)));
		cas[0] = LogoNacitani.LIMIT_MS;
		Assert.assertTrue("přesně na limitu ještě ano", maBarevnyPixel(vykresliPozadi(k)));
		cas[0] = LogoNacitani.LIMIT_MS + 1;
		Assert.assertFalse(maBarevnyPixel(vykresliPozadi(k)));
	}

	@Test
	public void prazdnyKachlovnikLogoNeukonci() {
		final Kachlovnik prazdny = new Kachlovnik(true);
		vykresliPozadi(prazdny);
		Assert.assertTrue(LogoNacitani.jeAktivni());
	}
}
