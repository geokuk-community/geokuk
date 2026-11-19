package cz.geokuk.plugins.kesoid.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.Event0;
import cz.geokuk.framework.MyPreferences;

/** Sledování schránky je výchozí vypnuté, volba se pamatuje v nastavení a změna se ohlásí událostí. */
public class SledovatSchrankuNastaveniTest {

	private MyPreferences pref;
	private final List<Event0<?>> udalosti = new ArrayList<>();

	@Before
	public void priprav() {
		pref = MyPreferences.current().node("test-sledovat-schranku-" + UUID.randomUUID());
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	private KesoidModel model() {
		return new KesoidModel() {
			@Override
			protected MyPreferences currPrefe() {
				return pref;
			}

			@Override
			public void fire(final Event0<?> udalost) {
				udalosti.add(udalost);
			}
		};
	}

	@Test
	public void vychoziJeVypnuto() {
		Assert.assertFalse(model().isSledovatSchranku());
		Assert.assertFalse(pref.node(FPref.KESOID_node).getBoolean(FPref.SLEDOVAT_SCHRANKU_value, false));
	}

	@Test
	public void zmenaSeUloziAOhlasi() {
		final KesoidModel m = model();
		m.setSledovatSchranku(true);
		Assert.assertTrue(m.isSledovatSchranku());
		Assert.assertTrue("volba se pamatuje v nastavení", pref.node(FPref.KESOID_node).getBoolean(FPref.SLEDOVAT_SCHRANKU_value, false));
		Assert.assertEquals(1, udalosti.size());
		Assert.assertTrue(((SledovaniSchrankyEvent) udalosti.get(0)).isOnoff());
		m.setSledovatSchranku(true);
		Assert.assertEquals("beze změny se nic neohlašuje", 1, udalosti.size());
		m.setSledovatSchranku(false);
		Assert.assertFalse(pref.node(FPref.KESOID_node).getBoolean(FPref.SLEDOVAT_SCHRANKU_value, true));
		Assert.assertEquals(2, udalosti.size());
	}
}
