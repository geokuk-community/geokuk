package cz.geokuk.core.napoveda;

import java.net.URLDecoder;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.program.FConst;

public class ZadatProblemActionTest {

	@Test
	public void odkazObsahujeDiagnostiku() throws Exception {
		final String odkaz = ZadatProblemAction.odkaz("Geokuk 6.0.1 & Java");
		Assert.assertTrue(odkaz.startsWith(FConst.POST_PROBLEM_URL + "?body="));
		Assert.assertFalse(odkaz.contains("+"));
		Assert.assertTrue(URLDecoder.decode(odkaz, "UTF-8").contains("Geokuk 6.0.1 & Java"));
	}

	@Test
	public void dlouhyTextSeZkrati() throws Exception {
		final StringBuilder sb = new StringBuilder("Geokuk 6.0.1\n");
		for (int i = 0; i < 2000; i++) {
			sb.append("řádek ").append(i).append('\n');
		}
		final String odkaz = ZadatProblemAction.odkaz(sb.toString());
		Assert.assertTrue(odkaz.length() <= 6000);
		final String telo = URLDecoder.decode(odkaz, "UTF-8");
		Assert.assertTrue(telo.contains("Geokuk 6.0.1"));
		Assert.assertTrue(telo.contains("zkráceno"));
	}

	@Test
	public void nejdrivUkazeInformace() {
		final boolean[] ukazano = new boolean[1];
		new ZadatProblemAction(() -> ukazano[0] = true).actionPerformed(null);
		Assert.assertTrue(ukazano[0]);
	}
}
