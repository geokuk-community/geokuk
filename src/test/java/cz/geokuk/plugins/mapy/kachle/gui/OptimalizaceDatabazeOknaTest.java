package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

import cz.geokuk.plugins.mapy.kachle.gui.PanelOptimalizace.Faze;
import cz.geokuk.plugins.mapy.kachle.podklady.*;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;
import cz.geokuk.util.file.Filex;

public class OptimalizaceDatabazeOknaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File soubor;
	private OptimalizaceDatabaze optimalizace;

	@Before
	public void setUp() throws Exception {
		final File slozka = tmp.newFolder("cache");
		soubor = new File(slozka, "tiles.sqlite");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozka, false, true));
		optimalizace = KachleManagerFactory.getInstance(holder).optimalizace();
	}

	private void vyrobCache() throws Exception {
		final SqlJetDb db = SqlJetDb.open(soubor, true);
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		db.createTable("CREATE TABLE tiles (x int, y int, z int, s varchar(10), image blob, PRIMARY KEY(x, y, z, s))");
		final ISqlJetTable t = db.getTable("tiles");
		final Random r = new Random(1);
		for (int i = 0; i < 400; i++) {
			final byte[] d = new byte[15000];
			r.nextBytes(d);
			t.insertOr(SqlJetConflictAction.REPLACE, i, 0, 14, "o1", d);
		}
		db.commit();
		db.beginTransaction(SqlJetTransactionMode.WRITE);
		final ISqlJetCursor c = t.open();
		while (!c.eof()) {
			c.delete();
			if (!c.eof()) {
				c.next();
			}
		}
		c.close();
		db.commit();
		db.close();
	}

	private static void pockej(final PanelOptimalizace panel, final Faze cil) throws Exception {
		final long konec = System.currentTimeMillis() + 30_000;
		while (System.currentTimeMillis() < konec) {
			final Faze[] f = new Faze[1];
			SwingUtilities.invokeAndWait(() -> f[0] = panel.faze());
			if (f[0] == cil) {
				return;
			}
			Thread.sleep(50);
		}
		Assert.fail("fáze " + cil + " nenastala");
	}

	private static String texty(final Component c) {
		final StringBuilder sb = new StringBuilder();
		if (c.isVisible()) {
			if (c instanceof JLabel) {
				sb.append(((JLabel) c).getText()).append('\n');
			} else if (c instanceof javax.swing.text.JTextComponent) {
				sb.append(((javax.swing.text.JTextComponent) c).getText()).append('\n');
			} else if (c instanceof AbstractButton) {
				sb.append('[').append(((AbstractButton) c).getText()).append("]\n");
			}
			if (c instanceof Container) {
				for (final Component d : ((Container) c).getComponents()) {
					sb.append(texty(d));
				}
			}
		}
		return sb.toString();
	}

	/** Snímek pro ruční kontrolu; píše se jen s -Dsnimky.dir. */
	private static void snimek(final JComponent c, final String nazev) throws Exception {
		final String dir = System.getProperty("snimky.dir");
		if (dir == null) {
			return;
		}
		final Dimension d = c.getPreferredSize();
		c.setSize(d);
		c.doLayout();
		layoutRekurzivne(c);
		final BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
		final java.awt.Graphics2D g = img.createGraphics();
		g.setColor(java.awt.Color.WHITE);
		g.fillRect(0, 0, d.width, d.height);
		c.paint(g);
		g.dispose();
		ImageIO.write(img, "png", new File(dir, nazev + ".png"));
	}

	private static void layoutRekurzivne(final Component c) {
		if (c instanceof Container) {
			((Container) c).doLayout();
			for (final Component d : ((Container) c).getComponents()) {
				layoutRekurzivne(d);
			}
		}
	}

	@Test
	public void bezCacheNelzeOptimalizovat() throws Exception {
		final PanelOptimalizace panel = new PanelOptimalizace(optimalizace, () -> {});
		Assert.assertEquals(Faze.NELZE, panel.faze());
		Assert.assertTrue(texty(panel), texty(panel).contains("není co optimalizovat"));
		Assert.assertFalse(panel.tlacitkoOptimalizovat().isVisible());
		snimek(panel, "dialog-nelze");
	}

	@Test
	public void priprave_beh_a_hotovo() throws Exception {
		vyrobCache();
		final PanelOptimalizace panel = new PanelOptimalizace(optimalizace, () -> {});
		Assert.assertEquals(Faze.PRIPRAVENO, panel.faze());
		Assert.assertTrue(panel.tlacitkoOptimalizovat().isVisible());
		final String pred = texty(panel);
		Assert.assertTrue(pred, pred.contains("Volných stránek:") && pred.contains("Potrvá asi 1 minutu"));
		snimek(panel, "dialog-pripraveno");
		SwingUtilities.invokeAndWait(panel::spust);
		Assert.assertTrue(panel.bezi() || panel.faze() == Faze.HOTOVO);
		pockej(panel, Faze.HOTOVO);
		Assert.assertTrue(texty(panel), texty(panel).contains("Hotovo. Velikost cache dlaždic klesla"));
		snimek(panel, "dialog-hotovo");
		Assert.assertEquals(4096, optimalizace.stav().velikostStranky());
	}

	@Test
	public void chybaUkazeZpravuAPuvodniCacheZustane() throws Exception {
		vyrobCache();
		final byte[] pred = java.nio.file.Files.readAllBytes(soubor.toPath());
		new File(soubor.getPath() + ".new").mkdir(); // .new nejde založit
		new File(soubor.getPath() + ".new", "x").createNewFile();
		final PanelOptimalizace panel = new PanelOptimalizace(optimalizace, () -> {});
		SwingUtilities.invokeAndWait(panel::spust);
		pockej(panel, Faze.CHYBA);
		Assert.assertTrue(texty(panel), texty(panel).contains("cache zůstala beze změny"));
		Assert.assertArrayEquals(pred, java.nio.file.Files.readAllBytes(soubor.toPath()));
		snimek(panel, "dialog-chyba");
	}

	@Test
	public void okno_pri_startu_texty_a_snimky() throws Exception {
		final StavSouboruCache stav = new StavSouboruCache(1900L << 20, 1024, 1_845_313, 856_789);
		final JLabel odpocet = new JLabel(OptimalizaceTexty.odpocet(30, false));
		final JPanel doporucit = OknoDoporuceniOptimalizace.panel(stav, Doporuceni.DOPORUCIT, 100L << 30, odpocet, new JButton("Optimalizovat teď"),
				new JButton("Spustit bez optimalizace"));
		final String t = texty(doporucit);
		Assert.assertTrue(t, t.contains("fragmentovaná (46 % volného místa, 1.9 GB)") || t.contains("fragmentovaná (46 % volného místa, 1,9 GB)"));
		Assert.assertTrue(t, t.contains("[Optimalizovat teď]") && t.contains("Připomeneme to za 3 dny") && !t.contains("Připomenout za týden"));
		snimek(doporucit, "start-doporuceni");
		final JPanel malo = OknoDoporuceniOptimalizace.panel(stav, Doporuceni.MALO_MISTA, 200L << 20, odpocet, new JButton("Optimalizovat teď"),
				new JButton("Spustit bez optimalizace"));
		final String m = texty(malo);
		Assert.assertFalse(m, m.contains("[Optimalizovat teď]"));
		Assert.assertTrue(m, m.contains("chybí místo na disku"));
		snimek(malo, "start-malo-mista");
	}

	@Test
	public void oknoPriStartuOdpocetAVolby() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		final StavSouboruCache stav = new StavSouboruCache(1900L << 20, 1024, 1_845_313, 856_789);
		final String[] volba = new String[1];
		final OknoDoporuceniOptimalizace.Volba v = new OknoDoporuceniOptimalizace.Volba() {
			@Override
			public void optimalizovat() {
				volba[0] = "opt";
			}

			@Override
			public void bezOptimalizace() {
				volba[0] = "bez";
			}

			@Override
			public void vyprseni() {
				volba[0] = "vyprseni";
			}
		};
		SwingUtilities.invokeAndWait(() -> OknoDoporuceniOptimalizace.zobraz(null, stav, Doporuceni.DOPORUCIT, 100L << 30, v));
		final java.awt.Window[] okna = new java.awt.Window[1];
		SwingUtilities.invokeAndWait(() -> {
			for (final java.awt.Window w : java.awt.Window.getWindows()) {
				if (w.isShowing() && w instanceof JDialog) {
					okna[0] = w;
				}
			}
		});
		Assert.assertNotNull(okna[0]);
		SwingUtilities.invokeAndWait(() -> {
			final JButton b = hledej(okna[0], "Spustit bez optimalizace");
			b.doClick();
		});
		Assert.assertEquals("bez", volba[0]);
		Assert.assertFalse(okna[0].isShowing());
	}

	private static JButton hledej(final Container c, final String text) {
		for (final Component d : c.getComponents()) {
			if (d instanceof JButton && text.equals(((JButton) d).getText())) {
				return (JButton) d;
			}
			if (d instanceof Container) {
				final JButton b = hledej((Container) d, text);
				if (b != null) {
					return b;
				}
			}
		}
		return null;
	}

	private static java.awt.Window zobrazOkno(final OknoDoporuceniOptimalizace.Volba v) throws Exception {
		final StavSouboruCache stav = new StavSouboruCache(1900L << 20, 1024, 1_845_313, 856_789);
		SwingUtilities.invokeAndWait(() -> OknoDoporuceniOptimalizace.zobraz(null, stav, Doporuceni.DOPORUCIT, 100L << 30, v));
		final java.awt.Window[] okna = new java.awt.Window[1];
		SwingUtilities.invokeAndWait(() -> {
			for (final java.awt.Window w : java.awt.Window.getWindows()) {
				if (w.isShowing() && w instanceof JDialog) {
					okna[0] = w;
				}
			}
			// Mimo obrazovku, ať myš nad oknem nepozastaví odpočet.
			okna[0].setLocation(-20000, -20000);
		});
		return okna[0];
	}

	private static void tikej(final java.awt.Window okno, final int kolikrat) throws Exception {
		final java.lang.reflect.Field f = OknoDoporuceniOptimalizace.class.getDeclaredField("casovac");
		f.setAccessible(true);
		final javax.swing.Timer casovac = (javax.swing.Timer) f.get(okno);
		final java.awt.event.ActionListener[] posluchaci = casovac.getActionListeners();
		SwingUtilities.invokeAndWait(() -> {
			casovac.stop();
			for (int i = 0; i < kolikrat && okno.isShowing(); i++) {
				for (final java.awt.event.ActionListener l : posluchaci) {
					l.actionPerformed(new java.awt.event.ActionEvent(casovac, 0, "tik"));
				}
			}
		});
	}

	private static OknoDoporuceniOptimalizace.Volba zaznam(final String[] volba) {
		return new OknoDoporuceniOptimalizace.Volba() {
			@Override
			public void optimalizovat() {
				volba[0] = "opt";
			}

			@Override
			public void bezOptimalizace() {
				volba[0] = "bez";
			}

			@Override
			public void vyprseni() {
				volba[0] = "vyprseni";
			}
		};
	}

	/** Po 30 s okno zavře časovač a oznámí vypršení, ne volbu „Spustit bez optimalizace“. */
	@Test
	public void vyprseniOdpoctuOznamiVyprseni() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		final String[] volba = new String[1];
		final java.awt.Window okno = zobrazOkno(zaznam(volba));
		tikej(okno, OknoDoporuceniOptimalizace.ODPOCET_S - 1);
		Assert.assertNull("před koncem odpočtu nic", volba[0]);
		tikej(okno, 1);
		Assert.assertEquals("vyprseni", volba[0]);
		Assert.assertFalse(okno.isShowing());
	}

	/** Klik do okna odpočet zruší: ani po vypršení času se okno nezavře a nic se neuloží. */
	@Test
	public void klikOdpocetZrusi() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		final String[] volba = new String[1];
		final java.awt.Window okno = zobrazOkno(zaznam(volba));
		SwingUtilities.invokeAndWait(() -> {
			final java.awt.Component panel = ((JDialog) okno).getContentPane();
			for (final java.awt.event.MouseListener l : panel.getMouseListeners()) {
				l.mousePressed(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 5, 5, 1, false));
			}
		});
		tikej(okno, OknoDoporuceniOptimalizace.ODPOCET_S + 5);
		Assert.assertNull(volba[0]);
		Assert.assertTrue(okno.isShowing());
		SwingUtilities.invokeAndWait(() -> hledej(okno, "Spustit bez optimalizace").doClick());
		Assert.assertEquals("bez", volba[0]);
	}
}
