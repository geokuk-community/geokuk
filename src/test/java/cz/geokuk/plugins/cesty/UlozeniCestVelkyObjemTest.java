package cz.geokuk.plugins.cesty;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;
import cz.geokuk.plugins.kesoid.importek.NacitacGpxPristup;

/**
 * Cesty > Uložit a Otevřít v reálném objemu: stovky cest po stovkách bodů, každý třetí úsek po cestě s rozvinutou trasou, jedna dlouhá cesta o desítkách tisíc
 * bodů, názvy se znaky {@code < & >} a diakritikou. Po uložení do GPX a načtení zpět musí být všechno stejné. Počet cest mění {@code scenare.cest} (0 test přeskočí).
 */
public class UlozeniCestVelkyObjemTest {

	private static final int CEST = Integer.getInteger("scenare.cest", 300);
	private static final int BODU = 100;
	private static final int BODU_DLOUHE = 50_000;
	private static final int BODU_V_TRASE = 20;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Updator updator = new Updator();

	private static Wgs bod(final int cesta, final int i) {
		return new Wgs(49.5 + cesta * 0.0007 + i * 0.0002, 13.0 + cesta % 50 * 0.01 + Math.sin(i * 0.1) * 0.002);
	}

	private static String nazev(final int c) {
		return "Cesta " + c + " <Žlutý & kůň> č. " + c;
	}

	private Cesta cesta(final Doc doc, final String nazev, final int cislo, final int bodu, final boolean poCeste) {
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.setNazev(cesta, nazev);
		final List<Wgs> body = new ArrayList<>();
		for (int i = 0; i < bodu; i++) {
			body.add(bod(cislo, i));
			updator.pridejNaKonec(cesta, body.get(i).toMou());
		}
		if (poCeste) {
			int k = 0;
			for (final Usek usek : cesta.getUseky()) {
				if (k % 3 == 0) {
					updator.setTyp(usek, TypUseku.PO_CESTE);
					final Wgs od = body.get(k);
					final Wgs kam = body.get(k + 1);
					final List<Wgs> trasa = new ArrayList<>();
					trasa.add(od);
					for (int t = 1; t <= BODU_V_TRASE; t++) {
						final double f = (double) t / (BODU_V_TRASE + 1);
						trasa.add(new Wgs(od.lat + (kam.lat - od.lat) * f + 0.00005 * Math.sin(t), od.lon + (kam.lon - od.lon) * f));
					}
					trasa.add(kam);
					assertTrue(updator.setTrasa(usek, od.toMou(), kam.toMou(), Trasa.nalezena(trasa)));
				} else if (k % 3 == 1) {
					updator.setVzdusny(usek, true);
				}
				k++;
			}
		}
		return cesta;
	}

	private static List<Bod> body(final Cesta cesta) {
		final List<Bod> v = new ArrayList<>();
		cesta.getBody().forEach(v::add);
		return v;
	}

	private static List<Usek> useky(final Cesta cesta) {
		final List<Usek> v = new ArrayList<>();
		cesta.getUseky().forEach(v::add);
		return v;
	}

	@Test(timeout = 240000)
	public void stoveCestUlozeniANacteniNicNeztrati() throws Exception {
		Assume.assumeTrue("scenare.cest=0 test vypíná", CEST > 0);
		final Doc doc = new Doc();
		final List<Cesta> puvodni = new ArrayList<>();
		for (int c = 0; c < CEST; c++) {
			puvodni.add(cesta(doc, nazev(c), c, BODU, true));
		}
		puvodni.add(cesta(doc, "Dlouhá", CEST, BODU_DLOUHE, false));

		final File soubor = tmp.newFile("cesty.gpx");
		new Ukladac().uloz(soubor, doc);
		assertTrue("soubor má stovky tisíc bodů: " + soubor.length(), soubor.length() > 1_000_000);
		final DocImportBuilder builder = new DocImportBuilder();
		try (InputStream in = new BufferedInputStream(new FileInputStream(soubor))) {
			NacitacGpxPristup.nacti(in, builder);
		}
		final List<Cesta> nactene = builder.getCesty();
		assertEquals("počet cest", puvodni.size(), nactene.size());
		for (int c = 0; c < puvodni.size(); c++) {
			final Cesta p = puvodni.get(c);
			final Cesta n = nactene.get(c);
			final String kde = "cesta " + c;
			assertEquals(kde + " název", p.getNazev(), n.getNazev());
			final List<Bod> pb = body(p);
			final List<Bod> nb = body(n);
			assertEquals(kde + " počet řídicích bodů", pb.size(), nb.size());
			for (int i = 0; i < pb.size(); i++) {
				final Wgs a = pb.get(i).getMou().toWgs();
				final Wgs b = nb.get(i).getMou().toWgs();
				if (Math.abs(a.lat - b.lat) > 1e-5 || Math.abs(a.lon - b.lon) > 1e-5) {
					fail(kde + " bod " + i + ": " + a + " ≠ " + b);
				}
			}
			final List<Usek> pu = useky(p);
			final List<Usek> nu = useky(n);
			assertEquals(kde + " počet úseků", pu.size(), nu.size());
			for (int i = 0; i < pu.size(); i++) {
				assertEquals(kde + " typ úseku " + i, pu.get(i).getTyp(), nu.get(i).getTyp());
				if (pu.get(i).getTyp() == TypUseku.PO_CESTE) {
					assertEquals(kde + " trasa úseku " + i, pu.get(i).getTrasa().size(), nu.get(i).getTrasa().size());
				}
			}
			assertEquals(kde + " délka", p.dalka(), n.dalka(), 5);
		}
	}
}
