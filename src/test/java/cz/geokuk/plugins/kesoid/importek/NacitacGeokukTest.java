package cz.geokuk.plugins.kesoid.importek;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;

import org.junit.Assert;
import org.junit.Test;

/** Export GeoKuku (.geokuk) načtený zpět. */
public class NacitacGeokukTest {

	private static final String HLAVICKA = "*geokuk:exportversion=2\n";
	private static final String KES = ":GC12345|Traditional Cache|Small|2|3.5|false|true|true|false| Kačer |2020-01-01|CZ|Praha|5|80|10|75|pod kamenem|http://coord.info/GC12345\n"
			+ "-GC|Geocache|50.1|14.4|Keš u řeky\n"
			+ "-PK|Parking Area|50.2|14.5|Parkoviště\n"
			+ "/\n";

	private static List<GpxWpt> nacti(final String obsah) throws Exception {
		final List<GpxWpt> w = new ArrayList<>();
		final IImportBuilder builder = new IImportBuilder() {
			@Override
			public void addGpxWpt(final GpxWpt g) {
				w.add(g);
			}

			@Override
			public void init() {}

			@Override
			public void done() {}

			@Override
			public void addTrackWpt(final GpxWpt wpt) {}

			@Override
			public void begTrack() {}

			@Override
			public void begTrackSegment() {}

			@Override
			public void endTrack() {}

			@Override
			public void endTrackSegment() {}

			@Override
			public void setTrackName(final String nazev) {}
		};
		new NacitacGeokuk().nacti(new ByteArrayInputStream(obsah.getBytes(StandardCharsets.UTF_8)), "test.geokuk", builder, null);
		return w;
	}

	@Test
	public void kesAWaypoint() throws Exception {
		final List<GpxWpt> w = nacti(HLAVICKA + KES);
		Assert.assertEquals(2, w.size());
		final GpxWpt kes = w.get(0);
		Assert.assertEquals("GC12345", kes.name);
		Assert.assertEquals(KesoidImportBuilder.GEOCACHE_FOUND, kes.sym);
		Assert.assertEquals("Traditional Cache", kes.groundspeak.type);
		Assert.assertEquals("Small", kes.groundspeak.container);
		Assert.assertEquals("3.5", kes.groundspeak.terrain);
		Assert.assertFalse(kes.groundspeak.archived);
		Assert.assertFalse("druhý příznak je nedostupnost", kes.groundspeak.availaible);
		Assert.assertEquals("Kačer", kes.groundspeak.placedBy);
		Assert.assertEquals("Keš u řeky", kes.groundspeak.name);
		Assert.assertEquals(80, kes.gpxg.hodnoceni);
		Assert.assertEquals(75, kes.gpxg.znamka);
		Assert.assertEquals("pod kamenem", kes.groundspeak.encodedHints);
		Assert.assertEquals(50.1, kes.wgs.lat, 1e-9);
		final GpxWpt parkoviste = w.get(1);
		Assert.assertEquals("PK12345", parkoviste.name);
		Assert.assertEquals("Parking Area", parkoviste.sym);
		Assert.assertEquals("Parkoviště", parkoviste.cmt);
	}

	@Test
	public void hlavickaSBom() throws Exception {
		Assert.assertEquals(2, nacti("﻿" + HLAVICKA + KES).size());
	}

	@Test
	public void prazdnySouborNicNenacte() throws Exception {
		Assert.assertTrue(nacti("").isEmpty());
	}

	@Test
	public void prazdnaPrvniRadkaJeSpatnaHlavicka() throws Exception {
		try {
			nacti("\n" + KES);
			Assert.fail();
		} catch (final RuntimeException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("hlavička"));
		}
	}

	@Test(expected = RuntimeException.class)
	public void ciziSouborSeOdmitne() throws Exception {
		nacti("<?xml version=\"1.0\"?>\n<gpx/>\n");
	}

	@Test
	public void vadnyRadekNeshodiOstatni() throws Exception {
		final List<GpxWpt> w = nacti(HLAVICKA + ":GC1|x\n-GC|Geocache|nečíslo|14|Vadná\n/\n" + KES);
		Assert.assertEquals(2, w.size());
		Assert.assertEquals("GC12345", w.get(0).name);
	}

	@Test
	public void poznaPriponu() {
		final NacitacGeokuk n = new NacitacGeokuk();
		Assert.assertTrue(n.umiNacist(new File("Export.GEOKUK")));
		Assert.assertTrue(n.umiNacist(new ZipEntry("a/b.geokuk")));
		Assert.assertFalse(n.umiNacist(new File("a.gpx")));
	}
}
