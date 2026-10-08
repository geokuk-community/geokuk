package cz.geokuk.core.program;

import java.awt.*;
import java.lang.reflect.Field;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/** Ve stavovém řádku užším než všechna pole se žádné pole neořízne ani neskryje a výška nezávisí na datech. */
public class JStatusBarSirkaTest {

	@Test
	public void vUzkemOkneJeVidetVse() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final Field f = JStatusBar.class.getDeclaredField("odPozice");
		f.setAccessible(true);
		((JPanel) f.get(radek)).setVisible(true);
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		okno.setSize(radek.getPreferredSize().width * 6 / 10, 400);
		okno.doLayout();
		radek.doLayout();
		Assert.assertTrue(radek.getHeight() > radek.getComponent(0).getHeight());
		for (final Component panel : radek.getComponents()) {
			if (!panel.isVisible()) {
				continue;
			}
			Assert.assertTrue(panel.toString(), uvnitr(panel, radek));
			((Container) panel).doLayout();
			for (final Component pole : ((Container) panel).getComponents()) {
				if (pole.isVisible()) {
					Assert.assertTrue(pole.toString(), uvnitr(pole, panel));
				}
			}
		}
	}

	private static boolean uvnitr(final Component c, final Component rodic) {
		return c.getX() >= 0 && c.getY() >= 0 && c.getX() + c.getWidth() <= rodic.getWidth() && c.getY() + c.getHeight() <= rodic.getHeight();
	}

	@Test
	public void vyskaNezavisiNaDatech() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		final int sirka = radek.getPreferredSize().width * 6 / 10;
		okno.setSize(sirka, 400);
		final Dimension predDaty = radek.getPreferredSize();

		for (final String pole : new String[] { "souradnice", "souradnicePozice", "celkovePoctyVsude", "filtrovanePocetyVsude", "celkovePoctyVyrez", "filtrovanePocetyVyrez" }) {
			((JTextComponent) pole(radek, pole)).setText("12345");
		}
		((JTextComponent) pole(radek, "souradnice")).setText("N50°04.800 E014°25.200");
		((JTextComponent) pole(radek, "celkovePoctyVsude")).setText("123456/104337");
		((JPanel) pole(radek, "jFilterProgressPanel")).add(new JProgressBar());
		final JLabel varovani = (JLabel) pole(radek, "varovaniPoctuPrekrocenych");
		varovani.setText("Limit 30000 waypointů");
		varovani.setVisible(true);
		okno.setSize(sirka, 400);
		Assert.assertEquals(predDaty.height, radek.getPreferredSize().height);
	}

	@Test
	public void varovaniSeVzdyVejdeCele() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		final JLabel varovani = (JLabel) pole(radek, "varovaniPoctuPrekrocenych");
		final java.lang.reflect.Method varuj = JStatusBar.class.getDeclaredMethod("setVarujPrekroceni", boolean.class, boolean.class, int.class);
		varuj.setAccessible(true);
		final int plna = radek.getPreferredSize().width;
		for (int sirka = plna / 2; sirka <= plna + 50; sirka += 7) {
			varuj.invoke(radek, false, false, 0);
			okno.setSize(sirka, 400);
			okno.doLayout();
			final int vyska = radek.getPreferredSize().height;
			varuj.invoke(radek, true, false, 30_000);
			okno.doLayout();
			radek.doLayout();
			Assert.assertEquals("šířka " + sirka, vyska, radek.getHeight());
			Assert.assertEquals("šířka " + sirka, varovani.getPreferredSize().width, varovani.getWidth());
			Assert.assertTrue("šířka " + sirka, varovani.getX() + varovani.getWidth() <= radek.getWidth());
		}
	}

	@Test
	public void pravyBlokStojiVzdyVpravoDole() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		final JPanel pravy = (JPanel) pole(radek, "pravyBlok");
		final int plna = radek.getPreferredSize().width;
		final int sirkaPraveho = pravy.getPreferredSize().width;
		for (int sirka = plna / 2; sirka <= plna + 50; sirka += 7) {
			okno.setSize(sirka, 400);
			okno.doLayout();
			radek.doLayout();
			Assert.assertEquals("šířka " + sirka, sirkaPraveho, pravy.getWidth());
			Assert.assertEquals("šířka " + sirka, radek.getWidth(), pravy.getX() + pravy.getWidth());
			Assert.assertEquals("šířka " + sirka, radek.getHeight(), pravy.getY() + pravy.getHeight());
			for (final Component panel : radek.getComponents()) {
				if (panel != pravy && panel.isVisible() && panel.getWidth() > 0) {
					Assert.assertFalse("šířka " + sirka + " " + panel + " pravy " + pravy.getBounds() + " radek " + radek.getSize(), panel.getBounds().intersects(pravy.getBounds()));
				}
			}
		}
	}

	/** Na monitoru 1920 px (okno bez 16 px rámečku) se běžný stavový řádek vejde na jeden řádek i s rezervou 40 px na širší písmo. */
	@Test
	public void naFullHdJedenRadek() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		// Šířky písma se liší podle systému; cílem je běžné písmo Windows.
		Assume.assumeTrue("jen Windows", System.getProperty("os.name", "").startsWith("Windows"));
		final JStatusBar radek = new JStatusBar();
		((JPanel) pole(radek, "odPozice")).setVisible(true);
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		okno.setSize(1920 - 16, 400);
		okno.doLayout();
		radek.doLayout();
		int vyskaRadku = 0;
		for (final Component c : radek.getComponents()) {
			vyskaRadku = Math.max(vyskaRadku, c.getPreferredSize().height);
		}
		Assert.assertEquals("jeden řádek", vyskaRadku, radek.getHeight());
		final int sirka = radek.getPreferredSize().width;
		Assert.assertTrue("rezerva na širší písmo: " + sirka, sirka <= 1920 - 16 - 40);
	}

	/** Po načtení dat (nejdelší reálné počty) se žádný blok stavového řádku nepohne ani nezmění šířku. */
	@Test
	public void poNacteniDatSeNicNepohne() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		for (final int sirka : new int[] { 1000, 1100, 1200, 1366, 1920 }) {
			final JStatusBar radek = new JStatusBar();
			final JPanel okno = new JPanel(new BorderLayout());
			okno.add(radek, BorderLayout.SOUTH);
			okno.setSize(sirka - 16, 400);
			okno.doLayout();
			radek.doLayout();
			final java.util.List<Rectangle> pred = new java.util.ArrayList<>();
			for (final Component c : radek.getComponents()) {
				pred.add(c.getBounds());
			}
			((JTextComponent) pole(radek, "celkovePoctyVsude")).setText("2647424/1873209");
			((JTextComponent) pole(radek, "filtrovanePocetyVsude")).setText("2647424/1873209");
			((JTextComponent) pole(radek, "celkovePoctyVyrez")).setText("264742");
			((JTextComponent) pole(radek, "filtrovanePocetyVyrez")).setText("264742");
			okno.setSize(sirka - 16, 400);
			okno.doLayout();
			radek.doLayout();
			for (int i = 0; i < radek.getComponentCount(); i++) {
				Assert.assertEquals("šířka " + sirka + " " + radek.getComponent(i), pred.get(i), radek.getComponent(i).getBounds());
			}
		}
	}

	@Test
	public void blokCestJenSCestami() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel cesty = (JPanel) pole(radek, "cesty");
		Assert.assertFalse(cesty.isVisible());
		final cz.geokuk.plugins.cesty.data.Doc doc = new cz.geokuk.plugins.cesty.data.Doc() {
			@Override
			public boolean isEmpty() {
				return false;
			}
		};
		final cz.geokuk.plugins.cesty.CestyModel model = new cz.geokuk.plugins.cesty.CestyModel() {
			@Override
			public cz.geokuk.plugins.cesty.data.Doc getDoc() {
				return doc;
			}
		};
		final cz.geokuk.plugins.cesty.CestyChangedEvent event = new cz.geokuk.plugins.cesty.CestyChangedEvent(doc, null);
		event.setModel(model);
		radek.onEvent(event);
		Assert.assertTrue(cesty.isVisible());
	}

	@Test
	public void mistoProCestyPriObnoveVyletuPriStartu() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final int sirka = sirkaKdeCestyPridajiRadek();
		final JStatusBar radek = new JStatusBar();
		radek.inject(cestyModel(true));
		radek.initAfterInject();
		final int bezCest = vyska(radek, sirka);
		radek.onEvent(cestyEvent(false));
		Assert.assertEquals(bezCest, vyska(radek, sirka));
	}

	@Test
	public void mistoProCestyZustanePoZavreniVyletu() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final int sirka = sirkaKdeCestyPridajiRadek();
		final JStatusBar radek = new JStatusBar();
		radek.inject(cestyModel(false));
		radek.initAfterInject();
		final int bezCest = vyska(radek, sirka);
		radek.onEvent(cestyEvent(false));
		final int sCestami = vyska(radek, sirka);
		Assert.assertTrue(sCestami > bezCest);
		radek.onEvent(cestyEvent(true));
		Assert.assertFalse(((JPanel) pole(radek, "cesty")).isVisible());
		Assert.assertEquals(sCestami, vyska(radek, sirka));
	}

	/** Šířka, ve které by objevení bloku cest bez rezervace přidalo řádek. */
	private static int sirkaKdeCestyPridajiRadek() throws Exception {
		final JStatusBar radek = new JStatusBar();
		for (int sirka = radek.getPreferredSize().width + 300; sirka > 300; sirka--) {
			final JStatusBar bez = new JStatusBar();
			final int vyskaBez = vyska(bez, sirka);
			bez.onEvent(cestyEvent(false));
			if (vyska(bez, sirka) > vyskaBez) {
				return sirka;
			}
		}
		throw new AssertionError("blok cest nikde nepřidá řádek");
	}

	private static int vyska(final JStatusBar radek, final int sirka) {
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		okno.setSize(sirka, 600);
		okno.doLayout();
		return radek.getPreferredSize().height;
	}

	private static cz.geokuk.plugins.cesty.CestyModel cestyModel(final boolean otevreVyletPriStartu) {
		return new cz.geokuk.plugins.cesty.CestyModel() {
			@Override
			public boolean otevreVyletPriStartu() {
				return otevreVyletPriStartu;
			}
		};
	}

	private static cz.geokuk.plugins.cesty.CestyChangedEvent cestyEvent(final boolean prazdny) {
		final cz.geokuk.plugins.cesty.data.Doc doc = new cz.geokuk.plugins.cesty.data.Doc() {
			@Override
			public boolean isEmpty() {
				return prazdny;
			}
		};
		final cz.geokuk.plugins.cesty.CestyModel model = new cz.geokuk.plugins.cesty.CestyModel() {
			@Override
			public cz.geokuk.plugins.cesty.data.Doc getDoc() {
				return doc;
			}
		};
		final cz.geokuk.plugins.cesty.CestyChangedEvent event = new cz.geokuk.plugins.cesty.CestyChangedEvent(doc, null);
		event.setModel(model);
		return event;
	}

	@Test
	public void hvezdickaNeulozenehoVyletuNemeniSirkuBlokuCest() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = new JStatusBar();
		final JPanel cesty = (JPanel) pole(radek, "cesty");
		final JLabel hvezdicka = (JLabel) pole(radek, "jSouborSVyletemPotrebujeUlozit");
		final Dimension bez = cesty.getPreferredSize();
		hvezdicka.setText("*");
		Assert.assertEquals(bez, cesty.getPreferredSize());
		Assert.assertTrue(hvezdicka.getPreferredSize().width >= hvezdicka.getFontMetrics(hvezdicka.getFont()).stringWidth("*"));
	}

	private static Object pole(final JStatusBar radek, final String jmeno) throws Exception {
		final Field f = JStatusBar.class.getDeclaredField(jmeno);
		f.setAccessible(true);
		return f.get(radek);
	}
}
