package cz.geokuk.plugins.kesoid.mvc;

import java.awt.*;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

import javax.swing.*;

import cz.geokuk.plugins.kesoid.importek.*;
import cz.geokuk.plugins.kesoid.importek.StavZdroju.StavVyberu;
import cz.geokuk.util.file.Filex;
import cz.geokuk.util.lang.FString;

/**
 * Blok stavového řádku „Zdroje:“ a u každého typu zdroje zaškrtávátko, ikona stavu a název. Zaškrtávátko zapne či vypne typ i během načítání. Najetí na popisek vysune
 * tabulku všech zdrojů, najetí na název (v úzkém okně na ikonu) jen zdroje typu.
 */
public class JPrepinaceZdroju extends JPanel {

	private static final long serialVersionUID = 1L;

	/** Pod touto šířkou okna se skryjí názvy zdrojů, zůstane zaškrtávátko a ikona. */
	static final int PRAH_KOMPAKTNI = JTabulkaZdroju.PRAH_UZKE;

	/** Okraj aktivní plochy popisku a názvu; vedle zaškrtávátka a ikony zůstává místo, které popup nevyvolá. */
	private static final int PADDING = 3;

	/** Jak dlouho musí myš zůstat na popisku či názvu, než se popup otevře. */
	static final int PRODLEVA_OTEVRENI_MS = 150;
	/** Když už je otevřený jiný popup: přejetí přes jiný název cestou k popupu ho nepřepne. */
	static final int PRODLEVA_PREPNUTI_MS = 500;
	/** Jak dlouho smí být myš mimo blok i popup, než se popup zavře. */
	static final int PRODLEVA_ZAVRENI_MS = 500;
	private static final int KROK_HLIDANI_MS = 50;

	static final int VYSKA = 20;

	private final JLabel popisek = new JLabel("Zdroje:");
	private final JTabulkaZdroju uplna = new JTabulkaZdroju(null);
	private final JComponent souhrn = obal(uplna);
	private final Map<TypZdroje, JComponent> popupyTypu = new EnumMap<>(TypZdroje.class);
	private final Map<TypZdroje, JTabulkaZdroju> detaily = new EnumMap<>(TypZdroje.class);
	private final Map<TypZdroje, JCheckBox> zaskrtavatka = new EnumMap<>(TypZdroje.class);
	private final Map<TypZdroje, JLabel> ikony = new EnumMap<>(TypZdroje.class);
	private final Map<TypZdroje, JLabel> nazvy = new EnumMap<>(TypZdroje.class);
	private final Map<TypZdroje, JPanel> bunky = new EnumMap<>(TypZdroje.class);
	private final javax.swing.Timer hlidaniZavreni;
	/** Od kdy je myš mimo blok i popup, -1 když není. */
	private long mimoOd = -1;
	/** Popup, který se otevře, když na jeho cíli myš vydrží; null, když se nic nečeká. */
	private JComponent cekajici;
	private Runnable cekajiciUkazani;
	private long cekaOd;
	/** Popup bez zachytávání myši a fokusu, aby první klik mimo něj (třeba na zaškrtávátko) nepropadl. */
	private Popup popup;
	private JComponent obsahPopupu;

	private StavZdroju stav = StavZdroju.PRAZDNY;
	private OvladaniZdroju ovladani;

