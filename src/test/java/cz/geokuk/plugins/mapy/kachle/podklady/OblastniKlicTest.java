package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mapsforge.core.model.BoundingBox;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.OblastniKlic.Zdroj;

/** Typ dlaždice v cache závisí jen na souborech, které dlaždici pokrývají. */
public class OblastniKlicTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final int Z = 10;
	/** Dlaždice, do které padají zdroje; sousedé jsou o jednu a o dvě dlaždice dál. */
	private static final int X = 550;
	private static final int Y = 350;

	private static KaLoc dlazdice(final int dx) {
		return KaLoc.ofXyz(X + dx, Y, Z);
	}

	/** Malý rozsah uprostřed dlaždice, takže sousední dlaždice se ho dotýkají jen přes okolí. */
	private static BoundingBox uvnitr() {
		final BoundingBox oblast = OblastniKlic.oblast(dlazdice(0));
		final double sirka = (oblast.maxLongitude - oblast.minLongitude) / 3;
		final double vyska = (oblast.maxLatitude - oblast.minLatitude) / 3;
		final double lon = oblast.minLongitude + sirka * 1.45;
		final double lat = oblast.minLatitude + vyska * 1.45;
		return new BoundingBox(lat, lon, lat + vyska * 0.1, lon + sirka * 0.1);
	}

	private static Zdroj zdroj(final BoundingBox rozsah, final String otisk) {
		return new Zdroj(rozsah, otisk);
	}

	private static String klic(final List<Zdroj> zdroje, final int dx) {
		return new OblastniKlic("o0000000a", zdroje).klic(dlazdice(dx));
	}

	@Test
	public void tvarKlice() {
		Assert.assertTrue(klic(Arrays.asList(zdroj(uvnitr(), "a")), 0), UklidOfflineCache.TYP.matcher(klic(Arrays.asList(zdroj(uvnitr(), "a")), 0)).matches());
	}

	@Test
	public void zmenaSouboruMimoDlazdiciJiNezneplatni() {
		final BoundingBox a = uvnitr();
		final BoundingBox b = new BoundingBox(10, 10, 11, 11);
		final String pred = klic(Arrays.asList(zdroj(a, "A"), zdroj(b, "B")), 0);
		Assert.assertEquals(pred, klic(Arrays.asList(zdroj(a, "A"), zdroj(b, "B-nové")), 0));
		Assert.assertEquals("přidaný soubor jinde dlaždici nemění", pred, klic(Arrays.asList(zdroj(a, "A"), zdroj(b, "B"), zdroj(new BoundingBox(-30, 100, -29, 101), "C")), 0));
		Assert.assertEquals("odebraný soubor jinde dlaždici nemění", pred, klic(Arrays.asList(zdroj(a, "A")), 0));
	}

	@Test
	public void zmenaSouboruKryjicihoDlazdiciJiZneplatni() {
		final BoundingBox a = uvnitr();
		final String pred = klic(Arrays.asList(zdroj(a, "A")), 0);
		Assert.assertNotEquals(pred, klic(Arrays.asList(zdroj(a, "A-nové")), 0));
		Assert.assertNotEquals("nový soubor v oblasti", pred, klic(Arrays.asList(zdroj(a, "A"), zdroj(a, "D")), 0));
		Assert.assertNotEquals("odebraný soubor z oblasti", pred, klic(Arrays.<Zdroj> asList(), 0));
	}

	/** Popisky a okraje se berou ze sousedních dlaždic: soubor uvnitř jedné se projeví i v sousedních, ne ve vzdálenějších. */
	@Test
	public void okoliDlazdiceSeZapocita() {
		final BoundingBox a = uvnitr();
		final List<Zdroj> pred = Arrays.asList(zdroj(a, "A"));
		final List<Zdroj> po = Arrays.asList(zdroj(a, "A-nové"));
		Assert.assertNotEquals(klic(pred, -1), klic(po, -1));
		Assert.assertNotEquals(klic(pred, 1), klic(po, 1));
		Assert.assertEquals(klic(pred, 3), klic(po, 3));
		Assert.assertEquals(klic(pred, -3), klic(po, -3));
	}

	@Test
	public void poradiSouboruVOblastiRozhoduje() {
		final BoundingBox a = uvnitr();
		final BoundingBox daleko = new BoundingBox(10, 10, 11, 11);
		final Zdroj x = zdroj(a, "X");
		final Zdroj y = zdroj(a, "Y");
		Assert.assertNotEquals(klic(Arrays.asList(x, y), 0), klic(Arrays.asList(y, x), 0));
		final Zdroj p = zdroj(daleko, "P");
		final Zdroj q = zdroj(new BoundingBox(20, 20, 21, 21), "Q");
		Assert.assertEquals("pořadí souborů mimo oblast nevadí", klic(Arrays.asList(x, p, q), 0), klic(Arrays.asList(x, q, p), 0));
	}

	@Test
	public void globalniCastMeniVsechnyDlazdice() {
		final List<Zdroj> zdroje = Arrays.asList(zdroj(uvnitr(), "A"));
		for (final int dx : new int[] { 0, 1, 5 }) {
			Assert.assertNotEquals(new OblastniKlic("o0000000a", zdroje).klic(dlazdice(dx)), new OblastniKlic("o0000000b", zdroje).klic(dlazdice(dx)));
		}
		Assert.assertNotEquals("podklad a překryv se nepletou", new OblastniKlic("o0000000a", zdroje).klic(dlazdice(0)), new OblastniKlic("t0000000a", zdroje).klic(dlazdice(0)));
	}

	@Test
	public void sestavaSeMeniPriJakekolivZmene() {
		final Zdroj a = zdroj(uvnitr(), "A");
		final Zdroj b = zdroj(new BoundingBox(10, 10, 11, 11), "B");
		final String s = new OblastniKlic("o0000000a", Arrays.asList(a, b)).getSestava();
		Assert.assertNotEquals(s, new OblastniKlic("o0000000a", Arrays.asList(a, zdroj(new BoundingBox(10, 10, 11, 11), "B-nové"))).getSestava());
		Assert.assertNotEquals(s, new OblastniKlic("o0000000a", Arrays.asList(a)).getSestava());
		Assert.assertEquals(s, new OblastniKlic("o0000000a", Arrays.asList(a, b)).getSestava());
	}

	@Test
	public void bunkaDemPodleJmena() {
		Assert.assertEquals(new BoundingBox(50, 14, 51, 15), OblastniKlic.bunkaDem("N50E014.hgt"));
		Assert.assertEquals(new BoundingBox(50, 14, 51, 15), OblastniKlic.bunkaDem("europe/cz/n50e014.HGT"));
		Assert.assertEquals(new BoundingBox(-10, -3, -9, -2), OblastniKlic.bunkaDem("S10W003.hgt"));
		Assert.assertNull(OblastniKlic.bunkaDem("czech.hgt"));
		Assert.assertNull(OblastniKlic.bunkaDem("N95E014.hgt"));
	}

	/** Stínování sousedí přes švy: DEM buňka ovlivní dlaždice u svého okraje, ne vzdálené. */
	@Test
	public void demBunkaPlatiProDlazdiceVJejimOkoli() throws Exception {
		final File hgt = tmp.newFile("N50E014.hgt");
		final List<Zdroj> zdroje = new ArrayList<>();
		OblastniKlic.dem(zdroje, hgt);
		final List<Zdroj> zmena = new ArrayList<>();
		Assert.assertTrue(hgt.setLastModified(hgt.lastModified() + 5000));
		OblastniKlic.dem(zmena, hgt);
		final OblastniKlic pred = new OblastniKlic("o0000000a", zdroje);
		final OblastniKlic po = new OblastniKlic("o0000000a", zmena);
		final KaLoc uvnitr = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(50.5, 14.5).toMou(), 14);
		final KaLoc pod = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(49.995, 14.5).toMou(), 14);
		final KaLoc daleko = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(49.5, 14.5).toMou(), 14);
		final KaLoc jinde = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(48.5, 11.5).toMou(), 14);
		Assert.assertNotEquals(pred.klic(uvnitr), po.klic(uvnitr));
		Assert.assertNotEquals("sousední dlaždice přes šev buňky", pred.klic(pod), po.klic(pod));
		Assert.assertEquals(pred.klic(daleko), po.klic(daleko));
		Assert.assertEquals(pred.klic(jinde), po.klic(jinde));
	}

	@Test
	public void demZipPlatiJenProSvéBunky() throws Exception {
		final File zip = tmp.newFile("cesko.zip");
		try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
			z.putNextEntry(new ZipEntry("N50E014.hgt"));
			z.write(1);
			z.closeEntry();
			z.putNextEntry(new ZipEntry("N49E015.hgt"));
			z.write(1);
			z.closeEntry();
		}
		final List<Zdroj> zdroje = new ArrayList<>();
		OblastniKlic.dem(zdroje, zip);
		Assert.assertEquals(2, zdroje.size());
	}

	@Test
	public void nerozpoznanyDemPlatiProCelySvet() throws Exception {
		final File hgt = tmp.newFile("neznamy.hgt");
		final File zip = tmp.newFile("rozbity.zip");
		final List<Zdroj> zdroje = new ArrayList<>();
		OblastniKlic.dem(zdroje, hgt);
		OblastniKlic.dem(zdroje, zip);
		Assert.assertEquals(2, zdroje.size());
		final KaLoc kdekoli = KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(-30, 120).toMou(), 14);
		Assert.assertNotEquals(OblastniKlic.bezSouboru("o0000000a").klic(kdekoli), new OblastniKlic("o0000000a", zdroje).klic(kdekoli));
	}

	/** Záložní vypnutí: každý soubor platí pro každou dlaždici. */
	@Test
	public void bezOblastniCastiMeniVsechnoVse() {
		OblastniKlic.oblastni = false;
		try {
			final BoundingBox b = new BoundingBox(10, 10, 11, 11);
			final String pred = klic(Arrays.asList(zdroj(uvnitr(), "A"), zdroj(b, "B")), 0);
			Assert.assertNotEquals(pred, klic(Arrays.asList(zdroj(uvnitr(), "A"), zdroj(b, "B-nové")), 0));
		} finally {
			OblastniKlic.oblastni = true;
		}
	}

	/** Soubor téhož jména se stejným rozsahem, ale jinou délkou nebo datem mapy, je jiný soubor. */
	@Test
	public void jinaDelkaNeboDatumMapyJeJinyKlic() throws Exception {
		final File a = new File(tmp.newFolder("a"), "mapa.map");
		final File b = new File(tmp.newFolder("b"), "mapa.map");
		java.nio.file.Files.write(a.toPath(), new byte[10]);
		java.nio.file.Files.write(b.toPath(), new byte[11]);
		Assert.assertTrue(b.setLastModified(a.lastModified()));
		final BoundingBox rozsah = uvnitr();
		final String zaklad = klic(Arrays.asList(OblastniKlic.soubor("mapa", a, rozsah, 1000)), 0);
		Assert.assertEquals(zaklad, klic(Arrays.asList(OblastniKlic.soubor("mapa", a, rozsah, 1000)), 0));
		Assert.assertNotEquals("jiná délka", zaklad, klic(Arrays.asList(OblastniKlic.soubor("mapa", b, rozsah, 1000)), 0));
		Assert.assertNotEquals("jiné datum mapy", zaklad, klic(Arrays.asList(OblastniKlic.soubor("mapa", a, rozsah, 2000)), 0));
	}

	/** Trojúhelník pod přeponou (lon 14,07–24,15, lat 49–55,23): obdélník souboru obsahuje Prahu, obrys ne. */
	private static cz.geokuk.plugins.mapy.sprava.PolygonPokryti polsko() throws Exception {
		return cz.geokuk.plugins.mapy.sprava.PolygonPokryti.nacti(new java.io.StringReader("polsko\n1\n 14.07 49.0\n 24.15 49.0\n 24.15 55.23\n 14.07 49.0\nEND\nEND\n"));
	}

	private static String klicSObrysem(final String otisk, final KaLoc loc) throws Exception {
		final BoundingBox obdelnik = new BoundingBox(48.99, 14.07, 55.23, 24.15);
		return new OblastniKlic("o0000000a", Arrays.asList(new Zdroj(obdelnik, otisk, polsko()))).klic(loc);
	}

	private static KaLoc na(final double lat, final double lon, final int zoom) {
		return KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(lat, lon).toMou(), zoom);
	}

	/** Velký obdélník souboru nezneplatní dlaždice, které leží v obdélníku, ale daleko od obrysu (Praha × Polsko). */
	@Test
	public void dlazdiceMimoObrysVObdelnikuSouboruSeNezneplatni() throws Exception {
		final KaLoc praha = na(50.08, 14.42, 12);
		Assert.assertEquals(klicSObrysem("polsko-1", praha), klicSObrysem("polsko-2", praha));
	}

	@Test
	public void dlazdiceUvnitrObrysuAUHranicSeZneplatni() throws Exception {
		final KaLoc uvnitr = na(51.0, 21.0, 12);
		Assert.assertNotEquals(klicSObrysem("polsko-1", uvnitr), klicSObrysem("polsko-2", uvnitr));
		// Přepona leží v lat = 49 + (lon - 14,07) * 0,618; v lon 18 je to 51,429; dlaždice 0,10° nad ní (mimo obrys) data ještě dostane.
		final KaLoc zaHranici = na(51.429 + 0.10, 18.0, 16);
		Assert.assertNotEquals("0,10° za obrysem", klicSObrysem("polsko-1", zaHranici), klicSObrysem("polsko-2", zaHranici));
		// Kolmo na přeponu (sklon 0,618) 0,14° od ní: data souboru tam ještě sahají, bez bufferu by dlaždice zůstala stará.
		final KaLoc v14 = na(51.429 + 0.14 / 0.852, 18.0, 16);
		Assert.assertNotEquals("0,14° za obrysem", klicSObrysem("polsko-1", v14), klicSObrysem("polsko-2", v14));
		final KaLoc daleko = na(51.429 + 0.80, 18.0, 16);
		Assert.assertEquals("0,7° za obrysem už ne", klicSObrysem("polsko-1", daleko), klicSObrysem("polsko-2", daleko));
	}

	/** Bez obrysu (soubor uživatele) platí celý obdélník jako dřív. */
	@Test
	public void bezObrysuPlatiObdelnik() {
		final KaLoc praha = na(50.08, 14.42, 12);
		final BoundingBox obdelnik = new BoundingBox(48.99, 14.07, 55.23, 24.15);
		Assert.assertNotEquals(new OblastniKlic("o0000000a", Arrays.asList(new Zdroj(obdelnik, "a"))).klic(praha), new OblastniKlic("o0000000a", Arrays.asList(new Zdroj(obdelnik, "b"))).klic(praha));
	}

	@Test
	public void obrysRasterNadDirouAHranicemi() throws Exception {
		final cz.geokuk.plugins.mapy.sprava.PolygonPokryti ctverec = cz.geokuk.plugins.mapy.sprava.PolygonPokryti.nacti(new java.io.StringReader("c\n1\n 10 10\n 12 10\n 12 12\n 10 12\nEND\nEND\n"));
		final ObrysRaster r = new ObrysRaster(ctverec, OblastniKlic.BUFFER_OBRYSU);
		Assert.assertTrue(r.zasahuje(new BoundingBox(10.9, 10.9, 11.0, 11.0)));
		Assert.assertTrue("do bufferu", r.zasahuje(new BoundingBox(12.1, 11, 12.12, 11.02)));
		Assert.assertFalse("daleko za bufferem", r.zasahuje(new BoundingBox(12.5, 11, 12.52, 11.02)));
		Assert.assertFalse(r.zasahuje(new BoundingBox(30, 30, 30.1, 30.1)));
		Assert.assertTrue("obdélník přes celý obrys", r.zasahuje(new BoundingBox(0, 0, 40, 40)));
	}

	private static BoundingBox sjednoceni(final BoundingBox a, final BoundingBox b) {
		return new BoundingBox(Math.min(a.minLatitude, b.minLatitude), Math.min(a.minLongitude, b.minLongitude), Math.max(a.maxLatitude, b.maxLatitude), Math.max(a.maxLongitude, b.maxLongitude));
	}

	/** Mimo sjednocený rozsah souborů se kreslí šedá plocha: dlaždice vyčnívající ze sjednocení závisí na celé sestavě, vnitřní ne. */
	@Test
	public void dlazdiceNaOkrajiSjednoceniZavisiNaCeleSestave() {
		final BoundingBox a = OblastniKlic.bezOkoli(dlazdice(0));
		final BoundingBox b = new BoundingBox(10, 10, 11, 11);
		final Zdroj za = zdroj(a, "A");
		final Zdroj zb = zdroj(b, "B");
		final OblastniKlic bez = new OblastniKlic("o0000000a", Arrays.asList(za), a);
		final OblastniKlic s = new OblastniKlic("o0000000a", Arrays.asList(za, zb), sjednoceni(a, b));
		Assert.assertEquals("vnitřní dlaždice vzdálený soubor nezajímá", bez.klic(dlazdice(0)), s.klic(dlazdice(0)));
		Assert.assertNotEquals("dlaždice vyčnívající ze sjednocení: přidaný vzdálený soubor mění šedou plochu", bez.klic(dlazdice(1)), s.klic(dlazdice(1)));
		Assert.assertEquals("dlaždice uvnitř sjednocení vzdálený soubor nezajímá ani na okraji souboru", bez.klic(dlazdice(-1)), s.klic(dlazdice(-1)));
	}

	@Test
	public void bezSjednoceniSeNeuplatni() {
		final BoundingBox a = OblastniKlic.bezOkoli(dlazdice(0));
		final Zdroj za = zdroj(a, "A");
		final Zdroj zb = zdroj(new BoundingBox(10, 10, 11, 11), "B");
		Assert.assertEquals(new OblastniKlic("o0000000a", Arrays.asList(za), null).klic(dlazdice(1)), new OblastniKlic("o0000000a", Arrays.asList(za, zb), null).klic(dlazdice(1)));
	}

	/** Obrys z katalogu platí jen pro soubor odpovídající katalogové mapě. */
	@Test
	public void obrysPlatiJenKdyzSouborOdpovidaMape() throws Exception {
		final cz.geokuk.plugins.mapy.sprava.PolygonPokryti p = polsko();
		Assert.assertSame(p, OblastniKlic.platnyObrys(p, new BoundingBox(48.99, 14.07, 55.23, 24.15)));
		Assert.assertSame("data o trochu za obrysem", p, OblastniKlic.platnyObrys(p, new BoundingBox(48.9, 13.95, 55.3, 24.3)));
		Assert.assertNull("oříznutý soubor", OblastniKlic.platnyObrys(p, new BoundingBox(50, 14.07, 55.23, 24.15)));
		Assert.assertNull("větší soubor", OblastniKlic.platnyObrys(p, new BoundingBox(45, 10, 58, 28)));
		Assert.assertNull(OblastniKlic.platnyObrys(null, new BoundingBox(48.99, 14.07, 55.23, 24.15)));
	}

	@org.junit.Test
	public void hexJeStejnyJakoStringFormat() {
		for (final long h : new long[] { 0L, 1L, 15L, 255L, 0xabcdefL, 0x0fffffffL, 0x80000000L, 0xdeadbeefL, 0xffffffffL }) {
			org.junit.Assert.assertEquals(String.format("%08x", h), OblastniKlic.hex(h));
		}
	}
}
