package cz.geokuk.plugins.cesty.routovani;

import java.io.File;
import java.io.IOException;

/** Syntetická mapa cest pro testy z jiných balíčků. */
public final class RoutovacTestPristup {

	private RoutovacTestPristup() {}

	public static void zkopirujMapu(final File slozka) throws IOException {
		RoutovacTest.zkopirujMapu(slozka);
	}
}
