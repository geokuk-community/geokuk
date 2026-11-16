package cz.geokuk.plugins.kesoid.mapicon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;

public class IkonNacitacManagerTest {

	private final List<Model> modely = new ArrayList<>();

	private Model novyModel() {
		final Model m = new Model();
		modely.add(m);
		return m;
	}

	@After
	public void vypniModely() {
		for (final Model m : modely) {
			m.aktivni = false;
		}
	}

	/** Časovač může přijít dřív, než se nastaví umístění souborů. */
	@Test
	public void predNastavenimUmisteniNicNedela() {
		final KesoidModel kesoidModel = new KesoidModel();
		Assert.assertNull(kesoidModel.getUmisteniSouboru());
		new IkonNacitacManager(kesoidModel).startLoad(false);
	}

	/** První nastavení umístění změní umístění ikon, takže se ikony načtou. */
	@Test
	public void prvniUmisteniZmeniUmisteniIkon() {
		final KesoidUmisteniSouboru u = new KesoidUmisteniSouboru();
		u.setImage3rdPartyDir(KesoidUmisteniSouboru.IMAGE_3RDPARTY_DIR);
		u.setImageMyDir(KesoidUmisteniSouboru.IMAGE_MY_DIR);
		Assert.assertFalse(u.equalsImageLocations(null));
	}

	/**
	 * Model jen s tím, co načítání ikon čte; mění se z EDT. Po testu umístění nevrací, aby časovač vlastního správce ikon modelu nic nenačítal (model nemá
	 * rozesílání událostí).
	 */
	private static final class Model extends KesoidModel {
		private final KesoidUmisteniSouboru umisteni = new KesoidUmisteniSouboru();
		volatile boolean aktivni = true;
		Zvyrazneni zvyrazneni = Zvyrazneni.TEREN;
		ASada sada = ASada.STANDARD;

		Model() {
			umisteni.setImage3rdPartyDir(KesoidUmisteniSouboru.IMAGE_3RDPARTY_DIR);
			umisteni.setImageMyDir(KesoidUmisteniSouboru.IMAGE_MY_DIR);
		}

		@Override
		public KesoidUmisteniSouboru getUmisteniSouboru() {
			return aktivni ? umisteni : null;
		}

		@Override
		public Zvyrazneni getZvyrazneni() {
			return zvyrazneni;
		}

		@Override
		public ASada getJmenoAktualniSadyIkon() {
			return sada;
		}

		final List<IkonBag> pouzite = new ArrayList<>();

		@Override
		public void setIkonBag(final IkonBag ikonBag) {
			pouzite.add(ikonBag);
		}
	}

	/**
	 * Každé načtení čeká, až ho test pustí ({@link #pust(int)}); zaznamená, s čím se načítalo, a kolik načítání běželo současně. Vrátí {@link #vysledek}, první
	 * načtení s {@link #prvniSelze} skončí výjimkou.
	 */
	private static final class ZdrzeneNacitani extends IkonNacitacLoader {
		final List<String> nacteni = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch bezi = new CountDownLatch(1);
		private final List<CountDownLatch> pusteni = new ArrayList<>();
		private final AtomicInteger soubezne = new AtomicInteger();
		final AtomicInteger nejvicSoubezne = new AtomicInteger();
		volatile IkonBag vysledek;
		volatile boolean prvniSelze;

		ZdrzeneNacitani() {
			for (int i = 0; i < 10; i++) {
				pusteni.add(new CountDownLatch(1));
			}
		}

		void pust(final int poradi) {
			pusteni.get(poradi).countDown();
		}

		void pustVse() {
			pusteni.forEach(CountDownLatch::countDown);
		}

		@Override
		public IkonBag nacti(final boolean prenacti, final ASada sada, final Zvyrazneni zvyrazneni) throws IOException {
			final int poradi;
			synchronized (nacteni) {
				poradi = nacteni.size();
				nacteni.add(sada + "/" + zvyrazneni.getNazev() + (prenacti ? "/znovu" : ""));
			}
			nejvicSoubezne.accumulateAndGet(soubezne.incrementAndGet(), Math::max);
			bezi.countDown();
			try {
				pusteni.get(poradi).await(10, TimeUnit.SECONDS);
				if (poradi == 0 && prvniSelze) {
					throw new IOException("ikony nejdou načíst");
				}
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				soubezne.decrementAndGet();
			}
			return vysledek; // null = nic nového, model se nemění
		}
	}

