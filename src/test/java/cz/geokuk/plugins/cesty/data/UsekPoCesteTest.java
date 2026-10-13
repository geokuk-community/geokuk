package cz.geokuk.plugins.cesty.data;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

/** Úsek po cestě: trasa platí jen pro krajní body, pro které se spočítala. */
public class UsekPoCesteTest {

	private static final Wgs A = new Wgs(50.00, 14.00);
	private static final Wgs X = new Wgs(50.005, 14.003);
	private static final Wgs B = new Wgs(50.01, 14.00);
	private static final Wgs C = new Wgs(50.01, 14.02);

	private final Updator updator = new Updator();
	private Doc doc;
	private Cesta cesta;

	@Before
	public void setUp() {
		doc = new Doc();
		cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, A.toMou());
		updator.pridejNaKonec(cesta, B.toMou());
		updator.pridejNaKonec(cesta, C.toMou());
	}

	private Usek prvni() {
		return cesta.getStart().getUvpred();
	}

	private Usek druhy() {
		return cesta.getCil().getUvzad();
	}

	private boolean spocitej(final Usek usek, final Wgs... body) {
		return updator.setTrasa(usek, usek.getBvzad().getMou(), usek.getBvpred().getMou(), Trasa.nalezena(Arrays.asList(body)));
	}

	@Test
	public void novyUsekJeRovne() {
		assertEquals(TypUseku.ROVNE, prvni().getTyp());
		assertNull(prvni().getTrasa());
		assertNull(prvni().getStavTrasy());
	}

	@Test
	public void trasaUrcujeDelku() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		final double rovne = prvni().dalka();
		assertTrue(spocitej(prvni(), A, X, B));
		assertEquals(3, prvni().getTrasa().size());
		assertEquals(Trasa.Stav.NALEZENO, prvni().getStavTrasy());
		assertEquals(A.vzdalenost(X) + X.vzdalenost(B), prvni().dalka(), 1);
		assertTrue(prvni().dalka() > rovne + 10);
		assertEquals(prvni().dalka(), cesta.dalkaPoCestach(), 1e-9);
		assertEquals(1, cesta.getPocetUsekuPoCeste());
	}

	@Test
	public void vzdalenostADelkyPodelTrasy() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		final long rovne = prvni().computeKvadratVzdalenosti(X.toMou());
		spocitej(prvni(), A, X, B);
		assertTrue("bod trasy je na úseku", prvni().computeKvadratVzdalenosti(X.toMou()) < rovne / 1000);
		assertEquals(A.vzdalenost(X), prvni().getMouDelkaVzad(X.toMou()), 1);
		assertEquals(X.vzdalenost(B), prvni().getMouDelkaVpred(X.toMou()), 1);
	}

	@Test
	public void rovnyUsekTrasuNeprijme() {
		assertFalse(spocitej(prvni(), A, X, B));
		assertNull(prvni().getTrasa());
	}

	@Test
	public void posunutyBodTrasuZneplatni() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		spocitej(prvni(), A, X, B);
		updator.setMouable(cesta.getStart(), new Wgs(50.0, 13.99).toMou());
		assertNull(prvni().getTrasa());
		assertNull(prvni().getStavTrasy());
		assertEquals(new Wgs(50.0, 13.99).vzdalenost(B), prvni().dalka(), 1);
	}

	@Test
	public void malePosunutiTrasuNecha() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		spocitej(prvni(), A, X, B);
		// Přichycení bodu na keš po načtení posune bod nejvýš o kousek.
		updator.setMouableButNoChange(cesta.getStart(), new Wgs(50.00001, 14.0).toMou());
		assertNotNull(prvni().getTrasa());
	}

	@Test
	public void vysledekProStareBodySeNeulozi() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		final Mou od = prvni().getBvzad().getMou();
		final Mou kam = prvni().getBvpred().getMou();
		updator.setMouable(cesta.getStart(), new Wgs(50.0, 13.99).toMou());
		assertFalse(updator.setTrasa(prvni(), od, kam, Trasa.nalezena(Arrays.asList(A, X, B))));
		assertNull(prvni().getStavTrasy());
	}

	@Test
	public void otocenaCestaMaOtocenouTrasu() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		spocitej(prvni(), A, X, B);
		updator.reverse(cesta);
		final Usek usek = cesta.getCil().getUvzad();
		assertEquals(B.toMou(), usek.getTrasa().get(0));
		assertEquals(A.toMou(), usek.getTrasa().get(2));
	}

	@Test
	public void stavBezTrasy() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		assertTrue(updator.setTrasa(prvni(), prvni().getBvzad().getMou(), prvni().getBvpred().getMou(), Trasa.bez(Trasa.Stav.BEZ_MAPY)));
		assertEquals(Trasa.Stav.BEZ_MAPY, prvni().getStavTrasy());
		assertNull(prvni().getTrasa());
		assertEquals(A.vzdalenost(B), prvni().dalka(), 1);
		updator.zapomenTrasu(prvni());
		assertNull(prvni().getStavTrasy());
	}

	@Test
	public void zruseneHledaniSeNeulozi() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		assertFalse(updator.setTrasa(prvni(), prvni().getBvzad().getMou(), prvni().getBvpred().getMou(), Trasa.bez(Trasa.Stav.ZRUSENO)));
	}

	@Test
	public void rozdeleniUsekuPoCesteDavaDvaPoCeste() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		spocitej(prvni(), A, X, B);
		updator.rozdelUsekNaDvaNove(prvni(), X.toMou());
		assertEquals(TypUseku.PO_CESTE, prvni().getTyp());
		assertEquals(TypUseku.PO_CESTE, prvni().getBvpred().getUvpred().getTyp());
		assertNull(prvni().getTrasa());
	}

	@Test
	public void smazaniBoduSpojiUsekyPodleTypu() {
		updator.setTyp(prvni(), TypUseku.PO_CESTE);
		updator.removeBod(prvni().getBvpred());
		assertEquals(TypUseku.PO_CESTE, prvni().getTyp());
		assertNull(prvni().getTrasa());

		final Cesta c2 = Cesta.create();
		updator.xadd(doc, c2);
		updator.pridejNaKonec(c2, A.toMou());
		updator.pridejNaKonec(c2, B.toMou());
		updator.pridejNaKonec(c2, C.toMou());
		updator.setVzdusny(c2.getStart().getUvpred(), true);
		updator.removeBod(c2.getStart().getUvpred().getBvpred());
		assertEquals(TypUseku.ROVNE, c2.getStart().getUvpred().getTyp());
	}

	@Test
	public void vzdusnyZustavaVzdusny() {
		updator.setVzdusny(druhy(), true);
		assertTrue(druhy().isVzdusny());
		assertEquals(TypUseku.VZDUSNY, druhy().getTyp());
		assertEquals(0, druhy().dalka(), 0);
		updator.setVzdusny(druhy(), false);
		assertEquals(TypUseku.ROVNE, druhy().getTyp());
	}
}
