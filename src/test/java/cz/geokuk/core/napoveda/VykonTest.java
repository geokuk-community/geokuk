package cz.geokuk.core.napoveda;

import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.napoveda.Vykon.Souhrn;
import cz.geokuk.core.napoveda.Vykon.Velicina;

/** Klouzavé okno měření: medián, p95, přetečení, vynulování a měření událostí na EDT. */
public class VykonTest {

	@Before
	@After
	public void vynuluj() {
		Vykon.vynuluj();
	}

	@Test
	public void medianAP95() {
		for (int ms = 1; ms <= 100; ms++) {
			Vykon.zaznamenej(Velicina.PREKRESLENI, ms * 1_000_000L);
		}
		final Souhrn s = Vykon.souhrn(Velicina.PREKRESLENI);
		Assert.assertEquals(100, s.pocet);
		Assert.assertEquals(50.0, s.median, 0.001);
		Assert.assertEquals(95.0, s.p95, 0.001);
		Assert.assertEquals(100.0, s.max, 0.001);
		Assert.assertEquals(100, s.zaPoslednich10s);
	}

	@Test
	public void oknoDrziJenPosledniHodnoty() {
		for (int i = 0; i < Vykon.VELIKOST_OKNA; i++) {
			Vykon.zaznamenej(Velicina.DLAZDICE_ONLINE, 1_000_000_000L);
		}
		for (int i = 0; i < Vykon.VELIKOST_OKNA; i++) {
			Vykon.zaznamenej(Velicina.DLAZDICE_ONLINE, 2_000_000L);
		}
		final Souhrn s = Vykon.souhrn(Velicina.DLAZDICE_ONLINE);
		Assert.assertEquals(2 * Vykon.VELIKOST_OKNA, s.pocet);
		Assert.assertEquals("medián jen z posledních hodnot", 2.0, s.p95, 0.001);
		Assert.assertEquals("max od vynulování", 1000.0, s.max, 0.001);
	}

	@Test
	public void prazdneAVynulovani() {
		Assert.assertEquals(0, Vykon.souhrn(Velicina.EDT).pocet);
		Assert.assertEquals(0.0, Vykon.souhrn(Velicina.EDT).median, 0);
		Vykon.zaznamenej(Velicina.EDT, (Vykon.PRAH_EDT_MS + 1) * 1_000_000);
		Assert.assertEquals(1, Vykon.edtNadPrahem());
		Vykon.vynuluj();
		Assert.assertEquals(0, Vykon.souhrn(Velicina.EDT).pocet);
		Assert.assertEquals(0, Vykon.edtNadPrahem());
	}

	@Test
	public void textAJson() {
		Vykon.zaznamenej(Velicina.PREKRESLENI, 12_000_000);
		Vykon.zaznamenej(Velicina.DLAZDICE_OFFLINE, 3_000_000);
		final String text = Vykon.text();
		Assert.assertTrue(text, text.contains("Překreslení mapy: medián 12.0 ms"));
		Assert.assertTrue(text, text.contains("Získání dlaždice online: bez záznamu"));
		Assert.assertTrue(text, text.contains("Dlaždic za sekundu (posledních 10 s): 0.1"));
		final String json = Vykon.json();
		Assert.assertTrue(json, json.contains("\"prekresleni\":{\"pocet\":1,\"medianMs\":12.000"));
		Assert.assertTrue(json, json.contains("\"dlazdicZaSekundu\":0.10"));
	}

	/** Událost, jejíž obsluha vyhodí výjimku. */
	private static final class ChybnaUdalost extends java.awt.AWTEvent implements java.awt.ActiveEvent {
		private static final long serialVersionUID = 1L;

		ChybnaUdalost() {
			super(new Object(), java.awt.AWTEvent.RESERVED_ID_MAX + 1);
		}

		@Override
		public void dispatch() {
			throw new IllegalStateException("zkouška");
		}
	}

	@Test
	public void mericiFrontaMeriAPropoustiVyjimky() throws Exception {
		final Vykon.MericiFronta fronta = new Vykon.MericiFronta();
		Toolkit.getDefaultToolkit().getSystemEventQueue().push(fronta);
		EventQueue.invokeAndWait(() -> {
			try {
				Thread.sleep(25);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		Assert.assertTrue(Vykon.souhrn(Velicina.EDT).max >= 20.0);
		final RuntimeException[] zachycena = new RuntimeException[1];
		final long pred = Vykon.souhrn(Velicina.EDT).pocet;
		EventQueue.invokeAndWait(() -> {
			try {
				fronta.dispatchEvent(new ChybnaUdalost());
			} catch (final RuntimeException e) {
				zachycena[0] = e;
			}
		});
		Assert.assertTrue("výjimka projde beze změny", zachycena[0] instanceof IllegalStateException);
		Assert.assertEquals("zkouška", zachycena[0].getMessage());
		Assert.assertTrue("vnořená událost se změří i s výjimkou", Vykon.souhrn(Velicina.EDT).pocet > pred);
	}

	@Test
	public void modalniSmyckaVeVnoreneUdalostiSeNemeri() throws Exception {
		Toolkit.getDefaultToolkit().getSystemEventQueue().push(new Vykon.MericiFronta());
		EventQueue.invokeAndWait(() -> {
			final SecondaryLoop vnejsi = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
			EventQueue.invokeLater(() -> {
				final SecondaryLoop vnitrni = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
				final Thread zavri = new Thread(() -> {
					try {
						Thread.sleep(300);
					} catch (final InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					vnitrni.exit();
				});
				zavri.start();
				vnitrni.enter();
				vnejsi.exit();
			});
			vnejsi.enter();
		});
		final Souhrn edt = Vykon.souhrn(Velicina.EDT);
		Assert.assertTrue("EDT max " + edt.max, edt.max < 100);
	}
}
