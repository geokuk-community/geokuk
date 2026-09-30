package cz.geokuk.plugins.kesoid.importek;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;

/** Čtení GPX ve tvaru, jaký vytváří GeoGet a geocaching.com. */
public class NacitacGpxTest {

	private static final String GPX_GEOGET = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
			+ "<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\">\n"
			+ " <wpt lat=\"50.08520\" lon=\"14.42430\">\n"
			+ "  <time>2015-06-01T00:00:00Z</time>\n"
			+ "  <name>GC12345</name>\n"
			+ "  <desc>Keš u řeky by Kačer (2/3.5)</desc>\n"
			+ "  <url>https://coord.info/GC12345</url>\n"
			+ "  <urlname>Keš u řeky</urlname>\n"
			+ "  <sym>Geocache Found</sym>\n"
			+ "  <type>Geocache|Traditional Cache</type>\n"
			+ "  <groundspeak:cache id=\"1\" available=\"True\" archived=\"False\" xmlns:groundspeak=\"http://www.groundspeak.com/cache/1/0/1\">\n"
			+ "   <groundspeak:name>Keš u řeky</groundspeak:name>\n"
			+ "   <groundspeak:placed_by>Kačer</groundspeak:placed_by>\n"
			+ "   <groundspeak:owner id=\"1487776\">Kačer</groundspeak:owner>\n"
			+ "   <groundspeak:type>Traditional Cache</groundspeak:type>\n"
			+ "   <groundspeak:container>Regular</groundspeak:container>\n"
			+ "   <groundspeak:difficulty>2</groundspeak:difficulty>\n"
			+ "   <groundspeak:terrain>3.5</groundspeak:terrain>\n"
			+ "   <groundspeak:country>Czech Republic</groundspeak:country>\n"
			+ "   <groundspeak:state>Praha</groundspeak:state>\n"
			+ "   <groundspeak:short_description html=\"True\"><![CDATA[Krátký <b>popis</b>]]></groundspeak:short_description>\n"
			+ "   <groundspeak:long_description html=\"True\"><![CDATA[Dlouhý popis]]></groundspeak:long_description>\n"
			+ "   <groundspeak:encoded_hints>Pod kamenem</groundspeak:encoded_hints>\n"
			+ "   <groundspeak:logs><groundspeak:log id=\"1\"><groundspeak:text>TFTC</groundspeak:text></groundspeak:log></groundspeak:logs>\n"
			+ "  </groundspeak:cache>\n"
			+ "  <gpxg:GeogetExtension xmlns:gpxg=\"https://www.geoget.cz/GpxExtensions/v2\">\n"
			+ "   <gpxg:Found>2020-05-01T10:00:00</gpxg:Found>\n"
			+ "   <gpxg:Flag>2</gpxg:Flag>\n"
			+ "   <gpxg:Tags>\n"
			+ "    <gpxg:Tag Category=\"Hodnoceni\"><![CDATA[80%]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"Hodnoceni-Pocet\"><![CDATA[12x]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"Znamka\"><![CDATA[75]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"BestOf\"><![CDATA[3]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"favorites\"><![CDATA[15]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"Elevation\"><![CDATA[467]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"CZ kraj\"><![CDATA[Praha]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"CZ okres\"><![CDATA[Praha]]></gpxg:Tag>\n"
			+ "    <gpxg:Tag Category=\"geokuk_barva\"><![CDATA[modra]]></gpxg:Tag>\n"
			+ "   </gpxg:Tags>\n"
			+ "  </gpxg:GeogetExtension>\n"
			+ " </wpt>\n"
			+ " <wpt lat=\"50.086\" lon=\"14.425\">\n"
			+ "  <name>PK12345</name>\n"
			+ "  <cmt>Parkoviště</cmt>\n"
			+ "  <desc>Parking Area</desc>\n"
			+ "  <sym>Parking Area</sym>\n"
			+ "  <type>Waypoint|Parking Area</type>\n"
			+ " </wpt>\n"
			+ "</gpx>\n";

