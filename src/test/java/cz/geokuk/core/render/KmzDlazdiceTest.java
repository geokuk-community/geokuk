package cz.geokuk.core.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.*;

/** Rozdělení obrázku pro Google Earth na dlaždice a obsah souboru KMZ (LatLonBox, pořadí, rotace). */
public class KmzDlazdiceTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static List<DlazdicovaMetrikaXY.Dlazdice> dlazdice(final DlazdicovaMetrikaXY metrika) {
		final List<DlazdicovaMetrikaXY.Dlazdice> vysledek = new ArrayList<>();
		metrika.forEach(vysledek::add);
		return vysledek;
	}

	private static final int[] SIRKY = { 99, 790, 791, 800, 809, 810, 811, 1600, 1824, 4000 };
	private static final int[] VYSKY = { 100, 892, 893, 1600, 3999 };

	@Test
	public void dlazdiceNavazujiBezDerPocetSediAZacinajiOdKrajeObrazku() {
		for (final int max : new int[] { 400, 800, 1000 }) {
			for (final int w : SIRKY) {
				for (final int h : VYSKY) {
					final DlazdicovaMetrikaXY m = new DlazdicovaMetrikaXY(new DlazdicovaMetrika(max, w), new DlazdicovaMetrika(max, h));
					final List<DlazdicovaMetrikaXY.Dlazdice> vse = dlazdice(m);
					final String kde = "max " + max + " " + w + "×" + h;
					Assert.assertEquals(kde + ": počet dlaždic pro průběh", m.getPcoetDlazdic(), vse.size());
					final Set<String> cisla = new HashSet<>();
					for (final DlazdicovaMetrikaXY.Dlazdice d : vse) {
						Assert.assertTrue(kde + ": číslo dlaždice je jedinečné", cisla.add(d.xn + "," + d.yn));
						Assert.assertTrue(kde + ": dlaždice " + d.dim, d.dim.width <= max && d.dim.height <= max);
						final int levy = d.xs - d.dim.width / 2;
						final int horni = d.ys - d.dim.height / 2;
						Assert.assertTrue(kde + ": první sloupec/řádek začíná na okraji", d.xn > 0 || levy == 0);
						Assert.assertTrue(kde, d.yn > 0 || horni == 0);
						Assert.assertTrue(kde + ": mezi sousedy není díra", d.xn == 0 || levy < (d.xn - 0) * d.dim.width);
					}
				}
			}
		}
	}

	@Test
	public void dlazdicePokryvajiCelyObrazek() {
		for (final int w : SIRKY) {
			for (final int h : VYSKY) {
				final DlazdicovaMetrikaXY m = new DlazdicovaMetrikaXY(new DlazdicovaMetrika(800, w), new DlazdicovaMetrika(800, h));
				int pravy = 0;
				int spodni = 0;
				for (final DlazdicovaMetrikaXY.Dlazdice d : dlazdice(m)) {
					pravy = Math.max(pravy, d.xs - d.dim.width / 2 + d.dim.width);
					spodni = Math.max(spodni, d.ys - d.dim.height / 2 + d.dim.height);
				}
				Assert.assertTrue(w + "×" + h + ": pokryto " + pravy + "×" + spodni, pravy >= w && spodni >= h);
			}
		}
	}

	private KmzParams params(final double sever, final double jih, final double zapad, final double vychod, final double rotace, final int x, final int y) {
		final KmzParams p = new KmzParams();
		p.sever = sever;
		p.jih = jih;
		p.zapad = zapad;
		p.vychod = vychod;
		p.rotation = rotace;
		p.xDlazdice = x;
		p.yDlazdice = y;
		p.drawOrder = 7;
		return p;
	}

	private static double cislo(final Element prvek, final String jmeno) {
		return Double.parseDouble(prvek.getElementsByTagName(jmeno).item(0).getTextContent());
	}

	@Test
	public void kmzMaLatLonBoxyObrazkyAPoradiDlazdic() throws Exception {
		final File kmz = tmp.newFile("test.kmz");
		final KmzWriter w = new KmzWriter(kmz, "Výlet žluťoučký", "Popis");
		w.appendDlazdici(new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB), params(50.1, 50.0, 14.0, 14.2, 0, 0, 0));
		w.appendDlazdici(new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB), params(50.1, 50.0, 14.2, 14.4, 12.5, 1, 0));
		w.finish();

		try (ZipFile zip = new ZipFile(kmz)) {
			final Set<String> jmena = new TreeSet<>();
			zip.stream().map(ZipEntry::getName).forEach(jmena::add);
			Assert.assertEquals(new TreeSet<>(Arrays.asList("doc.kml", "files/dlazdice1.png", "files/dlazdice2.png")), jmena);
			final Document doc;
			try (InputStream in = zip.getInputStream(zip.getEntry("doc.kml"))) {
				doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
			}
			Assert.assertEquals("Výlet žluťoučký", doc.getElementsByTagName("name").item(0).getTextContent());
			final NodeList overlaye = doc.getElementsByTagName("GroundOverlay");
			Assert.assertEquals(2, overlaye.getLength());
			final Element prvni = (Element) overlaye.item(0);
			final Element druhy = (Element) overlaye.item(1);
			Assert.assertEquals("[1,1]", prvni.getElementsByTagName("name").item(0).getTextContent());
			Assert.assertEquals("[2,1]", druhy.getElementsByTagName("name").item(0).getTextContent());
			Assert.assertEquals("files/dlazdice1.png", prvni.getElementsByTagName("href").item(0).getTextContent());
			Assert.assertEquals("7", prvni.getElementsByTagName("drawOrder").item(0).getTextContent());
			final Element box1 = (Element) prvni.getElementsByTagName("LatLonBox").item(0);
			Assert.assertEquals(50.1, cislo(box1, "north"), 1e-9);
			Assert.assertEquals(50.0, cislo(box1, "south"), 1e-9);
			Assert.assertEquals(14.0, cislo(box1, "west"), 1e-9);
			Assert.assertEquals(14.2, cislo(box1, "east"), 1e-9);
			Assert.assertEquals("bez natočení se rotace nezapisuje", 0, box1.getElementsByTagName("rotation").getLength());
			final Element box2 = (Element) druhy.getElementsByTagName("LatLonBox").item(0);
			Assert.assertEquals(14.2, cislo(box2, "west"), 1e-9);
			Assert.assertEquals(12.5, cislo(box2, "rotation"), 1e-9);
		}
	}
}
