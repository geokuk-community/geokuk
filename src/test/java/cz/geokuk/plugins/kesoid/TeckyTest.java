package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;

import org.junit.Test;

import cz.geokuk.plugins.kesoid.Tecky.Styl;

public class TeckyTest {

	private static final Dimension OKNO = new Dimension(1000, 800);

	@Test
	public void prumerPodleHustoty() {
		assertEquals("bez keší největší", Tecky.MAX_PRUMER, Tecky.prumer(0, OKNO));
		assertEquals("málo keší největší", Tecky.MAX_PRUMER, Tecky.prumer(10, OKNO));
		assertEquals("tečky pokryjí okno zhruba dvakrát", 9, Tecky.prumer(20_000, OKNO));
		assertEquals("hodně keší nejmenší", Tecky.MIN_PRUMER, Tecky.prumer(1_000_000, OKNO));
	}

	@Test
	public void barvaPodleTypu() {
		assertEquals(Tecky.TRADICNI, Tecky.barvaTypu("Traditional Cache"));
		assertEquals(Tecky.MULTI, Tecky.barvaTypu("Multi-cache"));
		assertEquals(Tecky.MYSTERY, Tecky.barvaTypu("Unknown Cache"));
		assertEquals(Tecky.MYSTERY, Tecky.barvaTypu("Letterbox Hybrid"));
		assertEquals(Tecky.MYSTERY, Tecky.barvaTypu("Wherigo Cache"));
		assertEquals(Tecky.VIRTUALNI, Tecky.barvaTypu("Virtual Cache"));
		assertEquals(Tecky.VIRTUALNI, Tecky.barvaTypu("Webcam Cache"));
		assertEquals(Tecky.VIRTUALNI, Tecky.barvaTypu("Earthcache"));
		assertEquals(Tecky.EVENT, Tecky.barvaTypu("Event Cache"));
		assertEquals(Tecky.EVENT, Tecky.barvaTypu("Mega-Event Cache"));
		assertEquals(Tecky.EVENT, Tecky.barvaTypu("Cache In Trash Out Event"));
		assertEquals(Tecky.EVENT, Tecky.barvaTypu("Geocaching HQ Celebration"));
		assertEquals(Tecky.LAB, Tecky.barvaTypu("Lab Cache"));
		assertEquals(Tecky.OSTATNI, Tecky.barvaTypu("Project APE Cache"));
		assertEquals(Tecky.OSTATNI, Tecky.barvaTypu(null));
	}

	@Test
	public void nalezenaJeMensi() {
		final Tecky tecky = new Tecky();
		assertEquals(13, tecky.obrazek(Tecky.TRADICNI, Styl.BEZNA, false, 12).getWidth());
		assertEquals(9, tecky.obrazek(Tecky.TRADICNI, Styl.NALEZENA, false, 12).getWidth());
		assertEquals("nejmenší nalezená má aspoň 3 px", 4, tecky.obrazek(Tecky.TRADICNI, Styl.NALEZENA, false, 4).getWidth());
	}

	@Test
	public void bezPruhlednostiAMichaniBarev() {
		final Tecky tecky = new Tecky();
		for (final Styl styl : Styl.values()) {
			final BufferedImage img = tecky.obrazek(Tecky.MULTI, styl, false, 12);
			for (int x = 0; x < img.getWidth(); x++) {
				for (int y = 0; y < img.getHeight(); y++) {
					final int alfa = img.getRGB(x, y) >>> 24;
					assertTrue(styl + " pixel " + x + "," + y + " alfa " + alfa, alfa == 0 || alfa == 255);
				}
			}
		}
	}

	@Test
	public void stredMaBarvuTypuNeaktivniJeSvetlejsi() {
		final Tecky tecky = new Tecky();
		assertEquals(Tecky.MYSTERY.getRGB(), tecky.obrazek(Tecky.MYSTERY, Styl.BEZNA, false, 12).getRGB(6, 6));
		final Color neaktivni = new Color(tecky.obrazek(Tecky.MYSTERY, Styl.BEZNA, true, 12).getRGB(6, 6));
		assertEquals(new Color((Tecky.MYSTERY.getRed() + 255) / 2, (Tecky.MYSTERY.getGreen() + 255) / 2, (Tecky.MYSTERY.getBlue() + 255) / 2), neaktivni);
	}

	@Test
	public void obrysJenUVetsichTecek() {
		final Tecky tecky = new Tecky();
		assertNotEquals("velká tečka má obrys", Tecky.TRADICNI.getRGB(), tecky.obrazek(Tecky.TRADICNI, Styl.BEZNA, false, 12).getRGB(6, 0));
		final BufferedImage mala = tecky.obrazek(Tecky.TRADICNI, Styl.BEZNA, false, 4);
		for (int x = 0; x < mala.getWidth(); x++) {
			for (int y = 0; y < mala.getHeight(); y++) {
				final int rgb = mala.getRGB(x, y);
				assertTrue("malá tečka je jen v barvě typu", rgb >>> 24 == 0 || rgb == Tecky.TRADICNI.getRGB());
			}
		}
	}

	@Test
	public void vlastniMaTmavyObrysVetsiTeckaSilnejsi() {
		final Tecky tecky = new Tecky();
		final BufferedImage velka = tecky.obrazek(Tecky.TRADICNI, Styl.VLASTNI, false, 12);
		assertEquals(0x111111, velka.getRGB(1, 5) & 0xFFFFFF);
		assertEquals("obrys 2 px", 0x111111, velka.getRGB(2, 5) & 0xFFFFFF);
		assertEquals(Tecky.TRADICNI.getRGB(), velka.getRGB(6, 5));
		final BufferedImage mala = tecky.obrazek(Tecky.TRADICNI, Styl.VLASTNI, false, 6);
		assertEquals(0x111111, mala.getRGB(1, 3) & 0xFFFFFF);
		assertEquals("obrys 1 px", Tecky.TRADICNI.getRGB(), mala.getRGB(2, 3));
	}

	@Test
	public void obrazekSeZnovuPouzije() {
		final Tecky tecky = new Tecky();
		assertSame(tecky.obrazek(Tecky.EVENT, Styl.VLASTNI, false, 10), tecky.obrazek(Tecky.EVENT, Styl.VLASTNI, false, 10));
		assertNotSame(tecky.obrazek(Tecky.EVENT, Styl.VLASTNI, false, 10), tecky.obrazek(Tecky.EVENT, Styl.VLASTNI, true, 10));
		assertNotSame(tecky.obrazek(Tecky.EVENT, Styl.VLASTNI, false, 10), tecky.obrazek(Tecky.EVENT, Styl.BEZNA, false, 10));
	}
}
