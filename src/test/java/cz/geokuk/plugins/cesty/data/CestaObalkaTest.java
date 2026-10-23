package cz.geokuk.plugins.cesty.data;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Mouable;
import cz.geokuk.core.coordinates.Wgs;

/** Hledání nejbližšího bodu cesty zkouší nejdřív obálku cesty, ne všechny body. */
public class CestaObalkaTest {

	private static final long KVADRAT = 10_000L * 10_000L;

	private static class SPocitadlem implements Mouable {
		final Mou mou;
		final AtomicInteger dotazy;

		SPocitadlem(final Mou mou, final AtomicInteger dotazy) {
			this.mou = mou;
			this.dotazy = dotazy;
		}

		@Override
		public Mou getMou() {
			dotazy.incrementAndGet();
			return mou;
		}
	}

	@Test
	public void dalekoOdCestyNeProchazeBody() {
		final AtomicInteger dotazy = new AtomicInteger();
		final Updator updator = new Updator();
		final Doc doc = new Doc();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		for (int i = 0; i < 200; i++) {
			updator.pridejNaKonec(cesta, new SPocitadlem(new Wgs(50.0 + i * 0.0001, 14.0).toMou(), dotazy));
		}
		final Mou daleko = new Wgs(10.0, 100.0).toMou();
		Assert.assertNull(doc.locateNejblizsiDoKvadratuVzdalenosi(daleko, KVADRAT, null, true));
		dotazy.set(0);
		for (int i = 0; i < 20; i++) {
			Assert.assertNull(doc.locateNejblizsiDoKvadratuVzdalenosi(daleko, KVADRAT, null, true));
		}
		Assert.assertEquals("body se neprocházejí", 0, dotazy.get());
	}

	@Test
	public void poZmeneCestyObalkaPlati() {
		final Updator updator = new Updator();
		final Doc doc = new Doc();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.0, 14.0).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.001, 14.0).toMou());
		final Mou vedle = new Wgs(50.0, 14.5).toMou();
		Assert.assertNull(doc.locateNejblizsiDoKvadratuVzdalenosi(vedle, KVADRAT, null, true));
		updator.pridejNaKonec(cesta, vedle);
		Assert.assertNotNull(doc.locateNejblizsiDoKvadratuVzdalenosi(vedle, KVADRAT, null, true));
	}

	private Doc doc;
	private Cesta cesta;

	private Bod dvaBody(final Updator updator) {
		doc = new Doc();
		cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.0, 14.0).toMou());
		return updator.pridejNaKonec(cesta, new Wgs(50.001, 14.0).toMou());
	}

	/** Načtení cest ze souboru přesouvá body bez změny dokumentu; obálka musí jít s nimi. */
	@Test
	public void presunBoduPriNacteniPosuneObalku() {
		final Updator updator = new Updator();
		final Bod bod = dvaBody(updator);
		final Mou daleko = new Wgs(50.0, 14.5).toMou();
		Assert.assertNull(doc.locateNejblizsiDoKvadratuVzdalenosi(daleko, KVADRAT, null, true));
		updator.setMouableButNoChange(bod, daleko);
		Assert.assertNotNull("přesunutý bod se má najít", doc.locateNejblizsiDoKvadratuVzdalenosi(daleko, KVADRAT, null, true));
	}

	/** Trasa po cestě může vybočit daleko od bodů; i její zákrut musí jít najít. */
	@Test
	public void nactenaTrasaRozsiriObalku() {
		final Updator updator = new Updator();
		dvaBody(updator);
		final Mou zakrut = new Wgs(50.0005, 14.5).toMou();
		Assert.assertNull(doc.locateNejblizsiDoKvadratuVzdalenosi(zakrut, KVADRAT, null, true));
		final Usek usek = cesta.getUseky().iterator().next();
		updator.setNactenaTrasa(usek, Arrays.asList(new Wgs(50.0, 14.0).toMou(), zakrut, new Wgs(50.001, 14.0).toMou()));
		Assert.assertNotNull("zákrut trasy se má najít", doc.locateNejblizsiDoKvadratuVzdalenosi(zakrut, KVADRAT, null, true));
	}

	/** Bod kousek pod mezní vzdáleností od cesty se najde i přes obálku. */
	@Test
	public void kousekPodMezi() {
		final Updator updator = new Updator();
		dvaBody(updator);
		final Mou start = new Wgs(50.0, 14.0).toMou();
		final Mou blizko = new Mou(start.xx - 9_000, start.yy);
		Assert.assertNotNull(doc.locateNejblizsiDoKvadratuVzdalenosi(blizko, KVADRAT, null, true));
	}

	/** Když myš opustí mapu, hledá se bez polohy; nic se nenajde a nic nespadne. */
	@Test
	public void bezPolohyMysiNicNenajde() {
		final Updator updator = new Updator();
		final Doc doc = new Doc();
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.0, 14.0).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.001, 14.0).toMou());
		Assert.assertNull(doc.locateNejblizsiDoKvadratuVzdalenosi(null, KVADRAT, null, true));
	}
}
