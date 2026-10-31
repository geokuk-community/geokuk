package cz.geokuk.plugins.mapy.kachle.podklady;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.google.common.cache.Cache;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;

/** Skutečná paměťová cache získávače má velikost podle haldy, ne pevnou. */
public class KachleVPametiZiskavacTest {

	@SuppressWarnings("unchecked")
	private static Cache<Ka, ?> pametovaCache(final KachleZiskavac ziskavac) throws Exception {
		final Field f = KachleZiskavac.class.getDeclaredField("kachlmap");
		f.setAccessible(true);
		return (Cache<Ka, ?>) f.get(ziskavac);
	}

	@Test
	public void cacheZiskavaceMaVelikostPodleHaldy() throws Exception {
		final int ocekavane = KachleZiskavac.maxKachliVPameti(Runtime.getRuntime().maxMemory());
		// Při malé haldě je vzorec stejný jako stará pevná hodnota a test by nic nerozlišil.
		Assume.assumeTrue("halda je moc malá: " + ocekavane, ocekavane >= 2 * KachleZiskavac.MIN_KACHLI_V_PAMETI);
		final KachleZiskavac ziskavac = new KachleZiskavac();
		final Cache<Ka, ?> cache = pametovaCache(ziskavac);
		// Dlaždice se drží silně, aby je neuvolnil GC přes softValues.
		final List<Object> drzene = new ArrayList<>();
		@SuppressWarnings("unchecked")
		final java.util.Map<Ka, Object> mapa = (java.util.Map<Ka, Object>) (java.util.Map<?, ?>) cache.asMap();
		for (int i = 0; i < ocekavane * 3; i++) {
			final Ka ka = new Ka(KaLoc.ofXyz(i % 4096, i / 4096, 12), EKaType.TURIST_M);
			final Object kachlice = ziskavac.new Kachlice(ka, null, null);
			drzene.add(kachlice);
			mapa.put(ka, kachlice);
		}
		final long velikost = cache.size();
		Assert.assertTrue("v cache je " + velikost + " dlaždic, nejvýš " + ocekavane, velikost <= ocekavane);
		Assert.assertTrue("v cache je jen " + velikost + " dlaždic, má se vejít skoro " + ocekavane, velikost >= ocekavane * 0.9);
		Assert.assertEquals(ocekavane * 3, drzene.size());
	}
}
