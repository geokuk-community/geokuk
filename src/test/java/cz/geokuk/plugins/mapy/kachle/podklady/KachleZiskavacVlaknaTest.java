package cz.geokuk.plugins.mapy.kachle.podklady;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KachleZiskavacVlaknaTest {

	@Test
	public void vlaknaProCteniZCache() {
		final int jader = Runtime.getRuntime().availableProcessors();
		assertTrue(KachleZiskavac.NTHREADS_DISK >= 2 && KachleZiskavac.NTHREADS_DISK <= 4);
		assertTrue(jader < 4 || KachleZiskavac.NTHREADS_DISK == 4);
	}
}
