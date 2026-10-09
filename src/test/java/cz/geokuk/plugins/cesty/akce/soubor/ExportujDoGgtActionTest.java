package cz.geokuk.plugins.cesty.akce.soubor;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.plugins.kesoid.Wpt;

/** Export do GGT má smysl jen u cest s keší. */
public class ExportujDoGgtActionTest {

	private final Updator updator = new Updator();

	private Cesta cesta(final Doc doc) {
		final Cesta cesta = Cesta.create();
		updator.xadd(doc, cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.0, 14.0).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.01, 14.0).toMou());
		return cesta;
	}

	@Test
	public void cestaJenZBoduKesNeobsahuje() {
		final Doc doc = new Doc();
		cesta(doc);
		Assert.assertFalse(ExportujDoGgtAction.obsahujeKes(doc));
		Assert.assertFalse(ExportujDoGgtAction.obsahujeKes(null));
	}

	@Test
	public void cestaSKesi() {
		final Doc doc = new Doc();
		final Cesta cesta = cesta(doc);
		final Kes kes = new Kes();
		final Wpt wpt = new Wpt();
		wpt.setWgs(new Wgs(50.02, 14.0));
		kes.addWpt(wpt);
		updator.pridejNaKonec(cesta, wpt);
		Assert.assertTrue(ExportujDoGgtAction.obsahujeKes(doc));
	}
}
