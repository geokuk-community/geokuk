package cz.geokuk.plugins.cesty.data;

import java.util.*;

import org.junit.*;

import cz.geokuk.core.coordinates.*;

/** Úpravy cest tak, jak je dělá uživatel v mapě. */
public class CestaTest {

	private static final Mou A = new Wgs(50.00, 14.00).toMou();
	private static final Mou B = new Wgs(50.01, 14.00).toMou();
	private static final Mou C = new Wgs(50.01, 14.02).toMou();
	private static final Mou D = new Wgs(50.00, 14.02).toMou();

	private final Updator updator = new Updator();
	private Doc doc;

	@Before
	public void setUp() {
		doc = new Doc();
	}

	private Cesta cesta(final Mou... body) {
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		for (final Mou mou : body) {
			updator.pridejNaKonec(cesta, mou);
		}
		cesta.kontrolaKonzistence();
		return cesta;
	}

	private static List<Mou> body(final Cesta cesta) {
		final List<Mou> vysledek = new ArrayList<>();
		for (final Bod bod : cesta.getBody()) {
			vysledek.add(bod.getMou());
		}
		return vysledek;
	}

	private static Bod bod(final Cesta cesta, final int index) {
		final Iterator<Bod> it = cesta.getBody().iterator();
		for (int i = 0; i < index; i++) {
			it.next();
		}
		return it.next();
	}

	private static double dalka(final Mou... body) {
		double suma = 0;
		for (int i = 1; i < body.length; i++) {
			suma += FGeoKonvertor.dalka(body[i - 1].toWgs(), body[i].toWgs());
		}
		return suma;
	}

	@Test
	public void prazdnaAJednobodova() {
		final Cesta prazdna = cesta();
		Assert.assertTrue(prazdna.isEmpty());
		Assert.assertFalse(prazdna.isKruh());
		Assert.assertEquals(0, prazdna.dalka(), 0);

		final Cesta jednobodova = cesta(A);
		Assert.assertTrue(jednobodova.isJednobodova());
		Assert.assertFalse(jednobodova.isKruh());
		Assert.assertEquals(0, jednobodova.dalka(), 0);
		Assert.assertEquals(A, jednobodova.getStart().getMou());
		Assert.assertEquals(A, jednobodova.getCil().getMou());
	}

	@Test
	public void delkaJeSoucetUseku() {
		final Cesta cesta = cesta(A, B, C, D);
		Assert.assertEquals(Arrays.asList(A, B, C, D), body(cesta));
		Assert.assertEquals(dalka(A, B, C, D), cesta.dalka(), 0.01);
		Assert.assertEquals(dalka(A, D), cesta.dalkaStartuACile(), 0.01);
		Assert.assertEquals(1112, dalka(A, B), 2);
	}

	@Test
	public void kruh() {
		final Cesta cesta = cesta(A, B, C, D, A);
		Assert.assertTrue(cesta.isKruh());
		Assert.assertEquals(0, cesta.dalkaStartuACile(), 0.01);
	}

	@Test
	public void obraceni() {
		final Cesta cesta = cesta(A, B, C);
		final double dalka = cesta.dalka();
		updator.reverse(cesta);
		cesta.kontrolaKonzistence();
		Assert.assertEquals(Arrays.asList(C, B, A), body(cesta));
		Assert.assertEquals(dalka, cesta.dalka(), 0.01);
	}

	@Test
	public void odebraniBoduUprostred() {
		final Cesta cesta = cesta(A, B, C);
		final Bod b = bod(cesta, 1);
		updator.removeBod(b);
		cesta.kontrolaKonzistence();
		Assert.assertEquals(Arrays.asList(A, C), body(cesta));
		Assert.assertEquals(dalka(A, C), cesta.dalka(), 0.01);
	}

	@Test
	public void rozdeleniVBode() {
		final Cesta cesta = cesta(A, B, C, D);
		final double celkem = cesta.dalka();
		final Bod b = bod(cesta, 1);
		final Cesta druha = updator.rozdelCestuVBode(b);
		cesta.kontrolaKonzistence();
		druha.kontrolaKonzistence();
		Assert.assertEquals(Arrays.asList(A, B), body(cesta));
		Assert.assertEquals(Arrays.asList(B, C, D), body(druha));
		Assert.assertEquals(celkem, cesta.dalka() + druha.dalka(), 0.01);
		doc.kontrolaKonzistence();
	}

	@Test
	public void pripojeniCestyZa() {
		final Cesta prvni = cesta(A, B);
		final Cesta druha = cesta(C, D);
		updator.pipojitCestuZa(prvni, druha);
		prvni.kontrolaKonzistence();
		Assert.assertEquals(Arrays.asList(A, B, C, D), body(prvni));
		Assert.assertEquals(dalka(A, B, C, D), prvni.dalka(), 0.01);
	}

	@Test
	public void pripojeniNavazujiciCestySlouciSpolecnyBod() {
		final Cesta prvni = cesta(A, B);
		final Cesta druha = cesta(B, C);
		updator.pipojitCestuZa(prvni, druha);
		prvni.kontrolaKonzistence();
		Assert.assertEquals(Arrays.asList(A, B, C), body(prvni));
	}

	@Test
	public void vlozeniBoduDoUseku() {
		final Cesta cesta = cesta(A, C);
		final Usek usek = cesta.getUseky().iterator().next();
		updator.rozdelUsekNaDvaNove(usek, B);
		cesta.kontrolaKonzistence();
		Assert.assertEquals(Arrays.asList(A, B, C), body(cesta));
	}

	@Test
	public void vzdusneUseky() {
		final Cesta cesta = cesta(A, B, C, D);
		final Iterator<Usek> useky = cesta.getUseky().iterator();
		updator.setVzdusny(useky.next(), true);
		useky.next();
		updator.setVzdusny(useky.next(), true);
		Assert.assertEquals(2, cesta.getPocetVzdusnychUseku());
		Assert.assertEquals(2, cesta.getPocetPodcestVzdusnychUseku());
	}

	@Test
	public void nejblizsiBodNeboUsek() {
		final Cesta cesta = cesta(A, B, C, D);
		final Bousek0 naBode = cesta.locateNejblizsi(C);
		Assert.assertTrue(naBode instanceof Bod);
		Assert.assertEquals(C, ((Bod) naBode).getMou());

		final Bousek0 uprostredUseku = cesta.locateNejblizsi(new Wgs(50.0101, 14.01).toMou());
		Assert.assertTrue(uprostredUseku instanceof Usek);
		Assert.assertEquals(B, ((Usek) uprostredUseku).getBvzad().getMou());
	}

	/** Název cesty z GPX se v nabídkách zobrazí jako text. */
	@Test
	public void nazevVHtmlJeText() {
		final Cesta cesta = Cesta.create();
		cesta.setNazev("Výlet <img src=http://sledovac/c> & spol.");
		Assert.assertEquals(" <i>\"Výlet &lt;img src=http://sledovac/c&gt; &amp; spol.\"</i>", cesta.getNazevHtml());
	}
}
