package cz.geokuk.plugins.kesoid.mvc;

import org.junit.*;

import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.LimityKresleni;

/** Limity kreslení se ukládají do nastavení programu. */
public class LimityKresleniUlozeniTest {

	private final MyPreferences pref = MyPreferences.current().node("test-limity-kresleni");
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}
	};

	private final java.util.List<Object> udalosti = new java.util.ArrayList<>();

	@Before
	public void udalosti() {
		model.inject(udalosti::add);
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void ulozeniDoNastaveni() {
		Assert.assertEquals(LimityKresleni.VYCHOZI, model.getLimityKresleni());
		model.setLimityKresleni(LimityKresleni.of(60_000, 600_000));
		Assert.assertEquals(60_000, pref.node(FPref.KESOID_node).getInt(FPref.LIMIT_IKON_value, 0));
		Assert.assertEquals(600_000, pref.node(FPref.KESOID_node).getInt(FPref.LIMIT_TECEK_value, 0));
		Assert.assertEquals(LimityKresleni.of(60_000, 600_000), model.getLimityKresleni());
		Assert.assertEquals(1, udalosti.size());
		Assert.assertEquals(LimityKresleni.of(60_000, 600_000), ((LimityKresleniEvent) udalosti.get(0)).getLimity());
		model.setLimityKresleni(LimityKresleni.of(60_000, 600_000));
		Assert.assertEquals("beze změny se neohlásí", 1, udalosti.size());
	}

	@Test
	public void nacteniZNastaveni() {
		pref.node(FPref.KESOID_node).putInt(FPref.LIMIT_IKON_value, 60_000);
		pref.node(FPref.KESOID_node).putInt(FPref.LIMIT_TECEK_value, 600_000);
		model.nactiLimityKresleni(pref.node(FPref.KESOID_node));
		Assert.assertEquals(LimityKresleni.of(60_000, 600_000), model.getLimityKresleni());
		Assert.assertEquals(LimityKresleni.of(60_000, 600_000), ((LimityKresleniEvent) udalosti.get(0)).getLimity());
	}

	@Test
	public void nacteniBezNastaveniDaVychozi() {
		model.nactiLimityKresleni(pref.node(FPref.KESOID_node));
		Assert.assertEquals(LimityKresleni.VYCHOZI, model.getLimityKresleni());
	}

	@Test
	public void ulozeneTeckyPodMinimemSeZvednou() {
		pref.node(FPref.KESOID_node).putInt(FPref.LIMIT_IKON_value, 30_000);
		pref.node(FPref.KESOID_node).putInt(FPref.LIMIT_TECEK_value, 40_000);
		model.nactiLimityKresleni(pref.node(FPref.KESOID_node));
		Assert.assertEquals(30_000, model.getLimityKresleni().getIkon());
		Assert.assertEquals(LimityKresleni.MIN_TECEK, model.getLimityKresleni().getTecek());
	}
}
