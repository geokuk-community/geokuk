package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.*;

import cz.geokuk.core.program.prototyp.ZdrojeModel.*;

/**
 * Blok stavového řádku: u každého typu zdroje (GPX | GeoGet | GSAK | OpenSAK) zaškrtávátko záměru, ikona stavu a název. Zaškrtávátko zapne či vypne celý typ i během načítání. Najetí na „Zdroje:“ vysune tabulku
 * všech typů, najetí na název typu vysune jen jeho soubory nebo databáze.
 */
public class JPrepinaceZdroju extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int PRODLEVA_ZAVRENI_MS = 350;

	private final ZdrojeModel model;
	private final JPopupMenu souhrn = vytvorPopup();
	private final Map<Typ, JPopupMenu> popupyTypu = new EnumMap<>(Typ.class);
	private final javax.swing.Timer casovacZavreni;
	private final Map<Typ, JCheckBox> zaskrtavatka = new EnumMap<>(Typ.class);
	private final Map<Typ, JLabel> ikony = new EnumMap<>(Typ.class);
	private final Map<Typ, JLabel> nazvy = new EnumMap<>(Typ.class);
	private final JLabel nadpis = new JLabel("Zdroje:");

	public JPrepinaceZdroju(final ZdrojeModel model) {
		super(new FlowLayout(FlowLayout.CENTER, 8, 0));
		this.model = model;
		setBorder(BorderFactory.createEtchedBorder());

		souhrn.add(new JZdrojePopup(model, null));
		nadpis.setToolTipText("Najetím zobrazíte všechny zdroje");
		nadpis.addMouseListener(najeti(() -> ukaz(souhrn, nadpis)));
		add(nadpis);

		casovacZavreni = new javax.swing.Timer(PRODLEVA_ZAVRENI_MS, e -> zavriKdyzMimo());

		for (final Typ typ : Typ.values()) {
			final JPopupMenu popup = vytvorPopup();
			popup.add(new JZdrojePopup(model, typ));
			popupyTypu.put(typ, popup);

			final JCheckBox zaskrtavatko = new JCheckBox();
			zaskrtavatko.setFocusable(false);
			zaskrtavatko.setMargin(new Insets(0, 0, 0, 0));
			zaskrtavatko.addActionListener(e -> model.setTypZapnut(typ, zaskrtavatko.isSelected()));
			final JLabel ikona = new JLabel(IkonyZdroju.prazdna());
			final JLabel nazev = new JLabel(typ.getNazev());
			final MouseAdapter najeti = new MouseAdapter() {
				@Override
				public void mouseEntered(final MouseEvent e) {
					ukaz(popup, zaskrtavatko);
				}
			};
			ikona.addMouseListener(najeti);
			nazev.addMouseListener(najeti);

			final JPanel bunka = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
			bunka.setOpaque(false);
			bunka.add(zaskrtavatko);
			bunka.add(ikona);
			bunka.add(nazev);
			zaskrtavatka.put(typ, zaskrtavatko);
			ikony.put(typ, ikona);
			nazvy.put(typ, nazev);
			add(bunka);
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

	/** Mění se jen ikona a barva, velikost buněk zůstává, aby se nic v řádku neposouvalo. */
	private void obnov() {
		for (final Typ typ : Typ.values()) {
			final Stav stav = model.getStavTypu(typ);
			final boolean zapnuto = stav != Stav.VYPNUTO;
			zaskrtavatka.get(typ).setSelected(model.isTypZapnut(typ));
			zaskrtavatka.get(typ).setToolTipText(zapnuto ? typ.getNazev() + " vypnout (zruší i probíhající načítání)" : typ.getNazev() + " zapnout");
			ikony.get(typ).setIcon(zapnuto ? IkonyZdroju.pro(stav) : IkonyZdroju.prazdna());
			ikony.get(typ).setToolTipText(typ.getNazev() + ": " + stav.getText());
			nazvy.get(typ).setForeground(zapnuto ? UIManager.getColor("Label.foreground") : Color.GRAY);
		}
	}

	private void ukaz(final JPopupMenu popup, final Component kotva) {
		if (popup.isVisible()) {
			return;
		}
		zavriSeznam();
		final Dimension d = popup.getPreferredSize();
		// Těsně nad blokem, zarovnané k přepínači a uvnitř obrazovky, aby mezi nimi nebyla mezera.
		final int sirkaObrazovky = Toolkit.getDefaultToolkit().getScreenSize().width;
		final int zacatek = SwingUtilities.convertPoint(kotva, 0, 0, this).x;
		final int zleva = getLocationOnScreen().x + zacatek;
		final int x = Math.max(-getLocationOnScreen().x, zacatek - Math.max(0, zleva + d.width - sirkaObrazovky));
		popup.show(this, x, -d.height);
		casovacZavreni.start();
	}

	/** Zobrazí tabulku všech typů zdrojů. */
	public void ukazSouhrn() {
		ukaz(souhrn, nadpis);
	}

	/** Zobrazí jen soubory a databáze jednoho typu. */
	public void ukazTyp(final Typ typ) {
		ukaz(popupyTypu.get(typ), zaskrtavatka.get(typ));
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
