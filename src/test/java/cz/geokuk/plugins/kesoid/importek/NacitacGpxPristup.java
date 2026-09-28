package cz.geokuk.plugins.kesoid.importek;

import java.io.IOException;
import java.io.InputStream;

/** Čtení GPX pro testy z jiných balíků. */
public final class NacitacGpxPristup {

	public static void nacti(final InputStream in, final IImportBuilder builder) throws IOException {
		new NacitacGpx().nacti(in, "test.gpx", builder, null);
	}

	private NacitacGpxPristup() {}
}
