package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

import cz.geokuk.core.program.prototyp.ZdrojeModel.*;

/**
 * Blok stavového řádku: u každého typu zdroje (GPX | GeoGet | GSAK | OpenSAK) přepínač s ikonou stavu. Kliknutí zapne či vypne celý typ, najetí vysune seznam zdrojů.
 */
public class JPrepinaceZdroju extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int PRODLEVA_ZAVRENI_MS = 350;

	private final ZdrojeModel model;
	private final JPopupMenu popup = new JPopupMenu();
	private final javax.swing.Timer casovacZavreni;
	private final JLabel[] prepinace = new JLabel[Typ.values().length];

	public JPrepinaceZdroju(final ZdrojeModel model, final Runnable prehledZdroju) {
		super(new FlowLayout(FlowLayout.CENTER, 8, 0));
		this.model = model;
		setBorder(BorderFactory.createEtchedBorder());
		add(new JLabel("Zdroje:"));

		popup.setFocusable(false);
		popup.add(new JZdrojePopup(model, prehledZdroju));
		popup.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "zavri");
		popup.getActionMap().put("zavri", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				popup.setVisible(false);
			}
		});

		casovacZavreni = new javax.swing.Timer(PRODLEVA_ZAVRENI_MS, e -> zavriKdyzMimo());
		casovacZavreni.setRepeats(true);

		for (final Typ typ : Typ.values()) {
			final JLabel l = new JLabel(typ.getNazev());
			l.setIconTextGap(4);
			l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			l.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					model.setTypZapnut(typ, !model.isTypZapnut(typ));
				}

				@Override
				public void mouseEntered(final MouseEvent e) {
					ukazSeznam();
				}
			});
			prepinace[typ.ordinal()] = l;
			add(l);
		}
		model.addPosluchac(this::obnov);
		obnov();
	}

	private void obnov() {
		for (final Typ typ : Typ.values()) {
			final JLabel l = prepinace[typ.ordinal()];
			final Stav stav = model.getStavTypu(typ);
			l.setIcon(IkonyZdroju.pro(stav));
			l.setForeground(stav == Stav.VYPNUTO ? Color.GRAY : UIManager.getColor("Label.foreground"));
			l.setToolTipText(typ.getNazev() + ": " + stav.getText() + (stav == Stav.NACITA_SE ? " (kliknutím načítání zrušíte)" : " (kliknutím zapnete nebo vypnete)"));
		}
	}

	/** Zobrazí seznam zdrojů nad blokem. */
	public void ukazSeznam() {
		if (popup.isVisible()) {
			return;
		}
		final Dimension d = popup.getPreferredSize();
		final int presah = getLocationOnScreen().x + d.width - Toolkit.getDefaultToolkit().getScreenSize().width;
		popup.show(this, Math.min(0, -presah), -d.height);
		casovacZavreni.start();
	}

	public void zavriSeznam() {
		popup.setVisible(false);
		casovacZavreni.stop();
	}

	public boolean jeSeznamVidet() {
		return popup.isVisible();
	}

	private void zavriKdyzMimo() {
		if (!popup.isVisible()) {
			casovacZavreni.stop();
			return;
		}
		final PointerInfo info = MouseInfo.getPointerInfo();
		if (info == null) {
			return;
		}
		final Point p = info.getLocation();
		if (obsahuje(this, p) || obsahuje(popup, p)) {
			return;
		}
		zavriSeznam();
	}

	private static boolean obsahuje(final Component c, final Point obrazovka) {
		if (!c.isShowing()) {
			return false;
		}
		final Point nula = c.getLocationOnScreen();
		return new Rectangle(nula, c.getSize()).contains(obrazovka);
	}
}
