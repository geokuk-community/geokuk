package cz.geokuk.plugins.vylety;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.*;

import org.junit.*;

import cz.geokuk.util.gui.PopupNadBlokem;

/** Popup výletů nad popiskem ve stavovém řádku: otevře se nad ním a během úpravy jména se odjetím myši nezavře. */
public class JPopisekVyletuTest {

	private JFrame okno;
	private JPopisekVyletu popisek;

	private final VyletModel model = new VyletModel() {
		@Override
		public List<VyletInfo> getVylety() {
			return Arrays.asList(new VyletInfo(VyletyUlozisko.VYCHOZI_ID, "Výchozí", true, 1, 0), new VyletInfo("v2", "Šumava 2026", false, 48, 0));
		}

		@Override
		public String validujJmeno(final String jmeno, final String id) {
			return null;
		}

		@Override
		public CompletableFuture<Void> aktivovat(final String id) {
			return new CompletableFuture<>();
		}
	};

	@Before
	public void pred() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			popisek = new JPopisekVyletu();
			popisek.inject(model);
			popisek.obnovJmeno();
			okno = new JFrame();
			final JPanel radek = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			radek.add(popisek);
			okno.add(radek, BorderLayout.SOUTH);
			final Rectangle obrazovka = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
			okno.setBounds(obrazovka.x, obrazovka.y + 100, Math.min(1000, obrazovka.width), 500);
			okno.setVisible(true);
		});
	}

	@After
	public void po() throws Exception {
		if (okno != null) {
			SwingUtilities.invokeAndWait(() -> {
				popisek.zavri();
				okno.dispose();
			});
		}
	}

	private static JButton tlacitko(final Container c, final String text) {
		for (final Component k : c.getComponents()) {
			if (k instanceof JButton && text.equals(((JButton) k).getText())) {
				return (JButton) k;
			}
			if (k instanceof Container) {
				final JButton b = tlacitko((Container) k, text);
				if (b != null) {
					return b;
				}
			}
		}
		return null;
	}

	@Test
	public void popupNadPopiskemUvnitrOkna() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Assert.assertEquals("Výlet: Výchozí", popisek.getText());
			popisek.ukaz();
			final JComponent p = popisek.getPopup().viditelny();
			Assert.assertSame(popisek.getObsah(), p);
			Assert.assertEquals("těsně nad popiskem", popisek.getLocationOnScreen().y, p.getLocationOnScreen().y + p.getHeight());
			Assert.assertTrue("nevyjede z okna", new Rectangle(okno.getLocationOnScreen(), okno.getSize()).contains(new Rectangle(p.getLocationOnScreen(), p.getSize())));
		});
	}

	@Test
	public void behemUpravyJmenaSeNezavre() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			popisek.ukaz();
			final PopupNadBlokem popup = popisek.getPopup();
			final Point mimo = new Point(popisek.getLocationOnScreen().x - 300, popisek.getLocationOnScreen().y + 300);
			tlacitko(popisek.getObsah(), "Přejmenovat").doClick();
			popup.tik(mimo, 1000);
			popup.tik(mimo, 1000 + PopupNadBlokem.PRODLEVA_ZAVRENI_MS);
			Assert.assertNotNull("rozepsané jméno drží popup otevřený", popup.viditelny());
			popisek.getObsah().zrusUpravu();
			popup.tik(mimo, 3000);
			popup.tik(mimo, 3000 + PopupNadBlokem.PRODLEVA_ZAVRENI_MS);
			Assert.assertNull(popup.viditelny());
		});
	}

	@Test
	public void sOtevrenymPopupemBezTooltipu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final java.awt.event.MouseEvent mys = new java.awt.event.MouseEvent(popisek, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false);
			Assert.assertEquals("Aktivní výlet „Výchozí“", popisek.getToolTipText(mys));
			popisek.ukaz();
			Assert.assertNotNull(popisek.getPopup().viditelny());
			Assert.assertNull(popisek.getToolTipText(mys));
			popisek.zavri();
			Assert.assertEquals("Aktivní výlet „Výchozí“", popisek.getToolTipText(mys));
		});
	}
}
