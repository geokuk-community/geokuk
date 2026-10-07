package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.*;

import cz.geokuk.core.program.prototyp.ZdrojeModel.*;

/**
 * Blok stavového řádku: u každého typu zdroje (GPX | GeoGet | GSAK | OpenSAK) přepínač s ikonou stavu. Kliknutí zapne či vypne celý typ. Najetí na „Zdroje:“ vysune tabulku
 * všech typů, najetí na přepínač typu vysune jen jeho soubory nebo databáze.
 */
public class JPrepinaceZdroju extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int PRODLEVA_ZAVRENI_MS = 350;

	private final ZdrojeModel model;
	private final JPopupMenu souhrn = vytvorPopup();
	private final Map<Typ, JPopupMenu> popupyTypu = new EnumMap<>(Typ.class);
	private final javax.swing.Timer casovacZavreni;
	private final Map<Typ, JLabel> prepinace = new EnumMap<>(Typ.class);
	private final JLabel nadpis = new JLabel("Zdroje:");

	public JPrepinaceZdroju(final ZdrojeModel model, final Runnable prehledZdroju) {
		super(new FlowLayout(FlowLayout.CENTER, 8, 0));
		this.model = model;
		setBorder(BorderFactory.createEtchedBorder());

		souhrn.add(new JZdrojePopup(model, null, prehledZdroju));
		nadpis.setToolTipText("Najetím zobrazíte všechny zdroje");
		nadpis.addMouseListener(najeti(() -> ukaz(souhrn, nadpis)));
		add(nadpis);

		casovacZavreni = new javax.swing.Timer(PRODLEVA_ZAVRENI_MS, e -> zavriKdyzMimo());

		for (final Typ typ : Typ.values()) {
			final JPopupMenu popup = vytvorPopup();
			popup.add(new JZdrojePopup(model, typ, prehledZdroju));
			popupyTypu.put(typ, popup);
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
					ukaz(popup, l);
				}
			});
			prepinace.put(typ, l);
			add(l);
		}
		model.addPosluchac(this::obnov);
		obnov();
	}

	private JPopupMenu vytvorPopup() {
		final JPopupMenu popup = new JPopupMenu();
		popup.setFocusable(false);
		popup.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "zavri");
		popup.getActionMap().put("zavri", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				zavriSeznam();
			}
		});
		return popup;
	}

	private static MouseAdapter najeti(final Runnable akce) {
		return new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				akce.run();
			}
		};
	}

	private void obnov() {
		for (final Typ typ : Typ.values()) {
			final JLabel l = prepinace.get(typ);
			final Stav stav = model.getStavTypu(typ);
			l.setIcon(IkonyZdroju.pro(stav));
			l.setForeground(stav == Stav.VYPNUTO ? Color.GRAY : UIManager.getColor("Label.foreground"));
			l.setToolTipText(typ.getNazev() + ": " + stav.getText() + (stav == Stav.NACITA_SE ? " (kliknutím načítání zrušíte)" : " (kliknutím zapnete nebo vypnete)"));
		}
	}

	private void ukaz(final JPopupMenu popup, final Component kotva) {
		if (popup.isVisible()) {
			return;
		}
		zavriSeznam();
		final Dimension d = popup.getPreferredSize();
		final int presah = kotva.getLocationOnScreen().x + d.width - Toolkit.getDefaultToolkit().getScreenSize().width;
		popup.show(kotva, Math.min(0, -presah), -d.height);
		casovacZavreni.start();
	}

	/** Zobrazí tabulku všech typů zdrojů. */
	public void ukazSouhrn() {
		ukaz(souhrn, nadpis);
	}

	/** Zobrazí jen soubory a databáze jednoho typu. */
	public void ukazTyp(final Typ typ) {
		ukaz(popupyTypu.get(typ), prepinace.get(typ));
	}

	public void zavriSeznam() {
		souhrn.setVisible(false);
		for (final JPopupMenu p : popupyTypu.values()) {
			p.setVisible(false);
		}
	}

	private JPopupMenu viditelny() {
		if (souhrn.isVisible()) {
			return souhrn;
		}
		for (final JPopupMenu p : popupyTypu.values()) {
			if (p.isVisible()) {
				return p;
			}
		}
		return null;
	}

	private void zavriKdyzMimo() {
		final JPopupMenu popup = viditelny();
		if (popup == null) {
			casovacZavreni.stop();
			return;
		}
		final PointerInfo info = MouseInfo.getPointerInfo();
		if (info == null) {
			return;
		}
		final Point p = info.getLocation();
		if (!obsahuje(this, p) && !obsahuje(popup, p)) {
			zavriSeznam();
		}
	}

	private static boolean obsahuje(final Component c, final Point obrazovka) {
		if (!c.isShowing()) {
			return false;
		}
		return new Rectangle(c.getLocationOnScreen(), c.getSize()).contains(obrazovka);
	}
}
