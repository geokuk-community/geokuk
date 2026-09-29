package cz.geokuk.plugins.kesoid.importek;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.*;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.kind.kes.*;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.util.file.*;

/** Import GPX až po keše a waypointy, jak je pak vidí mapa a filtr. */
public class ImportKesiTest {

	private static final KesoidPluginManager PLUGINY = new KesoidPluginManager();

	static String kes(final String kod, final String sym, final String typ, final String autor, final int autorId, final boolean dostupna, final boolean archivovana, final String obtiznost,
			final String extra) {
		return "<wpt lat=\"50.1\" lon=\"14.4\"><name>" + kod + "</name><sym>" + sym + "</sym><type>Geocache|" + typ + "</type>"
				+ "<groundspeak:cache available=\"" + (dostupna ? "True" : "False") + "\" archived=\"" + (archivovana ? "True" : "False") + "\" xmlns:groundspeak=\"http://www.groundspeak.com/cache/1/0/1\">"
				+ "<groundspeak:name>Keš " + kod + "</groundspeak:name><groundspeak:placed_by>" + autor + "</groundspeak:placed_by><groundspeak:owner id=\"" + autorId + "\">" + autor
				+ "</groundspeak:owner><groundspeak:type>" + typ + "</groundspeak:type><groundspeak:container>Small</groundspeak:container><groundspeak:difficulty>" + obtiznost
				+ "</groundspeak:difficulty><groundspeak:terrain>1.5</groundspeak:terrain><groundspeak:encoded_hints>nápověda</groundspeak:encoded_hints></groundspeak:cache>" + extra + "</wpt>\n";
	}

	static String gpx(final String... wpt) {
		return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n" + String.join("", wpt) + "</gpx>";
	}

	static KesBag importuj(final String gpx) throws Exception {
		final ProgressModel progress = new ProgressModel();
		progress.inject(udalost -> {});
		final KesoidImportBuilder builder = new KesoidImportBuilder(new Genom(), new GccomNick("Ja", 42), progress, PLUGINY);
		builder.init();
		final File adresar = new File("data");
		builder.setCurrentlyLoading(new KeFile(new FileAndTime(new File(adresar, "test.gpx"), 0), new Root(adresar, new Root.Def(0, null, null))), true);
		new NacitacGpx().nacti(new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)), "test.gpx", builder, null);
		builder.done();
		return builder.getKesBag();
	}

	static final String HODNOCENI = "<gpxg:GeogetExtension xmlns:gpxg=\"https://www.geoget.cz/GpxExtensions/v2\"><gpxg:Tags>"
			+ "<gpxg:Tag Category=\"Hodnoceni\">80%</gpxg:Tag><gpxg:Tag Category=\"BestOf\">3</gpxg:Tag><gpxg:Tag Category=\"favorites\">15</gpxg:Tag>"
			+ "</gpxg:Tags></gpxg:GeogetExtension>";

	static final String GPX = gpx(
			kes("GC1111", "Geocache Found", "Traditional Cache", "Cizí", 1, true, false, "2", HODNOCENI),
			kes("GC2222", "Geocache", "Unknown Cache", "Cizí", 1, true, false, "3.5", ""),
			"<wpt lat=\"50.2\" lon=\"14.5\"><name>FI2222</name><desc>Final</desc><sym>Final Location</sym><type>Waypoint|Final Location</type></wpt>\n",
			kes("GC3333", "Geocache", "Traditional Cache", "Ja", 42, true, false, "1", ""),
			kes("GC4444", "Geocache", "Traditional Cache", "Cizí", 1, false, true, "5", ""),
			kes("GC5555", "Geocache", "Traditional Cache", "Cizí", 1, false, false, "1", ""),
			kes("GC1111", "Geocache", "Traditional Cache", "Cizí", 1, true, false, "2", ""),
			"<wpt lat=\"49.0\" lon=\"15.0\"><name>Samostatny</name><sym>Waypoint</sym></wpt>\n");

	private Map<String, Kesoid> kese;

	@Before
	public void setUp() throws Exception {
		kese = new HashMap<>();
		for (final Kesoid k : importuj(GPX).getKesoidy()) {
			kese.put(k.getIdentifier(), k);
		}
	}

	private Kes kes(final String kod) {
		return (Kes) kese.get(kod);
	}

	@Test
	public void vztahKKesi() {
		Assert.assertEquals(EKesVztah.FOUND, kes("GC1111").getVztah());
		Assert.assertEquals(EKesVztah.NORMAL, kes("GC2222").getVztah());
		Assert.assertEquals(EKesVztah.OWN, kes("GC3333").getVztah());
	}

	@Test
	public void stavKese() {
		Assert.assertEquals(EKesStatus.ACTIVE, kes("GC1111").getStatus());
		Assert.assertEquals(EKesStatus.ARCHIVED, kes("GC4444").getStatus());
		Assert.assertEquals(EKesStatus.DISABLED, kes("GC5555").getStatus());
	}

	@Test
	public void vlastnostiKese() {
		final Kes kes = kes("GC1111");
		Assert.assertEquals("Keš GC1111", kes.getNazev());
		Assert.assertEquals("Cizí", kes.getAuthor());
		Assert.assertEquals(EKesDiffTerRating.TWO, kes.getDifficulty());
		Assert.assertEquals(EKesDiffTerRating.ONE_HALF, kes.getTerrain());
		Assert.assertEquals(EKesSize.SMALL, kes.getSize());
		Assert.assertEquals("nápověda", kes.getHint());
		Assert.assertEquals(80, kes.getHodnoceni());
		Assert.assertEquals(3, kes.getBestOf());
		Assert.assertEquals(15, kes.getFavorit());
	}

	@Test
	public void kesBezRozsireniGeogetuNemaHodnoceni() {
		final Kes kes = kes("GC5555");
		Assert.assertEquals(Kes.NENI_HODNOCENI, kes.getHodnoceni());
		Assert.assertEquals(Kes.NENI_HODNOCENI, kes.getBestOf());
		Assert.assertEquals(Kes.NENI_HODNOCENI, kes.getFavorit());
		Assert.assertEquals(Kes.NENI_HODNOCENI, kes.getZnamka());
	}

	@Test
	public void finalPatriKeKesi() {
		final Kes mystery = kes("GC2222");
		Assert.assertEquals(2, mystery.getWptsCount());
		Assert.assertNotNull(mystery.getFinal());
		Assert.assertEquals("FI2222", mystery.getFinal().getName());
		Assert.assertEquals(50.2, mystery.getFinal().getWgs().lat, 1e-9);
		Assert.assertTrue(mystery.hasValidFinal());
		Assert.assertFalse(kes("GC1111").hasValidFinal());
	}

	@Test
	public void duplicitniKesSeNacteJednou() throws Exception {
		Assert.assertEquals(EKesVztah.FOUND, kes("GC1111").getVztah());
		long pocet = 0;
		for (final Kesoid k : importuj(GPX).getKesoidy()) {
			if ("GC1111".equals(k.getIdentifier())) {
				pocet++;
			}
		}
		Assert.assertEquals(1, pocet);
	}

	@Test
	public void samostatnyWaypoint() {
		Assert.assertNotNull(kese.get("Samostatny"));
		Assert.assertFalse(kese.get("Samostatny") instanceof Kes);
	}

	@Test
	public void pocetWaypointuBezDuplicit() throws Exception {
		Assert.assertEquals(7, importuj(GPX).getWpts().size());
	}
}
