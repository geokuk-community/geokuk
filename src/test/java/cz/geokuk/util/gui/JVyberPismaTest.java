package cz.geokuk.util.gui;

import java.awt.Font;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Výběr písma vrací písmo podle ovládacích prvků a hlásí jen skutečné změny. */
public class JVyberPismaTest {

	private JVyberPisma vyber;
	private int zmen;

	@Before
	public void setUp() {
		vyber = new JVyberPisma(new Font(Font.DIALOG, Font.PLAIN, 12));
		vyber.addChangeListener(e -> zmen++);
	}

	@Test
	public void nastaveniPismaNastaviOvladace() {
		vyber.setVybranePismo(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 20));
		Assert.assertEquals(Font.SERIF, vyber.seznamRodin.getSelectedValue());
		Assert.assertEquals("tučná kurzíva", vyber.rez.getSelectedItem());
		Assert.assertEquals(20, vyber.velikost.getValue());
		Assert.assertEquals(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 20), vyber.getVybranePismo());
		Assert.assertEquals(1, zmen);
	}

	@Test
	public void stejnePismoNehlasiZmenu() {
		vyber.setVybranePismo(new Font(Font.DIALOG, Font.PLAIN, 12));
		Assert.assertEquals(0, zmen);
	}

	@Test
	public void zmenaRezuAVelikosti() {
		vyber.rez.setSelectedItem("kurzíva");
		Assert.assertEquals(new Font(Font.DIALOG, Font.ITALIC, 12), vyber.getVybranePismo());
		vyber.velikost.setValue(16);
		Assert.assertEquals(new Font(Font.DIALOG, Font.ITALIC, 16), vyber.getVybranePismo());
		Assert.assertEquals(2, zmen);
	}

	@Test
	public void zmenaRodiny() {
		vyber.seznamRodin.setSelectedValue(Font.MONOSPACED, false);
		Assert.assertEquals(new Font(Font.MONOSPACED, Font.PLAIN, 12), vyber.getVybranePismo());
		Assert.assertEquals(1, zmen);
	}

	@Test
	public void neznamaRodinaZustaneVybrana() {
		vyber.setVybranePismo(new Font("Neexistující písmo", Font.BOLD, 14));
		Assert.assertEquals("Neexistující písmo", vyber.seznamRodin.getSelectedValue());
		Assert.assertEquals("Neexistující písmo", vyber.getVybranePismo().getName());
		Assert.assertEquals("tučné", vyber.rez.getSelectedItem());
	}
}
