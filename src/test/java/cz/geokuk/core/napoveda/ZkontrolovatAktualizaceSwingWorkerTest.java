package cz.geokuk.core.napoveda;

import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.jeNovejsi;
import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.nejnovejsiVerze;

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

	@Test
	public void testovaciVerze() {
		Assert.assertTrue(jeNovejsi("6.0.1-beta.1", "6.0.0"));
		Assert.assertTrue(jeNovejsi("6.0.1-beta.2", "6.0.1-beta.1"));
		Assert.assertTrue(jeNovejsi("6.0.1-beta.10", "6.0.1-beta.9"));
		Assert.assertTrue(jeNovejsi("6.0.1", "6.0.1-beta.2"));
		Assert.assertFalse(jeNovejsi("6.0.1-beta.2", "6.0.1"));
		Assert.assertFalse(jeNovejsi("6.0.1-beta.1", "6.0.1-beta.1"));
		Assert.assertFalse(jeNovejsi("6.0.0", "6.0.1-beta.1"));
	}

	@Test
	public void nejnovejsiZeSeznamu() {
		final String json = "[{\"tag_name\": \"v6.0.1-beta.1\"}, {\"tag_name\": \"v6.0.1-beta.2\"}, {\"tag_name\": \"v6.0.0\"}]";
		Assert.assertEquals("6.0.1-beta.2", nejnovejsiVerze(json));
		Assert.assertEquals("6.0.0", nejnovejsiVerze("{\"tag_name\":\"v6.0.0\",\"name\":\"x\"}"));
		Assert.assertNull(nejnovejsiVerze("[]"));
	}
}
