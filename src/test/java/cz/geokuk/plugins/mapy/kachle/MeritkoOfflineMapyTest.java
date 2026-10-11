package cz.geokuk.plugins.mapy.kachle;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac;

/** Rychlejší offline mapa kreslí dlaždice po 256 px i na displeji se zvětšením. */
public class MeritkoOfflineMapyTest {

	private static double meritko(final boolean ostra) {
		final KachleModel model = new KachleModel() {
			@Override
			public boolean isOfflineMapaOstra() {
				return ostra;
			}
		};
		final KachleZiskavac ziskavac = new KachleZiskavac();
		model.inject(ziskavac);
		model.setMeritkoDispleje(2);
		return ziskavac.getOfflineMapy().getMeritko();
	}

	@Test
	public void rychlejsiNezvetsuje() {
		Assert.assertEquals(1, meritko(false), 0);
	}

	@Test
	public void ostraPodleDispleje() {
		Assert.assertEquals(2, meritko(true), 0);
	}
}
