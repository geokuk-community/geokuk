package cz.geokuk.core.napoveda;

import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.jeNovejsi;

import org.junit.Assert;
import org.junit.Test;

public class ZkontrolovatAktualizaceSwingWorkerTest {

	@Test
	public void novejsiVerze() {
		Assert.assertTrue(jeNovejsi("6.0.1", "6.0.0"));
		Assert.assertTrue(jeNovejsi("6.1.0", "6.0.9"));
		Assert.assertTrue(jeNovejsi("10.0.0", "9.9.9"));
		Assert.assertTrue(jeNovejsi("6.0.0", "5.1.1c"));
	}

	@Test
	public void stejnaNeboStarsiVerze() {
		Assert.assertFalse(jeNovejsi("6.0.0", "6.0.0"));
		Assert.assertFalse(jeNovejsi("6.0", "6.0.0"));
		Assert.assertFalse(jeNovejsi("5.1.1c", "6.0.0"));
		Assert.assertFalse(jeNovejsi("6.0.0", "6.0.1"));
	}
}
