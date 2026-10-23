package cz.geokuk.plugins.kesoid.mapicon;

import org.junit.*;

import cz.geokuk.framework.Atom;

/** Chybějící vybraná sada ikon nesmí zablokovat načtení ikon ani keší. */
public class IkonNacitacLoaderTest {

	@Test
	public void chybejiciSadaSeNahradiStandardni() throws Exception {
		final IkonBag bag = new IkonNacitacLoader().nacti(true, Atom.valueOf(ASada.class, "Neexistující sada"), Zvyrazneni.VYCHOZI);
		Assert.assertNotNull(bag);
		Assert.assertEquals(ASada.STANDARD.name(), bag.getSada().getName());
	}

	/** Genom je součástí kontextu načtených keší; nový by při změně zvýraznění vynutil přečtení všech zdrojů. */
	@Test
	public void zmenaZvyrazneniZachovaGenom() throws Exception {
		final IkonNacitacLoader loader = new IkonNacitacLoader();
		final IkonBag prvni = loader.nacti(true, ASada.STANDARD, Zvyrazneni.VYCHOZI);
		final IkonBag druhy = loader.nacti(true, ASada.STANDARD, Zvyrazneni.BEZ);
		Assert.assertNotSame(prvni, druhy);
		Assert.assertSame(prvni.getGenom(), druhy.getGenom());
	}
}
