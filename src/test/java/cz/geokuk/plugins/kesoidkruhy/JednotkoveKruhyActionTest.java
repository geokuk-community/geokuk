package cz.geokuk.plugins.kesoidkruhy;

import org.junit.*;

import cz.geokuk.framework.MyPreferences;

/** Jednotkové kruhy jsou jen podoba zvýrazňovacích kruhů, jejich zapnutím se kruhy i zobrazí. */
public class JednotkoveKruhyActionTest {

	private final MyPreferences pref = MyPreferences.current().node("test-jednotkove-kruhy");
	private final KruhyModel model = new KruhyModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}
	};
	private final JednotkoveKruhyAction akce = new JednotkoveKruhyAction();

	@Before
	public void priprav() {
		model.inject(udalost -> {});
		model.reloadPreferences();
		akce.inject(model);
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void zapnutiJednotkovychZapneZvyraznovaciKruhy() {
		Assert.assertFalse(model.getData().isOnoff());
		akce.onSlectedChange(true);
		Assert.assertTrue(model.getData().isJednotkovaVelikost());
		Assert.assertTrue(model.getData().isOnoff());
	}

	@Test
	public void vypnutiJednotkovychNechaKruhyZapnute() {
		akce.onSlectedChange(true);
		akce.onSlectedChange(false);
		Assert.assertFalse(model.getData().isJednotkovaVelikost());
		Assert.assertTrue(model.getData().isOnoff());
	}

	@Test
	public void bublinaUvadiAktualniPolomer() {
		Assert.assertEquals("Kruhy o poloměru 100 m, jehož velikost na mapě se mění s přiblížením.", akce.getValue(javax.swing.Action.SHORT_DESCRIPTION));
		final KruhySettings d = model.getData();
		d.setPolomerMetru(1500);
		model.setData(d);
		akce.onEvent(new KruhyPreferencesChangeEvent(model.getData()));
		Assert.assertEquals("Kruhy o poloměru 1,5 km, jehož velikost na mapě se mění s přiblížením.", akce.getValue(javax.swing.Action.SHORT_DESCRIPTION));
		d.setPolomerMetru(250);
		akce.onEvent(new KruhyPreferencesChangeEvent(d));
		Assert.assertEquals("Kruhy o poloměru 250 m, jehož velikost na mapě se mění s přiblížením.", akce.getValue(javax.swing.Action.SHORT_DESCRIPTION));
	}

	@Test
	public void nazevPolozkyMenuJeSPolomeremAMeniSeSNastavenim() {
		final javax.swing.JCheckBoxMenuItem polozka = new javax.swing.JCheckBoxMenuItem();
		akce.join(polozka);
		Assert.assertEquals("Kruhy 100 m", polozka.getText());
		final KruhySettings d = model.getData();
		d.setPolomerMetru(1000);
		akce.onEvent(new KruhyPreferencesChangeEvent(d));
		Assert.assertEquals("Kruhy 1 km", polozka.getText());
		Assert.assertEquals("Kruhy 1 km", akce.getValue(javax.swing.Action.NAME));
		d.setPolomerMetru(161);
		akce.onEvent(new KruhyPreferencesChangeEvent(d));
		Assert.assertEquals("Kruhy 161 m", polozka.getText());
		Assert.assertEquals("Kruhy 1,5 km", JednotkoveKruhyAction.nazev(1500));
	}

	@Test
	public void dialogSeJmenujeNastaveniKruhu() throws Exception {
		org.junit.Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final javax.swing.JDialog d = new JKruhyDialog();
		Assert.assertEquals("Nastavení kruhů", d.getTitle());
	}
}