	private static final String GPX_11_S_TRASOU = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
			+ "<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
			+ " <wpt lat=\"-33.85\" lon=\"-70.65\"><ele>520.5</ele><name>Bod</name><link href=\"https://example.org/bod\"><text>Odkaz</text><type>text/html</type></link></wpt>\n"
			+ " <trk><name>Výlet</name>\n"
			+ "  <trkseg><trkpt lat=\"50.0\" lon=\"14.0\"/><trkpt lat=\"50.1\" lon=\"14.1\"/></trkseg>\n"
			+ "  <trkseg><trkpt lat=\"50.2\" lon=\"14.2\"/></trkseg>\n"
			+ " </trk>\n"
			+ "</gpx>\n";

	/** Zaznamenává, co parser předá builderu. */
	private static class Zaznam implements IImportBuilder {
		final List<GpxWpt> waypointy = new ArrayList<>();
		final List<String> trasy = new ArrayList<>();

		@Override
		public void addGpxWpt(final GpxWpt w) {
			waypointy.add(w);
		}

		@Override
		public void addTrackWpt(final GpxWpt w) {
			trasy.add("bod " + w.wgs.lat + "," + w.wgs.lon);
		}

		@Override
		public void begTrack() {
			trasy.add("trasa");
		}

		@Override
		public void begTrackSegment() {
			trasy.add("segment");
		}

		@Override
		public void endTrack() {
			trasy.add("konec trasy");
		}

		@Override
		public void endTrackSegment() {
			trasy.add("konec segmentu");
		}

		@Override
		public void setTrackName(final String nazev) {
			trasy.add("název " + nazev);
		}

		@Override
		public void init() {}

		@Override
		public void done() {}
	}

