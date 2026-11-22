package cz.geokuk.framework;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** Které otevřené okno zdrží nabídky; potřebuje displej (v CI xvfb). */
public class NabidkyPoStartuOknaTest {

	private JFrame ramec;

	@Before
	public void priprav() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> ramec = new JFrame("test nabídek"));
	}

	@After
	public void uklid() throws Exception {
		if (ramec != null) {
			SwingUtilities.invokeAndWait(ramec::dispose);
		}
	}

	private static boolean blokuje() throws Exception {
		final AtomicBoolean v = new AtomicBoolean();
		SwingUtilities.invokeAndWait(() -> v.set(NabidkyPoStartu.jeOtevrenoBlokujiciOkno()));
		return v.get();
	}

	private static void pockejNaZobrazeni(final JDialog d) throws Exception {
		final long konec = System.currentTimeMillis() + 10_000;
		while (!d.isShowing()) {
			Assert.assertTrue("okno se nezobrazilo", System.currentTimeMillis() < konec);
			Thread.sleep(20);
		}
	}

	@Test
	public void modalniOknoBlokujeDokudJeOtevrene() throws Exception {
		final JDialog[] d = new JDialog[1];
		SwingUtilities.invokeAndWait(() -> d[0] = new JDialog(ramec, "modální", true));
		SwingUtilities.invokeLater(() -> d[0].setVisible(true));
		pockejNaZobrazeni(d[0]);
		Assert.assertTrue(blokuje());
		SwingUtilities.invokeAndWait(d[0]::dispose);
		Assert.assertFalse(blokuje());
	}

	@Test
	public void nemodalniOknoBlokujeJenSPriznakem() throws Exception {
		final JDialog[] d = new JDialog[2];
		SwingUtilities.invokeAndWait(() -> {
			d[0] = new JDialog(ramec, "bez příznaku", false);
			d[0].setVisible(true);
		});
		pockejNaZobrazeni(d[0]);
		Assert.assertFalse(blokuje());
		SwingUtilities.invokeAndWait(() -> {
			d[1] = new JDialog(ramec, "s příznakem", false);
			NabidkyPoStartu.blokuje(d[1]);
			d[1].setVisible(true);
		});
		pockejNaZobrazeni(d[1]);
		Assert.assertTrue(blokuje());
		SwingUtilities.invokeAndWait(() -> {
			d[1].dispose();
			d[0].dispose();
		});
		Assert.assertFalse(blokuje());
	}
}
