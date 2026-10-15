package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TemaTuristickaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private BufferedImage vyrendruj(final File slozka, final TemaOfflineMapy tema) throws IOException {
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		try {
			mapy.kontrolaSlozkyNs = 0;
			mapy.nastav(slozka, tema);
			final OfflineRenderer r = mapy.pouzij();
			try {
				return r.vyrendruj(OfflineMapyTest.STRED_Z15);
			} finally {
				r.skonci();
			}
		} finally {
			mapy.zavri();
		}
	}

	/** Bez zvoleného tématu je Turistická, uložené původní téma zůstane. */
	@Test
	public void turistickaJeVychozi() {
		Assert.assertSame(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.zTextu(""));
		Assert.assertSame(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.zTextu(null));
		Assert.assertSame(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.zTextu(TemaOfflineMapy.TURISTICKA.naText()));
		Assert.assertSame(TemaOfflineMapy.VYCHOZI, TemaOfflineMapy.zTextu(TemaOfflineMapy.VYCHOZI.naText()));
		Assert.assertEquals("Turistická", TemaOfflineMapy.TURISTICKA.getNazev());
		Assert.assertEquals(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.VESTAVENA.get(0));
		Assert.assertTrue(TemaOfflineMapy.VESTAVENA.contains(TemaOfflineMapy.VYCHOZI));
	}

	/** Téma se načte celé (i se symboly z programu) a kreslí jinak než původní. */
	@Test
	public void turistickaSeNactePokresliJinakNezPuvodni() throws Exception {
		final OfflineRenderer.NacteneTema nactene = OfflineRenderer.nactiTema(TemaOfflineMapy.TURISTICKA);
		try {
			Assert.assertNull(nactene.chyba, nactene.chyba);
		} finally {
			nactene.uvolni();
		}
		final File slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final BufferedImage turisticka = vyrendruj(slozka, TemaOfflineMapy.TURISTICKA);
		final BufferedImage puvodni = vyrendruj(slozka, TemaOfflineMapy.VYCHOZI);
		Assert.assertNotNull(turisticka);
		Assert.assertFalse(java.util.Arrays.equals(turisticka.getRGB(0, 0, 256, 256, null, 0, 256), puvodni.getRGB(0, 0, 256, 256, null, 0, 256)));
	}
}
