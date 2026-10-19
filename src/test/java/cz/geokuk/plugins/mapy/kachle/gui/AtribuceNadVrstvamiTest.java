package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.*;
import javax.swing.text.BadLocationException;

import org.junit.*;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.napoveda.JCoJeNoveho;
import cz.geokuk.framework.JPrekryvnik;
import cz.geokuk.plugins.mapy.kachle.ReliefCuzkEvent;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Atribuce mapy je nad všemi vrstvami a její plné znění se ukáže v okně. */
public class AtribuceNadVrstvamiTest {

	private static final int SIRKA = 800;
	private static final int VYSKA = 600;
	private static final Color KESE = Color.RED;

	private static JKachlovnikPresCele kachlovnik() {
		final JKachlovnikPresCele k = new JKachlovnikPresCele() {
			private static final long serialVersionUID = 1L;

			@Override
			protected EKaType getKachloType() {
				return EKaType.OPEN_STREET;
			}
		};
		k.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		k.onEvent(new ReliefCuzkEvent(true));
		return k;
	}

	/** Vrstva keší přes celou mapu (jako v hlavním okně mezi odkazem rady a kachlovníkem) nesmí atribuci překreslit. */
	@Test
	public void atribuceJeNadVrstvouKesi() {
		final JKachlovnikPresCele k = kachlovnik();
		final JSingleSlide0 kese = new JSingleSlide0() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void paintComponent(final Graphics g) {
				g.setColor(KESE);
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		};
		final JPrekryvnik mapa = new JPrekryvnik() {
			private static final long serialVersionUID = 1L;
		};
		mapa.add(new JOdkazRadySlide(k));
		mapa.add(kese);
		mapa.add(k);
		mapa.setSize(SIRKA, VYSKA);
		mapa.doLayout();
		final BufferedImage img = new BufferedImage(SIRKA, VYSKA, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = img.createGraphics();
		mapa.paint(g);
		g.dispose();

		final Point ikona = najdiIkonu(k);
		Assert.assertNotNull("ikona ⓘ je vpravo dole", ikona);
		int atribuce = 0;
		for (int y = VYSKA - 20; y < VYSKA; y++) {
			for (int x = SIRKA - 200; x < SIRKA; x++) {
				if (img.getRGB(x, y) != KESE.getRGB()) {
					atribuce++;
				}
			}
		}
		Assert.assertTrue("atribuce je vidět přes keše: " + atribuce, atribuce > 1000);
		Assert.assertEquals("nad atribucí nic nekreslí", KESE.getRGB(), img.getRGB(SIRKA / 2, VYSKA / 2));
	}

	/** Klik na ⓘ otevře okno ve stylu Co je nového s plným zněním atribuce, ne vyskakovací menu. */
	@Test(timeout = 60000)
	public void klikNaIkonuOtevreOkno() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		final JKachlovnikPresCele k = kachlovnik();
		final JFrame[] okno = new JFrame[1];
		final Point[] ikona = new Point[1];
		SwingUtilities.invokeAndWait(() -> {
			okno[0] = new JFrame();
			okno[0].getContentPane().add(k);
			okno[0].setSize(SIRKA, VYSKA);
			okno[0].setVisible(true);
			final BufferedImage img = new BufferedImage(k.getWidth(), k.getHeight(), BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g = img.createGraphics();
			k.kresliAtribuci(g);
			g.dispose();
			ikona[0] = najdiIkonu(k);
		});
		Assert.assertNotNull(ikona[0]);
		try {
			SwingUtilities.invokeLater(() -> k.klikNaRadu(ikona[0]));
			JDialog dialog = null;
			for (final long konec = System.currentTimeMillis() + 10000; dialog == null && System.currentTimeMillis() < konec;) {
				Thread.sleep(50);
				for (final Window w : Window.getWindows()) {
					if (w instanceof JDialog && w.isShowing()) {
						dialog = (JDialog) w;
					}
				}
			}
			Assert.assertNotNull("klik na ⓘ otevře okno", dialog);
			final JDialog d = dialog;
			final String[] text = new String[1];
			final List<String> plna = k.plnaAtribuce();
			SwingUtilities.invokeAndWait(() -> {
				final JEditorPane editor = najdi(d.getContentPane(), JEditorPane.class);
				Assert.assertNotNull("okno má obsah Co je nového", najdi(d.getContentPane(), JCoJeNoveho.class));
				try {
					text[0] = editor.getDocument().getText(0, editor.getDocument().getLength());
				} catch (final BadLocationException e) {
					throw new IllegalStateException(e);
				}
				Assert.assertTrue(editor.getText(), editor.getText().contains("https://www.openstreetmap.org/copyright"));
				d.dispose();
			});
			Assert.assertTrue(plna.size() > 1);
			for (final String t : plna) {
				Assert.assertTrue(text[0], text[0].contains(t));
			}
		} finally {
			SwingUtilities.invokeAndWait(() -> okno[0].dispose());
		}
	}

	private static Point najdiIkonu(final JKachlovnikPresCele k) {
		for (int y = k.getHeight() - 1; y > k.getHeight() - 30; y--) {
			for (int x = k.getWidth() - 1; x > k.getWidth() - 60; x--) {
				if (k.jeNadOdkazemRady(new Point(x, y))) {
					return new Point(x, y);
				}
			}
		}
		return null;
	}

	private static <T> T najdi(final Container c, final Class<T> trida) {
		for (final Component ch : c.getComponents()) {
			if (trida.isInstance(ch)) {
				return trida.cast(ch);
			}
			if (ch instanceof Container) {
				final T t = najdi((Container) ch, trida);
				if (t != null) {
					return t;
				}
			}
		}
		return null;
	}
}
