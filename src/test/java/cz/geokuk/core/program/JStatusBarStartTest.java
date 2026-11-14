package cz.geokuk.core.program;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** Stavový řádek v okně užším než jeho plná podoba má výšku úsporného stupně hned při prvním rozložení okna i po zúžení okna; potřebuje displej. */
public class JStatusBarStartTest {

	private LookAndFeel puvodniLaf;
	private JFrame okno;

	@Before
	public void metal() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		puvodniLaf = UIManager.getLookAndFeel();
		UIManager.setLookAndFeel(new MetalLookAndFeel());
	}

	@After
	public void uklid() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			if (okno != null) {
				okno.dispose();
			}
		});
		if (puvodniLaf != null) {
			UIManager.setLookAndFeel(puvodniLaf);
		}
	}

	private void overVysku(final int sirka) throws Exception {
		final JStatusBar[] sb = new JStatusBar[1];
		final int[] vyska = new int[1];
		SwingUtilities.invokeAndWait(() -> {
			okno = new JFrame();
			okno.getContentPane().add(new JPanel(), BorderLayout.CENTER);
			sb[0] = new JStatusBar();
			okno.add(sb[0], BorderLayout.SOUTH);
			okno.setSize(sirka, 675);
			okno.setVisible(true);
			Assert.assertTrue(sirka + " px: stupeň " + sb[0].getStupenUspor(), sb[0].getStupenUspor() > 0);
			vyska[0] = sb[0].getHeight();
			Assert.assertEquals(sirka + " px: výška po prvním rozložení", sb[0].getPreferredSize().height, vyska[0]);
		});
		SwingUtilities.invokeAndWait(() -> {
		});
		SwingUtilities.invokeAndWait(() -> {
			okno.validate();
			Assert.assertEquals(sirka + " px: výška po ustálení", vyska[0], sb[0].getHeight());
		});
		SwingUtilities.invokeAndWait(() -> okno.dispose());
		okno = null;
	}

	@Test
	public void uzkeOkno1050() throws Exception {
		overVysku(1050);
	}

	@Test
	public void zuzeniOkna() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			okno = new JFrame();
			okno.getContentPane().add(new JPanel(), BorderLayout.CENTER);
			final JStatusBar sb = new JStatusBar();
			okno.add(sb, BorderLayout.SOUTH);
			okno.setSize(2400, 675);
			okno.setVisible(true);
			Assert.assertEquals("široké okno: stupeň", 0, sb.getStupenUspor());
			okno.setSize(1050, 675);
			okno.validate();
			Assert.assertTrue("úzké okno: stupeň " + sb.getStupenUspor(), sb.getStupenUspor() > 0);
			Assert.assertEquals("výška po zúžení", sb.getPreferredSize().height, sb.getHeight());
		});
	}

	@Test
	public void uzkeOkno752() throws Exception {
		overVysku(752);
	}
}
