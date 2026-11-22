package cz.geokuk.plugins.cesty;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.EventFirer;
import cz.geokuk.framework.MyPreferences;

/** Panel cesty je bez uložené volby zapnutý; kdo ho vypnul, tomu zůstane vypnutý. */
public class CestyModelPanelCestyTest {

	private final MyPreferences pref = MyPreferences.current().node("test-panel-cesty");

	private CestyModel model() {
		final CestyModel m = new CestyModel() {
			@Override
			protected MyPreferences currPrefe() {
				return pref;
			}
		};
		m.inject((EventFirer) event -> {});
		return m;
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void bezUlozeneVolbyJePanelZapnuty() {
		Assert.assertTrue(model().isPanelCesty());
	}

	@Test
	public void vypnutyPanelZustaneVypnuty() {
		model().setPanelCesty(false);
		Assert.assertFalse("volba se pamatuje", model().isPanelCesty());
		model().setPanelCesty(true);
		Assert.assertTrue(model().isPanelCesty());
	}
}