	private static Zaznam nacti(final String gpx) throws Exception {
		final Zaznam zaznam = new Zaznam();
		new NacitacGpx().nacti(new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)), "test.gpx", zaznam, null);
		return zaznam;
	}

	@Test
	public void kesZGeogetu() throws Exception {
		final GpxWpt kes = nacti(GPX_GEOGET).waypointy.get(0);
		Assert.assertEquals("GC12345", kes.name);
		Assert.assertEquals(50.0852, kes.wgs.lat, 1e-9);
		Assert.assertEquals(14.4243, kes.wgs.lon, 1e-9);
		Assert.assertEquals("2015-06-01T00:00:00Z", kes.time);
		Assert.assertEquals("Keš u řeky by Kačer (2/3.5)", kes.desc);
		Assert.assertEquals("https://coord.info/GC12345", kes.link.href);
		Assert.assertEquals("Keš u řeky", kes.link.text);
		Assert.assertEquals("Geocache Found", kes.sym);
		Assert.assertEquals("Geocache|Traditional Cache", kes.type);
	}

	@Test
	public void groundspeak() throws Exception {
		final Groundspeak gs = nacti(GPX_GEOGET).waypointy.get(0).groundspeak;
		Assert.assertEquals("Keš u řeky", gs.name);
		Assert.assertEquals("Kačer", gs.placedBy);
		Assert.assertEquals("Kačer", gs.owner);
		Assert.assertEquals(1487776, gs.ownerid);
		Assert.assertEquals("Traditional Cache", gs.type);
		Assert.assertEquals("Regular", gs.container);
		Assert.assertEquals("2", gs.difficulty);
		Assert.assertEquals("3.5", gs.terrain);
		Assert.assertEquals("Czech Republic", gs.country);
		Assert.assertEquals("Praha", gs.state);
		Assert.assertEquals("Krátký <b>popis</b>", gs.shortDescription);
		Assert.assertEquals("Pod kamenem", gs.encodedHints);
		Assert.assertTrue(gs.availaible);
		Assert.assertFalse(gs.archived);
	}

	@Test
	public void rozsireniGeogetu() throws Exception {
		final Gpxg g = nacti(GPX_GEOGET).waypointy.get(0).gpxg;
		Assert.assertEquals("2020-05-01T10:00:00", g.found);
		Assert.assertEquals(2, g.flag);
		Assert.assertEquals(80, g.hodnoceni);
		Assert.assertEquals(12, g.hodnoceniPocet);
		Assert.assertEquals(75, g.znamka);
		Assert.assertEquals(3, g.bestOf);
		Assert.assertEquals(15, g.favorites);
		Assert.assertEquals(467, g.elevation);
		Assert.assertEquals("Praha", g.czkraj);
		Assert.assertEquals("Praha", g.czokres);
		Assert.assertEquals(Collections.singletonMap("barva", "modra"), g.userTags);
	}

	@Test
	public void pridavnyWaypointZaKesi() throws Exception {
		final List<GpxWpt> w = nacti(GPX_GEOGET).waypointy;
		Assert.assertEquals(2, w.size());
		final GpxWpt parkoviste = w.get(1);
		Assert.assertEquals("PK12345", parkoviste.name);
		Assert.assertEquals("Parkoviště", parkoviste.cmt);
		Assert.assertEquals("Parking Area", parkoviste.sym);
		Assert.assertNull(parkoviste.groundspeak);
	}

	@Test
	public void gpx11SOdkazemAVyskou() throws Exception {
		final GpxWpt bod = nacti(GPX_11_S_TRASOU).waypointy.get(0);
		Assert.assertEquals("Bod", bod.name);
		Assert.assertEquals(-33.85, bod.wgs.lat, 1e-9);
		Assert.assertEquals(520.5, bod.ele, 1e-9);
		Assert.assertEquals("https://example.org/bod", bod.link.href);
		Assert.assertEquals("Odkaz", bod.link.text);
		Assert.assertEquals("text/html", bod.link.type);
	}

	@Test
	public void trasaSeSegmenty() throws Exception {
		Assert.assertEquals(Arrays.asList("trasa", "název Výlet", "segment", "bod 50.0,14.0", "bod 50.1,14.1", "konec segmentu", "segment", "bod 50.2,14.2", "konec segmentu", "konec trasy"),
				nacti(GPX_11_S_TRASOU).trasy);
	}

	@Test
	public void groundspeak10() throws Exception {
		final String gpx = GPX_GEOGET.replace("http://www.groundspeak.com/cache/1/0/1", "http://www.groundspeak.com/cache/1/0");
		Assert.assertEquals("Keš u řeky", nacti(gpx).waypointy.get(0).groundspeak.name);
	}

	@Test
	public void staryJmennyProstorGeogetu() throws Exception {
		final String gpx = GPX_GEOGET.replace("https://www.geoget.cz/GpxExtensions/v2", "http://geoget.ararat.cz/GpxExtensions/v2");
		Assert.assertEquals(75, nacti(gpx).waypointy.get(0).gpxg.znamka);
	}

	@Test
	public void waypointBezSouradnicSeNacte() throws Exception {
		final String gpx = "<gpx xmlns=\"http://www.topografix.com/GPX/1/0\"><wpt><name>X</name></wpt><wpt lat=\"abc\" lon=\"1\"><name>Y</name></wpt></gpx>";
		final List<GpxWpt> w = nacti(gpx).waypointy;
		Assert.assertEquals(2, w.size());
		Assert.assertNull(w.get(0).wgs);
		Assert.assertNull(w.get(1).wgs);
	}

	@Test(expected = java.io.IOException.class)
	public void poskozeneXml() throws Exception {
		nacti("<gpx xmlns=\"http://www.topografix.com/GPX/1/0\"><wpt lat=\"1\" lon=\"2\"><name>X</name>");
	}
	@Test
	public void externiEntitaSeNenacte() throws Exception {
		final java.io.File tajny = java.io.File.createTempFile("geokuk-xxe", ".txt");
		tajny.deleteOnExit();
		java.nio.file.Files.write(tajny.toPath(), "TAJNE".getBytes(StandardCharsets.UTF_8));
		final String gpx = "<?xml version=\"1.0\"?>\n<!DOCTYPE gpx [<!ENTITY x SYSTEM \"" + tajny.toURI() + "\">]>\n"
				+ "<gpx version=\"1.0\" xmlns=\"http://www.topografix.com/GPX/1/0\"><wpt lat=\"50\" lon=\"14\"><name>&x;</name></wpt></gpx>";
		try {
			for (final GpxWpt w : nacti(gpx).waypointy) {
				Assert.assertFalse(String.valueOf(w.name).contains("TAJNE"));
			}
		} catch (final java.io.IOException e) {
			// Odmítnutí souboru je v pořádku.
		}
	}

	@Test
	public void doctypeBezEntitSeNacte() throws Exception {
		final String gpx = GPX_GEOGET.replace("<gpx ", "<!DOCTYPE gpx SYSTEM \"http://127.0.0.1:9/gpx.dtd\">\n<gpx ");
		Assert.assertEquals("GC12345", nacti(gpx).waypointy.get(0).name);
	}
}
