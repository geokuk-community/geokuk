package cz.geokuk.core.ovladani;

import javax.swing.JCheckBoxMenuItem;

import org.junit.Assert;
import org.junit.Test;

public class DalkoveOvladaniActionTest {

	/** Ovládání zapnuté parametrem --ovladani: položka v menu je zaškrtnutá, nastavení se nemění. */
	@Test
	public void polozkaUkazujeBeziciOvladani() throws Exception {
		final boolean vNastaveni = DalkoveOvladani.jeZapnuteVNastaveni();
		final DalkoveOvladaniAction akce = new DalkoveOvladaniAction();
		final JCheckBoxMenuItem polozka = new JCheckBoxMenuItem();
		akce.join(polozka);
		akce.setSelected(false);
		final DalkoveOvladani ovladani = new DalkoveOvladani();
		akce.inject(ovladani);
		ovladani.spust(0);
		try {
			akce.ukazStav();
			Assert.assertTrue(polozka.isSelected());
			Assert.assertTrue(akce.isSelected());
			Assert.assertEquals(vNastaveni, DalkoveOvladani.jeZapnuteVNastaveni());
			Assert.assertTrue(ovladani.bezi());
		} finally {
			ovladani.zastav();
		}
		akce.ukazStav();
		Assert.assertFalse(polozka.isSelected());
		Assert.assertEquals(vNastaveni, DalkoveOvladani.jeZapnuteVNastaveni());
	}
}
