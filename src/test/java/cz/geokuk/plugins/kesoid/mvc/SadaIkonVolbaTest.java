package cz.geokuk.plugins.kesoid.mvc;

import org.junit.*;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.mapicon.ASada;

/** Opakovaná volba stejné sady ikon ikony znovu nenačítá. */
public class SadaIkonVolbaTest {

	private final MyPreferences pref = MyPreferences.current().node("test-sada-ikon");
	private int nacteni;
	private final KesoidModel model = new KesoidModel() {
		@Override
		protected MyPreferences currPrefe() {
			return pref;
		}

		@Override
		public void startIkonLoad(final boolean prenacti) {
			nacteni++;
		}
	};

	@Before
	public void udalosti() {
		model.inject(udalost -> {});
	}

	@After
	public void uklid() throws Exception {
		pref.removeNode();
	}

	@Test
	public void stejnaSadaPodruheNicNeprenacte() {
		model.setJmenoAktualniSadyIkon(ASada.STANDARD);
		model.setJmenoAktualniSadyIkon(ASada.STANDARD);
		Assert.assertEquals(1, nacteni);
		Assert.assertEquals(ASada.STANDARD, model.getJmenoAktualniSadyIkon());
	}
}
