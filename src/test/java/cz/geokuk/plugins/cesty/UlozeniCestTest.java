package cz.geokuk.plugins.cesty;

import java.io.*;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.kesoid.importek.NacitacGpxPristup;

/** Uložení cest do GPX a jejich načtení zpět nesmí nic ztratit. */
public class UlozeniCestTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final Updator updator = new Updator();

	private static final Wgs[] BODY = { new Wgs(50.0, 14.0), new Wgs(50.01, 14.0), new Wgs(50.01, 14.02), new Wgs(50.0, 14.02) };

	private Cesta cesta(final Doc doc, final String nazev, final Wgs... body) {
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.setNazev(cesta, nazev);
		for (final Wgs w : body) {
			updator.pridejNaKonec(cesta, w.toMou());
		}
		return cesta;
	}

	private List<Cesta> ulozANacti(final Doc doc) throws Exception {
		final File soubor = tmp.newFile("cesty.gpx");
		new Ukladac().uloz(soubor, doc);
		final DocImportBuilder builder = new DocImportBuilder();
		try (InputStream in = new FileInputStream(soubor)) {
			NacitacGpxPristup.nacti(in, builder);
		}
		return builder.getCesty();
	}

	private static List<Wgs> body(final Cesta cesta) {
		final List<Wgs> vysledek = new ArrayList<>();
		for (final Bod bod : cesta.getBody()) {
			vysledek.add(bod.getMou().toWgs());
		}
		return vysledek;
	}

	private static List<Boolean> vzdusne(final Cesta cesta) {
		final List<Boolean> vysledek = new ArrayList<>();
		for (final Usek usek : cesta.getUseky()) {
			vysledek.add(usek.isVzdusny());
		}
		return vysledek;
	}

	@Test
	public void bodyANazevPrezijou() throws Exception {
		final Doc doc = new Doc();
		final Cesta puvodni = cesta(doc, "Okruh", BODY);
		final List<Cesta> nactene = ulozANacti(doc);
		Assert.assertEquals(1, nactene.size());
		final Cesta cesta = nactene.get(0);
		Assert.assertEquals("Okruh", cesta.getNazev());
		final List<Wgs> body = body(cesta);
		Assert.assertEquals(BODY.length, body.size());
		for (int i = 0; i < BODY.length; i++) {
			Assert.assertEquals(BODY[i].lat, body.get(i).lat, 1e-5);
			Assert.assertEquals(BODY[i].lon, body.get(i).lon, 1e-5);
		}
		Assert.assertEquals(puvodni.dalka(), cesta.dalka(), 1);
	}

	@Test
	public void viceCest() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "První", BODY[0], BODY[1]);
		cesta(doc, "Druhá", BODY[2], BODY[3]);
		final List<Cesta> nactene = ulozANacti(doc);
		Assert.assertEquals(2, nactene.size());
		Assert.assertEquals("První", nactene.get(0).getNazev());
		Assert.assertEquals("Druhá", nactene.get(1).getNazev());
	}

	@Test
	public void vzdusneUsekyPrezijou() throws Exception {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc, "Se skokem", BODY);
		final Iterator<Usek> useky = cesta.getUseky().iterator();
		useky.next();
		updator.setVzdusny(useky.next(), true);
		Assert.assertEquals(Arrays.asList(false, true, false), vzdusne(cesta));
		Assert.assertEquals(Arrays.asList(false, true, false), vzdusne(ulozANacti(doc).get(0)));
	}

	@Test
	public void nazevSeZnakyXml() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Keše & výlety <1> \"A\"", BODY[0], BODY[1]);
		Assert.assertEquals("Keše & výlety <1> \"A\"", ulozANacti(doc).get(0).getNazev());
	}

	@Test
	public void nazevSProcentem() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Hotovo na 100 %d%%", BODY[0], BODY[1]);
		Assert.assertEquals("Hotovo na 100 %d%%", ulozANacti(doc).get(0).getNazev());
	}

	@Test
	public void cestaBezNazvu() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, null, BODY[0], BODY[1]);
		Assert.assertEquals(2, body(ulozANacti(doc).get(0)).size());
	}

	@Test
	public void neulozitelnaCestaNechaPuvodniSoubor() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Okruh", BODY);
		final File soubor = tmp.newFile("cesty.gpx");
		new Ukladac().uloz(soubor, doc);
		final long delka = soubor.length();
		try {
			new Ukladac().uloz(new File(soubor, "nelze"), doc);
			Assert.fail("chyba zápisu se má ohlásit");
		} catch (final java.io.IOException e) {
			// čekáno
		}
		Assert.assertEquals("původní soubor zůstal", delka, soubor.length());
	}

	@Test
	public void jednobodovaCesta() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Bod", BODY[0]);
		final Cesta cesta = ulozANacti(doc).get(0);
		Assert.assertTrue(cesta.isJednobodova());
	}
}
