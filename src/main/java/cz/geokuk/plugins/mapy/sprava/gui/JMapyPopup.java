package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.LongSupplier;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.PokrytiMap;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import cz.geokuk.util.process.BrowserOpener;

/**
 * Obsah popupu Mapy ve stavovém řádku: mapy ve složce offline map, mapy pro zobrazené místo a další mapy ke stažení. Každý řádek má ikonu stavu, krátký popis
 * a nejvýš jedno tlačítko; podrobnosti jsou v tooltipu.
 */
public class JMapyPopup extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String NADPIS = "Offline mapy";
	static final String VE_SLOZCE = "Ve složce";
	static final String ZADNA_MAPA = "Zatím tu není žádná offline mapa.";
	static final String PRO_MISTO = "Pro zobrazené místo";
	static final String DALSI = "Další mapy ke stažení";
	static final String OBRYS = "Ukázat v mapě, kde offline mapy jsou";
	static final String STINOVANI = "Stínovat terén";
	static final String WEB = "Další offline mapy:";
	static final String STAHNOUT = "Stáhnout";
	static final String SMAZAT_VRSTVU = "×";
	static final String SLOUPEC_MAPA = "Mapa";
	static final String JEN_REGIONY = "jen regiony";
	static final String SLOUPEC_TRASY = "Trasy";
	static final String SLOUPEC_VRSTEVNICE = "Vrstevnice";
	static final String SLOUPEC_STINOVANI = "Stínování";
	static final String NENI_VRSTVA = "—";
	static final String SMAZAT_REGIONY = "Smazat regiony";
	static final String SMAZAT_CELEK = "Smazat celou zemi";
	static final String POKRACOVAT = "Pokračovat";
	static final String AKTUALIZOVAT = "Aktualizovat";
	static final String STAHNOUT_ZNOVU = "Stáhnout znovu";
	static final String ZKUSIT_ZNOVU = "Zkusit znovu";
	static final String ZRUSIT = "Zrušit";
	static final String ZKONTROLOVAT = "Zkontrolovat aktualizace";
	static final String ZKONTROLOVAT_TIP = "Zjistí na serveru, jestli jsou pro stažené mapy novější data.";
	static final String OTEVRIT_SLOZKU = "Otevřít složku";
	static final String BEHEM_STAHOVANI_NEJDE_MAZAT = "Během stahování nejde mazat.";
	static final String STAHUJE_SE_JINA = "Najednou se stahuje jen jedna mapa.";
	static final String VSE_AKTUALNI = "Vše aktuální";
	static final String KONTROLA_SELHALA = "Kontrola se nepovedla";

	static final String URL_PAWS = "https://osm.paws.cz/";
	static final String URL_OAM = "https://www.openandromaps.org/";
	static final String ZDROJ = "Mapy ke stažení:";
	static final String URL_MAPSFORGE = "https://github.com/mapsforge/mapsforge";
	static final String URL_ODBL = "https://opendatacommons.org/licenses/odbl/";
	static final String URL_OSM = "https://www.openstreetmap.org/copyright";
	static final String RUSI_SE = "Ruší se…";

	/** Šířka sloupce vrstvy (trasy, vrstevnice, stínování), ve všech oddílech stejná, aby byly buňky pod sebou. */
	static final int SIRKA_BUNKY = 112;

	/** Šířka sloupce s akcí mapy (Stáhnout, Zrušit…), ve všech oddílech stejná. */
	static final String STAHNOUT_VSE = "Stáhnout vše";
	static final int SIRKA_AKCE = 88;

	/** Delší popis (třeba text chyby) se zkrátí, celý je v tooltipu. */
	static final int SIRKA_POPISU = 176;

	/** Nejvyšší výška seznamu dalších map, pak se posouvá. */
	static final int VYSKA_SEZNAMU = 340;

	/** Od kolika dalších map se nabídne hledání. */
	static final int HLEDANI_OD = 8;
	static final String HLEDAT = "Hledat v dalších mapách";
	static final String NIC_NENALEZENO = "Nic nenalezeno.";

	private static final Color SEDY_TEXT = new Color(90, 90, 90);
	private static final Color ODKAZ = new Color(0x1A5FB4);

	private transient OvladaniMap ovladani;
	/** Klíč řádku, jehož obrys je teď v mapě zvýrazněný; null, když žádný. */
	private String zvyraznena;
	private transient List<RadekMapy> radky = Collections.emptyList();
	/** Ikona, název a popis podle id řádku, aby šly při stahování obnovit bez přestavění popupu. */
	private final Map<String, JLabel[]> popisy = new HashMap<>();
	/** Procenta právě stahované vrstvy podle řádku a sloupce, aby šla při stahování přepsat bez přestavění popupu. */
	private final Map<String, JLabel> postupVrstev = new HashMap<>();
	private final JCheckBox obrys = new JCheckBox(OBRYS);
	private final JCheckBox stinovani = new JCheckBox(STINOVANI);
	private boolean stinovaniDostupne;
	private JScrollPane seznam;
	/** Hledání mezi dalšími mapami; filtruje se skrytím řádků, popup se nepřestavuje, aby hledání nepřišlo o fokus. */
	private final JTextField hledat = new JTextField(14) {
		private static final long serialVersionUID = 1L;

		@Override
		protected void paintComponent(final java.awt.Graphics g) {
			super.paintComponent(g);
			if (getText().isEmpty() && !hasFocus()) {
				g.setColor(Color.GRAY);
				g.setFont(getFont().deriveFont(Font.ITALIC));
				g.drawString(HLEDAT + "…", getInsets().left + 2, (getHeight() + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
			}
		}
	};
	/** Řádky dalších map: jejich políčka a text, podle kterého se hledá. */
	private final transient List<RadekHledani> hledatelne = new ArrayList<>();
	/** Uživatel píše do pole hledání; nastaví se fokusem v poli, ruší klikem mimo popup a zavřením popupu. */
	private boolean uzivatelPise;

	/** Pole hledání o fokus přišlo při změně obsahu a žádá se o jeho vrácení; jen v tomto okně se klávesy berou do pole. */
	private boolean cekaNaFokus;

	/** Správce fokusu, u kterého je dispatcher kláves zaregistrovaný; v testech nahraditelný. */
	transient java.util.function.Supplier<KeyboardFocusManager> spravceFokusu = KeyboardFocusManager::getCurrentKeyboardFocusManager;

	/** Okno aplikace, které má právě fokus; v testech nahraditelné. */
	transient java.util.function.Supplier<Window> aktivniOkno = () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
	/** Kolikrát se po znovuzobrazení popupu žádal fokus do pole (pro test). */
	int pozadavkuNaFokus;
	/** Země s regiony v seznamu dalších map. */
	private final transient List<Uzel> uzly = new ArrayList<>();
	/** Země, které má uživatel rozbalené; přežije přestavbu popupu. */
	private final Set<String> rozbalene = new HashSet<>();
	private JLabel nicNenalezeno;
	private Runnable poZmeneObsahu = () -> {};
	/** Mapa, jejíž stahování uživatel zrušil a čeká se na potvrzení; null, když nic. */
	private String rusenaMapa;
	private final transient OdhadStahovani odhad = new OdhadStahovani();
	/** Čas pro odhad stahování; v testech nahrazený. */
	transient LongSupplier hodiny = () -> System.nanoTime() / 1_000_000;
	/** Volné místo na disku se složkou offline map v bajtech, -1 neznámé. */
	private long volneMisto = -1;
	/** Celé země, které jsou ve složce zároveň se svými regiony. */
	private transient List<PokrytiMap.DvojitaMapa> dvojite = Collections.emptyList();

	public JMapyPopup() {
		super(new GridBagLayout());
		setBorder(new CompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(6, 8, 6, 8)));
		obrys.setFont(obrys.getFont().deriveFont(Font.PLAIN, 11f));
		obrys.setFocusable(false);
		stinovani.setFont(stinovani.getFont().deriveFont(Font.PLAIN, 11f));
		stinovani.setFocusable(false);
		stinovani.addActionListener(e -> {
			if (ovladani != null) {
				ovladani.setStinovani(stinovani.isSelected());
			}
		});
		obrys.addActionListener(e -> {
			if (ovladani != null) {
				ovladani.setObrysPokryti(obrys.isSelected());
			}
		});
		addHierarchyListener(e -> {
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
				return;
			}
			if (isShowing()) {
				// Těžký popup (přesahuje okno) nepřijme fokus, písmena by šla do hlavního okna a spouštěla jeho zkratky.
				final Window okno = SwingUtilities.getWindowAncestor(this);
				hledat.setVisible(okno == null || okno instanceof Frame || okno instanceof Dialog);
				vidiSe = true;
				zacniSledovatKlikyMimo();
			} else {
				zvyrazni(null);
				vidiSe = false;
				prestanSledovatKlikyMimo();
			}
		});
		hledat.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusGained(final java.awt.event.FocusEvent e) {
				uzivatelPise = true;
				cekaNaFokus = false;
			}
		});
		hledat.setFont(hledat.getFont().deriveFont(Font.PLAIN, 11f));
		hledat.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(final DocumentEvent e) {
				filtruj();
			}

			@Override
			public void removeUpdate(final DocumentEvent e) {
				filtruj();
			}

			@Override
			public void changedUpdate(final DocumentEvent e) {
				filtruj();
			}
		});
		sestav();
	}

	/** Popup se zavřel: hledání se vymaže, příště začne od nuly. */
	public void popupZavren() {
		uzivatelPise = false;
		cekaNaFokus = false;
		hledat.setText("");
	}

	/** Uživatel píše do pole hledání (pole dostalo fokus a nekliklo se mimo popup); popup se nesmí zavřít odjetím myši. */
	public boolean isHledaniAktivni() {
		return uzivatelPise;
	}

	/** Obsah se změnil: popup se znovu zobrazí a píše-li uživatel, vrátí se mu fokus do pole. */
	private void zmenaObsahu() {
		poZmeneObsahu.run();
		poZnovuZobrazeni();
	}

	/** Popup se zobrazil znovu (změna velikosti): fokus zmizel spolu s odebraným popupem, píše-li uživatel, vrátí se do pole. */
	public void poZnovuZobrazeni() {
		if (uzivatelPise) {
			pozadavkuNaFokus++;
			cekaNaFokus = true;
			SwingUtilities.invokeLater(hledat::requestFocusInWindow);
		}
	}

	/** Klik mimo popup ukončí psaní: pole přijde o fokus a popup se pak zavře odjetím myši jako jindy. */
	private final java.awt.event.AWTEventListener klikMimo = e -> {
		if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED && uzivatelPise && !(e.getSource() instanceof Component && SwingUtilities.isDescendingFrom((Component) e.getSource(), this))) {
			uzivatelPise = false;
			cekaNaFokus = false;
			java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().clearFocusOwner();
		}
	};

	/** Popup je vidět; nastavuje ho sledování viditelnosti (v testech i ručně). */
	boolean vidiSe;

	/**
	 * Píše-li uživatel do popupu a fokus je zrovna jinde (popup se při změně obsahu na okamžik zobrazí znovu a pole o fokus přijde), psané znaky se nenechají
	 * propadnout do hlavního okna, kde by spouštěly klávesové zkratky (o, t, z… přepínají mapu), ale vloží se do pole hledání. Ctrl/Alt/Meta, Enter a Esc jdou dál.
	 */
	final KeyEventDispatcher klavesy = e -> {
		if (!cekaNaFokus || !uzivatelPise || !vidiSe || !hledat.isVisible() || hledat.isFocusOwner() || e.isControlDown() || e.isAltDown() || e.isMetaDown()) {
			return false;
		}
		final Window okno = SwingUtilities.getWindowAncestor(this);
		if (okno != null && aktivniOkno.get() != okno) {
			return false;
		}
		final char c = e.getKeyChar();
		if (c == KeyEvent.CHAR_UNDEFINED || c == '\u001b' || c == '\n' || c == '\r' || c == '\t' || c == '\u007f') {
			return false;
		}
		if (e.getID() == KeyEvent.KEY_TYPED) {
			final javax.swing.text.Document d = hledat.getDocument();
			try {
				if (c == '\b') {
					if (d.getLength() > 0) {
						d.remove(d.getLength() - 1, 1);
					}
				} else if (c >= ' ') {
					d.insertString(d.getLength(), String.valueOf(c), null);
				}
			} catch (final javax.swing.text.BadLocationException ex) {
				throw new IllegalStateException(ex);
			}
		}
		return true;
	};

	void zacniSledovatKlikyMimo() {
		Toolkit.getDefaultToolkit().removeAWTEventListener(klikMimo);
		Toolkit.getDefaultToolkit().addAWTEventListener(klikMimo, AWTEvent.MOUSE_EVENT_MASK);
		final KeyboardFocusManager m = spravceFokusu.get();
		m.removeKeyEventDispatcher(klavesy);
		m.addKeyEventDispatcher(klavesy);
	}

	void prestanSledovatKlikyMimo() {
		Toolkit.getDefaultToolkit().removeAWTEventListener(klikMimo);
		spravceFokusu.get().removeKeyEventDispatcher(klavesy);
	}

	JTextField getHledat() {
		return hledat;
	}

	/** Políčka jednoho řádku seznamu dalších map a text, podle kterého se hledá; řádek země s regiony je hlavička uzlu. */
	private static final class RadekHledani {
		final String text;
		/** Země s regiony, ke které řádek patří; null u samostatné mapy. */
		final String uzel;
		final boolean hlavicka;
		final List<Component> bunky;

		RadekHledani(final String text, final String uzel, final boolean hlavicka, final List<Component> bunky) {
			this.text = text;
			this.uzel = uzel;
			this.hlavicka = hlavicka;
			this.bunky = bunky;
		}
	}

	/** Země s regiony ve stromu dalších map: vlastní mapa celé země (může chybět) a regiony. */
	private static final class Uzel {
		final String nazev;
		RadekMapy celek;
		final List<RadekMapy> regiony = new ArrayList<>();
		/** Popisek hlavičky, na kterém je šipka rozbalení. */
		JLabel hlavicka;

		Uzel(final String nazev) {
			this.nazev = nazev;
		}

		/** Celek (když je) a regiony. */
		List<RadekMapy> vsechnyRadky() {
			final List<RadekMapy> v = new ArrayList<>();
			if (celek != null) {
				v.add(celek);
			}
			v.addAll(regiony);
			return v;
		}
	}

	/** Šipka rozbalení: trojúhelník doprava, rozbalený dolů. */
	private static final class Sipka implements Icon {
		static final int VELIKOST = 9;
		private final boolean rozbaleno;

		Sipka(final boolean rozbaleno) {
			this.rozbaleno = rozbaleno;
		}

		@Override
		public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
			final Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(SEDY_TEXT);
				final int[] px = rozbaleno ? new int[] { x, x + VELIKOST, x + VELIKOST / 2 } : new int[] { x + 2, x + 2, x + VELIKOST - 1 };
				final int[] py = rozbaleno ? new int[] { y + 2, y + 2, y + VELIKOST - 1 } : new int[] { y, y + VELIKOST, y + VELIKOST / 2 };
				g2.fillPolygon(px, py, 3);
			} finally {
				g2.dispose();
			}
		}

		@Override
		public int getIconWidth() {
			return VELIKOST;
		}

		@Override
		public int getIconHeight() {
			return VELIKOST;
		}
	}

	static String bezDiakritiky(final String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	/** Každé slovo hledaného textu je v textu řádku (v libovolném pořadí). */
	static boolean obsahujeVse(final String text, final String hledany) {
		for (final String slovo : hledany.split("\\s+")) {
			if (!slovo.isEmpty() && !text.contains(slovo)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Ukáže jen další mapy, jejichž název nebo země obsahuje hledaný text (bez ohledu na diakritiku a velikost písmen). Regiony země jsou sbalené, dokud je
	 * uživatel nerozbalí nebo nehledá; hledání rozbalí země s nalezenou mapou.
	 */
	void filtruj() {
		filtruj(bezDiakritiky(hledat.getText().trim()));
	}

	private void filtruj(final String hledany) {
		final Set<String> uzlySeShodou = new HashSet<>();
		for (final RadekHledani r : hledatelne) {
			if (r.uzel != null && !r.hlavicka && obsahujeVse(r.text, hledany)) {
				uzlySeShodou.add(r.uzel);
			}
		}
		int viditelnych = 0;
		for (final RadekHledani r : hledatelne) {
			final boolean ano;
			if (r.hlavicka) {
				ano = hledany.isEmpty() || obsahujeVse(r.text, hledany) || uzlySeShodou.contains(r.uzel);
			} else if (r.uzel != null) {
				ano = hledany.isEmpty() ? rozbalene.contains(r.uzel) : obsahujeVse(r.text, hledany);
			} else {
				ano = obsahujeVse(r.text, hledany);
			}
			viditelnych += ano ? 1 : 0;
			for (final Component c : r.bunky) {
				c.setVisible(ano);
			}
		}
		for (final Uzel u : uzly) {
			u.hlavicka.setIcon(new Sipka(hledany.isEmpty() ? rozbalene.contains(u.nazev) : uzlySeShodou.contains(u.nazev)));
		}
		if (nicNenalezeno != null) {
			nicNenalezeno.setVisible(viditelnych == 0);
		}
		if (seznam != null) {
			seznam.revalidate();
			seznam.repaint();
		}
	}

	/** Klik na zemi s regiony rozbalí nebo sbalí její regiony. */
	void prepniUzel(final String nazev) {
		if (!rozbalene.remove(nazev)) {
			rozbalene.add(nazev);
		}
		filtruj();
	}

	private void zvyrazni(final String klic) {
		if (Objects.equals(klic, zvyraznena)) {
			return;
		}
		zvyraznena = klic;
		if (ovladani != null) {
			ovladani.zvyrazniMapu(klic);
		}
	}

	public void setOvladani(final OvladaniMap ovladani) {
		this.ovladani = ovladani;
		obrys.setSelected(ovladani != null && ovladani.isObrysPokryti());
		stinovani.setSelected(ovladani == null || ovladani.isStinovani());
		sestav();
	}

	public void setObrysZapnuty(final boolean zapnuty) {
		obrys.setSelected(zapnuty);
	}

	/** Ve složce vrstev jsou DEM dlaždice: popup nabídne přepínač stínování terénu. */
	public void setStinovaniDostupne(final boolean dostupne) {
		if (dostupne != stinovaniDostupne) {
			stinovaniDostupne = dostupne;
			stinovani.setSelected(ovladani == null || ovladani.isStinovani());
			sestav();
			poZmeneObsahu.run();
		}
	}

	/** Zavolá se, když se změnila velikost obsahu a popup se má zobrazit znovu. */
	public void setPoZmeneObsahu(final Runnable poZmeneObsahu) {
		this.poZmeneObsahu = poZmeneObsahu;
	}

	/** Nové řádky; při stahování se jen přepíší popisy, jinak se popup sestaví znovu. */
	public void obnov(final List<RadekMapy> nove) {
		odhad.zapomenKrome(stahovane(nove));
		final boolean stejnaStruktura = struktura(nove).equals(struktura(radky));
		radky = new ArrayList<>(nove);
		if (stejnaStruktura) {
			boolean sirsi = false;
			for (final RadekMapy r : radky) {
				final JLabel[] bunky = popisy.get(klic(r));
				if (bunky == null) {
					continue;
				}
				final JLabel popis = bunky[2];
				final int predtim = popis.getPreferredSize().width;
				popis.setText(r.id != null && r.id.equals(rusenaMapa) ? RUSI_SE : zkrat(popisRadku(r), popis.getFontMetrics(popis.getFont())));
				sirsi |= popis.getPreferredSize().width > predtim;
				final String tooltip = r.tooltip();
				for (final JLabel b : bunky) {
					b.setToolTipText(tooltip);
				}
				for (final MatriceMap.Cast c : VRSTVY) {
					final JLabel postup = postupVrstev.get(klic(r) + "#" + c);
					final MatriceMap.Bunka vrstva = r.bunka(c);
					if (postup != null && vrstva != null) {
						postup.setText(procenta(vrstva));
					}
				}
			}
			if (sirsi) {
				revalidate();
				zmenaObsahu();
			}
			return;
		}
		rusenaMapa = null;
		final int posun = seznam == null ? 0 : seznam.getVerticalScrollBar().getValue();
		sestav();
		if (seznam != null) {
			seznam.getVerticalScrollBar().setValue(posun);
		}
		zmenaObsahu();
	}

	List<RadekMapy> getRadky() {
		return radky;
	}

	private List<String> struktura(final List<RadekMapy> radky) {
		final List<String> s = new ArrayList<>();
		for (final RadekMapy r : radky) {
			s.add(klic(r) + "|" + r.stav + "|" + r.proZobrazeneMisto + "|" + r.rozpracovano + "|" + r.kontroluje + "|" + r.overuje + "|" + (r.chyba != null) + "|" + r.zkontrolovano + "|" + nedostatekMista(r) + "|" + strukturaVrstev(r));
		}
		return s;
	}

	private String strukturaVrstev(final RadekMapy r) {
		final StringBuilder sb = new StringBuilder();
		for (final MatriceMap.Cast c : VRSTVY) {
			final MatriceMap.Bunka b = r.bunka(c);
			if (b != null) {
				sb.append(b.getStav()).append(b.getChyba() != null).append(nedostatekMista(b)).append(',');
			}
		}
		return sb.toString();
	}

	private static final java.time.format.DateTimeFormatter DATUM_DAT = java.time.format.DateTimeFormatter.ofPattern("d. M. yyyy");

	private static final MatriceMap.Cast[] VRSTVY = { MatriceMap.Cast.TRASY, MatriceMap.Cast.VRSTEVNICE, MatriceMap.Cast.STINOVANI };

	private static String klic(final RadekMapy r) {
		return r.vlastni ? "soubor:" + r.nazev : r.id;
	}

	/** Výsledek poslední kontroly: vše aktuální, počet novějších, nebo že kontrola selhala; s časem; null, když se ještě nekontrolovalo. */
	String vysledekKontroly(final List<RadekMapy> kontrolovane) {
		Instant nejnovejsi = null;
		int novejsich = 0;
		boolean selhala = false;
		for (final RadekMapy r : kontrolovane) {
			if (r.zkontrolovano != null && (nejnovejsi == null || r.zkontrolovano.isAfter(nejnovejsi))) {
				nejnovejsi = r.zkontrolovano;
			}
			novejsich += r.stav == RadekMapy.Stav.NOVEJSI ? 1 : 0;
			selhala |= r.chyba != null;
		}
		if (nejnovejsi == null) {
			return null;
		}
		final String cas = " · zkontrolováno " + cas(nejnovejsi);
		if (selhala) {
			return KONTROLA_SELHALA + cas;
		}
		return (novejsich == 0 ? VSE_AKTUALNI : "Novější data: " + novejsich + " " + (novejsich == 1 ? "mapa" : novejsich < 5 ? "mapy" : "map")) + cas;
	}

	private String cas(final Instant i) {
		final ZonedDateTime t = i.atZone(ZoneId.systemDefault());
		final boolean dnes = t.toLocalDate().equals(LocalDate.now(ZoneId.systemDefault()));
		return t.format(DateTimeFormatter.ofPattern(dnes ? "H:mm" : "d. M. H:mm"));
	}

	private static Set<String> stahovane(final List<RadekMapy> radky) {
		final Set<String> s = new HashSet<>();
		for (final RadekMapy r : radky) {
			if (r.id != null && r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje && !r.overuje) {
				s.add(r.id);
			}
		}
		return s;
	}

	/** Popis řádku; u stahované mapy s odhadem, za jak dlouho se dostáhne. */
	private String popisRadku(final RadekMapy r) {
		final String popis = r.popis();
		if (r.id == null || r.stav != RadekMapy.Stav.STAHUJE || r.kontroluje || r.overuje || r.velikost <= 0) {
			return popis;
		}
		final long sekund = odhad.zbyva(r.id, r.stazeno, r.velikost, hodiny.getAsLong());
		return sekund < 0 ? popis : popis + " · zbývá " + OdhadStahovani.text(sekund);
	}

	/** Země, které jsou ve složce dvakrát (celá i s regiony); popup nabídne smazání jedné z variant. */
	public void setDvojite(final List<PokrytiMap.DvojitaMapa> nove) {
		final List<String> stare = dvojiteKlice(dvojite);
		dvojite = nove == null ? Collections.emptyList() : nove;
		if (!stare.equals(dvojiteKlice(dvojite))) {
			sestav();
			poZmeneObsahu.run();
		}
	}

	private static List<String> dvojiteKlice(final List<PokrytiMap.DvojitaMapa> d) {
		final List<String> k = new ArrayList<>();
		for (final PokrytiMap.DvojitaMapa m : d) {
			k.add(m.getCelek().getId() + m.getRegiony().size());
		}
		return k;
	}

	/** Volné místo na disku se složkou offline map; mapy, které se nevejdou, nejdou stáhnout. */
	public void setVolneMisto(final long bajtu) {
		if (bajtu == volneMisto) {
			return;
		}
		final List<RadekMapy> vsechny = radky;
		final Set<String> pred = bezMista(vsechny);
		volneMisto = bajtu;
		final Set<String> po = bezMista(vsechny);
		if (!po.equals(pred) || !po.isEmpty()) { // tooltip vypnutých tlačítek ukazuje volné místo

			sestav();
			poZmeneObsahu.run();
		}
	}

	private Set<String> bezMista(final List<RadekMapy> vsechny) {
		final Set<String> s = new HashSet<>();
		for (final RadekMapy r : vsechny) {
			if (nedostatekMista(r)) {
				s.add(klic(r));
			}
			for (final MatriceMap.Cast c : VRSTVY) {
				final MatriceMap.Bunka b = r.bunka(c);
				if (b != null && nedostatekMista(b)) {
					s.add(klic(r) + "#" + c);
				}
			}
		}
		return s;
	}

	/** Mapa ke stažení nebo k aktualizaci se na disk nevejde (s toutéž rezervou jako ve stahovači). */
	private boolean nedostatekMista(final RadekMapy r) {
		return volneMisto >= 0 && potrebneMisto(r) > volneMisto;
	}

	private boolean nedostatekMista(final MatriceMap.Bunka b) {
		return volneMisto >= 0 && potrebneMisto(b) > volneMisto;
	}

	/** Místo pro stažení (zbytku) vrstvy včetně rezervy stahovače; 0, když se nic nestahuje. */
	static long potrebneMisto(final MatriceMap.Bunka b) {
		if (b.getVelikost() <= 0) {
			return 0;
		}
		switch (b.getStav()) {
		case KE_STAZENI:
		case CHYBA:
			return Math.max(0, b.getVelikost() - b.getStazeno()) + SpravaMapModel.REZERVA_MISTA;
		case NOVEJSI:
			return b.getVelikost() + SpravaMapModel.REZERVA_MISTA;
		default:
			return 0;
		}
	}

	/** Místo, které stažení (zbytku) mapy potřebuje včetně rezervy stahovače; 0, když se nic nestahuje. */
	static long potrebneMisto(final RadekMapy r) {
		if (r.vlastni || r.velikost <= 0) {
			return 0;
		}
		switch (r.stav) {
		case NEMAME:
		case CHYBA:
			return Math.max(0, r.velikost - r.stazeno) + SpravaMapModel.REZERVA_MISTA;
		case NOVEJSI:
			return r.velikost + SpravaMapModel.REZERVA_MISTA;
		default:
			return 0;
		}
	}

	private int sirkaNazvu;

	private void sestav() {
		zvyrazni(null);
		removeAll();
		popisy.clear();
		postupVrstev.clear();
		hledatelne.clear();
		uzly.clear();
		nicNenalezeno = null;
		final FontMetrics fm = getFontMetrics(new JLabel().getFont());
		sirkaNazvu = 0;
		for (final RadekMapy r : radky) {
			sirkaNazvu = Math.max(sirkaNazvu, fm.stringWidth(r.nazev) + 2);
			if (r.skupina != null) {
				sirkaNazvu = Math.max(sirkaNazvu, fm.stringWidth(r.skupina) + 2 + ODSAZENI);
			}
		}
		if (radky.stream().anyMatch(r -> r.skupina != null)) {
			sirkaNazvu += 2 * ODSAZENI; // šipka a odsazení regionů ve stromu zemí
		}
		final boolean stahujeSe = radky.stream().anyMatch(r -> r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje || r.bezicVrstva() != null);
		final GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;

		final JPanel hlavicka = new JPanel(new BorderLayout(12, 0));
		hlavicka.setOpaque(false);
		final JLabel nadpis = new JLabel(NADPIS);
		nadpis.setFont(nadpis.getFont().deriveFont(Font.BOLD));
		hlavicka.add(nadpis, BorderLayout.WEST);
		hlavicka.add(odkaz(OTEVRIT_SLOZKU, e -> {
			if (ovladani != null) {
				ovladani.otevriSlozku();
			}
		}), BorderLayout.EAST);
		add(hlavicka, gbc);
		if (radky.stream().anyMatch(r -> r.matice != null && !r.vlastni)) {
			gbc.gridy++;
			add(hlavickaSloupcu(), gbc);
		}

		final List<RadekMapy> veSlozce = new ArrayList<>();
		final List<RadekMapy> proMisto = new ArrayList<>();
		final List<RadekMapy> dalsi = new ArrayList<>();
		for (final RadekMapy r : radky) {
			if (r.isVeSlozce()) {
				veSlozce.add(r);
			} else if (r.proZobrazeneMisto || r.stav == RadekMapy.Stav.STAHUJE || r.bezicVrstva() != null) {
				proMisto.add(r);
			} else {
				dalsi.add(r);
			}
		}

		gbc.gridy++;
		final JPanel oddilSlozka = new JPanel(new BorderLayout());
		oddilSlozka.setOpaque(false);
		final boolean slozkaOtevrena = jeSlozkaOtevrena(veSlozce.size()) || veSlozce.stream().anyMatch(JMapyPopup::vyzadujePozornost);
		final JLabel nadpisSlozky = nadpisOddilu(VE_SLOZCE);
		if (!veSlozce.isEmpty()) {
			nadpisSlozky.setIcon(new Sipka(slozkaOtevrena));
			nadpisSlozky.setIconTextGap(4);
			nadpisSlozky.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			nadpisSlozky.setToolTipText(slozkaOtevrena ? "Sbalit seznam map ve složce" : "Rozbalit seznam map ve složce");
			nadpisSlozky.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					prepniSlozku(!slozkaOtevrena);
				}
			});
		}
		oddilSlozka.add(nadpisSlozky, BorderLayout.WEST);
		final List<RadekMapy> kontrolovatelne = new ArrayList<>();
		for (final RadekMapy r : veSlozce) {
			if (!r.vlastni && (r.stav == RadekMapy.Stav.AKTUALNI || r.stav == RadekMapy.Stav.NOVEJSI)) {
				kontrolovatelne.add(r);
			}
		}
		if (!kontrolovatelne.isEmpty()) {
			final JButton zkontrolovat = odkaz(ZKONTROLOVAT, e -> {
				if (ovladani != null) {
					for (final RadekMapy r : kontrolovatelne) {
						ovladani.zkontroluj(r.id);
					}
				}
			});
			zkontrolovat.setToolTipText(ZKONTROLOVAT_TIP);
			zkontrolovat.setEnabled(!stahujeSe);
			oddilSlozka.add(zkontrolovat, BorderLayout.EAST);
		}
		add(oddilSlozka, gbc);
		final String vysledek = vysledekKontroly(kontrolovatelne);
		if (vysledek != null) {
			gbc.gridy++;
			add(maly(vysledek), gbc);
		}
		final JPanel slozka = panelRadku();
		if (veSlozce.isEmpty()) {
			final JLabel nic = maly(ZADNA_MAPA);
			slozka.add(nic, bunka(0, 0, 4));
		}
		int y = 0;
		y = stromSlozky(slozka, y, veSlozce, stahujeSe);
		slozka.setVisible(slozkaOtevrena || veSlozce.isEmpty());
		gbc.gridy++;
		add(slozka, gbc);
		for (final PokrytiMap.DvojitaMapa d : dvojite) {
			final StringBuilder regiony = new StringBuilder();
			for (final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa r : d.getRegiony()) {
				regiony.append(regiony.length() > 0 ? ", " : "").append(r.getNazev());
			}
			final JLabel upozorneni = maly(d.getCelek().getNazev() + " je ve složce celá i s regiony (" + regiony + "), území je dvakrát.");
			upozorneni.setForeground(IkonyMap.CERVENA);
			gbc.gridy++;
			add(upozorneni, gbc);
			final JPanel akceDvojite = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			akceDvojite.setOpaque(false);
			final JButton smazRegiony = odkaz(SMAZAT_REGIONY, e -> smazDvojite(d, true));
			smazRegiony.setEnabled(!stahujeSe);
			akceDvojite.add(smazRegiony);
			if (d.isRegionyPokryvajiCelek()) {
				final JButton smazCelek = odkaz(SMAZAT_CELEK, e -> smazDvojite(d, false));
				smazCelek.setEnabled(!stahujeSe);
				akceDvojite.add(smazCelek);
			}
			gbc.gridy++;
			add(akceDvojite, gbc);
		}

		if (!proMisto.isEmpty()) {
			gbc.gridy++;
			add(nadpisOddilu(PRO_MISTO), gbc);
			final JPanel misto = panelRadku();
			y = 0;
			for (final RadekMapy r : proMisto) {
				y = pridejRadek(misto, y, r, stahujeSe);
			}
			gbc.gridy++;
			add(misto, gbc);
		}

		seznam = null;
		if (!dalsi.isEmpty()) {
			gbc.gridy++;
			add(nadpisOddilu(DALSI), gbc);
			if (dalsi.size() >= HLEDANI_OD) {
				gbc.gridy++;
				add(hledat, gbc);
			}
			final JPanel ostatni = panelRadkuVSeznamu();
			y = 0;
			final java.text.Collator cs = java.text.Collator.getInstance(new Locale("cs"));
			final Map<String, Uzel> podleZeme = new TreeMap<>(cs);
			for (final RadekMapy r : dalsi) {
				if (r.skupina != null) {
					podleZeme.computeIfAbsent(r.skupina, Uzel::new).regiony.add(r);
				} else {
					podleZeme.computeIfAbsent(r.nazev, Uzel::new).celek = r;
				}
			}
			for (final Uzel u : podleZeme.values()) {
				u.regiony.sort((a, b) -> cs.compare(a.nazev, b.nazev));
				if (u.regiony.isEmpty()) {
					y = pridejRadekSeznamu(ostatni, y, u.celek, stahujeSe, ODSAZENI, null, false);
					continue;
				}
				uzly.add(u);
				if (u.celek != null) {
					y = pridejRadekSeznamu(ostatni, y, u.celek, stahujeSe, 0, u, true);
				} else {
					y = radekZemeBezCelku(ostatni, y, u);
				}
				for (final RadekMapy r : u.regiony) {
					y = pridejRadekSeznamu(ostatni, y, r, stahujeSe, 2 * ODSAZENI, u, false);
				}
			}
			nicNenalezeno = maly(NIC_NENALEZENO);
			nicNenalezeno.setVisible(false);
			ostatni.add(nicNenalezeno, bunka(0, y++, 4));
			final JPanel obal = new JPanel(new BorderLayout());
			obal.setOpaque(false);
			obal.add(ostatni, BorderLayout.NORTH);
			// Velikost podle seznamu bez hledání (regiony sbalené), aby se popup při hledání a rozbalování nemusel zavírat a znovu otevírat.
			filtruj("");
			final Dimension vse = obal.getPreferredSize();
			seznam = new JScrollPane(obal, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
				private static final long serialVersionUID = 1L;

				@Override
				public Dimension getPreferredSize() {
					return new Dimension(vse.width + getVerticalScrollBar().getPreferredSize().width, Math.min(vse.height, VYSKA_SEZNAMU));
				}
			};
			seznam.setBorder(BorderFactory.createEmptyBorder());
			seznam.getVerticalScrollBar().setUnitIncrement(16);
			gbc.gridy++;
			add(seznam, gbc);
		}

		if (radky.stream().anyMatch(r -> !r.vlastni)) {
			// Projekt, licence a atribuce map z katalogu.
			gbc.gridy++;
			final JPanel zdroj = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
			zdroj.setOpaque(false);
			zdroj.add(maly(ZDROJ));
			zdroj.add(odkaz("mapsforge", URL_MAPSFORGE));
			zdroj.add(maly("·"));
			zdroj.add(odkaz("licence ODbL", URL_ODBL));
			zdroj.add(maly("·"));
			zdroj.add(odkaz("© přispěvatelé OpenStreetMap", URL_OSM));
			add(zdroj, gbc);
		}

		if (seznam != null) {
			filtruj();
		}
		gbc.gridy++;
		gbc.insets = new Insets(6, 0, 0, 0);
		add(new JSeparator(), gbc);
		gbc.insets = new Insets(2, 0, 0, 0);
		if (ovladani != null && ovladani.isObrysDostupny()) {
			gbc.gridy++;
			add(obrys, gbc);
		}
		if (stinovaniDostupne) {
			gbc.gridy++;
			add(stinovani, gbc);
		}
		gbc.gridy++;
		final JPanel web = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		web.setOpaque(false);
		web.add(maly(WEB));
		web.add(odkaz("osm.paws.cz", URL_PAWS));
		web.add(odkaz("OpenAndroMaps", URL_OAM));
		add(web, gbc);
		if (uzivatelPise) {
			cekaNaFokus = true;
			SwingUtilities.invokeLater(hledat::requestFocusInWindow);
		}
		revalidate();
		repaint();
	}

	/** Klíč řádku pro zvýraznění obrysu mapy: id z katalogu, u souboru uživatele jeho jméno. */
	static String klicZvyrazneni(final RadekMapy r) {
		return r.vlastni ? JObrysMapSlide.VLASTNI + r.nazev : r.id;
	}

	private int pridejRadek(final JPanel panel, final int y, final RadekMapy r, final boolean stahujeSe) {
		radek(panel, y, r, stahujeSe);
		return y + 1;
	}

	/** Záhlaví tabulky nad oddíly: Mapa nad popisem a akcí, pak sloupce vrstev; šířky jako v řádcích. */
	private JPanel hlavickaSloupcu() {
		final JPanel p = new JPanel(new GridBagLayout());
		p.setOpaque(false);
		final int vlevo = IkonyMap.pro(RadekMapy.Stav.NEMAME).getIconWidth() + 4 + sirkaNazvu;
		final GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		c.gridx = 0;
		p.add(Box.createHorizontalStrut(vlevo), c);
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		p.add(hlavickaSloupce(SLOUPEC_MAPA, 8), c);
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.gridx = 2;
		p.add(Box.createHorizontalStrut(SIRKA_AKCE), c);
		int x = 3;
		for (final String nazev : new String[] { SLOUPEC_TRASY, SLOUPEC_VRSTEVNICE, SLOUPEC_STINOVANI }) {
			c.gridx = x++;
			final JLabel l = hlavickaSloupce(nazev, 4);
			l.setPreferredSize(new Dimension(SIRKA_BUNKY, l.getPreferredSize().height));
			p.add(l, c);
		}
		p.setBorder(new CompoundBorder(new EmptyBorder(0, 0, 0, sirkaPosuvniku()), BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 60))));
		return p;
	}

	private static JLabel hlavickaSloupce(final String text, final int zleva) {
		final JLabel l = maly(text);
		l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
		l.setBorder(new EmptyBorder(6, zleva, 2, 0));
		return l;
	}

	/** Šířka svislého posuvníku seznamu dalších map, o kterou se posouvají buňky v seznamu; ostatní oddíly ji odsazují, aby sloupce lícovaly. */
	private static int sirkaPosuvniku() {
		return new JScrollBar(Adjustable.VERTICAL).getPreferredSize().width;
	}

	/** Odsazení názvu o šířku šipky rozbalení, aby se názvy zemí a regionů zarovnaly. */
	private static final int ODSAZENI = Sipka.VELIKOST + 4;

	/** Přidá řádek mapy do seznamu dalších map a zapíše ho pro hledání; vrací další volný řádek mřížky. */
	private int pridejRadekSeznamu(final JPanel panel, final int y, final RadekMapy r, final boolean stahujeSe, final int odsazeni, final Uzel uzel, final boolean hlavicka) {
		final int pred = panel.getComponentCount();
		radek(panel, y, r, stahujeSe);
		final JLabel nazev = popisy.get(klic(r))[1];
		nazev.setBorder(new EmptyBorder(0, odsazeni, 0, 0));
		if (hlavicka && uzel != null) {
			nazev.setBorder(null);
			uzel.hlavicka = nazev;
			nastavRozbaleni(uzel, nazev);
			pridejStahnoutVse(panel, popisy.get(klic(r))[2], uzel, stahujeSe);
		}
		final String zeme = uzel == null ? null : uzel.nazev;
		hledatelne.add(new RadekHledani(bezDiakritiky(r.nazev + " " + (r.skupina == null ? "" : r.skupina)), zeme, hlavicka, new ArrayList<>(Arrays.asList(panel.getComponents()).subList(pred, panel.getComponentCount()))));
		return y + 1;
	}

	/** Země, kterou nejde stáhnout celou, jen po regionech: vypadá jako ostatní země (název s šipkou rozbalení), v buňce Mapa je „jen regiony“. */
	private int radekZemeBezCelku(final JPanel panel, final int y, final Uzel u) {
		return radekZemeBezCelku(panel, y, u, JEN_REGIONY, false, null);
	}

	/** Řádek země bez celku; v oddílu Ve složce s vlastním popisem (souhrn regionů) a vlastním přepínáním, v seznamu dalších map s hledáním. */
	private int radekZemeBezCelku(final JPanel panel, final int y, final Uzel u, final String popisText, final boolean rozbaleno, final Runnable prepni) {
		final int pred = panel.getComponentCount();
		final GridBagConstraints ci = bunka(0, y, 1);
		ci.insets = new Insets(1, 0, 1, 4);
		panel.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.NEMAME)), ci);
		final JLabel nazev = new JLabel(u.nazev);
		nazev.setPreferredSize(new Dimension(sirkaNazvu, nazev.getPreferredSize().height));
		if (prepni == null) {
			u.hlavicka = nazev;
			nastavRozbaleni(u, nazev);
		} else {
			nastavRozbaleni(nazev, rozbaleno, prepni);
		}
		panel.add(nazev, bunka(1, y, 1));
		final JLabel popis = maly(popisText);
		popis.setPreferredSize(new Dimension(SIRKA_POPISU, popis.getPreferredSize().height));
		final GridBagConstraints cp = bunka(2, y, 1);
		cp.weightx = 1;
		cp.insets = new Insets(1, 8, 1, 8);
		panel.add(popis, cp);
		if (prepni == null) {
			pridejStahnoutVse(panel, popis, u, false);
		}
		final GridBagConstraints ca = bunka(3, y, 1);
		ca.fill = GridBagConstraints.NONE;
		panel.add(sloupecAkce(null), ca);
		for (int i = 0; i < VRSTVY.length; i++) {
			final GridBagConstraints cb = bunka(PRVNI_SLOUPEC_VRSTEV + i, y, 1);
			cb.fill = GridBagConstraints.NONE;
			panel.add(prazdnaBunka(), cb);
		}
		if (prepni == null) {
			hledatelne.add(new RadekHledani(bezDiakritiky(u.nazev), u.nazev, true, new ArrayList<>(Arrays.asList(panel.getComponents()).subList(pred, panel.getComponentCount()))));
		}
		return y + 1;
	}

	/** Odkaz „Stáhnout vše“ vedle popisu země, pokud k zemi je co stáhnout nebo aktualizovat. */
	private void pridejStahnoutVse(final JPanel panel, final JLabel popis, final Uzel u, final boolean stahujeSe) {
		final List<String> klice = new ArrayList<>();
		boolean coStahnout = false;
		for (final RadekMapy r : u.vsechnyRadky()) {
			if (r.matice == null || r.vlastni) {
				continue;
			}
			klice.add(r.id);
			for (final MatriceMap.Cast c : MatriceMap.Cast.values()) {
				coStahnout |= r.matice.getBunka(c).jeKeStazeni();
			}
		}
		if (!coStahnout) {
			return;
		}
		final GridBagConstraints cp = ((GridBagLayout) panel.getLayout()).getConstraints(popis);
		final JButton vse = odkaz(STAHNOUT_VSE, e -> {
			if (ovladani != null) {
				ovladani.stahniVse(u.nazev, u.celek == null ? null : u.celek.id, klice);
			}
		});
		vse.setToolTipText(stahujeSe ? STAHUJE_SE_JINA : "Stáhne k zemi mapu, trasy, vrstevnice i stínování, co chybí nebo je zastaralé.");
		vse.setEnabled(!stahujeSe);
		popis.setPreferredSize(new Dimension(Math.max(40, SIRKA_POPISU - vse.getPreferredSize().width), popis.getPreferredSize().height));
		final JPanel obal = new JPanel(new BorderLayout());
		obal.setOpaque(false);
		panel.remove(popis);
		obal.add(popis, BorderLayout.CENTER);
		obal.add(vse, BorderLayout.EAST);
		panel.add(obal, cp);
	}

	/** Popisek země s regiony se chová jako přepínač: šipka a klik rozbalí regiony. */
	private void nastavRozbaleni(final Uzel uzel, final JLabel popisek) {
		nastavRozbaleni(popisek, false, () -> prepniUzel(uzel.nazev));
	}

	private static void nastavRozbaleni(final JLabel popisek, final boolean rozbaleno, final Runnable prepni) {
		popisek.setIcon(new Sipka(rozbaleno));
		popisek.setIconTextGap(4);
		popisek.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		popisek.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				prepni.run();
			}
		});
	}

	/** Od kolika map ve složce je oddíl Ve složce na začátku sbalený. */
	static final int SLOZKA_ROZBALENA_DO = 5;

	/** Stav oddílu Ve složce zvolený uživatelem, null = ještě nevolil (rozhodne počet map); ukládá se přes {@link #ulozStavSlozky}. */
	private Boolean slozkaOtevrena;
	transient java.util.function.Supplier<Optional<Boolean>> nactiStavSlozky = Optional::empty;
	transient java.util.function.Consumer<Boolean> ulozStavSlozky = b -> {};
	private final Set<String> rozbaleneSlozka = new HashSet<>();

	/** Stahuje se mapa nebo její vrstva, nebo se něco nepovedlo: takový řádek je vidět i ve sbaleném oddílu a sbalené zemi. */
	private static boolean vyzadujePozornost(final RadekMapy r) {
		final RadekMapy.Stav s = r.celkovyStav();
		// Kontrola aktualizací řádek jen označí, nic se nestahuje, proto oddíl nerozbaluje.
		return s == RadekMapy.Stav.STAHUJE && !r.kontroluje || s == RadekMapy.Stav.CHYBA;
	}

	private boolean jeSlozkaOtevrena(final int pocetMap) {
		if (slozkaOtevrena == null) {
			slozkaOtevrena = nactiStavSlozky.get().orElse(null);
		}
		return slozkaOtevrena != null ? slozkaOtevrena : pocetMap <= SLOZKA_ROZBALENA_DO;
	}

	/** Sbalí nebo rozbalí celý oddíl Ve složce a zapamatuje si to. */
	void prepniSlozku(final boolean otevrit) {
		slozkaOtevrena = otevrit;
		ulozStavSlozky.accept(otevrit);
		prestav();
	}

	/** Klik na zemi ve složce rozbalí nebo sbalí její stažené regiony. */
	void prepniUzelSlozky(final String nazev) {
		if (!rozbaleneSlozka.remove(nazev)) {
			rozbaleneSlozka.add(nazev);
		}
		prestav();
	}

	/** Přestaví popup po změně sbalení; posun seznamu dalších map zůstane. */
	private void prestav() {
		final int posun = seznam == null ? 0 : seznam.getVerticalScrollBar().getValue();
		sestav();
		if (seznam != null) {
			seznam.getVerticalScrollBar().setValue(posun);
		}
		zmenaObsahu();
	}

	/** Stažené mapy po zemích: regiony pod zemí se šipkou, souhrn u země bez staženého celku; celý strom v tabulce Ve složce. */
	private int stromSlozky(final JPanel panel, final int zacatek, final List<RadekMapy> mapy, final boolean stahujeSe) {
		int y = zacatek;
		final java.text.Collator cs = java.text.Collator.getInstance(new Locale("cs"));
		final Map<String, Uzel> podleZeme = new TreeMap<>(cs);
		final List<RadekMapy> vlastni = new ArrayList<>();
		for (final RadekMapy r : mapy) {
			if (r.vlastni) {
				vlastni.add(r);
			} else if (r.skupina != null) {
				podleZeme.computeIfAbsent(r.skupina, Uzel::new).regiony.add(r);
			} else {
				podleZeme.computeIfAbsent(r.nazev, Uzel::new).celek = r;
			}
		}
		for (final Uzel u : podleZeme.values()) {
			u.regiony.sort((a, b) -> cs.compare(a.nazev, b.nazev));
			final boolean rozbaleno = rozbaleneSlozka.contains(u.nazev) || u.regiony.stream().anyMatch(JMapyPopup::vyzadujePozornost);
			if (u.regiony.isEmpty()) {
				y = pridejRadek(panel, y, u.celek, stahujeSe);
				continue;
			}
			if (u.celek != null) {
				radek(panel, y++, u.celek, stahujeSe);
				final JLabel nazev = popisy.get(klic(u.celek))[1];
				nazev.setBorder(null);
				nastavRozbaleni(nazev, rozbaleno, () -> prepniUzelSlozky(u.nazev));
			} else {
				y = radekZemeBezCelku(panel, y, u, souhrnRegionu(u.regiony), rozbaleno, () -> prepniUzelSlozky(u.nazev));
			}
			if (rozbaleno) {
				for (final RadekMapy r : u.regiony) {
					radek(panel, y++, r, stahujeSe);
					popisy.get(klic(r))[1].setBorder(new EmptyBorder(0, 2 * ODSAZENI, 0, 0));
				}
			}
		}
		for (final RadekMapy r : vlastni) {
			y = pridejRadek(panel, y, r, stahujeSe);
		}
		return y;
	}

	/** „3 regiony · 1,9 GB“ ze stažených regionů země. */
	static String souhrnRegionu(final List<RadekMapy> regiony) {
		long velikost = 0;
		for (final RadekMapy r : regiony) {
			velikost += Math.max(0, r.velikost);
		}
		final int n = regiony.size();
		return n + " " + (n == 1 ? "region" : n < 5 ? "regiony" : "regionů") + (velikost > 0 ? " · " + RadekMapy.velikost(velikost) : "");
	}

	private void radek(final JPanel panel, final int y, final RadekMapy r, final boolean stahujeSe) {
		final MouseAdapter nadRadkem = new MouseAdapter() {
			@Override
			public void mouseEntered(final MouseEvent e) {
				zvyrazni(klicZvyrazneni(r));
			}

			@Override
			public void mouseExited(final MouseEvent e) {
				zvyrazni(null);
			}
		};
		final String tooltip = r.tooltip();
		final JLabel ikona = new JLabel(IkonyMap.pro(r.stav));
		ikona.addMouseListener(nadRadkem);
		ikona.setToolTipText(tooltip);
		final GridBagConstraints c = bunka(0, y, 1);
		c.insets = new Insets(1, 0, 1, 4);
		panel.add(ikona, c);

		final JLabel nazev = new JLabel(r.nazev);
		nazev.addMouseListener(nadRadkem);
		nazev.setToolTipText(tooltip);
		// Stejná šířka sloupce názvů ve všech oddílech, aby popisy byly pod sebou.
		nazev.setPreferredSize(new Dimension(sirkaNazvu, nazev.getPreferredSize().height));
		panel.add(nazev, bunka(1, y, 1));

		final JLabel popis = maly("");
		popis.setText(zkrat(popisRadku(r), popis.getFontMetrics(popis.getFont())));
		popis.setPreferredSize(new Dimension(SIRKA_POPISU, popis.getPreferredSize().height));
		popis.setToolTipText(tooltip);
		popis.addMouseListener(nadRadkem);
		if (r.stav == RadekMapy.Stav.CHYBA) {
			popis.setForeground(IkonyMap.CERVENA);
		}
		final GridBagConstraints cp = bunka(2, y, 1);
		cp.weightx = 1;
		cp.insets = new Insets(1, 8, 1, 8);
		panel.add(popis, cp);
		popisy.put(klic(r), new JLabel[] { ikona, nazev, popis });

		final JButton akce = akce(r, stahujeSe);
		if (akce != null) {
			akce.addMouseListener(nadRadkem);
		}
		final GridBagConstraints ca = bunka(3, y, 1);
		ca.anchor = GridBagConstraints.WEST;
		ca.fill = GridBagConstraints.NONE;
		panel.add(sloupecAkce(akce), ca);
		for (int i = 0; i < VRSTVY.length; i++) {
			final boolean maVrstvy = r.matice != null && !r.vlastni;
			final JComponent bunka = maVrstvy ? bunkaVrstvy(r, VRSTVY[i], stahujeSe, nadRadkem) : prazdnaBunka();
			final GridBagConstraints cb = bunka(PRVNI_SLOUPEC_VRSTEV + i, y, 1);
			cb.anchor = GridBagConstraints.WEST;
			cb.fill = GridBagConstraints.NONE;
			panel.add(bunka, cb);
		}
	}

	/** Akce mapy ve sloupci pevné šířky, aby sloupce vrstev lícovaly i v řádcích bez akce. */
	private static JComponent sloupecAkce(final JButton akce) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SIRKA_AKCE, odkaz(STAHNOUT, e -> {}).getPreferredSize().height));
		if (akce != null) {
			p.add(akce);
		}
		return p;
	}

	private static JComponent prazdnaBunka() {
		final JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SIRKA_BUNKY, odkaz(STAHNOUT, e -> {}).getPreferredSize().height));
		return p;
	}

	/** Buňka vrstvy v řádku mapy: stav a velikost, akce podle stavu (stáhnout, aktualizovat, zrušit, smazat). */
	private JComponent bunkaVrstvy(final RadekMapy r, final MatriceMap.Cast cast, final boolean stahujeSe, final MouseAdapter nadRadkem) {
		final MatriceMap.Bunka b = r.bunka(cast);
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SIRKA_BUNKY, odkaz(STAHNOUT, e -> {}).getPreferredSize().height));
		p.addMouseListener(nadRadkem);
		if (b.getStav() == MatriceMap.StavBunky.NENI) {
			final JLabel nic = maly(NENI_VRSTVA);
			nic.setBorder(new EmptyBorder(0, 6, 0, 0));
			p.add(nic);
			return p;
		}
		final String nazev = nazevVrstvy(cast);
		final String velikost = b.getVelikost() > 0 ? RadekMapy.velikost(b.getVelikost()) : "";
		switch (b.getStav()) {
		case STAZENO: {
			p.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.AKTUALNI)));
			final JLabel l = maly(velikost);
			l.setToolTipText(nazev + ": staženo" + (b.getDatumDat() == null ? "" : ", data z " + DATUM_DAT.format(b.getDatumDat())));
			p.add(l);
			break;
		}
		case NOVEJSI: {
			p.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.NOVEJSI)));
			final JButton aktualizovat = odkaz(AKTUALIZOVAT, e -> stahniVrstvu(b));
			aktualizovat.setToolTipText(nazev + ": novější verze, " + velikost);
			zakazDokudNeniMisto(aktualizovat, b, stahujeSe);
			p.add(aktualizovat);
			break;
		}
		case STAHUJE: {
			p.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.STAHUJE)));
			final JLabel procenta = maly(procenta(b));
			postupVrstev.put(klic(r) + "#" + cast, procenta);
			p.add(procenta);
			final JButton zrusit = odkaz(ZRUSIT, e -> {
				if (ovladani != null) {
					ovladani.zrusStahovani();
				}
			});
			p.add(zrusit);
			return p;
		}
		case CHYBA: {
			p.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.CHYBA)));
			final JButton znovu = odkaz(ZKUSIT_ZNOVU, e -> stahniVrstvu(b));
			znovu.setToolTipText(nazev + ": " + (b.getChyba() == null ? "stažení se nepovedlo" : b.getChyba()));
			zakazDokudNeniMisto(znovu, b, stahujeSe);
			p.add(znovu);
			break;
		}
		case KE_STAZENI:
		default: {
			final JButton stahnout = odkaz("↓ " + velikost, e -> stahniVrstvu(b));
			stahnout.setToolTipText(nazev + ": stáhnout" + (velikost.isEmpty() ? "" : ", " + velikost) + (b.getStazeno() > 0 ? " (rozpracováno)" : ""));
			zakazDokudNeniMisto(stahnout, b, stahujeSe);
			p.add(stahnout);
			break;
		}
		}
		if (b.jeCoMazat()) {
			final JButton smazat = odkaz(SMAZAT_VRSTVU, e -> {
				if (ovladani != null) {
					ovladani.smazVrstvu(b.getId());
				}
			});
			smazat.setForeground(IkonyMap.CERVENA);
			smazat.setToolTipText("Smazat vrstvu " + nazev.toLowerCase(new Locale("cs")));
			if (stahujeSe) {
				smazat.setEnabled(false);
				smazat.setToolTipText(BEHEM_STAHOVANI_NEJDE_MAZAT);
			}
			p.add(smazat);
		}
		return p;
	}

	private void zakazDokudNeniMisto(final JButton b, final MatriceMap.Bunka bunka, final boolean stahujeSe) {
		if (stahujeSe) {
			b.setEnabled(false);
			b.setToolTipText(STAHUJE_SE_JINA);
		} else if (nedostatekMista(bunka)) {
			b.setEnabled(false);
			b.setToolTipText("Na disku je volných jen " + RadekMapy.velikost(volneMisto) + ", potřebuje asi " + RadekMapy.velikost(potrebneMisto(bunka)) + ".");
		}
	}

	private static String procenta(final MatriceMap.Bunka b) {
		return b.getVelikost() > 0 ? Math.min(100, b.getStazeno() * 100 / b.getVelikost()) + " %" : "";
	}

	static String nazevVrstvy(final MatriceMap.Cast cast) {
		switch (cast) {
		case TRASY:
			return "Turistické trasy";
		case VRSTEVNICE:
			return "Vrstevnice";
		case STINOVANI:
		default:
			return "Stínování terénu";
		}
	}

	private void stahniVrstvu(final MatriceMap.Bunka b) {
		if (ovladani != null) {
			ovladani.stahniVrstvu(b.getId());
		}
	}

	/** Jediná akce řádku podle stavu; soubory uživatele žádnou nemají. */
	private JButton akce(final RadekMapy r, final boolean stahujeSe) {
		if (r.vlastni) {
			return null;
		}
		final JButton b;
		switch (r.stav) {
		case STAHUJE:
			if (r.kontroluje) {
				return null;
			}
			final JButton zrusit = odkaz(ZRUSIT, (ActionListener) null);
			zrusit.addActionListener(e -> {
				if (ovladani != null) {
					ovladani.zrusStahovani();
				}
				// Zaseknutý server zrušení potvrdí až po chvíli.
				rusenaMapa = r.id;
				zrusit.setEnabled(false);
				final JLabel[] bunky = popisy.get(klic(r));
				if (bunky != null) {
					bunky[2].setText(RUSI_SE);
				}
			});
			return zrusit;
		case AKTUALNI:
			return null;
		case NOVEJSI:
			b = odkaz(AKTUALIZOVAT, e -> stahni(r));
			break;
		case CHYBA:
			b = odkaz(r.isVeSlozce() ? STAHNOUT_ZNOVU : ZKUSIT_ZNOVU, e -> stahni(r));
			break;
		case NEMAME:
		default:
			b = odkaz(r.rozpracovano ? POKRACOVAT : STAHNOUT, e -> stahni(r));
			break;
		}
		if (stahujeSe) {
			b.setEnabled(false);
			b.setToolTipText(STAHUJE_SE_JINA);
		} else if (nedostatekMista(r)) {
			b.setEnabled(false);
			b.setToolTipText("Na disku je volných jen " + RadekMapy.velikost(volneMisto) + ", mapa potřebuje asi " + RadekMapy.velikost(potrebneMisto(r)) + ".");
		}
		return b;
	}

	private void smazDvojite(final PokrytiMap.DvojitaMapa d, final boolean regiony) {
		if (ovladani != null) {
			ovladani.smazDvojite(d.getCelek().getId(), regiony);
		}
	}

	private void stahni(final RadekMapy r) {
		if (ovladani != null) {
			ovladani.stahni(r.id);
		}
	}

	static String zkrat(final String text, final FontMetrics fm) {
		if (text == null || fm.stringWidth(text) <= SIRKA_POPISU) {
			return text;
		}
		int n = text.length();
		while (n > 1 && fm.stringWidth(text.substring(0, n) + "…") > SIRKA_POPISU) {
			n--;
		}
		return text.substring(0, n).trim() + "…";
	}

	private static JPanel panelRadku() {
		return new TabulkaRadku(PRVNI_SLOUPEC_VRSTEV, sirkaPosuvniku());
	}

	/** Řádky v posuvném seznamu; posuvník si šířku bere sám. */
	private static JPanel panelRadkuVSeznamu() {
		return new TabulkaRadku(PRVNI_SLOUPEC_VRSTEV, 0);
	}

	/** Sloupce mřížky řádku: ikona, název, popis, akce, pak buňky vrstev. */
	static final int PRVNI_SLOUPEC_VRSTEV = 4;

	private static GridBagConstraints bunka(final int x, final int y, final int sirka) {
		final GridBagConstraints c = new GridBagConstraints();
		c.gridx = x;
		c.gridy = y;
		c.gridwidth = sirka;
		c.anchor = GridBagConstraints.WEST;
		c.insets = new Insets(1, 0, 1, 0);
		return c;
	}

	private static JLabel nadpisOddilu(final String text) {
		final JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
		l.setForeground(SEDY_TEXT);
		l.setBorder(new EmptyBorder(8, 0, 2, 0));
		return l;
	}

	private static JLabel maly(final String text) {
		final JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.PLAIN, 11f));
		l.setForeground(SEDY_TEXT);
		return l;
	}

	/** Akce jako odkaz: v dlouhém seznamu map je to klidnější než řada tlačítek. */
	private static JButton odkaz(final String text, final ActionListener akce) {
		final JButton b = new JButton(text);
		b.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
		b.setContentAreaFilled(false);
		b.setFocusable(false);
		b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
		b.setForeground(ODKAZ);
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		if (akce != null) {
			b.addActionListener(akce);
		}
		return b;
	}

	/** Odkaz otevře úvodní stránku webu v prohlížeči. */
	private static JButton odkaz(final String text, final String url) {
		final JButton b = odkaz(text, e -> {
			try {
				BrowserOpener.displayURL(new URL(url));
			} catch (final MalformedURLException ex) {
				throw new IllegalStateException(ex);
			}
		});
		b.setToolTipText(url);
		return b;
	}
}
