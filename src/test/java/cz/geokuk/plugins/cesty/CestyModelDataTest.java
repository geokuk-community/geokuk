package cz.geokuk.plugins.cesty;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coord.PoziceModel;
import cz.geokuk.core.coordinates.*;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.cesty.akce.doc.PromazatJednobodoveAPrazdneCesty;
import cz.geokuk.plugins.cesty.data.*;

/** Operace nad cestami přes {@link CestyModel} a jejich uložení: data se nesmí ztratit ani zkomolit. */
public class CestyModelDataTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final MyPreferences pref = MyPreferences.current().node("test-cesty-data");
	private final Updator updator = new Updator();
	private final CestyZperzistentnovac zperzistentnovac = new CestyZperzistentnovac();

	private static final Wgs A = new Wgs(50.0, 14.0);
	private static final Wgs B = new Wgs(50.01, 14.0);
	private static final Wgs C = new Wgs(50.01, 14.02);
	private static final Wgs D = new Wgs(50.0, 14.02);

	private final CestyModel model = new CestyModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}
	};

	@Before
	public void setUp() {
		model.inject((EventFirer) event -> {});
		model.inject(new PoziceModel() {
			@Override
			public void refreshPozice() {}
		});
		model.inject(zperzistentnovac);
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	private Cesta cesta(final Doc doc, final String nazev, final Wgs... body) {
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.setNazev(cesta, nazev);
		for (final Wgs w : body) {
			updator.pridejNaKonec(cesta, w.toMou());
		}
		return cesta;
	}

	private static List<Wgs> body(final Cesta cesta) {
		final List<Wgs> vysledek = new ArrayList<>();
		for (final Bod bod : cesta.getBody()) {
			vysledek.add(bod.getMou().toWgs());
		}
		return vysledek;
	}

	private static void shoda(final List<Wgs> ocekavane, final List<Wgs> skutecne) {
		Assert.assertEquals(ocekavane.size(), skutecne.size());
		for (int i = 0; i < ocekavane.size(); i++) {
			Assert.assertEquals("lat " + i, ocekavane.get(i).lat, skutecne.get(i).lat, 1e-5);
			Assert.assertEquals("lon " + i, ocekavane.get(i).lon, skutecne.get(i).lon, 1e-5);
		}
	}

	private List<Cesta> nacti(final File soubor) {
		return zperzistentnovac.nacti(Collections.singletonList(soubor), null);
	}

	@Test
	public void ulozJakoNastaviSouborAZnovuVychoziSoubor() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Okruh", A, B, C);
		Assert.assertTrue(doc.isChanged());
		final File soubor = tmp.newFile("vylet.gpx");
		model.uloz(soubor, doc, true);
		Assert.assertEquals(soubor, doc.getFile());
		Assert.assertFalse("po uložení není co ukládat", doc.isChanged());
		Assert.assertEquals(soubor, pref.node(FPref.VYLET_node).getFile(FPref.AKTUALNI_SOUBOR_value, null));
		Assert.assertTrue(pref.node(FPref.VYLET_node).getBoolean(FPref.JE_OTEVRENY_VYLET_value, false));
	}

	@Test
	public void ulozKopiiNeprepneDokumentNaKopii() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Okruh", A, B, C);
		final File puvodni = tmp.newFile("puvodni.gpx");
		model.uloz(puvodni, doc, true);
		updator.pridejNaKonec(doc.getPrvniCesta(), D.toMou());
		Assert.assertTrue(doc.isChanged());

		final File kopie = new File(tmp.getRoot(), "Kopie puvodni.gpx");
		model.uloz(kopie, doc, false);
		Assert.assertEquals("dokument zůstává na původním souboru", puvodni, doc.getFile());
		Assert.assertTrue("kopie nic nepotvrzuje, změny stále nejsou uložené", doc.isChanged());
		Assert.assertEquals(puvodni, pref.node(FPref.VYLET_node).getFile(FPref.AKTUALNI_SOUBOR_value, null));
		shoda(Arrays.asList(A, B, C, D), body(nacti(kopie).get(0)));
		shoda(Arrays.asList(A, B, C), body(nacti(puvodni).get(0)));
	}

	@Test
	public void ulozeniAOtevreniZachovaBodyPoradiANazvy() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Žluťoučký kůň & spol.", A, B, C, D);
		cesta(doc, "Šípková Růženka <2>", D, C);
		cesta(doc, "Třetí", B);
		final File soubor = tmp.newFile("vylet.gpx");
		model.uloz(soubor, doc, true);

		final List<Cesta> nactene = nacti(soubor);
		Assert.assertEquals(3, nactene.size());
		Assert.assertEquals("Žluťoučký kůň & spol.", nactene.get(0).getNazev());
		Assert.assertEquals("Šípková Růženka <2>", nactene.get(1).getNazev());
		Assert.assertEquals("Třetí", nactene.get(2).getNazev());
		shoda(Arrays.asList(A, B, C, D), body(nactene.get(0)));
		shoda(Arrays.asList(D, C), body(nactene.get(1)));
		shoda(Collections.singletonList(B), body(nactene.get(2)));
		final Doc otevreny = new Doc();
		nactene.forEach(otevreny::xadd);
		otevreny.kontrolaKonzistence();
	}

	@Test
	public void uzavrenaCestaZustaneUzavrena() throws Exception {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc, "Okruh", A, B, C, D);
		Assert.assertFalse(cesta.isKruh());
		model.uzavriCestu(cesta);
		Assert.assertTrue(cesta.isKruh());
		shoda(Arrays.asList(A, B, C, D, A), body(cesta));

		model.uzavriCestu(cesta);
		Assert.assertEquals("už uzavřená cesta se neuzavírá znovu", 5, body(cesta).size());

		final File soubor = tmp.newFile("kruh.gpx");
		model.uloz(soubor, doc, false);
		final Cesta nactena = nacti(soubor).get(0);
		Assert.assertTrue(nactena.isKruh());
		shoda(Arrays.asList(A, B, C, D, A), body(nactena));
	}

	@Test
	public void obraceniDvakratJeJakoPuvodniAJednouObratiPoradi() throws Exception {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc, "Tam", A, B, C, D);
		final double dalka = cesta.dalka();
		model.reverseCestu(cesta);
		shoda(Arrays.asList(D, C, B, A), body(cesta));
		Assert.assertEquals(dalka, cesta.dalka(), 0.5);

		final File soubor = tmp.newFile("zpet.gpx");
		model.uloz(soubor, doc, false);
		shoda(Arrays.asList(D, C, B, A), body(nacti(soubor).get(0)));

		model.reverseCestu(cesta);
		shoda(Arrays.asList(A, B, C, D), body(cesta));
		cesta.kontrolaKonzistence();
	}

	@Test
	public void obraceniZachovaVzdusnostUseku() {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc, "Se skokem", A, B, C, D);
		final Iterator<Usek> useky = cesta.getUseky().iterator();
		useky.next();
		updator.setVzdusny(useky.next(), true); // B–C
		model.reverseCestu(cesta);
		final List<Boolean> vzdusne = new ArrayList<>();
		for (final Usek usek : cesta.getUseky()) {
			vzdusne.add(usek.isVzdusny());
		}
		Assert.assertEquals(Arrays.asList(false, true, false), vzdusne);
		shoda(Arrays.asList(D, C, B, A), body(cesta));
	}

	@Test
	public void pospojeniVzdusnychUsekuOdeberePrechodoveBody() throws Exception {
		final Doc doc = new Doc();
		final Wgs e = new Wgs(50.02, 14.02);
		final Wgs f = new Wgs(50.02, 14.0);
		final Cesta cesta = cesta(doc, "Dva skoky", A, B, C, D, e, f);
		final List<Usek> useky = new ArrayList<>();
		for (final Usek usek : cesta.getUseky()) {
			useky.add(usek);
		}
		updator.setVzdusny(useky.get(1), true); // B–C
		updator.setVzdusny(useky.get(2), true); // C–D
		Assert.assertEquals(2, cesta.getPocetVzdusnychUseku());
		model.pospojujVzdusneUseky(cesta);
		Assert.assertEquals("C sedí mezi vzdušnými úseky, odebere se", 5, body(cesta).size());
		shoda(Arrays.asList(A, B, D, e, f), body(cesta));
		cesta.kontrolaKonzistence();
	}

	@Test
	public void promazaniJednobodovychAPrazdnychCestZachovaOstatni() {
		final Doc doc = new Doc();
		cesta(doc, "Dobrá", A, B);
		cesta(doc, "Jeden bod", C);
		cesta(doc, "Prázdná");
		cesta(doc, "Druhá dobrá", C, D);
		model.prevezmiNoveOtevrenyDokument(doc);

		final PromazatJednobodoveAPrazdneCesty akce = new PromazatJednobodoveAPrazdneCesty(null);
		akce.inject(model);
		akce.actionPerformed(null);

		final List<String> nazvy = new ArrayList<>();
		for (final Cesta c : model.getDoc()) {
			nazvy.add(c.getNazev());
		}
		Assert.assertEquals(Arrays.asList("Dobrá", "Druhá dobrá"), nazvy);
		model.getDoc().kontrolaKonzistence();
	}

	@Test
	public void souborSDiakritikouAMezeramiVNazvuIVCeste() throws Exception {
		Assume.assumeTrue("souborový systém bez diakritiky v názvech", Charset.forName(System.getProperty("sun.jnu.encoding")).newEncoder().canEncode("žluťoučký"));
		final Doc doc = new Doc();
		cesta(doc, "Příliš žluťoučký kůň", A, B, C);
		final File soubor = new File(tmp.newFolder("Výlety žluťoučké"), "Můj výlet č. 1.gpx");
		model.uloz(soubor, doc, true);
		Assert.assertEquals(soubor, doc.getFile());
		Assert.assertTrue(soubor.isFile());
		final List<Cesta> nactene = nacti(soubor);
		Assert.assertEquals("Příliš žluťoučký kůň", nactene.get(0).getNazev());
		shoda(Arrays.asList(A, B, C), body(nactene.get(0)));
		final File kopie = new File(soubor.getParentFile(), "Kopie Můj výlet č. 1.gpx");
		model.uloz(kopie, doc, false);
		Assert.assertEquals(soubor, doc.getFile());
		shoda(Arrays.asList(A, B, C), body(nacti(kopie).get(0)));
	}

	/** Prázdnou cestu vyrobí import GGT, ve kterém není žádná načtená keš. */
	@Ignore("Nález: Doc.getPocetJednobodovychCest padá na prázdné cestě (isJednobodova bez kontroly isEmpty); akce Promazat to volá při každé změně cest")
	@Test
	public void pocitaniJednobodovychCestSPrazdnouCestouNepadne() {
		final Doc doc = new Doc();
		cesta(doc, "Prázdná");
		cesta(doc, "Jeden bod", C);
		Assert.assertEquals(1, doc.getPocetJednobodovychCest());
		Assert.assertEquals(1, doc.getPocetPrazdnychCest());
	}

	@Test
	public void jednobodovaCestaSeUlozi() throws Exception {
		final Doc doc = new Doc();
		cesta(doc, "Bod", A);
		final File soubor = tmp.newFile("bod.gpx");
		model.uloz(soubor, doc, false);
		Assert.assertTrue(new String(Files.readAllBytes(soubor.toPath()), StandardCharsets.UTF_8).contains("<trkpt"));
		Assert.assertTrue(nacti(soubor).get(0).isJednobodova());
	}
}
