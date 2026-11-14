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

/**
 * Stavový řádek užší než jeho plná podoba má výšku úsporného stupně hned při prvním rozložení rodiče, při startu i po zúžení; potřebuje displej. Rodič je rozložený jako obsah okna, ale bez
 * okna, aby šířku neomezovala obrazovka.
 */
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

	private static JPanel rodic(final JStatusBar sb) {
		final JPanel rodic = new JPanel(new BorderLayout());
		rodic.add(new JPanel(), BorderLayout.CENTER);
		rodic.add(sb, BorderLayout.SOUTH);
		return rodic;
	}

	private static void rozloz(final JPanel rodic, final JStatusBar sb, final int sirka) {
		rodic.setSize(sirka, 675);
		rodic.doLayout();
		sb.doLayout();
	}

	private static void overVysku(final int sirka) throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JStatusBar sb = new JStatusBar();
			final JPanel rodic = rodic(sb);
			rozloz(rodic, sb, sirka);
			Assert.assertTrue(sirka + " px: stupeň " + sb.getStupenUspor(), sb.getStupenUspor() > 0);
			final int vyska = sb.getHeight();
			Assert.assertEquals(sirka + " px: výška po prvním rozložení", sb.getPreferredSize().height, vyska);
			rozloz(rodic, sb, sirka);
			Assert.assertEquals(sirka + " px: výška po dalším rozložení", vyska, sb.getHeight());
		});
	}

	@Test
	public void start1050() throws Exception {
		overVysku(1050);
	}

	@Test
	public void start752() throws Exception {
		overVysku(752);
	}

	@Test
	public void zuzeni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JStatusBar sb = new JStatusBar();
			final JPanel rodic = rodic(sb);
			rozloz(rodic, sb, 2400);
			Assert.assertEquals("široký rodič: stupeň", 0, sb.getStupenUspor());
			rozloz(rodic, sb, 1050);
			Assert.assertTrue("úzký rodič: stupeň " + sb.getStupenUspor(), sb.getStupenUspor() > 0);
			Assert.assertEquals("výška po zúžení", sb.getPreferredSize().height, sb.getHeight());
		});
	}

	/** Skutečné okno; na macOS ne, protože aktivace okna po přepnutí vzhledu shodí okna jiných testů ve vzhledu Aqua. */
	@Test
	public void oknoPriStartu() throws Exception {
		Assume.assumeFalse("macOS", System.getProperty("os.name", "").startsWith("Mac"));
		final JStatusBar[] sb = new JStatusBar[1];
		final int[] vyska = new int[1];
		SwingUtilities.invokeAndWait(() -> {
			okno = new JFrame();
			okno.getContentPane().add(new JPanel(), BorderLayout.CENTER);
			sb[0] = new JStatusBar();
			okno.add(sb[0], BorderLayout.SOUTH);
			okno.setSize(752, 675);
			okno.setVisible(true);
			Assert.assertTrue("stupeň " + sb[0].getStupenUspor(), sb[0].getStupenUspor() > 0);
			vyska[0] = sb[0].getHeight();
			Assert.assertEquals("výška po prvním rozložení okna", sb[0].getPreferredSize().height, vyska[0]);
		});
		SwingUtilities.invokeAndWait(() -> {
		});
		SwingUtilities.invokeAndWait(() -> {
			okno.validate();
			Assert.assertEquals("výška po ustálení", vyska[0], sb[0].getHeight());
		});
	}
}
