package cz.geokuk.core.program;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import cz.geokuk.framework.FKurzory;
import lombok.extern.slf4j.Slf4j;

/** Při startu vedle EDT načte data lokalizace, písma a třídy Swingu, které by si okno jinak načítalo na EDT samo. */
@Slf4j
final class PredehrevaniStartu {

	private static final String[] TRIDY = { "javax.swing.JFrame", "javax.swing.JMenuBar", "javax.swing.JMenu", "javax.swing.JMenuItem", "javax.swing.JPopupMenu", "javax.swing.JToolBar",
			"javax.swing.JButton", "javax.swing.JToggleButton", "javax.swing.JCheckBox", "javax.swing.JLabel", "javax.swing.JPanel", "javax.swing.JScrollPane", "javax.swing.JTable",
			"javax.swing.JTextField", "javax.swing.JSlider", "javax.swing.JComboBox", "javax.swing.JSpinner", "javax.swing.JTabbedPane", "javax.swing.JLayeredPane",
			"javax.swing.OverlayLayout", "javax.swing.text.html.HTMLEditorKit", "javax.swing.text.html.StyleSheet", "javax.swing.border.EmptyBorder" };

	private PredehrevaniStartu() {
	}

	static void spust() {
		final Thread vlakno = new Thread(PredehrevaniStartu::predehrej, "Předehřátí startu");
		vlakno.setDaemon(true);
		vlakno.start();
	}

	static void predehrej() {
		try {
			// První použití formátování času, čísel a kalendáře čte lokalizační data JDK.
			DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now());
			NumberFormat.getInstance().format(1234.5);
			Calendar.getInstance();
			Toolkit.getDefaultToolkit();
			final FontRenderContext frc = new FontRenderContext(null, true, true);
			final Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
			for (final String rodina : new String[] { Font.DIALOG, Font.SANS_SERIF, Font.MONOSPACED }) {
				for (final int styl : new int[] { Font.PLAIN, Font.BOLD }) {
					final Font font = new Font(rodina, styl, 12);
					font.getLineMetrics("Ážq0", frc);
					g.getFontMetrics(font).stringWidth("Ážq0");
				}
			}
			g.dispose();
			for (final String trida : TRIDY) {
				Class.forName(trida);
			}
			// Kurzory se kreslí při prvním použití třídy.
			Class.forName(FKurzory.class.getName());
		} catch (final Exception | LinkageError e) {
			log.debug("Předehřátí startu selhalo: {}", e.toString());
		}
	}
}
