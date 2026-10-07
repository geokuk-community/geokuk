package cz.geokuk.plugins.kesoid.mvc;

import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.swing.*;

import org.junit.*;

import cz.geokuk.plugins.kesoid.importek.StavZdroje;
import cz.geokuk.plugins.kesoid.importek.StavZdroju;
import cz.geokuk.plugins.kesoid.importek.TypZdroje;

/** Blok Zdroje ve stavovém řádku: pevné rozměry, aktivní plochy, popupy a jejich zavírání. */
public class JPrepinaceZdrojuTest {

	private final StavyZdrojuProTesty data = StavyZdrojuProTesty.vzorek();
	private final StavyZdrojuProTesty.Zaznam ovladani = new StavyZdrojuProTesty.Zaznam();
	private JFrame okno;
	private JPrepinaceZdroju blok;

	@Before
	public void pred() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			blok = new JPrepinaceZdroju();
			blok.setOvladani(ovladani);
			blok.obnov(data.snimek());
			okno = new JFrame();
			final JPanel radek = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			radek.add(blok);
			okno.add(radek, BorderLayout.SOUTH);
			okno.setBounds(20, 100, 1200, 300);
			okno.setVisible(true);
		});
		naEdt(() -> blok.prizpusob());
	}

	@After
	public void po() throws Exception {
		if (okno != null) {
			SwingUtilities.invokeAndWait(() -> {
				blok.zavriSeznam();
				okno.dispose();
			});
		}
	}

	private static void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private Dimension rozmer(final StavZdroju stav) throws Exception {
		final Dimension[] d = new Dimension[1];
		naEdt(() -> {
			blok.obnov(stav);
			d[0] = blok.getPreferredSize();
		});
		return d[0];
	}

	@Test
	public void rozmerNezavisiNaStavu() throws Exception {
		final Dimension nacteno = rozmer(data.snimek());
		final File gsak = data.snimek().getPolozky(TypZdroje.GSAK).get(0).getSoubor();
		data.registr.cekaNaZapis(gsak);
		Assert.assertEquals(nacteno, rozmer(data.snimek()));
		data.registr.chyba(data.snimek().getPolozky(TypZdroje.GPX).get(0).getSoubor(), "Vadný soubor");
		Assert.assertEquals(nacteno, rozmer(data.snimek()));
		data.vypnuteTypy.addAll(Arrays.asList(TypZdroje.values()));
		data.prepisZapnuti();
		Assert.assertEquals(nacteno, rozmer(data.snimek()));
		Assert.assertEquals(nacteno, rozmer(StavZdroju.PRAZDNY));
	}

	@Test
	public void vypnutyTypMaPrazdnouIkonuASedyNazev() throws Exception {
		data.vypnuteTypy.add(TypZdroje.GSAK);
		data.prepisZapnuti();
		naEdt(() -> blok.obnov(data.snimek()));
		Assert.assertSame(IkonyZdroju.prazdna(), blok.getIkona(TypZdroje.GSAK).getIcon());
		Assert.assertEquals(Color.GRAY, blok.getNazev(TypZdroje.GSAK).getForeground());
		Assert.assertSame(IkonyZdroju.pro(StavZdroje.NACTENO), blok.getIkona(TypZdroje.GEOGET).getIcon());
	}

	@Test
	public void zamekVBubline() throws Exception {
		data.registr.cekaNaZapis(data.snimek().getPolozky(TypZdroje.GSAK).get(0).getSoubor());
		naEdt(() -> blok.obnov(data.snimek()));
		Assert.assertSame(IkonyZdroju.pro(StavZdroje.CEKA_NA_ZAPIS), blok.getIkona(TypZdroje.GSAK).getIcon());
		final String tip = blok.getIkona(TypZdroje.GSAK).getToolTipText();
		Assert.assertTrue(tip, tip.contains("Zamčeno jiným programem") && tip.contains("Domov.db3") && tip.contains("Zavřete program"));
	}

	@Test
	public void aktivniPlochyNaCelouVyskuAMezeryNereaguji() throws Exception {
		naEdt(() -> {
			final Insets ins = blok.getInsets();
			final int vyska = blok.getHeight() - ins.top - ins.bottom;
			final List<JPrepinaceZdroju.Oblast> oblasti = blok.oblasti();
			for (final JPrepinaceZdroju.Oblast o : oblasti) {
				Assert.assertEquals(o.druh + " " + o.typ, vyska, o.plocha.height);
			}
			final Rectangle z = blok.getZaskrtavatko(TypZdroje.GEOGET).getBounds();
			final Rectangle i = blok.getIkona(TypZdroje.GEOGET).getBounds();
			Assert.assertTrue("mezera mezi zaškrtávátkem a ikonou", i.x > z.x + z.width);
			final Point mezera = new Point(z.x + z.width, z.y + z.height / 2);
			for (final JPrepinaceZdroju.Oblast o : oblasti) {
				Assert.assertFalse(o.druh + " " + o.typ, o.plocha.contains(mezera));
			}
		});
	}

	@Test
	public void popisekOteviraUplnouANazevDetail() throws Exception {
		naEdt(() -> {
			najed(blok.getPopisek());
			Assert.assertSame(blok.getObsahSouhrnu(), blok.viditelny());
			najed(blok.getNazev(TypZdroje.GSAK));
			Assert.assertSame(blok.getObsahDetailu(TypZdroje.GSAK), blok.viditelny());
			najed(blok.getIkona(TypZdroje.GEOGET));
			Assert.assertSame("ikona s názvem detail neotvírá", blok.getObsahDetailu(TypZdroje.GSAK), blok.viditelny());
		});
	}

	private static void najed(final JComponent c) {
		final java.awt.event.MouseEvent e = new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 2, 2, 0, false);
		for (final java.awt.event.MouseListener l : c.getMouseListeners()) {
			l.mouseEntered(e);
		}
	}

	@Test
	public void popupTesneNadBlokemAUvnitrObrazovky() throws Exception {
		naEdt(() -> {
			najed(blok.getPopisek());
			final JComponent p = blok.viditelny();
			final Point bp = blok.getLocationOnScreen();
			final Point pp = p.getLocationOnScreen();
			Assert.assertEquals(bp.y, pp.y + p.getHeight());
			Assert.assertEquals("úplná zarovnaná vpravo", bp.x + blok.getWidth(), pp.x + p.getWidth());
			najed(blok.getNazev(TypZdroje.GPX));
			final JComponent d = blok.viditelny();
			Assert.assertEquals(bp.y, d.getLocationOnScreen().y + d.getHeight());
			final Rectangle obrazovka = blok.getGraphicsConfiguration().getBounds();
			Assert.assertTrue(obrazovka.contains(new Rectangle(d.getLocationOnScreen(), d.getSize())));
		});
	}

	@Test
	public void zaviraSePo350msMimo() throws Exception {
		naEdt(() -> {
			najed(blok.getPopisek());
			final Point mimo = new Point(blok.getLocationOnScreen().x - 300, blok.getLocationOnScreen().y + 5);
			final Point uvnitr = new Point(blok.getLocationOnScreen().x + 5, blok.getLocationOnScreen().y + 5);
			blok.zavriKdyzMimo(mimo, 1000);
			blok.zavriKdyzMimo(mimo, 1349);
			Assert.assertNotNull(blok.viditelny());
			blok.zavriKdyzMimo(uvnitr, 1360);
			blok.zavriKdyzMimo(mimo, 1400);
			blok.zavriKdyzMimo(mimo, 1749);
			Assert.assertNotNull("návrat do bloku odpočet zrušil", blok.viditelny());
			blok.zavriKdyzMimo(mimo, 1750);
			Assert.assertNull(blok.viditelny());
		});
	}

	@Test
	public void zaskrtavatkoPrepinaTyp() throws Exception {
		naEdt(() -> blok.getZaskrtavatko(TypZdroje.GSAK).doClick());
		Assert.assertEquals(Arrays.asList("typ GSAK false"), ovladani.volani);
	}

	@Test
	public void uzkeOknoBezNazvuAIkonaOteviraDetail() throws Exception {
		final Dimension siroky = rozmer(data.snimek());
		naEdt(() -> {
			okno.setSize(800, 300);
			blok.prizpusob();
			okno.validate();
		});
		naEdt(() -> {
			Assert.assertTrue(blok.isKompaktni());
			Assert.assertTrue(blok.getPreferredSize().width < siroky.width);
			Assert.assertTrue(blok.getUplna().isUzka());
			najed(blok.getIkona(TypZdroje.OPENSAK));
			Assert.assertSame(blok.getObsahDetailu(TypZdroje.OPENSAK), blok.viditelny());
			Assert.assertTrue(blok.viditelny().getWidth() <= 800);
		});
	}
}
