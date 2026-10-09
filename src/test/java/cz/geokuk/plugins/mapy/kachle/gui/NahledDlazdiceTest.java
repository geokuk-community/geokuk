package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

public class NahledDlazdiceTest {

	private final Map<Ka, Image> cache = new HashMap<>();

	private Ka ka(final KaLoc loc) {
		return new Ka(loc, EKaType.OFFLINE_MF);
	}

	private void uloz(final KaLoc loc, final int sirka) {
		cache.put(ka(loc), new BufferedImage(sirka, sirka, BufferedImage.TYPE_INT_ARGB));
	}

	private List<NahledDlazdice> najdi(final KaLoc loc) {
		return NahledDlazdice.najdi(ka(loc), cache::get, 256);
	}

	@Test
	public void rodicADiteJsouVzajemneInverzni() {
		for (final int x : new int[] { -5, -1, 0, 3 }) {
			final KaLoc loc = KaLoc.ofJZ(new Mou(x << 20, 7 << 20), 12);
			for (int vpravo = 0; vpravo <= 1; vpravo++) {
				for (int nahoru = 0; nahoru <= 1; nahoru++) {
					Assert.assertEquals(loc, loc.dite(vpravo, nahoru).rodic());
				}
			}
		}
	}

	@Test
	public void bezCacheNicNenajde() {
		Assert.assertTrue(najdi(KaLoc.ofJZ(new Mou(0, 0), 12)).isEmpty());
	}

	@Test
	public void vyrezRodiceUrovenNahoru() {
		final KaLoc rodic = KaLoc.ofJZ(new Mou(0, 0), 12);
		uloz(rodic, 256);
		// západní jižní dítě je vlevo dole, východní severní vpravo nahoře
		final NahledDlazdice jz = najdi(rodic.dite(0, 0)).get(0);
		Assert.assertArrayEquals(new int[] { 0, 128, 128, 256 }, new int[] { jz.sx1, jz.sy1, jz.sx2, jz.sy2 });
		final NahledDlazdice sv = najdi(rodic.dite(1, 1)).get(0);
		Assert.assertArrayEquals(new int[] { 128, 0, 256, 128 }, new int[] { sv.sx1, sv.sy1, sv.sx2, sv.sy2 });
		Assert.assertArrayEquals(new int[] { 0, 0, 256, 256 }, new int[] { sv.dx1, sv.dy1, sv.dx2, sv.dy2 });
	}

	@Test
	public void vyrezRodiceVysokeRozliseniADveUrovne() {
		final KaLoc praroditel = KaLoc.ofJZ(new Mou(-1 << 22, 3 << 22), 10);
		uloz(praroditel, 512);
		final KaLoc vnuk = praroditel.dite(1, 0).dite(0, 1);
		final NahledDlazdice n = najdi(vnuk).get(0);
		// x index 2 ze 4, y index 1 ze 4 odspodu, tedy 2 shora – dílky po 128 px
		Assert.assertArrayEquals(new int[] { 256, 256, 384, 384 }, new int[] { n.sx1, n.sy1, n.sx2, n.sy2 });
	}

	@Test
	public void bliznsiRodicMaPrednost() {
		final KaLoc loc = KaLoc.ofJZ(new Mou(0, 0), 12);
		uloz(loc.rodic(), 256);
		uloz(loc.rodic().rodic(), 256);
		Assert.assertEquals(256 / 2, najdi(loc).get(0).sx2 - najdi(loc).get(0).sx1);
	}

	@Test
	public void ctyriDetiSeSlozi() {
		final KaLoc loc = KaLoc.ofJZ(new Mou(0, 0), 12);
		uloz(loc.dite(0, 1), 256);
		uloz(loc.dite(1, 0), 512);
		final List<NahledDlazdice> n = najdi(loc);
		Assert.assertEquals(2, n.size());
		for (final NahledDlazdice d : n) {
			Assert.assertEquals(128, d.dx2 - d.dx1);
			Assert.assertEquals(128, d.dy2 - d.dy1);
			Assert.assertEquals(d.obrazek.getWidth(null), d.sx2);
		}
		// severozápadní dítě vlevo nahoře, jihovýchodní vpravo dole
		Assert.assertEquals(0, n.get(0).dx1 + n.get(0).dy1);
		Assert.assertEquals(512, n.get(1).dx2 + n.get(1).dy2);
	}

	@Test
	public void nejnizsiMeritkoNemaRodice() {
		final KaLoc loc = KaLoc.ofJZ(new Mou(0, 0), 1);
		Assert.assertNull(loc.rodic());
	}
}
