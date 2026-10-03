package cz.geokuk.plugins.cesty;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.program.MainFrameHolder;
import cz.geokuk.framework.ChybyVDiagnostice;

public class CestyModelTest {

	@Test
	public void chybaKopirovaniDoSchrankySeOhlasi() throws Exception {
		final CestyModel model = new CestyModel();
		model.inject(new MainFrameHolder()); // bez hlavního okna schránka selže
		final String okolnost = "Kopírování odkazů do schránky";
		final int puvodne = ChybyVDiagnostice.pocet(okolnost);
		model.nasypVyletDoGeogetu();
		Assert.assertTrue(ChybyVDiagnostice.pribude(okolnost, puvodne));
	}
}
