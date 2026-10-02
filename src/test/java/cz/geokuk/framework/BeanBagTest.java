package cz.geokuk.framework;

import static org.junit.Assert.*;

import javax.swing.SwingUtilities;

import org.junit.Test;

/** Injekce podle typu parametru metod inject a pořadí inicializace. */
public class BeanBagTest {

	public interface Sluzba {}

	public static class PrvniSluzba implements Sluzba {}

	public static class DruhaSluzba implements Sluzba {}

	public static class Klient implements AfterInjectInit {
		Sluzba sluzba;
		boolean sluzbaPriInitu;

		public void inject(final Sluzba sluzba) {
			this.sluzba = sluzba;
		}

		@Override
		public void initAfterInject() {
			sluzbaPriInitu = sluzba != null;
		}
	}

	public static class Posluchac {
		int prijato;

		public void onEvent(final EventManagerTest.Udalost u) {
			prijato++;
		}
	}

	private static BeanBag bag() {
		final BeanBag bag = new BeanBag();
		bag.inject(new EventManager());
		return bag;
	}

	@Test
	public void injektujePodleTypuPredInitAfterInject() throws Exception {
		final BeanBag bag = bag();
		final PrvniSluzba sluzba = bag.registerSigleton(new PrvniSluzba());
		final Klient klient = bag.registerSigleton(new Klient());
		SwingUtilities.invokeAndWait(bag::init);
		assertSame(sluzba, klient.sluzba);
		assertTrue("initAfterInject až po injekci", klient.sluzbaPriInitu);
	}

	@Test
	public void dveImplementaceJsouChyba() throws Exception {
		final BeanBag bag = bag();
		bag.registerSigleton(new PrvniSluzba());
		bag.registerSigleton(new DruhaSluzba());
		bag.registerSigleton(new Klient());
		try {
			bag.init();
			fail("Dvě implementace téhož typu se nesmí tiše vybrat");
		} catch (final RuntimeException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("Prilis mnoho"));
		}
	}

	@Test(expected = RuntimeException.class)
	public void chybejiciImplementaceJeChyba() {
		final BeanBag bag = bag();
		bag.registerSigleton(new Klient());
		bag.init();
	}

	@Test(expected = RuntimeException.class)
	public void poInicializaciNelzeRegistrovat() throws Exception {
		final BeanBag bag = bag();
		SwingUtilities.invokeAndWait(bag::init);
		bag.registerSigleton(new PrvniSluzba());
	}

	@Test
	public void initRegistrujePosluchaceUdalosti() throws Exception {
		final EventManager em = new EventManager();
		final BeanBag bag = new BeanBag();
		bag.inject(em);
		final Posluchac p = new Posluchac();
		SwingUtilities.invokeAndWait(() -> {
			bag.init(p);
			em.fire(new EventManagerTest.Udalost(1));
		});
		assertEquals(1, p.prijato);
	}
}
