package cz.geokuk.core.napoveda;

import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.jeNovejsi;
import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.jePrechodNaStabilni;
import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.nabidnout;
import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.nejnovejsiVerze;
import static cz.geokuk.core.napoveda.ZkontrolovatAktualizaceSwingWorker.tlacitka;

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
		Assert.assertTrue(jeNovejsi("6.1.0-beta.1", "6.0.1-beta.5"));
		Assert.assertTrue(jeNovejsi("6.1.0", "6.0.1-beta.5"));
		Assert.assertTrue(jeNovejsi("6.0.1-beta.2", "6.0.1-beta.1"));
		Assert.assertTrue(jeNovejsi("6.0.1-beta.10", "6.0.1-beta.9"));
		Assert.assertTrue(jeNovejsi("6.0.1", "6.0.1-beta.2"));
		Assert.assertFalse(jeNovejsi("6.0.1-beta.2", "6.0.1"));
		Assert.assertFalse(jeNovejsi("6.0.1-beta.1", "6.0.1-beta.1"));
		Assert.assertFalse(jeNovejsi("6.0.0", "6.0.1-beta.1"));
	}

	/** Verze z odpovědi serveru jde do dialogu i do porovnání, neplatná se ignoruje. */
	@Test
	public void neplatneVerzeSeIgnoruji() {
		Assert.assertEquals("6.0.0", nejnovejsiVerze("[{\"tag_name\": \"v<b>9</b>\"}, {\"tag_name\": \"v6.0.0\"}]"));
		Assert.assertEquals("6.0.0", nejnovejsiVerze("[{\"tag_name\": \"v99999999999999999999.0.0\"}, {\"tag_name\": \"v6.0.0\"}]"));
		Assert.assertNull(nejnovejsiVerze("[{\"tag_name\": \"<html>\"}]"));
		Assert.assertEquals("6.3.0-beta.2", nejnovejsiVerze("[{\"tag_name\": \"v6.3.0-beta.2\"}, {\"tag_name\": \"v6.3.0-beta.1\"}]"));
	}

	@Test
	public void obriCisloVeVerziNepadne() {
		Assert.assertTrue(jeNovejsi("6.0.99999999999999999999", "6.0.0"));
	}

	@Test
	public void nejnovejsiZeSeznamu() {
		final String json = "[{\"tag_name\": \"v6.0.1-beta.1\"}, {\"tag_name\": \"v6.0.1-beta.2\"}, {\"tag_name\": \"v6.0.0\"}]";
		Assert.assertEquals("6.0.1-beta.2", nejnovejsiVerze(json));
		Assert.assertEquals("6.0.0", nejnovejsiVerze("{\"tag_name\":\"v6.0.0\",\"name\":\"x\"}"));
		Assert.assertNull(nejnovejsiVerze("[]"));
	}

	@Test
	public void navratZBety() {
		Assert.assertTrue(nabidnout("6.0.0", "6.0.1-beta.2", false, true));
		Assert.assertTrue(nabidnout("6.0.0", "6.0.1-dev.20", false, true));
		Assert.assertFalse(nabidnout("6.0.0", "6.0.1-beta.2", true, true));
		Assert.assertFalse(nabidnout("6.0.0", "6.0.0", false, true));
		Assert.assertTrue(nabidnout("6.0.1", "6.0.0", false, true));
		Assert.assertFalse(nabidnout("5.9.0", "6.0.0", false, true));
	}

	@Test
	public void navratZBetyJenPriRucniKontrole() {
		Assert.assertFalse(nabidnout("6.0.0", "6.2.0-beta.8", false, false));
		Assert.assertTrue(nabidnout("6.2.0", "6.2.0-beta.8", false, false));
		Assert.assertTrue(nabidnout("6.0.1", "6.0.0", false, false));
	}

	@Test
	public void prechodNaStabilni() {
		Assert.assertTrue(jePrechodNaStabilni("6.0.0", "6.2.0-beta.8", false));
		Assert.assertFalse(jePrechodNaStabilni("6.0.0", "6.2.0-beta.8", true));
		Assert.assertFalse(jePrechodNaStabilni("6.2.0", "6.2.0-beta.8", false));
		Assert.assertFalse(jePrechodNaStabilni("6.0.0", "6.0.1", false));
		Assert.assertFalse(jePrechodNaStabilni("6.2.0-beta.7", "6.2.0-beta.8", false));
	}

	@Test
	public void tlacitkaNoveVerze() {
		Assert.assertArrayEquals(new Object[] { "Aktualizovat", "Zobrazit na webu", "Připomenout za týden" }, tlacitka(false));
		Assert.assertArrayEquals(new Object[] { "Přejít na stabilní verzi", "Zobrazit na webu", "Zůstat u testovací verze" }, tlacitka(true));
	}

	@Test
	public void indexyTlacitek() {
		Assert.assertEquals("Aktualizovat", tlacitka(false)[ZkontrolovatAktualizaceSwingWorker.AKTUALIZOVAT]);
		Assert.assertEquals("Zobrazit na webu", tlacitka(false)[ZkontrolovatAktualizaceSwingWorker.WEB]);
		Assert.assertEquals("Připomenout za týden", tlacitka(false)[ZkontrolovatAktualizaceSwingWorker.POZDEJI]);
		Assert.assertEquals("Přejít na stabilní verzi", tlacitka(true)[ZkontrolovatAktualizaceSwingWorker.AKTUALIZOVAT]);
		Assert.assertEquals("Zůstat u testovací verze", tlacitka(true)[ZkontrolovatAktualizaceSwingWorker.POZDEJI]);
	}
}
