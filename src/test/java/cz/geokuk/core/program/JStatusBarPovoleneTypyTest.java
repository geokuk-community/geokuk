package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.Field;

import javax.swing.JPanel;

import org.junit.*;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.mvc.JPrepinaceZdroju;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.util.file.Filex;

/** Stavový řádek zná typy zdrojů z nastavení hned po vytvoření, takže se po načtení modelu blok Zdroje ani Výlet nepohne. */
public class JStatusBarPovoleneTypyTest {

	private final MyPreferences pref = MyPreferences.current().node("test-stavovy-radek-povolene-typy");

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void typyZNastaveniUzPredNactenimModelu() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final MyPreferences u = pref.node(FPref.UMISTENI_SOUBORU_node);
		u.putFilex("kesDir", new Filex(new File("/kese"), false, true));
		u.putFilex("geogetDataDir", new Filex(new File("/geoget"), false, false));
		u.putFilex("gsakDataDir", new Filex(new File("/gsak"), false, false));
		u.putFilex(FPref.OPENSAK_DATA_DIR_value, new Filex(new File("/opensak"), false, true));
		final KesoidModel model = new KesoidModel() {
			@Override
			protected MyPreferences currPrefe() {
				return pref;
			}

			@Override
			public void fire(final Event0<?> udalost) {}
		};
		Assert.assertNull("model ještě nenačten", model.getUmisteniSouboru());
		final JStatusBar radek = new JStatusBar();
		radek.inject(model);
		final Field f = JStatusBar.class.getDeclaredField("prepinaceZdroju");
		f.setAccessible(true);
		final JPrepinaceZdroju blok = (JPrepinaceZdroju) f.get(radek);
		final int sirka = blok.getPreferredSize().width;
		blok.setPovoleneTypy(JPrepinaceZdroju.povoleneTypy(model.getUmisteniSouboruNeboZNastaveni()));
		Assert.assertEquals(sirka, blok.getPreferredSize().width);
		final JPanel plny = new JPrepinaceZdroju();
		Assert.assertTrue("GeoGet a GSAK skryté", sirka < plny.getPreferredSize().width);
	}
}
