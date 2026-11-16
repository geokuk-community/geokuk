package cz.geokuk.plugins.kesoid.mapicon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
	}

	/** První načtení čeká na {@link #pust}; zaznamená, s čím se načítalo. */
	private static final class ZdrzeneNacitani extends IkonNacitacLoader {
		final List<String> nacteni = Collections.synchronizedList(new ArrayList<>());
		final CountDownLatch bezi = new CountDownLatch(1);
		final CountDownLatch pust = new CountDownLatch(1);

		@Override
		public IkonBag nacti(final boolean prenacti, final ASada sada, final Zvyrazneni zvyrazneni) {
			nacteni.add(sada + "/" + zvyrazneni.getNazev() + (prenacti ? "/znovu" : ""));
			bezi.countDown();
			try {
				pust.await(10, TimeUnit.SECONDS);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return null; // nic nového, model se nemění
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
		nacitani.pust.countDown();
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
		nacitani.pust.countDown();
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
		nacitani.pust.countDown();
		pockejNa(nacitani.nacteni, 2, 500);
		Assert.assertEquals(1, nacitani.nacteni.size());
	}
}
