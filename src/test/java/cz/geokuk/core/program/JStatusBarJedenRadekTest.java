package cz.geokuk.core.program;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.EnumSet;

import javax.swing.JPanel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.importek.TypZdroje;
import cz.geokuk.plugins.kesoid.mvc.JPrepinaceZdroju;
import cz.geokuk.util.gui.ZalamovaciLayout;

/** Stavový řádek s všemi zdroji, otevřeným výletem a místem pro varování se při Full HD vejde do jednoho řádku; v úzkém okně se zalomí. */
public class JStatusBarJedenRadekTest {

	private static Object pole(final JStatusBar radek, final String jmeno) throws Exception {
		final Field f = JStatusBar.class.getDeclaredField(jmeno);
		f.setAccessible(true);
		return f.get(radek);
	}

	private static JStatusBar radekSVsemi() throws Exception {
		final JStatusBar radek = new JStatusBar();
		((JPrepinaceZdroju) pole(radek, "prepinaceZdroju")).setPovoleneTypy(EnumSet.allOf(TypZdroje.class));
		((JPanel) pole(radek, "odPozice")).setVisible(true);
		final JPanel cesty = (JPanel) pole(radek, "cesty");
		cesty.setVisible(true);
		((ZalamovaciLayout) pole(radek, "layout")).rezervuj(cesty);
		return radek;
	}

	/** Výška řádku po usazení: rozložení, případné zúžení zdrojů a nové rozložení. */
	private static int vyska(final JStatusBar radek, final int sirka) {
		final JPanel okno = new JPanel(new BorderLayout());
		okno.add(radek, BorderLayout.SOUTH);
		for (int i = 0; i < 3; i++) {
			okno.setSize(sirka, 400);
			JStatusBarSirkaTest.usad(okno, radek);
			radek.invalidate();
		}
		return radek.getPreferredSize().height;
	}

	/** Šířka řádku s názvy zdrojů skrytými; při ní se řádek ještě vejde do jednoho řádku. */
	private static int sirkaBezNazvuZdroju(final JStatusBar radek) throws Exception {
		final JPrepinaceZdroju zdroje = (JPrepinaceZdroju) pole(radek, "prepinaceZdroju");
		final boolean puvodne = zdroje.isUsporny();
		zdroje.setUsporny(true);
		final int sirka = ((ZalamovaciLayout) pole(radek, "layout")).sirkaVJednomRadku(radek);
		zdroje.setUsporny(puvodne);
		return sirka;
	}

	@Test
	public void bezMistaSeZdrojeZuziNaZaskrtavatka() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = radekSVsemi();
		final int jedenRadek = vyska(new JStatusBar(), 5000);
		final int uzka = sirkaBezNazvuZdroju(radek);
		final int plna = ((ZalamovaciLayout) pole(radek, "layout")).sirkaVJednomRadku(radek);
		Assert.assertTrue("názvy zdrojů zabírají místo: " + plna + " × " + uzka, plna > uzka);
		Assert.assertEquals(jedenRadek, vyska(radek, uzka + 5));
		Assert.assertTrue(((JPrepinaceZdroju) pole(radek, "prepinaceZdroju")).isUsporny());
	}

	@Test
	public void kdyzJeMistoZustanouNazvyZdroju() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = radekSVsemi();
		final int plna = ((ZalamovaciLayout) pole(radek, "layout")).sirkaVJednomRadku(radek);
		vyska(radek, plna + 5);
		Assert.assertFalse(((JPrepinaceZdroju) pole(radek, "prepinaceZdroju")).isUsporny());
	}

	@Test
	public void vUzkemOkneSeZalomi() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final JStatusBar radek = radekSVsemi();
		final int jedenRadek = vyska(new JStatusBar(), 5000);
		Assert.assertTrue(vyska(radek, sirkaBezNazvuZdroju(radek) / 2) > jedenRadek);
	}

	/** Na Full HD (1920 px, okno bez 16 px rámečku) se vejde i řádek se čtyřmi zdroji, výletem a místem pro varování. */
	@Test
	public void naFullHdSeCtyrmiZdrojiJedenRadek() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		Assume.assumeTrue("jen Windows (šířky písma)", System.getProperty("os.name", "").startsWith("Windows"));
		final JStatusBar radek = radekSVsemi();
		final int jedenRadek = vyska(new JStatusBar(), 5000);
		Assert.assertEquals(jedenRadek, vyska(radek, 1920 - 16));
	}
}