	public JPrepinaceZdroju() {
		setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		setBorder(BorderFactory.createEtchedBorder());

		popisek.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, PADDING));
		popisek.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				naCil(souhrn, JPrepinaceZdroju.this::ukazSouhrn, System.currentTimeMillis());
			}

			@Override
			public void mouseExited(final MouseEvent e) {
				opustenCil(souhrn);
			}
		});
		natahni(popisek);
		add(Box.createHorizontalStrut(5 - PADDING));
		add(popisek);
		getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "zavriZdroje");
		getActionMap().put("zavriZdroje", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isEnabled() {
				return popup != null;
			}

			@Override
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				zavriSeznam();
			}
		});

		for (final TypZdroje typ : TypZdroje.values()) {
			final JTabulkaZdroju detail = new JTabulkaZdroju(typ);
			detaily.put(typ, detail);
			popupyTypu.put(typ, obal(detail));

			final JCheckBox zaskrtavatko = new JCheckBox(IkonyZdroju.zaskrtavatko(StavVyberu.VYPNUTO)) {
				private static final long serialVersionUID = 1L;

				@Override
				public Point getToolTipLocation(final MouseEvent e) {
					return bublinaNadBlokem(this, e);
				}
			};
			zaskrtavatko.setFocusable(false);
			zaskrtavatko.setOpaque(false);
			zaskrtavatko.setMargin(new Insets(0, 0, 0, 0));
			zaskrtavatko.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
			zaskrtavatko.addActionListener(e -> {
				if (ovladani != null) {
					JTabulkaZdroju.klikTyp(stav, typ, ovladani);
				}
			});
			final JLabel ikona = new JLabel(IkonyZdroju.prazdna()) {
				private static final long serialVersionUID = 1L;

				@Override
				public Point getToolTipLocation(final MouseEvent e) {
					return bublinaNadBlokem(this, e);
				}
			};
			final JLabel nazev = new JLabel(typ.getNazev());
			nazev.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, PADDING));
			nazev.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(final MouseEvent e) {
					naCil(popupyTypu.get(typ), () -> ukazTyp(typ), System.currentTimeMillis());
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					opustenCil(popupyTypu.get(typ));
				}
			});
			ikona.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(final MouseEvent e) {
					if (!nazev.isVisible()) {
						naCil(popupyTypu.get(typ), () -> ukazTyp(typ), System.currentTimeMillis());
					}
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					opustenCil(popupyTypu.get(typ));
				}
			});
			natahni(zaskrtavatko);
			natahni(ikona);
			natahni(nazev);
			final JPanel bunka = new JPanel();
			bunka.setLayout(new BoxLayout(bunka, BoxLayout.X_AXIS));
			bunka.setOpaque(false);
			bunka.add(Box.createHorizontalStrut(8));
			bunka.add(zaskrtavatko);
			bunka.add(Box.createHorizontalStrut(2));
			bunka.add(ikona);
			bunka.add(nazev);
			add(bunka);
			bunky.put(typ, bunka);
			zaskrtavatka.put(typ, zaskrtavatko);
			ikony.put(typ, ikona);
			nazvy.put(typ, nazev);
		}
		add(Box.createHorizontalStrut(2));

		hlidaniZavreni = new javax.swing.Timer(KROK_HLIDANI_MS, e -> tik());
		addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
			@Override
			public void ancestorResized(final HierarchyEvent e) {
				prizpusob();
			}
		});
		obnov(StavZdroju.PRAZDNY);
	}

	public void setOvladani(final OvladaniZdroju ovladani) {
		this.ovladani = ovladani;
		uplna.setOvladani(ovladani);
		for (final JTabulkaZdroju t : detaily.values()) {
			t.setOvladani(ovladani);
		}
	}

	/**
	 * Typy zdrojů, které má uživatel v Nastavení (Umístění souborů); ostatní buňky ani řádky tabulek nejsou. Blok tak mění šířku jen se změnou Nastavení.
	 */
	public void setPovoleneTypy(final Set<TypZdroje> povolene) {
		boolean zmena = false;
		for (final Map.Entry<TypZdroje, JPanel> e : bunky.entrySet()) {
			final boolean videt = povolene.contains(e.getKey());
			zmena |= e.getValue().isVisible() != videt;
			e.getValue().setVisible(videt);
		}
		uplna.setPovoleneTypy(povolene);
		if (zmena) {
			zavriSeznam();
			revalidate();
			repaint();
		}
	}

	/** Typy, jejichž datová složka je v Nastavení zapnutá. */
	public static Set<TypZdroje> povoleneTypy(final KesoidUmisteniSouboru u) {
		final Set<TypZdroje> povolene = EnumSet.noneOf(TypZdroje.class);
		if (u == null) {
			return EnumSet.allOf(TypZdroje.class);
		}
		pridejKdyzAktivni(povolene, TypZdroje.GPX, u.getKesDir());
		pridejKdyzAktivni(povolene, TypZdroje.GEOGET, u.getGeogetDataDir());
		pridejKdyzAktivni(povolene, TypZdroje.GSAK, u.getGsakDataDir());
		pridejKdyzAktivni(povolene, TypZdroje.OPENSAK, u.getOpensakDataDir());
		return povolene;
	}

	private static void pridejKdyzAktivni(final Set<TypZdroje> povolene, final TypZdroje typ, final Filex slozka) {
		if (slozka != null && slozka.isActive()) {
			povolene.add(typ);
		}
	}

	public void setCasDat(final String cas) {
		uplna.setCasDat(cas);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		SwingUtilities.invokeLater(this::prizpusob);
	}

	@Override
	public void removeNotify() {
		hlidaniZavreni.stop();
		zavriSeznam();
		super.removeNotify();
	}

	/** Bublina celá nad blokem: u spodního okraje obrazovky by jinak skončila pod kurzorem a zachytila klik. */
	Point bublinaNadBlokem(final JComponent c, final MouseEvent e) {
		final String text = c.getToolTipText(e);
		if (text == null) {
			return null;
		}
		final JToolTip tip = c.createToolTip();
		tip.setTipText(text);
		final int vBloku = SwingUtilities.convertPoint(c, 0, 0, this).y;
		return new Point(e.getX(), -vBloku - tip.getPreferredSize().height - 2);
	}

	/** Komponenta zabere celou výšku bloku, aby plocha pro najetí a klik byla dost velká. */
	private static void natahni(final JComponent c) {
		final Dimension d = c.getPreferredSize();
		c.setMaximumSize(new Dimension(d.width, Integer.MAX_VALUE));
		c.setAlignmentY(CENTER_ALIGNMENT);
	}

	@Override
	public Dimension getPreferredSize() {
		final Dimension d = super.getPreferredSize();
		final Insets ins = getInsets();
		return new Dimension(d.width, Math.max(d.height, VYSKA + ins.top + ins.bottom));
	}

	@Override
	public Dimension getMaximumSize() {
		return getPreferredSize();
	}

	private int sirkaOkna() {
		final Window okno = SwingUtilities.getWindowAncestor(this);
		return okno != null ? okno.getWidth() : Integer.MAX_VALUE;
	}

	/** V úzkém okně jen zaškrtávátko a ikona, popupy se zúží. */
	void prizpusob() {
		final int sirka = sirkaOkna();
		final boolean kompaktni = sirka < PRAH_KOMPAKTNI;
		boolean zmena = false;
		for (final JLabel n : nazvy.values()) {
			zmena |= n.isVisible() == kompaktni;
			n.setVisible(!kompaktni);
		}
		uplna.nastavDostupnouSirku(sirka);
		for (final JTabulkaZdroju t : detaily.values()) {
			t.nastavDostupnouSirku(sirka);
		}
		if (zmena) {
			revalidate();
			repaint();
		}
	}

	boolean isKompaktni() {
		return !nazvy.get(TypZdroje.GPX).isVisible();
	}

	private static JComponent obal(final JComponent obsah) {
		final JPanel obal = new JPanel(new BorderLayout());
		obal.setBorder(BorderFactory.createLineBorder(new Color(0x8AA0C0)));
		obal.add(obsah);
		return obal;
	}

	/** Mění se jen ikony, texty bublin a barva názvu; velikost buněk zůstává, aby se nic v řádku neposouvalo. */
	public void obnov(final StavZdroju novy) {
		stav = novy;
		for (final TypZdroje typ : TypZdroje.values()) {
			final StavZdroje stavTypu = stav.getStavTypu(typ);
			final StavVyberu volba = stav.getStavVyberuTypu(typ);
			final boolean zapnuto = stavTypu != StavZdroje.VYPNUTO;
			final JCheckBox z = zaskrtavatka.get(typ);
			z.setIcon(IkonyZdroju.zaskrtavatko(volba));
			z.setSelectedIcon(IkonyZdroju.zaskrtavatko(volba));
			z.setToolTipText(tooltipZaskrtavatka(typ, volba));
			final JLabel ikona = ikony.get(typ);
			ikona.setIcon(zapnuto ? IkonyZdroju.pro(stavTypu) : IkonyZdroju.prazdna());
			ikona.setToolTipText(tooltipIkony(stav, typ));
			nazvy.get(typ).setForeground(zapnuto ? UIManager.getColor("Label.foreground") : Color.GRAY);
		}
		uplna.obnov(stav);
		for (final JTabulkaZdroju t : detaily.values()) {
			t.obnov(stav);
		}
		final JComponent viditelny = viditelny();
		if (viditelny != null && !viditelny.getSize().equals(viditelny.getPreferredSize())) {
			ukaz(viditelny, viditelny == souhrn ? null : kotvaTypu(viditelny), true);
		}
	}

	static String tooltipZaskrtavatka(final TypZdroje typ, final StavVyberu volba) {
		switch (volba) {
		case VYPNUTO:
			return typ.getNazev() + " zapnout";
		case CASTECNE:
			return typ.getNazev() + ": načítá se jen část položek, kliknutím vypnete";
		default:
			return typ.getNazev() + " vypnout";
		}
	}

	/** Stav typu; u zámku a chyby i dotčené položky. */
	static String tooltipIkony(final StavZdroju stav, final TypZdroje typ) {
		final StavZdroje s = stav.getStavTypu(typ);
		if (s == StavZdroje.VYPNUTO) {
			return null;
		}
		final StringBuilder sb = new StringBuilder("<html>").append(FString.html(typ.getNazev() + ": " + JTabulkaZdroju.textStavu(s, postup(stav, typ))));
		if (s == StavZdroje.CEKA_NA_ZAPIS || s == StavZdroje.CHYBA) {
			for (final StavPolozky p : stav.getPolozky(typ)) {
				if (p.isNacitat() && p.getStav() == s) {
					sb.append("<br>").append(FString.html(p.getNazev() + (p.getChyba() != null ? ": " + p.getChyba() : "")));
				}
			}
			if (s == StavZdroje.CEKA_NA_ZAPIS) {
				sb.append("<br>").append(FString.html(JTabulkaZdroju.RADA_ZAMCENO));
			}
		}
		return sb.toString();
	}

	private static int postup(final StavZdroju stav, final TypZdroje typ) {
		int soucet = 0;
		int pocet = 0;
		for (final StavPolozky p : stav.getPolozky(typ)) {
			if (p.isNacitat()) {
				soucet += p.getStav() == StavZdroje.NACITA_SE ? p.getPostup() : p.getStav() == StavZdroje.CEKA_NA_RADU ? 0 : 100;
				pocet++;
			}
		}
		return pocet == 0 ? 0 : soucet / pocet;
	}

	private Component kotvaTypu(final JComponent obsah) {
		for (final Map.Entry<TypZdroje, JComponent> e : popupyTypu.entrySet()) {
			if (e.getValue() == obsah) {
				return zaskrtavatka.get(e.getKey());
			}
		}
		return null;
	}

	/** Zobrazí tabulku všech zdrojů. */
	public void ukazSouhrn() {
		ukaz(souhrn, null, false);
	}

	/** Zobrazí jen zdroje jednoho typu. */
	public void ukazTyp(final TypZdroje typ) {
		ukaz(popupyTypu.get(typ), zaskrtavatka.get(typ), false);
	}

	/** Úplná tabulka je zarovnaná k pravému okraji bloku, detail k buňce typu; vždy těsně nad blokem a uvnitř obrazovky. */
	private void ukaz(final JComponent obsah, final Component kotva, final boolean znovu) {
		if (!isShowing() || obsahPopupu == obsah && !znovu) {
			return;
		}
		zavriSeznam();
		prizpusob();
		obsah.setSize(obsah.getPreferredSize());
		final Point p = polohaPopupu(obsah.getPreferredSize(), kotva);
		SwingUtilities.convertPointToScreen(p, this);
		popup = PopupFactory.getSharedInstance().getPopup(this, obsah, p.x, p.y);
		obsahPopupu = obsah;
		popup.show();
		mimoOd = -1;
		hlidaniZavreni.start();
	}

	Point polohaPopupu(final Dimension d, final Component kotva) {
		Rectangle obrazovka = getGraphicsConfiguration() != null ? getGraphicsConfiguration().getBounds() : new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
		final Window okno = SwingUtilities.getWindowAncestor(this);
		if (okno != null && okno.isShowing()) {
			// Do okna, pokud se tam vejde; jinak aspoň na obrazovku.
			final Rectangle vOkne = obrazovka.intersection(new Rectangle(okno.getLocationOnScreen(), okno.getSize()));
			if (vOkne.width >= d.width) {
				obrazovka = new Rectangle(vOkne.x, obrazovka.y, vOkne.width, obrazovka.height);
			}
		}
		final Point blok = getLocationOnScreen();
		int x = kotva == null ? getWidth() - d.width : SwingUtilities.convertPoint(kotva.getParent(), kotva.getLocation(), this).x;
		x = Math.max(obrazovka.x - blok.x, Math.min(x, obrazovka.x + obrazovka.width - blok.x - d.width));
		final int y = Math.max(obrazovka.y - blok.y, -d.height);
		return new Point(x, y);
	}

	public void zavriSeznam() {
		if (popup != null) {
			popup.hide();
			popup = null;
			obsahPopupu = null;
		}
	}

	/** Obsah zobrazeného popupu, nebo null. */
	JComponent viditelny() {
		return obsahPopupu;
	}

	JComponent getObsahSouhrnu() {
		return souhrn;
	}

	JComponent getObsahDetailu(final TypZdroje typ) {
		return popupyTypu.get(typ);
	}

	/** Myš vjela na popisek či název: popup se otevře nebo přepne, až na něm vydrží. */
	void naCil(final JComponent obsah, final Runnable ukazani, final long ted) {
		if (obsahPopupu == obsah) {
			cekajici = null;
			return;
		}
		cekajici = obsah;
		cekajiciUkazani = ukazani;
		cekaOd = ted;
		hlidaniZavreni.start();
	}

	void opustenCil(final JComponent obsah) {
		if (cekajici == obsah) {
			cekajici = null;
		}
	}

	private void tik() {
		final PointerInfo info = MouseInfo.getPointerInfo();
		if (info != null) {
			tik(info.getLocation(), System.currentTimeMillis());
		}
		if (popup == null && cekajici == null) {
			hlidaniZavreni.stop();
		}
	}

	/** Otevře čekající popup, když na cíli myš vydržela, a zavře otevřený, když je myš mimo blok i popup aspoň {@link #PRODLEVA_ZAVRENI_MS}. */
	void tik(final Point mys, final long ted) {
		if (cekajici != null && ted - cekaOd >= (popup == null ? PRODLEVA_OTEVRENI_MS : PRODLEVA_PREPNUTI_MS)) {
			final Runnable ukazani = cekajiciUkazani;
			cekajici = null;
			ukazani.run();
		}
		if (obsahPopupu == null || obsahuje(this, mys) || obsahuje(obsahPopupu, mys)) {
			mimoOd = -1;
			return;
		}
		if (mimoOd < 0) {
			mimoOd = ted;
		} else if (ted - mimoOd >= PRODLEVA_ZAVRENI_MS) {
			zavriSeznam();
			cekajici = null;
		}
	}

	private static boolean obsahuje(final Component c, final Point obrazovka) {
		return c.isShowing() && new Rectangle(c.getLocationOnScreen(), c.getSize()).contains(obrazovka);
	}

	/** Co plocha bloku dělá. */
	enum Druh {
		UPLNA_TABULKA, PREPINA, DETAIL, TOOLTIP
	}

	/** Plocha bloku, která reaguje na najetí nebo klik; souřadnice v bloku. */
	static final class Oblast {
		final Rectangle plocha;
		final Druh druh;
		final TypZdroje typ;

		Oblast(final Rectangle plocha, final Druh druh, final TypZdroje typ) {
			this.plocha = plocha;
			this.druh = druh;
			this.typ = typ;
		}
	}

	/** Plochy, ve kterých blok reaguje; mezery mezi nimi nereagují. */
	List<Oblast> oblasti() {
		final List<Oblast> oblasti = new ArrayList<>();
		oblasti.add(new Oblast(plocha(popisek), Druh.UPLNA_TABULKA, null));
		for (final TypZdroje typ : TypZdroje.values()) {
			if (!bunky.get(typ).isVisible()) {
				continue;
			}
			oblasti.add(new Oblast(plocha(zaskrtavatka.get(typ)), Druh.PREPINA, typ));
			final boolean bezNazvu = !nazvy.get(typ).isVisible();
			oblasti.add(new Oblast(plocha(ikony.get(typ)), bezNazvu ? Druh.DETAIL : Druh.TOOLTIP, typ));
			if (!bezNazvu) {
				oblasti.add(new Oblast(plocha(nazvy.get(typ)), Druh.DETAIL, typ));
			}
		}
		return oblasti;
	}

	private Rectangle plocha(final Component c) {
		return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this);
	}

	JPanel getBunka(final TypZdroje typ) {
		return bunky.get(typ);
	}

	JCheckBox getZaskrtavatko(final TypZdroje typ) {
		return zaskrtavatka.get(typ);
	}

	JLabel getIkona(final TypZdroje typ) {
		return ikony.get(typ);
	}

	JLabel getNazev(final TypZdroje typ) {
		return nazvy.get(typ);
	}

	JLabel getPopisek() {
		return popisek;
	}

	JTabulkaZdroju getUplna() {
		return uplna;
	}

	JTabulkaZdroju getDetail(final TypZdroje typ) {
		return detaily.get(typ);
	}
}
