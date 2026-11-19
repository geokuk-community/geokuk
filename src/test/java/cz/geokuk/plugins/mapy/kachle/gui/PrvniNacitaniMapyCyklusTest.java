package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coord.PrvniNacitaniMapy;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloStav;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;

/** První načítání mapy ve skutečném okně: vrstva nad mapou se po prahu, po chybě i po limitu bez sítě nakreslí sama, bez další události. */
public class PrvniNacitaniMapyCyklusTest {

	private static final int DLAZDIC = 5;

	private static class Kachlovnik extends JKachlovnik {
		private static final long serialVersionUID = 1L;

		Kachlovnik() {
			super("test", Priority.KACHLE);
			setBounds(0, 0, DLAZDIC * 256, 300);
		}

		@Override
		protected boolean kreslitLogoNacitani() {
			return true;
		}
	}

	private static class Vrstva extends JSingleSlide0 {
		private static final long serialVersionUID = 1L;
		final AtomicInteger kresleni = new AtomicInteger();

		Vrstva() {
			setBounds(0, 0, DLAZDIC * 256, 300);
			setOpaque(false);
		}

		@Override
		protected boolean jeNadMapouKNacitani() {
			return true;
		}

		@Override
		protected void paintComponent(final Graphics g) {
			kresleni.incrementAndGet();
		}
	}

	private JFrame okno;
	private Kachlovnik kachlovnik;
	private Vrstva vrstva;
	private final List<JKachle> dlazdice = new ArrayList<>();

	private static JKachle novaDlazdice(final int i) {
		final KaLoc loc = KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13);
		final JKachle d = new JKachle(null, new Ka(loc, EKaType.OFFLINE_MF));
		d.setBounds(i * 256, 20, 256, 256);
		return d;
	}

	private static void hotova(final JKachle d) {
		d.prijmi(new KachloStav(new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)), Priority.KACHLE);
	}

	private static boolean pockej(final BooleanSupplier podminka, final long ms) throws InterruptedException {
		final long konec = System.currentTimeMillis() + ms;
		while (System.currentTimeMillis() < konec) {
			if (podminka.getAsBoolean()) {
				return true;
			}
			Thread.sleep(20);
		}
		return podminka.getAsBoolean();
	}

	@Before
	public void pred() {
		PrvniNacitaniMapy.obnovProTesty();
	}

	@After
	public void po() throws Exception {
		if (okno != null) {
			SwingUtilities.invokeAndWait(okno::dispose);
		}
		PrvniNacitaniMapy.obnovProTesty();
	}

	private void otevriOkno() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			okno = new JFrame();
			kachlovnik = new Kachlovnik();
			vrstva = new Vrstva();
			final JLayeredPane vrstvy = new JLayeredPane();
			vrstvy.setPreferredSize(new Dimension(DLAZDIC * 256, 320));
			for (int i = 0; i < DLAZDIC; i++) {
				final JKachle d = novaDlazdice(i);
				dlazdice.add(d);
				kachlovnik.add(d);
			}
			vrstvy.add(kachlovnik, Integer.valueOf(0));
			vrstvy.add(vrstva, Integer.valueOf(1));
			okno.add(vrstvy);
			okno.pack();
			okno.setVisible(true);
		});
		Assert.assertTrue(pockej(kachlovnik::isShowing, 3000));
		Thread.sleep(300);
	}

	@Test
	public void limitBezSiteZobraziVrstvuSamBezDalsiUdalosti() throws Exception {
		final long start = System.currentTimeMillis();
		PrvniNacitaniMapy.nastavHodiny(() -> PrvniNacitaniMapy.LIMIT_MS - 1_200 + (System.currentTimeMillis() - start));
		otevriOkno();
		Assert.assertEquals("dlaždice nedorazily, vrstva ještě čeká", 0, vrstva.kresleni.get());
		// Nic nepřijde: překreslení musí vyvolat sám časovač limitu.
		Assert.assertTrue("po limitu se vrstva sama nakreslí", pockej(() -> vrstva.kresleni.get() > 0, 5000));
		Assert.assertFalse(PrvniNacitaniMapy.probiha());
	}

	@Test
	public void prahCtyricetProcentSeVrstvaNakresliSama() throws Exception {
		otevriOkno();
		hotova(dlazdice.get(0));
		Thread.sleep(400);
		Assert.assertEquals("20 % ještě ne", 0, vrstva.kresleni.get());
		hotova(dlazdice.get(1));
		Assert.assertTrue("40 % ano, bez další události", pockej(() -> vrstva.kresleni.get() > 0, 3000));
		Assert.assertTrue("logo trvá do úplného načtení", PrvniNacitaniMapy.probiha());
	}

	@Test
	public void dlazdiceJenSNahledemSeDoPrahuNepocita() throws Exception {
		final Kachlovnik k = new Kachlovnik();
		final List<JKachle> d = new ArrayList<>();
		final Map<Ka, java.awt.Image> cache = new HashMap<>();
		for (int i = 0; i < DLAZDIC; i++) {
			final JKachle j = novaDlazdice(i);
			k.add(j);
			d.add(j);
		}
		for (int i = 0; i < 2; i++) {
			final Ka ka = new Ka(KaLoc.ofJZ(new Mou(0x23800000, 0x28100000), 13), EKaType.OFFLINE_MF);
			cache.clear();
			cache.put(new Ka(ka.getLoc().rodic(), EKaType.OFFLINE_MF), new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB));
			final List<NahledDlazdice> nahled = NahledDlazdice.najdi(ka, cache::get, 256);
			Assert.assertFalse(nahled.isEmpty());
			d.get(i).nastavNahled(nahled);
		}
		Assert.assertFalse("náhled není hotový obrázek", d.get(0).maHotovyObrazek());
		vykresli(k);
		Assert.assertTrue("2 z 5 s náhledem jsou 0 % hotových", PrvniNacitaniMapy.vrstvyCekaji());
		hotova(d.get(0));
		vykresli(k);
		Assert.assertTrue("1 hotová a 1 s náhledem z 5 = 20 %", PrvniNacitaniMapy.vrstvyCekaji());
		hotova(d.get(1));
		vykresli(k);
		Assert.assertFalse("2 hotové z 5 = 40 %", PrvniNacitaniMapy.vrstvyCekaji());
	}

	private static void vykresli(final Kachlovnik k) {
		final BufferedImage img = new BufferedImage(DLAZDIC * 256, 300, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		try {
			k.paintComponent(g);
		} finally {
			g.dispose();
		}
	}
}
