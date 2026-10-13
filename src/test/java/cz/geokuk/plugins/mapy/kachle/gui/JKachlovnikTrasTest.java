package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.PrekryvTrasEvent;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** Překryv tras: vždy dlaždice tras, viditelný jen zapnutý a ne nad offline mapou, která trasy kreslí sama. */
public class JKachlovnikTrasTest {

	private static final KaLoc LOC = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);

	@Test
	public void dlazdiceJsouVzdyTrasy() {
		final JKachlovnikTras k = new JKachlovnikTras();
		k.setKachloType(EKaType.OPEN_STREET);
		Assert.assertSame(EKaType.TRASY, k.getKachloType());
		final JKachlovnikRendrovaci r = (JKachlovnikRendrovaci) k.createRenderableSlide();
		r.setKachloType(EKaType.CUZK_ORTO);
		Assert.assertSame("do tisku a exportu jdou také trasy", EKaType.TRASY, r.getKachloType());
	}

	@Test
	public void viditelnostPodleUdalosti() {
		final JKachlovnikTras k = new JKachlovnikTras();
		Assert.assertFalse("výchozí vypnuto", k.isVisible());
		k.onEvent(new PrekryvTrasEvent(true, false));
		Assert.assertTrue(k.isVisible());
		k.onEvent(new PrekryvTrasEvent(true, true));
		Assert.assertFalse("nad offline mapou s trasami se nekreslí", k.isVisible());
		k.onEvent(new PrekryvTrasEvent(false, false));
		Assert.assertFalse(k.isVisible());
	}

	@Test
	public void atribuceJenKdyzJiPodkladNema() {
		Assert.assertTrue(JKachlovnikTras.potrebujeAtribuci(EKaType.CUZK_ORTO));
		Assert.assertTrue(JKachlovnikTras.potrebujeAtribuci(EKaType.TURIST_M));
		Assert.assertFalse(JKachlovnikTras.potrebujeAtribuci(EKaType.OPEN_STREET));
		Assert.assertFalse(JKachlovnikTras.potrebujeAtribuci(EKaType.OFFLINE_MF));
		Assert.assertFalse(JKachlovnikTras.potrebujeAtribuci(null));
		Assert.assertEquals("Trasy © přispěvatelé OpenStreetMap", JKachlovnikTras.ATRIBUCE);
	}

	private static int nepruhlednych(final JKachle jkachle) {
		final BufferedImage img = new BufferedImage(JKachle.KACHLE_WIDTH, JKachle.KACHLE_WIDTH, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		jkachle.paintComponent(g);
		g.dispose();
		int n = 0;
		for (final int p : img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth())) {
			if (p >>> 24 != 0) {
				n++;
			}
		}
		return n;
	}

	/** Rozpracovaná ani chybná dlaždice překryvu nezakryje podklad; dlaždice podkladu v tu chvíli něco kreslí. */
	@Test
	public void prekryvBezObrazkuNicNekresli() {
		final JKachle trasy = new JKachle(null, new Ka(LOC, EKaType.TRASY));
		final JKachle podklad = new JKachle(null, new Ka(LOC, EKaType.OFFLINE_MF));
		Assert.assertTrue(nepruhlednych(podklad) > 0);
		Assert.assertEquals(0, nepruhlednych(trasy));
		trasy.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		podklad.prijmi(new KachloStav(new RuntimeException("chyba")), Priority.KACHLE);
		Assert.assertTrue(nepruhlednych(podklad) > 0);
		Assert.assertEquals(0, nepruhlednych(trasy));
	}
}
