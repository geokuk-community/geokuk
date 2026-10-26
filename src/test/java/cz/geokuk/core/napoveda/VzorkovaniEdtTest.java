package cz.geokuk.core.napoveda;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

public class VzorkovaniEdtTest {

	private static StackTraceElement ramec(final String trida, final String metoda, final int radek) {
		return new StackTraceElement(trida, metoda, trida.substring(trida.lastIndexOf('.') + 1) + ".java", radek);
	}

	private static StackTraceElement[] necinny() {
		return new StackTraceElement[] { ramec("java.lang.Object", "wait0", -2), ramec("java.awt.EventQueue", "getNextEvent", 560),
				ramec("java.awt.EventDispatchThread", "pumpOneEventForFilters", 100) };
	}

	private static StackTraceElement[] mapsforge() {
		return new StackTraceElement[] { ramec("java.util.zip.Inflater", "inflateBytesBytes", -2), ramec("org.mapsforge.map.reader.MapFile", "readMapData", 12),
				ramec("cz.geokuk.plugins.mapy.kachle.Cteni", "nacti", 40), ramec("java.awt.EventDispatchThread", "run", 1) };
	}

	private static StackTraceElement[] fonty() {
		return new StackTraceElement[] { ramec("sun.font.FileFontStrike", "_getGlyphImageFromWindows", -2), ramec("javax.swing.JLabel", "getPreferredSize", 5),
				ramec("cz.geokuk.util.gui.ZalamovaciLayout", "layoutContainer", 90) };
	}

	@Test
	public void necinnyZasobnikSeNepocitaDoKategorii() {
		final VzorkovaniEdt v = new VzorkovaniEdt();
		v.vzorek(necinny());
		v.vzorek(necinny());
		final String souhrn = v.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("2 vzorků"));
		Assert.assertTrue(souhrn, souhrn.contains("čekajících na událost 2, zaneprázdněných 0"));
		Assert.assertTrue(souhrn, souhrn.contains("org.mapsforge: 0 (0 %)"));
	}

	@Test
	public void kategorieAPrvniRamecGeokuk() {
		final VzorkovaniEdt v = new VzorkovaniEdt();
		v.vzorek(mapsforge());
		v.vzorek(mapsforge());
		v.vzorek(fonty());
		v.vzorek(necinny());
		final String souhrn = v.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("zaneprázdněných 3"));
		Assert.assertTrue(souhrn, souhrn.contains("org.mapsforge: 2 (66.7 %)"));
		Assert.assertTrue(souhrn, souhrn.contains("java.util.zip / jar: 2 (66.7 %)"));
		Assert.assertTrue(souhrn, souhrn.contains("sun.font: 1 (33.3 %)"));
		Assert.assertTrue(souhrn, souhrn.contains("javax.swing: 1 (33.3 %)"));
		Assert.assertTrue(souhrn, souhrn.contains("2× Cteni.nacti:40"));
		Assert.assertTrue(souhrn, souhrn.contains("1× ZalamovaciLayout.layoutContainer:90"));
		Assert.assertTrue(souhrn, souhrn.contains("2× cz.geokuk.plugins.mapy"));
	}

	@Test
	public void zasobnikJeZkracenAPrvniRamecGeokukZustane() {
		final StackTraceElement[] st = new StackTraceElement[20];
		for (int i = 0; i < 19; i++) {
			st[i] = ramec("jdk.Neco", "m" + i, i);
		}
		st[19] = ramec("cz.geokuk.Hlavni", "main", 7);
		final VzorkovaniEdt v = new VzorkovaniEdt();
		v.vzorek(st);
		final String souhrn = v.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("Neco.m11:11 ← … Hlavni.main:7"));
		Assert.assertFalse(souhrn, souhrn.contains("Neco.m12:12"));
	}

	@Test
	public void defineClassASqlJetSePoznaji() {
		final VzorkovaniEdt v = new VzorkovaniEdt();
		v.vzorek(new StackTraceElement[] { ramec("java.lang.ClassLoader", "defineClass1", -2), ramec("org.tmatesoft.sqljet.core.internal.SqlJetFile", "read", 3) });
		final String souhrn = v.souhrn();
		Assert.assertTrue(souhrn, souhrn.contains("ClassLoader.defineClass: 1"));
		Assert.assertTrue(souhrn, souhrn.contains("sqlite / SqlJet: 1"));
		Assert.assertTrue(souhrn, souhrn.contains("(bez cz.geokuk)"));
	}

	@Test
	public void periodaZVlastnosti() {
		Assert.assertEquals(-1, VzorkovaniEdt.periodaZVlastnosti(null));
		Assert.assertEquals(-1, VzorkovaniEdt.periodaZVlastnosti(""));
		Assert.assertEquals(-1, VzorkovaniEdt.periodaZVlastnosti("false"));
		Assert.assertEquals(-1, VzorkovaniEdt.periodaZVlastnosti("0"));
		Assert.assertEquals(VzorkovaniEdt.VYCHOZI_PERIODA_MS, VzorkovaniEdt.periodaZVlastnosti("true"));
		Assert.assertEquals(80, VzorkovaniEdt.periodaZVlastnosti("80"));
		Assert.assertEquals(VzorkovaniEdt.NEJMENSI_PERIODA_MS, VzorkovaniEdt.periodaZVlastnosti("1"));
	}

	@Test
	public void balicekDoCtvrteUrovne() {
		Assert.assertEquals("cz.geokuk.plugins.mapy", VzorkovaniEdt.balicek("cz.geokuk.plugins.mapy.kachle.Cteni"));
		Assert.assertEquals("cz.geokuk", VzorkovaniEdt.balicek("cz.geokuk.Hlavni"));
	}

	/** Skutečné vzorkování vlákna, které právě pracuje v kódu GeoKuku. */
	@Test
	public void vzorkujeSkutecneVlakno() throws Exception {
		final AtomicBoolean konec = new AtomicBoolean();
		final Thread pracuje = new Thread(() -> {
			double x = 0;
			while (!konec.get()) {
				x += Math.sin(x + 1);
			}
		}, "Pracující vlákno");
		pracuje.start();
		try {
			final VzorkovaniEdt v = VzorkovaniEdt.vzorkuj(() -> pracuje, 10, 300);
			final String souhrn = v.souhrn();
			Assert.assertFalse(souhrn, souhrn.contains(" 0 vzorků"));
			Assert.assertTrue(souhrn, souhrn.contains("VzorkovaniEdtTest"));
		} finally {
			konec.set(true);
			pracuje.join();
		}
	}

	@Test
	public void bezVlaknaSeNicNevzorkuje() {
		final VzorkovaniEdt v = VzorkovaniEdt.vzorkuj(() -> null, 10, 100);
		Assert.assertTrue(v.souhrn(), v.souhrn().contains("0 vzorků"));
	}
}
