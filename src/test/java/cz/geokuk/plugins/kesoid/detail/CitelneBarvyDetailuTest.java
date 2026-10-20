package cz.geokuk.plugins.kesoid.detail;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import javax.swing.*;
import javax.swing.plaf.metal.*;

import org.junit.*;
import org.mockito.Mockito;

import cz.geokuk.core.lookandfeel.*;
import cz.geokuk.core.program.Akce;
import cz.geokuk.plugins.kesoid.EKesVztah;
import cz.geokuk.plugins.kesoid.kind.KesoidPlugin;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.kind.kes.JKesDetail;
import cz.geokuk.plugins.refbody.RefbodyModel;

/** Datum nálezu a kód keše v detailu musí být čitelné na pozadí každého vzhledu a zůstat zelené, resp. červené. */
public class CitelneBarvyDetailuTest {

	private LookAndFeel puvodni;
	private MetalTheme puvodniTema;

	@Before
	public void uloz() {
		puvodni = UIManager.getLookAndFeel();
		puvodniTema = MetalLookAndFeel.getCurrentTheme();
	}

	@After
	public void obnov() throws Exception {
		MetalLookAndFeel.setCurrentTheme(puvodniTema);
		UIManager.setLookAndFeel(puvodni);
	}

	@Test
	public void datumNalezuAKodKeseJsouCitelne() throws Exception {
		final List<String> chyby = new ArrayList<>();
		for (final MetalTheme tema : new MetalTheme[] { new OceanTheme(), new DefaultMetalTheme(), new AquaTheme(), new CharcoalTheme(), new ContrastTheme(), new EmeraldTheme(), new RubyTheme() }) {
			MetalLookAndFeel.setCurrentTheme(tema);
			UIManager.setLookAndFeel(new MetalLookAndFeel());
			over(tema.getName(), chyby);
		}
		UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel());
		over("Nimbus", chyby);
		Assert.assertTrue(String.join("\n", chyby), chyby.isEmpty());
	}

	@Test
	public void odstinSeZachovaJenKdyzJeCitelny() {
		Assert.assertEquals(Color.GREEN, JCitelnyPopisek.citelna(Color.GREEN, Color.BLACK));
		final Color zelena = JCitelnyPopisek.citelna(Color.GREEN, new Color(238, 238, 238));
		Assert.assertTrue(zelena.toString(), JCitelnyPopisek.kontrast(zelena, new Color(238, 238, 238)) >= JCitelnyPopisek.MIN_KONTRAST);
		final Color naTmave = JCitelnyPopisek.citelna(Color.RED, new Color(40, 40, 40));
		Assert.assertTrue(naTmave.toString(), naTmave.getRed() > 200 && JCitelnyPopisek.kontrast(naTmave, new Color(40, 40, 40)) >= JCitelnyPopisek.MIN_KONTRAST);
	}

	/** Šipka a paprsky ikony vlastní keše (nad nápisem) mají na světlých pozadích vzhledů obrys s kontrastem aspoň 3:1. */
	@Test
	public void ikonaVlastniKeseMaKontrastniObrys() throws Exception {
		final Method vztah = JKesoidDetailContainer.class.getDeclaredMethod("vztah", EKesVztah.class);
		vztah.setAccessible(true);
		final Icon ikona = (Icon) vztah.invoke(null, EKesVztah.OWN);
		final BufferedImage img = new BufferedImage(ikona.getIconWidth(), ikona.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = img.createGraphics();
		ikona.paintIcon(null, g, 0, 0);
		g.dispose();
		final int bezNapisu = img.getHeight() - 10;
		for (final Color pozadi : new Color[] { new Color(238, 238, 238), new Color(204, 204, 204), new Color(214, 217, 223), Color.WHITE }) {
			int okraj = 0;
			int slabych = 0;
			for (int y = 0; y < bezNapisu; y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					if (alfa(img, x, y) >= 128 && (alfa(img, x + 1, y) < 128 || alfa(img, x - 1, y) < 128 || alfa(img, x, y + 1) < 128 || alfa(img, x, y - 1) < 128)) {
						okraj++;
						if (JCitelnyPopisek.kontrast(new Color(img.getRGB(x, y)), pozadi) < 3) {
							slabych++;
						}
					}
				}
			}
			Assert.assertTrue("Na pozadí " + pozadi + " splývá " + slabych + " z " + okraj + " pixelů obrysu", slabych * 20 <= okraj);
		}
	}

	private static int alfa(final BufferedImage img, final int x, final int y) {
		if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
			return 0;
		}
		return img.getRGB(x, y) >>> 24;
	}

	private static void over(final String vzhled, final List<String> chyby) throws Exception {
		final JKesDetail detail = new JKesDetail();
		final JLabel datum = (JLabel) pole(JKesDetail.class, "jFoundTime", detail);
		zkontroluj(vzhled + " datum nálezu", datum, detail, true, chyby);

		final JKesoidDetailContainer kontejner = new JKesoidDetailContainer();
		kontejner.inject(Mockito.mock(RefbodyModel.class));
		kontejner.inject(new Akce());
		final KesoidPluginManager pluginy = Mockito.mock(KesoidPluginManager.class);
		Mockito.when(pluginy.createKesoidDetails()).thenReturn(new HashMap<KesoidPlugin, JKesoidDetail0>());
		kontejner.inject(pluginy);
		kontejner.initAfterInject();
		final JLabel kod = (JLabel) pole(JKesoidDetailContainer.class, "jKesoidCode", kontejner);
		zkontroluj(vzhled + " kód keše", kod, kontejner, false, chyby);
	}

	private static void zkontroluj(final String co, final JLabel popisek, final JComponent panel, final boolean zelena, final List<String> chyby) {
		final Color p = popisek.getForeground();
		final Color pozadi = panel.getBackground();
		final double k = JCitelnyPopisek.kontrast(p, pozadi);
		if (k < JCitelnyPopisek.MIN_KONTRAST) {
			chyby.add(co + ": kontrast " + String.format(Locale.ROOT, "%.2f", k) + " barvy " + p + " na " + pozadi);
		}
		final boolean odstin = zelena ? p.getGreen() > p.getRed() && p.getGreen() > p.getBlue() : p.getRed() > p.getGreen() && p.getRed() > p.getBlue();
		if (!odstin) {
			chyby.add(co + ": barva " + p + " není " + (zelena ? "zelená" : "červená"));
		}
	}

	private static Object pole(final Class<?> trida, final String jmeno, final Object objekt) throws Exception {
		final Field f = trida.getDeclaredField(jmeno);
		f.setAccessible(true);
		return f.get(objekt);
	}
}
