package cz.geokuk.plugins.kesoid.mapicon;

import org.junit.*;

import cz.geokuk.framework.Atom;

/** Chybějící vybraná sada ikon nesmí zablokovat načtení ikon ani keší. */
public class IkonNacitacLoaderTest {

	@Test
	public void chybejiciSadaSeNahradiStandardni() throws Exception {
		final IkonBag bag = new IkonNacitacLoader().nacti(null, true, Atom.valueOf(ASada.class, "Neexistující sada"));
		Assert.assertNotNull(bag);
		Assert.assertEquals(ASada.STANDARD.name(), bag.getSada().getName());
	}
}
