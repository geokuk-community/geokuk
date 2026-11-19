package cz.geokuk.plugins.mapy.kachle.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/** Atribuce v mapě: zkratky zdrojů, OpenStreetMap vždy první a vidět, řádek se vejde do šířky. */
public class AtribuceMapyTest {

	private static FontMetrics fm() {
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics().getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
	}

	@Test
	public void zkratkyZdroju() {
		assertEquals(Arrays.asList("© OpenStreetMap"), AtribuceMapy.zkrat("© přispěvatelé OpenStreetMap"));
		assertEquals(Arrays.asList("© Freemap Slovakia", "© OpenStreetMap"), AtribuceMapy.zkrat("© Freemap Slovakia, © OpenStreetMap contributors"));
		assertEquals("ČÚZK celé", Arrays.asList("© ČÚZK, CC BY 4.0"), AtribuceMapy.zkrat("© ČÚZK, CC BY 4.0"));
		assertEquals("Seznam.cz celé", Arrays.asList("© Seznam.cz, a.s. a další"), AtribuceMapy.zkrat("© Seznam.cz, a.s. a další"));
		assertEquals(Arrays.asList("Made with Natural Earth"), AtribuceMapy.zkrat("Made with Natural Earth"));
		assertEquals(Arrays.asList("© OpenStreetMap", "Made with Natural Earth"), AtribuceMapy.zkrat("© přispěvatelé OpenStreetMap, Made with Natural Earth"));
		assertTrue(AtribuceMapy.zkrat("").isEmpty());
	}

	@Test
	public void openStreetMapJeVzdyPrvni() {
		assertEquals(Arrays.asList("© OpenStreetMap", "Copernicus"), AtribuceMapy.slouc(Arrays.asList("Copernicus", "© OpenStreetMap", "© OpenStreetMap")));
	}

	@Test
	public void radekSeVejdeAZkratiOdKonce() {
		final FontMetrics fm = fm();
		final java.util.List<String> zdroje = Arrays.asList(AtribuceMapy.OSM, "Copernicus", "© Seznam.cz", "Made with Natural Earth");
		final AtribuceMapy.Rozlozeni siroke = AtribuceMapy.rozloz(zdroje, fm, 1000, 700, 800);
		assertEquals("© OpenStreetMap · Copernicus · © Seznam.cz · Made with Natural Earth", siroke.text);
		final AtribuceMapy.Rozlozeni uzke = AtribuceMapy.rozloz(zdroje, fm, 1000, 700, 170);
		assertTrue(uzke.text, uzke.text.startsWith(AtribuceMapy.OSM));
		assertTrue(uzke.text, uzke.text.endsWith("…"));
		assertTrue(uzke.pole.width <= 170);
		assertEquals("pole je u pravého a dolního okraje", 1000, uzke.pole.x + uzke.pole.width);
		assertEquals(700, uzke.pole.y + uzke.pole.height);
		assertTrue("ikona je uvnitř pole", uzke.pole.contains(uzke.ikona.getCenterX(), uzke.ikona.getCenterY()));
	}

	@Test
	public void velmiUzkeOknoZkratiIPrvniZdroj() {
		final AtribuceMapy.Rozlozeni r = AtribuceMapy.rozloz(Collections.singletonList(AtribuceMapy.OSM), fm(), 300, 200, 60);
		assertTrue(r.text, r.text.endsWith("…"));
		assertFalse(r.text.equals(AtribuceMapy.OSM));
		assertTrue(r.pole.width <= 60 + 2);
	}

	@Test
	public void meritkoMaOmezenouSirku() {
		assertEquals(960, cz.geokuk.plugins.mrizky.JMeritkoSlide.maximalniSirka(1920));
		assertEquals(150, cz.geokuk.plugins.mrizky.JMeritkoSlide.maximalniSirka(300));
	}

	@Test
	public void zkracenaAtribuceSePocitaJednouAJeNemenna() {
		final String zneni = "© Freemap Slovakia, © přispěvatelé OpenStreetMap, CC BY 4.0";
		final List<String> prvni = AtribuceMapy.zkrat(zneni);
		assertSame(prvni, AtribuceMapy.zkrat(zneni));
		assertEquals(Arrays.asList("© Freemap Slovakia", "© OpenStreetMap"), prvni);
		try {
			prvni.add("x");
			fail("výsledek se nesmí měnit");
		} catch (final UnsupportedOperationException e) {
			// čekané
		}
		assertTrue(AtribuceMapy.zkrat(null).isEmpty());
	}
}