	private static void pockejNa(final List<String> nacteni, final int pocet) throws InterruptedException {
		pockejNa(nacteni, pocet, 10_000);
	}

	private static void pockejNa(final List<String> nacteni, final int pocet, final long limitMs) throws InterruptedException {
		final long konec = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limitMs);
		while (nacteni.size() < pocet && System.nanoTime() < konec) {
			Thread.sleep(10);
		}
		Thread.sleep(200); // a žádné další už nepřibude
	}

	/** Změny zvýraznění během načítání se neztratí: po běžícím načtení se načte jednou, s posledním zvýrazněním (#824). */
	@Test(timeout = 30000)
	public void zmenaZvyrazneniBehemNacitaniSeNeztrati() throws Exception {
		final Model model = novyModel();
		final ZdrzeneNacitani nacitani = new ZdrzeneNacitani();
		final IkonNacitacManager manager = new IkonNacitacManager(model, nacitani);
		SwingUtilities.invokeAndWait(() -> manager.startLoad(true));
		Assert.assertTrue(nacitani.bezi.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			model.zvyrazneni = Zvyrazneni.OBTIZNOST;
			manager.startLoad(true);
			model.zvyrazneni = Zvyrazneni.BEZ;
			manager.startLoad(true);
		});
		Assert.assertEquals("běžící načtení drží zvýraznění ze svého začátku", 1, nacitani.nacteni.size());
		nacitani.pustVse();
		pockejNa(nacitani.nacteni, 2);
		Assert.assertEquals(Arrays.asList("Standard/" + Zvyrazneni.TEREN.getNazev() + "/znovu", "Standard/" + Zvyrazneni.BEZ.getNazev() + "/znovu"), nacitani.nacteni);
	}

	/** Změna sady ikon během načítání se také načte po něm. */
	@Test(timeout = 30000)
	public void zmenaSadyBehemNacitaniSeNeztrati() throws Exception {
		final Model model = novyModel();
		final ZdrzeneNacitani nacitani = new ZdrzeneNacitani();
		final IkonNacitacManager manager = new IkonNacitacManager(model, nacitani);
		SwingUtilities.invokeAndWait(() -> manager.startLoad(false));
		Assert.assertTrue(nacitani.bezi.await(10, TimeUnit.SECONDS));
		final ASada jina = cz.geokuk.framework.Atom.valueOf(ASada.class, "Jiná");
		SwingUtilities.invokeAndWait(() -> {
			model.sada = jina;
			manager.startLoad(true);
		});
		nacitani.pustVse();
		pockejNa(nacitani.nacteni, 2);
		Assert.assertEquals(Arrays.asList("Standard/" + Zvyrazneni.TEREN.getNazev(), "Jiná/" + Zvyrazneni.TEREN.getNazev() + "/znovu"), nacitani.nacteni);
	}

	/** Bez změny během načítání se po něm nic dalšího nespouští (časovač beze změny složek nic nenačítá). */
	@Test(timeout = 30000)
	public void bezZmenyZadneDalsiNacteni() throws Exception {
		final Model model = novyModel();
		final ZdrzeneNacitani nacitani = new ZdrzeneNacitani();
		final IkonNacitacManager manager = new IkonNacitacManager(model, nacitani);
		SwingUtilities.invokeAndWait(() -> manager.startLoad(true));
		Assert.assertTrue(nacitani.bezi.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> manager.startLoad(false));
		nacitani.pustVse();
		pockejNa(nacitani.nacteni, 2, 500);
		Assert.assertEquals(1, nacitani.nacteni.size());
	}

	/**
	 * Požadavky mezi dopočítáním workeru a jeho dokončením na EDT ({@code isDone()} už platí) nespustí druhé načítání souběžně: po dokončení proběhne jedno
	 * další načtení a nikdy dvě najednou.
	 */
	@Test(timeout = 30000)
	public void pozadavkyPredDokoncenimNespustiSoubezneNacitani() throws Exception {
		final Model model = novyModel();
		final ZdrzeneNacitani nacitani = new ZdrzeneNacitani();
		final IkonNacitacManager manager = new IkonNacitacManager(model, nacitani);
		SwingUtilities.invokeAndWait(() -> manager.startLoad(true));
		Assert.assertTrue(nacitani.bezi.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			nacitani.pust(0);
			// EDT drží dokončení workeru ve frontě, dokud tahle úloha neskončí.
			while (!manager.dopocitanoPredDokoncenim()) {
				Thread.yield();
			}
			model.zvyrazneni = Zvyrazneni.OBTIZNOST;
			manager.startLoad(true);
			model.zvyrazneni = Zvyrazneni.BEZ;
			manager.startLoad(true);
		});
		pockejNa(nacitani.nacteni, 2);
		nacitani.pustVse();
		pockejNa(nacitani.nacteni, 3, 500);
		Assert.assertEquals("načítání neběží dvakrát najednou", 1, nacitani.nejvicSoubezne.get());
		Assert.assertEquals(Arrays.asList("Standard/" + Zvyrazneni.TEREN.getNazev() + "/znovu", "Standard/" + Zvyrazneni.BEZ.getNazev() + "/znovu"), nacitani.nacteni);
	}

	/** Výsledek načtení, po kterém už čeká další, se nepoužije; použije se jen výsledek posledního. */
	@Test(timeout = 30000)
	public void zastaralyVysledekSeNepouzije() throws Exception {
		final Model model = novyModel();
		final ZdrzeneNacitani nacitani = new ZdrzeneNacitani();
		nacitani.vysledek = new IkonBag();
		final IkonNacitacManager manager = new IkonNacitacManager(model, nacitani);
		SwingUtilities.invokeAndWait(() -> manager.startLoad(true));
		Assert.assertTrue(nacitani.bezi.await(10, TimeUnit.SECONDS));
		SwingUtilities.invokeAndWait(() -> {
			model.zvyrazneni = Zvyrazneni.BEZ;
			manager.startLoad(true);
		});
		nacitani.pustVse();
		pockejNa(nacitani.nacteni, 2);
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < konec && pouzitych(model) < 1) {
			Thread.sleep(10);
		}
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("model dostal jen výsledek posledního načtení", 1, pouzitych(model));
	}

	private static int pouzitych(final Model model) throws Exception {
		final int[] n = new int[1];
		SwingUtilities.invokeAndWait(() -> n[0] = model.pouzite.size());
		return n[0];
	}

	/** Selže-li načtení, chyba se ohlásí a čekající načtení se přesto spustí. */
	@Test(timeout = 30000)
	public void poChybeNacteniSeCekajiciSpusti() throws Exception {
		final Model model = novyModel();
		final ZdrzeneNacitani nacitani = new ZdrzeneNacitani();
		nacitani.prvniSelze = true;
		final IkonNacitacManager manager = new IkonNacitacManager(model, nacitani);
		final List<Throwable> chyby = Collections.synchronizedList(new ArrayList<>());
		SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler((t, e) -> chyby.add(e)));
		try {
			SwingUtilities.invokeAndWait(() -> manager.startLoad(true));
			Assert.assertTrue(nacitani.bezi.await(10, TimeUnit.SECONDS));
			SwingUtilities.invokeAndWait(() -> {
				model.zvyrazneni = Zvyrazneni.BEZ;
				manager.startLoad(true);
			});
			nacitani.pustVse();
			pockejNa(nacitani.nacteni, 2);
			Assert.assertEquals(Arrays.asList("Standard/" + Zvyrazneni.TEREN.getNazev() + "/znovu", "Standard/" + Zvyrazneni.BEZ.getNazev() + "/znovu"), nacitani.nacteni);
			Assert.assertEquals("chyba prvního načtení se ohlásila", 1, chyby.size());
			Assert.assertTrue(String.valueOf(chyby.get(0).getMessage()), String.valueOf(chyby.get(0).getMessage()).contains("ikony nejdou načíst"));
		} finally {
			SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler(null));
		}
	}
}
