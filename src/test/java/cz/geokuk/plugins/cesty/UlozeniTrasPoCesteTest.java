package cz.geokuk.plugins.cesty;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;
import cz.geokuk.plugins.kesoid.importek.NacitacGpxPristup;

/** Úseky po cestě v GPX: trasa se rozvine do bodů, řídicí body nesou značku gk:bod a po načtení se trasa obnoví. */
public class UlozeniTrasPoCesteTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Updator updator = new Updator();

	private static final Wgs A = new Wgs(50.0, 14.0);
	private static final Wgs X1 = new Wgs(50.003, 14.002);
	private static final Wgs X2 = new Wgs(50.007, 14.001);
	private static final Wgs B = new Wgs(50.01, 14.0);
	private static final Wgs C = new Wgs(50.01, 14.02);
	private static final Wgs D = new Wgs(50.0, 14.02);

	private Cesta cesta(final Doc doc, final Wgs... body) {
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.setNazev(cesta, "Výlet");
		for (final Wgs w : body) {
			updator.pridejNaKonec(cesta, w.toMou());
		}
		return cesta;
	}

	private static Usek usek(final Cesta cesta, final int i) {
		int k = 0;
		for (final Usek u : cesta.getUseky()) {
			if (k++ == i) {
				return u;
			}
		}
		throw new IndexOutOfBoundsException();
	}

	private void poCeste(final Usek usek, final Wgs... body) {
		updator.setTyp(usek, TypUseku.PO_CESTE);
		assertTrue(updator.setTrasa(usek, usek.getBvzad().getMou(), usek.getBvpred().getMou(), Trasa.nalezena(Arrays.asList(body))));
	}

	private File uloz(final Doc doc) throws IOException {
		final File soubor = tmp.newFile();
		new Ukladac().uloz(soubor, doc);
		return soubor;
	}

	private static List<Cesta> nacti(final File soubor) throws Exception {
		final DocImportBuilder builder = new DocImportBuilder();
		try (InputStream in = new FileInputStream(soubor)) {
			NacitacGpxPristup.nacti(in, builder);
		}
		return builder.getCesty();
	}

	private static List<Cesta> nacti(final String gpx) throws Exception {
		final DocImportBuilder builder = new DocImportBuilder();
		NacitacGpxPristup.nacti(new ByteArrayInputStream(gpx.getBytes(StandardCharsets.UTF_8)), builder);
		return builder.getCesty();
	}

	private static int pocetBodu(final Cesta cesta) {
		int n = 0;
		for (final Iterator<Bod> it = cesta.getBody().iterator(); it.hasNext(); it.next()) {
			n++;
		}
		return n;
	}

	private static void shoda(final Wgs ocekavany, final Mou skutecny) {
		final Wgs w = skutecny.toWgs();
		assertEquals(ocekavany.lat, w.lat, 1e-5);
		assertEquals(ocekavany.lon, w.lon, 1e-5);
	}

	@Test
	public void trasaSeRozviniAObnovi() throws Exception {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc, A, B, C, D);
		poCeste(usek(cesta, 0), A, X1, X2, B);
		updator.setVzdusny(usek(cesta, 2), true);
		final File soubor = uloz(doc);

		final String text = new String(Files.readAllBytes(soubor.toPath()), StandardCharsets.UTF_8);
		assertEquals("řídicí body + 2 body trasy", 6, text.split("<trkpt").length - 1);
		assertEquals(4, text.split("<gk:bod/>").length - 1);
		assertTrue(text.contains("xmlns:gk=\"" + Ukladac.GK_NAMESPACE + "\""));

		final List<Cesta> cesty = nacti(soubor);
		assertEquals(1, cesty.size());
		final Cesta nactena = cesty.get(0);
		assertEquals(4, pocetBodu(nactena));
		final Usek u0 = usek(nactena, 0);
		assertEquals(TypUseku.PO_CESTE, u0.getTyp());
		assertEquals(Trasa.Stav.NALEZENO, u0.getStavTrasy());
		final List<Mou> trasa = u0.getTrasa();
		assertEquals(4, trasa.size());
		shoda(A, trasa.get(0));
		shoda(X1, trasa.get(1));
		shoda(X2, trasa.get(2));
		shoda(B, trasa.get(3));
		assertEquals(TypUseku.ROVNE, usek(nactena, 1).getTyp());
		assertEquals(TypUseku.VZDUSNY, usek(nactena, 2).getTyp());
		assertEquals(cesta.dalka(), nactena.dalka(), 1);
	}

	@Test
	public void bezTrasJeSouborJakoDosud() throws Exception {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc, A, B, C);
		// Úsek po cestě bez nalezené trasy se uloží jako rovně.
		updator.setTyp(usek(cesta, 0), TypUseku.PO_CESTE);
		final String text = new String(Files.readAllBytes(uloz(doc).toPath()), StandardCharsets.UTF_8);
		assertFalse(text.contains("gk:"));
		assertFalse(text.contains("extensions"));
		assertEquals(3, text.split("<trkpt").length - 1);
	}

	@Test
	public void ciziSouborBezZnacekMaKazdyBodJakoBod() throws Exception {
		final List<Cesta> cesty = nacti("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\"><trk><trkseg>"
				+ "<trkpt lat=\"50.0\" lon=\"14.0\"/><trkpt lat=\"50.003\" lon=\"14.002\"/><trkpt lat=\"50.01\" lon=\"14.0\"/></trkseg></trk></gpx>");
		assertEquals(3, pocetBodu(cesty.get(0)));
		for (final Usek u : cesty.get(0).getUseky()) {
			assertEquals(TypUseku.ROVNE, u.getTyp());
		}
	}

	@Test
	public void bodyTrasyBezKoncovehoRidicihoBoduZustanouBody() throws Exception {
		final List<Cesta> cesty = nacti("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gk=\"http://geokuk.cz/gpx/1\" version=\"1.1\"><trk><trkseg>"
				+ "<trkpt lat=\"50.0\" lon=\"14.0\"><extensions><gk:bod/></extensions></trkpt>"
				+ "<trkpt lat=\"50.003\" lon=\"14.002\"/><trkpt lat=\"50.007\" lon=\"14.001\"/></trkseg></trk></gpx>");
		assertEquals(3, pocetBodu(cesty.get(0)));
	}

	@Test
	public void znackyJenUCestySTrasou() throws Exception {
		final Doc doc = new Doc();
		final Cesta s = cesta(doc, A, B);
		poCeste(usek(s, 0), A, X1, B);
		cesta(doc, C, D);
		final List<Cesta> cesty = nacti(uloz(doc));
		assertEquals(2, cesty.size());
		assertEquals(2, pocetBodu(cesty.get(0)));
		assertNotNull(usek(cesty.get(0), 0).getTrasa());
		assertEquals(2, pocetBodu(cesty.get(1)));
	}
}
