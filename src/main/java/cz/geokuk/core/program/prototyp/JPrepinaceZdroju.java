package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;

import cz.geokuk.core.program.prototyp.ZdrojeModel.*;

/**
 * Blok stavového řádku: u každého typu zdroje (GPX | GeoGet | GSAK | OpenSAK) zaškrtávátko záměru, ikona stavu a název. Zaškrtávátko zapne či vypne celý typ i během načítání. Najetí na blok vysune tabulku
 * všech typů, najetí na název typu vysune jen jeho soubory nebo databáze.
 */
public class JPrepinaceZdroju extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Pod touto šířkou okna se skryjí názvy zdrojů, zůstane zaškrtávátko a ikona. */
	static final int PRAH_KOMPAKTNI = 900;

	/** Okraj aktivní plochy popisku a názvu; vedle zaškrtávátka a ikony zůstává místo, které popup nevyvolá. */
	private static final int PADDING = 3;

	private static final int PRODLEVA_ZAVRENI_MS = 350;

	private final ZdrojeModel model;
	private final JPopupMenu souhrn = vytvorPopup();
	private final Map<Typ, JPopupMenu> popupyTypu = new EnumMap<>(Typ.class);
	private final javax.swing.Timer casovacZavreni;
	private final JLabel popisek = new JLabel("Zdroje:");
	private final Map<Typ, JCheckBox> zaskrtavatka = new EnumMap<>(Typ.class);
	private final Map<Typ, JLabel> ikony = new EnumMap<>(Typ.class);
	private final java.util.List<JZdrojePopup> tabulky = new java.util.ArrayList<>();
	private final Map<Typ, JLabel> nazvy = new EnumMap<>(Typ.class);

	public JPrepinaceZdroju(final ZdrojeModel model) {
		super();
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		this.model = model;
		setBorder(BorderFactory.createEtchedBorder());

		popisek.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				ukazSouhrn();
			}
		});
		popisek.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, PADDING));
		natahni(popisek);
		add(Box.createHorizontalStrut(5 - PADDING));
		add(popisek);
		final JZdrojePopup uplna = new JZdrojePopup(model, null);
		tabulky.add(uplna);
		souhrn.add(uplna);

		casovacZavreni = new javax.swing.Timer(PRODLEVA_ZAVRENI_MS, e -> zavriKdyzMimo());

		for (final Typ typ : Typ.values()) {
			final JPopupMenu popup = vytvorPopup();
			final JZdrojePopup detail = new JZdrojePopup(model, typ);
			tabulky.add(detail);
			popup.add(detail);
			popupyTypu.put(typ, popup);

			final JCheckBox zaskrtavatko = new JCheckBox();
			zaskrtavatko.setFocusable(false);
			zaskrtavatko.setMargin(new Insets(0, 0, 0, 0));
			zaskrtavatko.addActionListener(e -> model.setTypZapnut(typ, zaskrtavatko.isSelected()));
			final JLabel ikona = new JLabel(IkonyZdroju.prazdna());
			final JLabel nazev = new JLabel(typ.getNazev());
			nazev.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(final MouseEvent e) {
					ukaz(popup, zaskrtavatko);
				}
			});
			// V kompaktním režimu bez názvu otevírá detail ikona, jinak jen ukazuje tooltip.
			ikona.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(final MouseEvent e) {
					if (!nazev.isVisible()) {
						ukaz(popup, zaskrtavatko);
					}
				}
			});

			nazev.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, PADDING));
			natahni(zaskrtavatko);
			natahni(ikona);
			natahni(nazev);
			final JPanel bunka = new JPanel();
			bunka.setLayout(new BoxLayout(bunka, BoxLayout.X_AXIS));
			bunka.setOpaque(false);
			bunka.add(zaskrtavatko);
			bunka.add(Box.createHorizontalStrut(2));
			bunka.add(ikona);
			bunka.add(Box.createHorizontalStrut(2 - PADDING > 0 ? 2 - PADDING : 0));
			bunka.add(nazev);
			add(Box.createHorizontalStrut(8));
			zaskrtavatka.put(typ, zaskrtavatko);
			ikony.put(typ, ikona);
			nazvy.put(typ, nazev);
			add(bunka);
		}
		addHierarchyBoundsListener(new java.awt.event.HierarchyBoundsAdapter() {
			@Override
			public void ancestorResized(final java.awt.event.HierarchyEvent e) {
				prizpusob();
			}
		});
		model.addPosluchac(this::obnov);
		obnov();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		SwingUtilities.invokeLater(this::prizpusob);
	}

	/** Komponenta zabere celou výšku bloku, aby byla plocha pro najetí a klik dostatečná, vzhled se nemění. */
	private static void natahni(final JComponent c) {
		c.setMaximumSize(new Dimension(c.getPreferredSize().width, Integer.MAX_VALUE));
	}

	@Override
	public Dimension getPreferredSize() {
		final Dimension d = super.getPreferredSize();
		return new Dimension(d.width, Math.max(d.height, 24));
	}

	private int sirkaOkna() {
		final Window okno = SwingUtilities.getWindowAncestor(this);
		return okno != null ? okno.getWidth() : Integer.MAX_VALUE;
	}

	/** V úzkém okně jen zaškrtávátko a ikona; názvy jsou v tooltipu. */
	private void prizpusob() {
		final boolean kompaktni = sirkaOkna() < PRAH_KOMPAKTNI;
		for (final JLabel n : nazvy.values()) {
			n.setVisible(!kompaktni);
		}
		for (final JZdrojePopup t : tabulky) {
			t.nastavDostupnouSirku(sirkaOkna());
		}
		revalidate();
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

	/** Mění se jen ikona a barva, velikost buněk zůstává, aby se nic v řádku neposouvalo. */
	private void obnov() {
		for (final Typ typ : Typ.values()) {
			final Stav stav = model.getStavTypu(typ);
			final boolean zapnuto = stav != Stav.VYPNUTO;
			zaskrtavatka.get(typ).setSelected(model.isTypZapnut(typ));
			zaskrtavatka.get(typ).setToolTipText(zapnuto ? typ.getNazev() + " vypnout (zruší i probíhající načítání)" : typ.getNazev() + " zapnout");
			ikony.get(typ).setIcon(zapnuto ? IkonyZdroju.pro(stav) : IkonyZdroju.prazdna());
			ikony.get(typ).setToolTipText(typ.getNazev() + ": " + stav.getText());
			nazvy.get(typ).setToolTipText(typ.getNazev());
			nazvy.get(typ).setForeground(zapnuto ? UIManager.getColor("Label.foreground") : Color.GRAY);
		}
	}

	private void ukaz(final JPopupMenu popup, final Component kotva) {
		if (popup.isVisible()) {
			return;
		}
		zavriSeznam();
		prizpusob();
		final Dimension d = popup.getPreferredSize();
		// Těsně nad blokem a uvnitř obrazovky; tabulka všech zdrojů je zarovnaná k pravému okraji bloku, detail k buňce zdroje.
		final int sirkaObrazovky = Toolkit.getDefaultToolkit().getScreenSize().width;
		final int vlevo = getLocationOnScreen().x;
		int x = kotva == this ? getWidth() - d.width : SwingUtilities.convertPoint(kotva, 0, 0, this).x;
		x = Math.max(-vlevo, Math.min(x, sirkaObrazovky - vlevo - d.width));
		popup.show(this, x, -d.height);
		casovacZavreni.start();
	}

	/** Co oblast dělá. */
	enum Druh {
		UPLNA_TABULKA, PREPINA, DETAIL, TOOLTIP
	}

	/** Plocha bloku, která reaguje na najetí nebo klik; souřadnice jsou v bloku. */
	static final class Oblast {
		final Rectangle plocha;
		final Druh druh;
		final String popis;

		Oblast(final Rectangle plocha, final Druh druh, final String popis) {
			this.plocha = plocha;
			this.druh = druh;
			this.popis = popis;
		}
	}

	/** Přesné oblasti, ve kterých blok reaguje; mezery a okraje mezi nimi nereagují. */
	List<Oblast> oblasti() {
		final List<Oblast> oblasti = new ArrayList<>();
		oblasti.add(new Oblast(plocha(popisek), Druh.UPLNA_TABULKA, "Zdroje"));
		for (final Typ typ : Typ.values()) {
			oblasti.add(new Oblast(plocha(zaskrtavatka.get(typ)), Druh.PREPINA, typ.getNazev() + " zaškrtávátko"));
			final boolean bezNazvu = !nazvy.get(typ).isVisible();
			oblasti.add(new Oblast(plocha(ikony.get(typ)), bezNazvu ? Druh.DETAIL : Druh.TOOLTIP, typ.getNazev() + " ikona"));
			if (!bezNazvu) {
				oblasti.add(new Oblast(plocha(nazvy.get(typ)), Druh.DETAIL, typ.getNazev() + " název"));
			}
		}
		return oblasti;
	}

	private Rectangle plocha(final Component c) {
		return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this);
	}

	/** Zobrazí tabulku všech typů zdrojů. */
	public void ukazSouhrn() {
		ukaz(souhrn, this);
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
