package cz.geokuk.core.program;

import java.io.File;
import java.util.*;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.render.*;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.file.Filex;

public class JPrehledSouboruTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** „Načítat až po vybrání“ a nová složka GSAK v jednom uložení: model musí dostat volbu dřív než složku. */
	@Test
	public void volbaGsakSeUloziPredSlozkami() throws Exception {
		final JPrehledSouboru panel = new JPrehledSouboru(null);
		final List<String> volani = new ArrayList<>();
		final KesoidModel kesoidModel = new KesoidModel() {
			@Override
			public void setGsakParametryNacitani(final GsakParametryNacitani g) {
				volani.add("gsak " + g.isNacistVsechnyDatabaze());
			}

			@Override
			public void setUmisteniSouboru(final KesoidUmisteniSouboru u) {
				volani.add("složky");
			}
		};
		final RenderModel renderModel = new RenderModel() {
			@Override
			public void setUmisteniSouboru(final RenderUmisteniSouboru u) {
				volani.add("render");
			}
		};
		panel.inject(kesoidModel);
		panel.inject(renderModel);

		final KesoidUmisteniSouboru u = new KesoidUmisteniSouboru();
		u.setKesDir(slozka("kese"));
		u.setGeogetDataDir(slozka("geoget"));
		u.setGsakDataDir(slozka("gsak"));
		u.setOpensakDataDir(slozka("opensak"));
		panel.onEvent(new KesoidUmisteniSouboruChangedEvent(u));
		final RenderUmisteniSouboru r = new RenderUmisteniSouboru();
		r.setOziDir(slozka("ozi"));
		r.setKmzDir(slozka("kmz"));
		r.setPictureDir(slozka("obrazky"));
		panel.onEvent(new RenderUmisteniSouboruChangedEvent(r));
		final GsakParametryNacitani g = new GsakParametryNacitani();
		g.setCasNalezu(Collections.emptySet());
		g.setCasNenalezu(Collections.emptySet());
		g.setNacistVsechnyDatabaze(false);
		panel.onEvent(new GsakParametryNacitaniChangedEvent(g));

		panel.uloz();

		Assert.assertEquals(Arrays.asList("gsak false", "složky", "render"), volani);
	}

	/** Uložit prověřuje složky mimo EDT: tlačítko je mezitím zakázané a vidět „Kontroluji složky…“, pak se nastavení uloží. */
	@Test(timeout = 20_000)
	public void ulozitProverujeSlozkyMimoEdt() throws Exception {
		final JPrehledSouboru panel = new JPrehledSouboru(null);
		final List<String> volani = Collections.synchronizedList(new ArrayList<>());
		panel.inject(new KesoidModel() {
			@Override
			public void setGsakParametryNacitani(final GsakParametryNacitani g) {}

			@Override
			public void setUmisteniSouboru(final KesoidUmisteniSouboru u) {
				volani.add("složky");
			}
		});
		panel.inject(new RenderModel() {
			@Override
			public void setUmisteniSouboru(final RenderUmisteniSouboru u) {
				volani.add("render");
			}
		});
		final KesoidUmisteniSouboru u = new KesoidUmisteniSouboru();
		u.setKesDir(slozka("kese2"));
		u.setGeogetDataDir(slozka("geoget2"));
		u.setGsakDataDir(slozka("gsak2"));
		u.setOpensakDataDir(slozka("opensak2"));
		panel.onEvent(new KesoidUmisteniSouboruChangedEvent(u));
		final RenderUmisteniSouboru r = new RenderUmisteniSouboru();
		r.setOziDir(slozka("ozi2"));
		r.setKmzDir(slozka("kmz2"));
		r.setPictureDir(slozka("obrazky2"));
		panel.onEvent(new RenderUmisteniSouboruChangedEvent(r));

		final java.util.concurrent.CountDownLatch pustit = new java.util.concurrent.CountDownLatch(1);
		final java.util.function.Function<File, JJedenSouborPanel.StavSlozky> puvodni = JJedenSouborPanel.kontrola;
		JJedenSouborPanel.kontrola = d -> {
			try {
				pustit.await();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return puvodni.apply(d);
		};
		try {
			final javax.swing.JButton ulozit = tlacitko(panel, "Uložit");
			javax.swing.SwingUtilities.invokeAndWait(ulozit::doClick);
			javax.swing.SwingUtilities.invokeAndWait(() -> {
				Assert.assertFalse("Uložit je během kontroly zakázané", ulozit.isEnabled());
				Assert.assertTrue(panel.kontroluji.isVisible());
				Assert.assertTrue("model se mění až po kontrole", volani.isEmpty());
			});
			pustit.countDown();
			while (volani.size() < 2) {
				Thread.sleep(20);
			}
			javax.swing.SwingUtilities.invokeAndWait(() -> {
				Assert.assertTrue(ulozit.isEnabled());
				Assert.assertFalse(panel.kontroluji.isVisible());
			});
			Assert.assertEquals(Arrays.asList("složky", "render"), volani);
		} finally {
			pustit.countDown();
			JJedenSouborPanel.kontrola = puvodni;
		}
	}

	private static javax.swing.JButton tlacitko(final java.awt.Container c, final String text) {
		for (final java.awt.Component k : c.getComponents()) {
			if (k instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) k).getText())) {
				return (javax.swing.JButton) k;
			}
			if (k instanceof java.awt.Container) {
				final javax.swing.JButton b = tlacitko((java.awt.Container) k, text);
				if (b != null) {
					return b;
				}
			}
		}
		return null;
	}

	private Filex slozka(final String jmeno) throws Exception {
		final File f = tmp.newFolder(jmeno);
		Assert.assertTrue(f.isDirectory());
		return new Filex(f, false, true);
	}
}
