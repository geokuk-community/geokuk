package cz.geokuk.core.program;

import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;

import org.junit.*;

import cz.geokuk.core.napoveda.Diagnostika;

public class StitekVerzeTest {

	private boolean betaKanal;

	@Before
	public void setUp() {
		betaKanal = Diagnostika.betaKanal();
	}

	@After
	public void tearDown() {
		Diagnostika.setBetaKanal(betaKanal);
	}

	@Test
	public void poVypnutiBetaKanaluStitekBetuNeukazuje() {
		final JButton verze = new JButton();
		Diagnostika.setBetaKanal(true);
		Menu.nastavStitekVerze(verze);
		Assert.assertTrue(verze.getText(), verze.getText().endsWith("· beta kanál"));
		Assert.assertTrue(verze.isVisible());
		Diagnostika.setBetaKanal(false);
		Menu.nastavStitekVerze(verze);
		Assert.assertFalse(verze.getText(), verze.getText().contains("beta kanál"));
		Assert.assertEquals(FConst.VERSION.contains("-"), verze.isVisible());
	}

	@Test
	public void zmenaBetaKanaluSeOhlasi() {
		final AtomicInteger zmen = new AtomicInteger();
		Diagnostika.poZmeneBetaKanalu(zmen::incrementAndGet);
		Diagnostika.setBetaKanal(true);
		Diagnostika.setBetaKanal(false);
		Assert.assertEquals(2, zmen.get());
	}
}
