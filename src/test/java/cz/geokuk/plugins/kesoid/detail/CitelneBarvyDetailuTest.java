package cz.geokuk.plugins.kesoid.detail;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;

import javax.swing.*;
import javax.swing.plaf.metal.*;

import org.junit.*;
import org.mockito.Mockito;

import cz.geokuk.core.lookandfeel.*;
import cz.geokuk.core.program.Akce;
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
