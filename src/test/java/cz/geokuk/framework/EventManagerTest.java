package cz.geokuk.framework;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.Test;

/** Doručování událostí pozorovatelům podle typu parametru metody onEvent. */
public class EventManagerTest {

	public static class Udalost extends Event0<Model0> {
		final int cislo;

		Udalost(final int cislo) {
			this.cislo = cislo;
		}
	}

	public static class JinaUdalost extends Event0<Model0> {}

	public static class Pozorovatel {
		final List<Integer> prijate = new ArrayList<>();
		int jinych;

		public void onEvent(final Udalost u) {
			prijate.add(u.cislo);
		}

		public void onEvent(final JinaUdalost u) {
			jinych++;
		}

		/** Metoda se jménem onEvent, ale jinými parametry, nesmí registraci ostatních zastavit. */
		public void onEvent(final Udalost u, final int navic) {}
	}

	private static void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	@Test
	public void dorucujePodleTypu() throws Exception {
		final EventManager em = new EventManager();
		final Pozorovatel p = new Pozorovatel();
		naEdt(() -> {
			em.registerWeakly(p, false);
			em.fire(new Udalost(1));
			em.fire(new Udalost(2));
		});
		assertEquals(java.util.Arrays.asList(1, 2), p.prijate);
		assertEquals(0, p.jinych);
	}

	@Test
	public void pozdeRegistrovanyDostanePosledniUdalost() throws Exception {
		final EventManager em = new EventManager();
		final Pozorovatel p = new Pozorovatel();
		naEdt(() -> {
			em.fire(new Udalost(1));
			em.fire(new Udalost(2));
			em.registerWeakly(p, false);
		});
		assertEquals(java.util.Collections.singletonList(2), p.prijate);
	}

	@Test
	public void jenVyvolatNeregistruje() throws Exception {
		final EventManager em = new EventManager();
		final Pozorovatel p = new Pozorovatel();
		naEdt(() -> {
			em.fire(new Udalost(1));
			em.registerWeakly(p, true);
			em.fire(new Udalost(2));
		});
		assertEquals(java.util.Collections.singletonList(1), p.prijate);
	}

	@Test
	public void odregistrovanyNedostava() throws Exception {
		final EventManager em = new EventManager();
		final Pozorovatel p = new Pozorovatel();
		naEdt(() -> {
			em.registerWeakly(p, false);
			em.fire(new Udalost(1));
			em.unregister(p);
			em.fire(new Udalost(2));
		});
		assertEquals(java.util.Collections.singletonList(1), p.prijate);
	}

	@Test(expected = RuntimeException.class)
	public void mimoEdtSeNesmiFirovat() {
		new EventManager().fire(new Udalost(1));
	}
}
