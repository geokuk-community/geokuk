package cz.geokuk.plugins.cesty;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.importek.ImportKesiTestPristup;

/** Export a import GGT, poškozené soubory a kódování souborů cest. */
public class CestyGgtASouboryTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Updator updator = new Updator();
	private final CestyZperzistentnovac zperzistentnovac = new CestyZperzistentnovac();

	private static final String GPX_HLAVA = "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">";
	private static final String TRASA = "<trk><name>Žluťoučká cesta</name><trkseg><trkpt lat=\"50.0\" lon=\"14.0\"/><trkpt lat=\"50.01\" lon=\"14.01\"/></trkseg></trk></gpx>";

	private KesBag kese() throws Exception {
		return ImportKesiTestPristup.importuj(ImportKesiTestPristup.kesNa("GC1111", "Geocache", "Traditional Cache", "Cizí", 1, true, false, 50.1, 14.4),
				ImportKesiTestPristup.kesNa("GC2222", "Geocache", "Traditional Cache", "Cizí", 1, true, false, 50.2, 14.5),
				ImportKesiTestPristup.kesNa("GC3333", "Geocache", "Traditional Cache", "Cizí", 1, true, false, 50.3, 14.6));
	}

	private static Kesoid kes(final KesBag bag, final String kod) {
		for (final Kesoid k : bag.getKesoidy()) {
			if (kod.equals(k.getIdentifier())) {
				return k;
			}
		}
		throw new AssertionError(kod);
	}

	private Doc doc(final Cesta... cesty) {
		final Doc doc = new Doc();
		for (final Cesta c : cesty) {
			updator.xadd(doc, c);
		}
		return doc;
	}

	private File soubor(final String jmeno, final byte[] obsah) throws Exception {
		final File f = tmp.newFile(jmeno);
		Files.write(f.toPath(), obsah);
		return f;
	}

	private File soubor(final String jmeno, final String obsah, final Charset kodovani) throws Exception {
		return soubor(jmeno, obsah.getBytes(kodovani));
	}

	private static List<String> radky(final File f) throws Exception {
		return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
	}

	// ----- export GGT -----

	@Test
	public void exportGgtMaJedinecneKodyKesiAVynechaBodyBezKese() throws Exception {
		final KesBag bag = kese();
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, kes(bag, "GC2222").getMainWpt());
		updator.pridejNaKonec(cesta, new Wgs(50.15, 14.45).toMou());
		updator.pridejNaKonec(cesta, kes(bag, "GC1111").getMainWpt());
		updator.pridejNaKonec(cesta, kes(bag, "GC2222").getMainWpt()); // keš podruhé
		final File ggt = new File(tmp.getRoot(), "lovim.ggt");
		zperzistentnovac.zapisGgt(doc(cesta), ggt);
		Assert.assertEquals(Arrays.asList("GC2222", "GC1111"), radky(ggt));
		Assert.assertTrue("čtení cest se po zápisu zase povolí", zperzistentnovac.smimCist());
	}

	@Test
	public void exportGgtPresPridavnyWaypointPridaKodKese() throws Exception {
		final KesBag bag = ImportKesiTestPristup.importuj(ImportKesiTestPristup.kesBezHodnoceni("GC1111"),
				"<wpt lat=\"50.101\" lon=\"14.401\"><name>PK1111</name><sym>Parking Area</sym></wpt>\n");
		Wpt parking = null;
		for (final Wpt w : kes(bag, "GC1111").getWpts()) {
			if ("PK1111".equals(w.getName())) {
				parking = w;
			}
		}
		Assert.assertNotNull("přídavný waypoint patří ke keši", parking);
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, parking);
		final File ggt = new File(tmp.getRoot(), "parking.ggt");
		zperzistentnovac.zapisGgt(doc(cesta), ggt);
		Assert.assertEquals(Arrays.asList("PK1111", "GC1111"), radky(ggt));
	}

	@Test
	public void zapisGgtCestyBezKesiVyrobiPrazdnySoubor() throws Exception {
		// Jádro zapíše prázdný soubor; případ bez keší hlídá až akce exportu před výběrem souboru.
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, new Wgs(50.0, 14.0).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.1, 14.1).toMou());
		final File ggt = new File(tmp.getRoot(), "bez-kesi.ggt");
		zperzistentnovac.zapisGgt(doc(cesta), ggt);
		Assert.assertTrue(ggt.isFile());
		Assert.assertEquals(0, ggt.length());
	}

	@Test
	public void chybaZapisuGgtPovoliCteniAZachovaPuvodniSoubor() throws Exception {
		final KesBag bag = kese();
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, kes(bag, "GC1111").getMainWpt());
		final File ggt = soubor("lovim.ggt", "STARY\n", StandardCharsets.UTF_8);
		try {
			zperzistentnovac.zapisGgt(doc(cesta), new File(ggt, "nelze"));
			Assert.fail("chyba zápisu se má ohlásit");
		} catch (final java.io.IOException e) {
			// čekáno
		}
		Assert.assertTrue("po chybě se čtení cest zase povolí", zperzistentnovac.smimCist());
		Assert.assertEquals(Collections.singletonList("STARY"), radky(ggt));
	}

	@Test
	public void exportovanyGgtSeNacteZpetNaStejneKese() throws Exception {
		final KesBag bag = kese();
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, kes(bag, "GC3333").getMainWpt());
		updator.pridejNaKonec(cesta, kes(bag, "GC1111").getMainWpt());
		final File ggt = new File(tmp.getRoot(), "zpet.ggt");
		zperzistentnovac.zapisGgt(doc(cesta), ggt);

		final List<Cesta> nactene = zperzistentnovac.nacti(Collections.singletonList(ggt), bag);
		Assert.assertEquals(1, nactene.size());
		final Set<Wgs> body = new HashSet<>();
		for (final Bod b : nactene.get(0).getBody()) {
			body.add(b.getMou().toWgs());
		}
		Assert.assertEquals(2, body.size());
		final Set<Double> lat = new TreeSet<>();
		for (final Wgs w : body) {
			lat.add(Math.round(w.lat * 10) / 10.0);
		}
		Assert.assertEquals(new TreeSet<>(Arrays.asList(50.1, 50.3)), lat);
	}

	@Test
	public void ggtSNeznamymiKodyAPrazdnymiRadkyBraJenZnameKese() throws Exception {
		final File ggt = soubor("cizi.ggt", "\n GC2222 \r\nGC9999\n\n\n", StandardCharsets.UTF_8);
		final List<Cesta> cesty = zperzistentnovac.nacti(Collections.singletonList(ggt), kese());
		Assert.assertEquals(1, cesty.size());
		Assert.assertEquals("jen GC2222, i když je řádek s mezerami", 1, pocetBodu(cesty.get(0)));
	}

	@Test
	public void ggtSOpakovanymKodemDaJedenBod() throws Exception {
		final File ggt = soubor("dvakrat.ggt", "GC2222\nGC2222\n", StandardCharsets.UTF_8);
		Assert.assertEquals(1, pocetBodu(zperzistentnovac.nacti(Collections.singletonList(ggt), kese()).get(0)));
	}

	private static int pocetBodu(final Cesta cesta) {
		int bodu = 0;
		for (final Bod b : cesta.getBody()) {
			bodu++;
		}
		return bodu;
	}

	@Test
	public void ggtSeZnackouUtf8SeNacteJakoBezNi() throws Exception {
		final File ggt = soubor("bom.ggt", "﻿GC1111\n", StandardCharsets.UTF_8);
		final List<Cesta> cesty = zperzistentnovac.nacti(Collections.singletonList(ggt), kese());
		Assert.assertFalse("kód s BOM se nesmí tvářit jako neznámý", cesty.get(0).isEmpty());
	}

	@Test
	public void ulozenaCestaPresKesSeAPoOtevreniZnovuPripneNaWaypoint() throws Exception {
		final KesBag bag = ImportKesiTestPristup.importuj(
				ImportKesiTestPristup.kesNa("GC1111", "Geocache", "Traditional Cache", "Cizí", 1, true, false, 50.123456, 14.654321));
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, new Wgs(50.0, 14.0).toMou());
		updator.pridejNaKonec(cesta, kes(bag, "GC1111").getMainWpt());
		updator.pridejNaKonec(cesta, new Wgs(50.2, 14.7).toMou());
		final File gpx = tmp.newFile("pres-kes.gpx");
		new Ukladac().uloz(gpx, doc(cesta));

		final List<Bod> body = new ArrayList<>();
		zperzistentnovac.nacti(Collections.singletonList(gpx), bag).get(0).getBody().forEach(body::add);
		Assert.assertEquals(3, body.size());
		Assert.assertFalse(body.get(0).getMouable() instanceof Wpt);
		Assert.assertTrue("bod na keši se po uložení a otevření zase spojí s keší", body.get(1).getMouable() instanceof Wpt);
		Assert.assertEquals("GC1111", ((Wpt) body.get(1).getMouable()).getKesoid().getIdentifier());
		Assert.assertFalse(body.get(2).getMouable() instanceof Wpt);
	}

	@Test
	public void bodVzdalenyOdKeseSeNepripne() throws Exception {
		final KesBag bag = kese(); // GC1111 na 50.1, 14.4
		final Cesta cesta = Cesta.create();
		updator.pridejNaKonec(cesta, new Wgs(50.1001, 14.4001).toMou()); // asi 13 m
		zperzistentnovac.pripniNaWayponty(doc(cesta).getCesty(), bag);
		Assert.assertFalse(cesta.getStart().getMouable() instanceof Wpt);
	}

	@Test
	public void otevreniCizihoGpxNenastaviSouborDokumentuAleSVlastnimUlozeni() throws Exception {
		final File cizi = soubor("garmin.gpx", "<?xml version=\"1.0\"?>\n<gpx version=\"1.1\" creator=\"eTrex 30\" xmlns=\"http://www.topografix.com/GPX/1/1\">"
				+ "<trk><trkseg><trkpt lat=\"50\" lon=\"14\"><ele>250</ele></trkpt><trkpt lat=\"50.1\" lon=\"14.1\"/></trkseg></trk></gpx>", StandardCharsets.UTF_8);
		Assert.assertNull("Uložit nesmí cizí soubor přepsat bez dotazu", new CestyOtevriSwingWorker(zperzistentnovac, null, null, cizi).doInBackground().getFile());

		final Doc doc = doc(Cesta.create());
		updator.pridejNaKonec(doc.getPrvniCesta(), new Wgs(50, 14).toMou());
		final File nas = tmp.newFile("nas.gpx");
		new Ukladac().uloz(nas, doc);
		final Doc otevreny = new CestyOtevriSwingWorker(zperzistentnovac, null, null, nas).doInBackground();
		Assert.assertEquals(nas, otevreny.getFile());
		Assert.assertEquals(1, otevreny.getPocetCest());
	}

	// ----- poškozené a podivné soubory -----

	private String chybaPriNacteni(final File f) {
		try {
			zperzistentnovac.nacti(Collections.singletonList(f), null);
		} catch (final RuntimeException e) {
			return e.getMessage();
		}
		return null;
	}

	@Test
	public void prazdnyGpxSeOhlasiSeJmenemSouboru() throws Exception {
		final File f = soubor("prazdny.gpx", new byte[0]);
		final String chyba = chybaPriNacteni(f);
		Assert.assertNotNull("nesmí mlčky vrátit nic", chyba);
		Assert.assertTrue(chyba, chyba.contains("prazdny.gpx"));
	}

	@Test
	public void zkracenyAPoskozenyGpxSeOhlasi() throws Exception {
		final String cely = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + GPX_HLAVA + TRASA;
		for (final String obsah : new String[] { cely.substring(0, cely.length() - 30), "tohle není xml", cely.replace("</trkseg>", "</trk>") }) {
			final File f = soubor("vadny" + obsah.length() + ".gpx", obsah, StandardCharsets.UTF_8);
			final String chyba = chybaPriNacteni(f);
			Assert.assertNotNull(obsah, chyba);
			Assert.assertTrue(chyba, chyba.contains(f.getName()));
		}
	}

	@Test
	public void binarniSmetiVGpxSeOhlasiAOstatniSouboryPrezijou() throws Exception {
		final byte[] smeti = new byte[512];
		new Random(42).nextBytes(smeti);
		final File vadny = soubor("smeti.gpx", smeti);
		final File dobry = soubor("dobry.gpx", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + GPX_HLAVA + TRASA, StandardCharsets.UTF_8);
		final List<Cesta> cesty = zperzistentnovac.nacti(Arrays.asList(vadny, dobry), null);
		Assert.assertEquals(1, cesty.size());
		Assert.assertEquals("Žluťoučká cesta", cesty.get(0).getNazev());
	}

	@Test
	public void zadnyZVyberuSeNenacetVyhodiChybuSeVsemiSoubory() throws Exception {
		final File a = soubor("a.gpx", new byte[0]);
		final File b = soubor("b.gpx", "ne", StandardCharsets.UTF_8);
		try {
			zperzistentnovac.nacti(Arrays.asList(a, b), null);
			Assert.fail();
		} catch (final RuntimeException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("a.gpx") && e.getMessage().contains("b.gpx"));
		}
	}

	@Test
	public void nacteniNeexistujicihoSouboruSeOhlasi() throws Exception {
		final String chyba = chybaPriNacteni(new File(tmp.getRoot(), "neni.gpx"));
		Assert.assertNotNull(chyba);
		Assert.assertTrue(chyba, chyba.contains("neni.gpx"));
	}

	@Test
	public void priponaMaVelikostPismenANeznamaSePreskoci() throws Exception {
		final File velke = soubor("TRASA.GPX", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + GPX_HLAVA + TRASA, StandardCharsets.UTF_8);
		Assert.assertEquals(1, zperzistentnovac.nacti(Collections.singletonList(velke), null).size());
		Assert.assertTrue(zperzistentnovac.nacti(Collections.singletonList(soubor("poznamky.txt", "x", StandardCharsets.UTF_8)), null).isEmpty());
	}

	// ----- kódování GPX -----

	@Test
	public void gpxDeklarovanyJakoWindows1250SeNacteSDiakritikou() throws Exception {
		final File f = soubor("cp1250.gpx", "<?xml version=\"1.0\" encoding=\"windows-1250\"?>" + GPX_HLAVA + TRASA, Charset.forName("windows-1250"));
		Assert.assertEquals("Žluťoučká cesta", zperzistentnovac.nacti(Collections.singletonList(f), null).get(0).getNazev());
	}

	@Test
	public void gpxIso88592SeNacteSDiakritikou() throws Exception {
		final File f = soubor("iso.gpx", "<?xml version=\"1.0\" encoding=\"ISO-8859-2\"?>" + GPX_HLAVA + TRASA, Charset.forName("ISO-8859-2"));
		Assert.assertEquals("Žluťoučká cesta", zperzistentnovac.nacti(Collections.singletonList(f), null).get(0).getNazev());
	}

	@Test
	public void gpxUtf8SeZnackouAUtf16SeNacte() throws Exception {
		final String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + GPX_HLAVA + TRASA;
		final File bom = soubor("bom.gpx", ("﻿" + xml).getBytes(StandardCharsets.UTF_8));
		Assert.assertEquals("Žluťoučká cesta", zperzistentnovac.nacti(Collections.singletonList(bom), null).get(0).getNazev());
		final File utf16 = soubor("utf16.gpx", xml.replace("UTF-8", "UTF-16"), StandardCharsets.UTF_16);
		Assert.assertEquals("Žluťoučká cesta", zperzistentnovac.nacti(Collections.singletonList(utf16), null).get(0).getNazev());
	}

	@Test
	public void gpxBezDeklaraceKodovaniSeCteJakoUtf8() throws Exception {
		final File f = soubor("bez.gpx", GPX_HLAVA + TRASA, StandardCharsets.UTF_8);
		Assert.assertEquals("Žluťoučká cesta", zperzistentnovac.nacti(Collections.singletonList(f), null).get(0).getNazev());
	}
}
