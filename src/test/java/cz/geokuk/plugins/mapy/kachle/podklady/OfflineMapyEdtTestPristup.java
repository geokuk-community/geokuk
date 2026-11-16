package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.concurrent.CountDownLatch;

/** Pomůcka testů z jiných balíčků: vlákno, které drží zámek offline map jako rozdělané otevírání map. */
public final class OfflineMapyEdtTestPristup {

	private OfflineMapyEdtTestPristup() {}

	public static Thread drzZamek(final OfflineMapy mapy, final CountDownLatch pust) throws InterruptedException {
		return OfflineMapyEdtTest.drzZamek(mapy, pust);
	}
}
