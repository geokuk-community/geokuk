package cz.geokuk.plugins.cesty.data;

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
}
