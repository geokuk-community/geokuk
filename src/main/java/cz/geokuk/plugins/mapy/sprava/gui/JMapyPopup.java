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

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce;
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
	static final String VE_SLOZCE = TextyMap.STAZENE_MAPY;
	static final String ZADNA_MAPA = TextyMap.ZADNA_MAPA;
	static final String PRO_MISTO = TextyMap.PRO_TOTO_MISTO;
	static final String DALSI = TextyMap.MAPY_KE_STAZENI;
	static final String OBRYS = TextyMap.OBRYS;
	static final String STINOVANI = "Stínovat terén";
	static final String WEB = TextyMap.DALSI_WEBY;
	static final String STAHNOUT = "Stáhnout";
	static final String SMAZAT_VRSTVU = "×";
	static final String SLOUPEC_MAPA = "Mapa";
	static final String JEN_REGIONY = "jen regiony";
	static final String CELA_VE_SLOZCE = TextyMap.CELA_ZEME;
	static final String SLOUPEC_TRASY = TextyMap.TRASY;
	static final String SLOUPEC_VRSTEVNICE = "Vrstevnice";
	static final String SLOUPEC_STINOVANI = "Stínování";
	static final String NENI_VRSTVA = "—";
	static final String SMAZAT_REGIONY = "Smazat regiony";
	static final String SMAZAT_CELEK = "Smazat celou zemi";
	static final String POKRACOVAT = TextyMap.DOKONCIT_STAZENI;
	static final String AKTUALIZOVAT = "Aktualizovat";
	static final String STAHNOUT_ZNOVU = TextyMap.ZKUSIT_ZNOVU;
	static final String ZKUSIT_ZNOVU = TextyMap.ZKUSIT_ZNOVU;
	static final String ZRUSIT = "Zrušit";
	static final String ZKONTROLOVAT = "Zkontrolovat aktualizace";
	static final String ZKONTROLOVAT_TIP = "Zjistí na serveru, jestli jsou pro stažené mapy novější data.";
	static final String OTEVRIT_SLOZKU = TextyMap.OTEVRIT_SLOZKU;
	static final String BEHEM_STAHOVANI_NEJDE_MAZAT = "Během stahování nejde mazat.";
	static final String STAHUJE_SE_JINA = "Najednou se stahuje jen jedna mapa.";
	static final String BEZI_STAHOVANI = "Až se dokončí běžící stahování.";
	static final String VSE_AKTUALNI = "Vše aktuální";
	static final String KONTROLA_SELHALA = "Kontrola se nepovedla";

	static final String URL_PAWS = "https://osm.paws.cz/";
	static final String URL_OAM = "https://www.openandromaps.org/";
	static final String ZDROJ = TextyMap.ZDROJ;
	static final String URL_MAPSFORGE = "https://github.com/mapsforge/mapsforge";
	static final String URL_ODBL = "https://opendatacommons.org/licenses/odbl/";
	static final String URL_OSM = "https://www.openstreetmap.org/copyright";
	static final String RUSI_SE = "Ruší se…";

	/** Šířka sloupce vrstvy (trasy, vrstevnice, stínování), ve všech oddílech stejná, aby byly buňky pod sebou. */
	static final int SIRKA_BUNKY = 112;

	static final String STAHNOUT_VSE = "Stáhnout vše";
	static final String ZAVRIT = "Zavřít";
	static final String DOSTAHNOUT_VRSTVY = TextyMap.STAHNOUT_VRSTVY_KE_STAZENYM;

	/** Šířka sloupce s akcí mapy (Stáhnout, Zrušit…), ve všech oddílech stejná. */
	static final int SIRKA_AKCE = 88;

	private boolean mapaBezi;
	private boolean cokoliBezi;
	/** Země podle názvu z celého katalogu bez ohledu na oddíl: celek a všechny regiony. */
	private final transient Map<String, Uzel> zemeVsechny = new HashMap<>();
	/** Kolik se ještě dostahuje z běžících stahování; to místo se z volného odečítá. */
	private long rozdelano;
	private boolean vrstvyPlne;

	/** Delší popis (třeba text chyby) se zkrátí, celý je v tooltipu. */
	static final int SIRKA_POPISU = 176;

	/** Nejvyšší výška seznamu dalších map, pak se posouvá. */
	static final int VYSKA_SEZNAMU = 340;

	/** Od kolika dalších map se nabídne hledání. */
	static final int HLEDANI_OD = 8;
	static final String HLEDAT = TextyMap.HLEDAT;
	static final String NIC_NENALEZENO = "Nic nenalezeno.";
	static final String MIMO_EVROPU = "Mimo Evropu";
	/** Kontinenty mimo Evropu podle prvního dílu id mapy (složky na serveru mapsforge). */
	private static final Set<String> KONTINENTY_MIMO_EVROPU = new HashSet<>(
			Arrays.asList("africa", "antarctica", "asia", "australia-oceania", "central-america", "north-america", "south-america"));
	/** Klíče v nastavení, pod kterými se pamatuje rozbalení zemí ve Všech mapách a ve složce. */
	static final String ROZBALENE_VSECHNY = "rozbaleneZeme";
	static final String ROZBALENE_SLOZKA = "rozbaleneZemeVeSlozce";

	private static final Color SEDY_TEXT = new Color(90, 90, 90);
	private static final Color ODKAZ = new Color(0x1A5FB4);

	private transient OvladaniMap ovladani;
	/** Klíč řádku, jehož obrys je teď v mapě zvýrazněný; null, když žádný. */
	private String zvyraznena;
	private transient List<RadekMapy> radky = Collections.emptyList();
	/** Komponenty řádků podle klíče řádku (stejná mapa může být ve dvou oddílech), aby se změna stavu přepsala na místě. */
	private final transient Map<String, List<RadekView>> pohledy = new HashMap<>();
	/** Odkazy mimo řádky, jejichž text nebo zapnutí závisí na stavech řádků; při aktualizaci na místě se nastaví znovu. */
	private final transient List<Runnable> naMiste = new ArrayList<>();
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
	private transient java.util.function.BiConsumer<String, Set<String>> ulozRozbalene = (k, z) -> {};
	/** Celé země, které minulá obnova nabízela ke stažení. */
	private Set<String> nabizeneCelky = Collections.emptySet();
	private JLabel nicNenalezeno;
	private JLabel nadpisMimoEvropu;
	/** Od kterého řádku hledání jsou mapy mimo Evropu. */
	private int mimoEvropuOd;
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
		pozornost.clear();
		slozkaSamaOtevrena = false;
		zemeSamyRozbalene.clear();
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
		boolean mimoEvropuVidet = false;
		for (int i = 0; i < hledatelne.size(); i++) {
			final RadekHledani r = hledatelne.get(i);
			final boolean ano;
			if (r.hlavicka) {
				ano = hledany.isEmpty() || obsahujeVse(r.text, hledany) || uzlySeShodou.contains(r.uzel);
			} else if (r.uzel != null) {
				ano = hledany.isEmpty() ? rozbalene.contains(r.uzel) : obsahujeVse(r.text, hledany);
			} else {
				ano = obsahujeVse(r.text, hledany);
			}
			viditelnych += ano ? 1 : 0;
			mimoEvropuVidet |= ano && i >= mimoEvropuOd;
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
		if (nadpisMimoEvropu != null) {
			nadpisMimoEvropu.setVisible(mimoEvropuVidet);
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
		ulozRozbalene.accept(ROZBALENE_VSECHNY, new HashSet<>(rozbalene));
		filtruj();
	}

	/** Načte rozbalení zemí z minulého spuštění a každé přepnutí uloží; nastavuje blok Mapy. */
	void pamatujRozbaleni(final java.util.function.Function<String, Set<String>> nacti, final java.util.function.BiConsumer<String, Set<String>> uloz) {
		rozbalene.addAll(nacti.apply(ROZBALENE_VSECHNY));
		rozbaleneSlozka.addAll(nacti.apply(ROZBALENE_SLOZKA));
		ulozRozbalene = uloz;
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
			naplanujPrestavbu();
		}
	}

	/** Zavolá se, když se změnila velikost obsahu a popup se má zobrazit znovu. */
	public void setPoZmeneObsahu(final Runnable poZmeneObsahu) {
		this.poZmeneObsahu = poZmeneObsahu;
	}

	/** Nové řádky: změna stavu se přepíše na místě v komponentách řádků, popup se přestaví jen při změně struktury (řádky v oddílech, odkazy, rozbalení). */
	public void obnov(final List<RadekMapy> nove) {
		odhad.zapomenKrome(stahovane(nove));
		// Regiony země, která se právě stáhla celá, už jsou navíc: ve Všech mapách se sbalí.
		final Set<String> stazeneCelky = celky(nove, true);
		if (rozbalene.removeIf(z -> nabizeneCelky.contains(z) && stazeneCelky.contains(z))) {
			ulozRozbalene.accept(ROZBALENE_VSECHNY, new HashSet<>(rozbalene));
		}
		nabizeneCelky = celky(nove, false);
		radky = new ArrayList<>(nove);
		if (zastaraly) {
			naplanujPrestavbu();
			return;
		}
		// Zavřenému popupu se rozbalení určí až při otevření, aby samo rozbalení nezůstalo po stahování, které mezitím skončilo.
		final boolean otevreny = zobrazeny.getAsBoolean();
		if (otevreny) {
			zaznamenejPozornost(veSlozce());
		}
		if (!struktura().equals(zobrazenaStruktura) || otevreny && !rozbaleni().equals(zobrazeneRozbaleni)) {
			naplanujPrestavbu();
			return;
		}
		aktualizujNaMiste();
	}

	/** Přepíše stavy řádků a odkazů v existujících komponentách; řádky, jejichž stav se nezměnil, zůstanou, jak jsou. */
	private void aktualizujNaMiste() {
		if (zastaraly) {
			return;
		}
		prepocitejSouhrny();
		if (rusenaMapa != null && radky.stream().noneMatch(r -> rusenaMapa.equals(r.id) && r.stav == RadekMapy.Stav.STAHUJE)) {
			rusenaMapa = null;
		}
		final Dimension predtim = getPreferredSize();
		for (final RadekMapy r : radky) {
			for (final RadekView v : pohledy.getOrDefault(klic(r), Collections.emptyList())) {
				v.aktualizuj(r);
			}
		}
		for (final Runnable r : naMiste) {
			r.run();
		}
		if (!predtim.equals(getPreferredSize())) {
			zmenaObsahu();
		}
	}

	/** Přestaví popup, jen když je vidět a neprobíhá hromadná změna; jinak si zapamatuje, že je zastaralý. */
	private void naplanujPrestavbu() {
		if (!zobrazeny.getAsBoolean() || hromadnaZmena > 0) {
			zastaraly = true;
			return;
		}
		prestavPopup();
	}

	/** Názvy celých zemí z katalogu, které jsou (nebo nejsou) ve složce. */
	private static Set<String> celky(final List<RadekMapy> radky, final boolean veSlozce) {
		final Set<String> v = new HashSet<>();
		for (final RadekMapy r : radky) {
			if (r.skupina == null && !r.vlastni && r.isVeSlozce() == veSlozce) {
				v.add(r.nazev);
			}
		}
		return v;
	}

	/** Nastaví odkaz podle stavů řádků hned a znovu při každé aktualizaci na místě. */
	private void nastavANechNaMiste(final Runnable nastav) {
		nastav.run();
		naMiste.add(nastav);
	}

	private void prestavPopup() {
		zastaraly = false;
		rusenaMapa = null;
		// Velikost otevřeného popupu mění jen akce uživatele, jinak by se popup při stahování znovu zobrazoval.
		sestav(isShowing() ? getHeight() : -1);
		zmenaObsahu();
	}

	/** Provede změny (řádky, volné místo, dvojité mapy…) a popup přestaví nejvýš jednou na konci. */
	public void zmenyNaraz(final Runnable zmeny) {
		hromadnaZmena++;
		try {
			zmeny.run();
		} finally {
			hromadnaZmena--;
		}
		if (zastaraly && hromadnaZmena == 0) {
			naplanujPrestavbu();
		}
	}

	/** Zavřený popup se nepřestavuje; nastavuje blok Mapy. */
	public void setZobrazeny(final java.util.function.BooleanSupplier zobrazeny) {
		this.zobrazeny = zobrazeny;
	}

	/** Před otevřením popupu: přestaví ho, když se od zavření změnila struktura nebo rozbalení nebo když drží výšku z minulého otevření. */
	public void pripravKZobrazeni() {
		zaznamenejPozornost(veSlozce());
		if (zastaraly || drzenaVyska > 0 || !rozbaleni().equals(zobrazeneRozbaleni)) {
			zastaraly = false;
			rusenaMapa = null;
			sestav();
		}
	}

	boolean isZastaraly() {
		return zastaraly;
	}

	private transient JScrollPane slozkaPosuvnik;
	private transient JScrollPane mistoPosuvnik;

	/** Klientská vlastnost první buňky řádku s klíčem řádku, podle kterého se po přestavbě najde tentýž řádek. */
	private static final String KLIC_RADKU = "klicRadku";

	/** Řádek nahoře v posuvníku, o kolik px je posuvník pod jeho začátkem, a posun v px pro případ, že řádek po přestavbě chybí. */
	private static final class Pozice {
		final String klic;
		final int pod;
		final int px;

		Pozice(final String klic, final int pod, final int px) {
			this.klic = klic;
			this.pod = pod;
			this.px = px;
		}
	}

	private static Pozice pozice(final JScrollPane sp) {
		if (sp == null) {
			return null;
		}
		final int y = sp.getViewport().getViewPosition().y;
		Component prvni = null;
		for (final Component c : radky(sp.getViewport().getView(), new ArrayList<>())) {
			if (y(c, sp) + c.getHeight() > y && (prvni == null || y(c, sp) < y(prvni, sp))) {
				prvni = c;
			}
		}
		return prvni == null ? new Pozice(null, 0, y) : new Pozice((String) ((JComponent) prvni).getClientProperty(KLIC_RADKU), y - y(prvni, sp), y);
	}

	/** První buňky viditelných řádků. */
	private static List<Component> radky(final Component c, final List<Component> v) {
		if (c instanceof JComponent && ((JComponent) c).getClientProperty(KLIC_RADKU) != null) {
			if (c.isVisible()) {
				v.add(c);
			}
		} else if (c instanceof Container) {
			for (final Component k : ((Container) c).getComponents()) {
				radky(k, v);
			}
		}
		return v;
	}

	private static int y(final Component c, final JScrollPane sp) {
		return SwingUtilities.convertPoint(c.getParent(), c.getLocation(), sp.getViewport().getView()).y;
	}

	/** Po přestavbě je v posuvnících oddílů nahoře tentýž řádek, i když nad ním řádek přibyl nebo ubyl; posuvník nahoře zůstane nahoře. */
	private void obnovPozice(final Pozice[] pozice) {
		validate();
		final JScrollPane[] sp = { slozkaPosuvnik, mistoPosuvnik, seznam };
		for (int i = 0; i < sp.length; i++) {
			if (sp[i] == null || pozice[i] == null || pozice[i].px <= 0) {
				continue;
			}
			int y = pozice[i].px;
			for (final Component c : radky(sp[i].getViewport().getView(), new ArrayList<>())) {
				if (((JComponent) c).getClientProperty(KLIC_RADKU).equals(pozice[i].klic)) {
					y = y(c, sp[i]) + pozice[i].pod;
					break;
				}
			}
			sp[i].getVerticalScrollBar().setValue(y);
		}
	}

	JScrollPane seznam() {
		return seznam;
	}

	JScrollPane getSlozkaPosuvnik() {
		return slozkaPosuvnik;
	}

	private transient java.util.function.BooleanSupplier zobrazeny = () -> true;
	/** Obsah neodpovídá řádkům a nastavení, přestaví se při nejbližší příležitosti. */
	private boolean zastaraly;
	private int hromadnaZmena;
	/** Kolikrát se popup sestavil znovu (pro test). */
	int prestaveb;
	/** Struktura a rozbalení z posledního sestavení; dokud se nezmění, mění se řádky jen na místě. */
	private List<String> zobrazenaStruktura = Collections.emptyList();
	private String zobrazeneRozbaleni = "";

	List<RadekMapy> getRadky() {
		return radky;
	}

	private List<RadekMapy> veSlozce() {
		final List<RadekMapy> v = new ArrayList<>();
		for (final RadekMapy r : radky) {
			if (r.isVeSlozce()) {
				v.add(r);
			}
		}
		return v;
	}

	/** Mapa mimo složku je i v oddílu Pro zobrazené místo: pokrývá ho, nebo se právě stahuje ona či její vrstva. */
	private static boolean proMisto(final RadekMapy r) {
		return !r.isVeSlozce() && (r.proZobrazeneMisto || r.stav == RadekMapy.Stav.STAHUJE || r.bezicVrstva() != null);
	}

	/** Co určuje rozložení popupu: řádky v oddílech a odkazy nad nimi; stavy řádků do ní nepatří, ty se mění na místě. */
	private List<String> struktura() {
		final List<String> s = new ArrayList<>();
		for (final RadekMapy r : radky) {
			s.add(klic(r) + "|" + r.isVeSlozce() + "|" + proMisto(r) + "|" + (r.matice != null));
		}
		final Set<String> zemeKeStazeni = new TreeSet<>();
		for (final Uzel u : zeme(radky).values()) {
			if (maStahnoutVse(u)) {
				zemeKeStazeni.add(u.nazev);
			}
		}
		final List<RadekMapy> kontrolovatelne = kontrolovatelne(radky);
		final List<String> kontrolovatelneKlice = new ArrayList<>();
		kontrolovatelne.forEach(r -> kontrolovatelneKlice.add(r.id));
		s.add("kontrola|" + kontrolovatelneKlice + "|" + (vysledekKontroly(kontrolovatelne) != null));
		s.add("stahnout vse|" + zemeKeStazeni);
		s.add("souhrn|" + souhrnDavky());
		return s;
	}

	/** Rozbalení oddílu Ve složce a jeho zemí, jak by ho ukázalo nové sestavení. */
	private String rozbaleni() {
		final Set<String> zeme = new TreeSet<>(rozbaleneSlozka);
		zeme.addAll(zemeSamyRozbalene);
		return jeSlozkaOtevrena(veSlozce().size()) + "|" + zeme;
	}

	/** Stav řádku, podle kterého se znovu naplní jeho akce a buňky vrstev; průběh, popis a tooltipy se přepisují vždy. */
	private String stavRadku(final RadekMapy r) {
		final StringBuilder sb = new StringBuilder().append(r.stav).append(r.rozpracovano).append(r.kontroluje).append(r.overuje).append(r.isVeSlozce()).append(mapaBezi).append(cokoliBezi)
				.append(vrstvyPlne);
		// Tooltip vypnutého tlačítka uvádí volné místo bez toho, co se ještě stáhne.
		sb.append(nedostatekMista(r) ? volneMisto - rozdelano : -1);
		for (final MatriceMap.Cast c : VRSTVY) {
			final MatriceMap.Bunka b = r.bunka(c);
			if (b != null) {
				sb.append('|').append(b.getStav()).append(b.isOveruje()).append(b.getChyba()).append(b.getVelikost()).append(b.getStazeno() > 0).append(b.getDatumDat())
						.append(nedostatekMista(b) ? volneMisto - rozdelano : -1);
			}
		}
		return sb.toString();
	}

	/** Co se během stahování počítá ze všech řádků: běží mapa, běží cokoli, plno souběžných vrstev, kolik se ještě dostahuje. */
	private void prepocitejSouhrny() {
		cokoliBezi = radky.stream().anyMatch(r -> r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje || r.bezicVrstva() != null);
		mapaBezi = radky.stream().anyMatch(r -> r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje);
		rozdelano = zbyvaStahnout(radky);
		vrstvyPlne = radky.stream().mapToInt(r -> r.bezicVrstvy().size()).sum() >= SpravaMapModel.SOUBEZNE_VRSTVY;
	}

	private PlanPruvodce planDostahnout() {
		final List<MatriceMap.Radek> matice = new ArrayList<>();
		for (final RadekMapy r : radky) {
			if (r.matice != null && !r.vlastni) {
				matice.add(r.matice);
			}
		}
		return PlanPruvodce.sestavDostahnout(EnumSet.of(MatriceMap.Cast.TRASY, MatriceMap.Cast.VRSTEVNICE, MatriceMap.Cast.STINOVANI), matice);
	}

	private static final java.time.format.DateTimeFormatter DATUM_DAT = java.time.format.DateTimeFormatter.ofPattern("d. M. yyyy");

	private static final MatriceMap.Cast[] VRSTVY = { MatriceMap.Cast.TRASY, MatriceMap.Cast.VRSTEVNICE, MatriceMap.Cast.STINOVANI };

	private static String klic(final RadekMapy r) {
		return r.vlastni ? "soubor:" + r.nazev : r.id;
	}

	/** Stažené mapy z katalogu, u kterých jde zjistit novější data. */
	private static List<RadekMapy> kontrolovatelne(final List<RadekMapy> radky) {
		final List<RadekMapy> k = new ArrayList<>();
		for (final RadekMapy r : radky) {
			if (r.isVeSlozce() && !r.vlastni && (r.stav == RadekMapy.Stav.AKTUALNI || r.stav == RadekMapy.Stav.NOVEJSI)) {
				k.add(r);
			}
		}
		return k;
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
			naplanujPrestavbu();
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
		if (bajtu != volneMisto) {
			volneMisto = bajtu;
			aktualizujNaMiste();
		}
	}

	static long zbyvaStahnout(final List<RadekMapy> radky) {
		long zbyva = 0;
		for (final RadekMapy r : radky) {
			if (r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje && r.velikost > 0) {
				zbyva += Math.max(0, r.velikost - r.stazeno);
			}
			for (final MatriceMap.Bunka v : r.bezicVrstvy()) {
				if (v.getVelikost() > 0) {
					zbyva += Math.max(0, v.getVelikost() - v.getStazeno());
				}
			}
		}
		return zbyva;
	}

	/** Mapa ke stažení nebo k aktualizaci se na disk nevejde (s toutéž rezervou jako ve stahovači). */
	private boolean nedostatekMista(final RadekMapy r) {
		return volneMisto >= 0 && potrebneMisto(r) > volneMisto - rozdelano;
	}

	private boolean nedostatekMista(final MatriceMap.Bunka b) {
		return volneMisto >= 0 && potrebneMisto(b) > volneMisto - rozdelano;
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
		sestav(-1);
	}

	/**
	 * Sestaví popup znovu a nechá posuvníky všech oddílů na stejných řádcích (klik na řádek ani šipka uzlu neskočí nahoru).
	 *
	 * @param vyska
	 *            výška, kterou má popup mít i po přestavbě, nebo -1, když se řídí obsahem
	 */
	private void sestav(final int vyska) {
		final Pozice[] pozice = { pozice(slozkaPosuvnik), pozice(mistoPosuvnik), pozice(seznam) };
		drzenaVyska = vyska;
		sestavZNovu();
		obnovPozice(pozice);
		// Rozměry posuvníků se určí až při rozložení: po něm se pozice nastaví znovu.
		SwingUtilities.invokeLater(() -> obnovPozice(pozice));
	}

	private void sestavZNovu() {
		prestaveb++;
		zvyrazni(null);
		removeAll();
		pohledy.clear();
		naMiste.clear();
		hledatelne.clear();
		uzly.clear();
		nicNenalezeno = null;
		nadpisMimoEvropu = null;
		final FontMetrics fm = getFontMetrics(new JLabel().getFont());
		sirkaNazvu = 0;
		for (final RadekMapy r : radky) {
			sirkaNazvu = Math.max(sirkaNazvu, fm.stringWidth(r.nazev) + 2);
			if (r.skupina != null) {
				sirkaNazvu = Math.max(sirkaNazvu, fm.stringWidth(r.skupina) + 2 + ODSAZENI);
			}
		}
		// Šipka (a odsazení regionů) ve stromu zemí; země bez regionů jsou odsazené o šipku vždy.
		sirkaNazvu += radky.stream().anyMatch(r -> r.skupina != null) ? 2 * ODSAZENI : ODSAZENI;
		prepocitejSouhrny();
		zemeVsechny.clear();
		zemeVsechny.putAll(zeme(radky));
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
		final JPanel akceHlavicky = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
		akceHlavicky.setOpaque(false);
		final JButton vrstvy = odkaz("", e -> {
			if (ovladani != null) {
				ovladani.dostahnoutVrstvy();
			}
		});
		nastavANechNaMiste(() -> {
			// Kontrolovaná mapa se na chvíli nepočítá jako stažená; odkaz se kvůli tomu nemění, aby za kontroly neblikal.
			if (!vrstvy.getText().isEmpty() && radky.stream().anyMatch(r -> r.kontroluje)) {
				return;
			}
			final PlanPruvodce dostahnout = planDostahnout();
			vrstvy.setVisible(!dostahnout.jeHotovo());
			vrstvy.setText(String.format(Locale.ROOT, DOSTAHNOUT_VRSTVY, RadekMapy.velikost(dostahnout.getCelkovaVelikost())));
			vrstvy.setToolTipText(cokoliBezi ? BEZI_STAHOVANI : TextyMap.STAHNOUT_VRSTVY_KE_STAZENYM_TIP);
			vrstvy.setEnabled(!cokoliBezi);
		});
		akceHlavicky.add(vrstvy);
		akceHlavicky.add(odkaz(OTEVRIT_SLOZKU, e -> {
			if (ovladani != null) {
				ovladani.otevriSlozku();
			}
		}));
		hlavicka.add(akceHlavicky, BorderLayout.EAST);
		add(hlavicka, gbc);
		final List<String> souhrn = souhrnDavky();
		if (!souhrn.isEmpty()) {
			gbc.gridy++;
			add(souhrnDavkyPanel(souhrn), gbc);
		}
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
			} else {
				if (proMisto(r)) {
					proMisto.add(r);
				}
				dalsi.add(r);
			}
		}

		gbc.gridy++;
		final JPanel oddilSlozka = new JPanel(new BorderLayout());
		oddilSlozka.setOpaque(false);
		zaznamenejPozornost(veSlozce);
		final boolean slozkaOtevrena = jeSlozkaOtevrena(veSlozce.size());
		slozkaZobrazenaOtevrena = slozkaOtevrena;
		final JLabel nadpisSlozky = nadpisOddilu(VE_SLOZCE);
		if (!veSlozce.isEmpty()) {
			nadpisSlozky.setIcon(new Sipka(slozkaOtevrena));
			nadpisSlozky.setIconTextGap(4);
			nadpisSlozky.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			nadpisSlozky.setToolTipText(slozkaOtevrena ? TextyMap.SBALIT_STAZENE : TextyMap.ROZBALIT_STAZENE);
			nadpisSlozky.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(final MouseEvent e) {
					prepniSlozku(!slozkaZobrazenaOtevrena);
				}
			});
		}
		oddilSlozka.add(nadpisSlozky, BorderLayout.WEST);
		final List<RadekMapy> kontrolovatelne = kontrolovatelne(veSlozce);
		if (!kontrolovatelne.isEmpty()) {
			final JButton zkontrolovat = odkaz(ZKONTROLOVAT, e -> {
				if (ovladani != null) {
					ovladani.zkontrolujKatalogVrstev();
					for (final RadekMapy r : kontrolovatelne) {
						ovladani.zkontroluj(r.id);
					}
				}
			});
			zkontrolovat.setToolTipText(ZKONTROLOVAT_TIP);
			nastavANechNaMiste(() -> zkontrolovat.setEnabled(!cokoliBezi));
			oddilSlozka.add(zkontrolovat, BorderLayout.EAST);
		}
		add(oddilSlozka, gbc);
		final String vysledek = vysledekKontroly(kontrolovatelne);
		if (vysledek != null) {
			gbc.gridy++;
			final JLabel vysledekKontrolyText = maly(vysledek);
			nastavANechNaMiste(() -> vysledekKontrolyText.setText(vysledekKontroly(kontrolovatelne(radky))));
			add(vysledekKontrolyText, gbc);
		}
		final TabulkaRadku slozka = panelRadku();
		if (veSlozce.isEmpty()) {
			final JLabel nic = maly(ZADNA_MAPA);
			slozka.add(nic, bunka(0, 0, 4));
		}
		int y = 0;
		y = stromSlozky(slozka, y, veSlozce);
		final boolean slozkaViditelna = slozkaOtevrena || veSlozce.isEmpty();
		final JPanel slozkaObal = new JPanel(new BorderLayout());
		slozkaObal.setOpaque(false);
		slozkaObal.setVisible(slozkaViditelna);
		gbc.gridy++;
		add(slozkaObal, gbc);
		for (final PokrytiMap.DvojitaMapa d : dvojite) {
			final StringBuilder regiony = new StringBuilder();
			for (final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa r : d.getRegiony()) {
				regiony.append(regiony.length() > 0 ? ", " : "").append(r.getNazev());
			}
			final JLabel upozorneni = maly(TextyMap.zemeDvakrat(d.getCelek().getNazev(), regiony.toString()));
			upozorneni.setForeground(IkonyMap.CERVENA);
			gbc.gridy++;
			add(upozorneni, gbc);
			final JPanel akceDvojite = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
			akceDvojite.setOpaque(false);
			final JButton smazRegiony = odkaz(SMAZAT_REGIONY, e -> smazDvojite(d, true));
			nastavANechNaMiste(() -> smazRegiony.setEnabled(!cokoliBezi));
			akceDvojite.add(smazRegiony);
			if (d.isRegionyPokryvajiCelek()) {
				final JButton smazCelek = odkaz(SMAZAT_CELEK, e -> smazDvojite(d, false));
				nastavANechNaMiste(() -> smazCelek.setEnabled(!cokoliBezi));
				akceDvojite.add(smazCelek);
			}
			gbc.gridy++;
			add(akceDvojite, gbc);
		}

		TabulkaRadku misto = null;
		final JPanel mistoObal = new JPanel(new BorderLayout());
		mistoObal.setOpaque(false);
		if (!proMisto.isEmpty()) {
			gbc.gridy++;
			add(nadpisOddilu(PRO_MISTO), gbc);
			misto = panelRadku();
			y = 0;
			for (final RadekMapy r : proMisto) {
				y = pridejRadek(misto, y, r);
				if (r.skupina != null) {
					// Plochý seznam: bez stromu by u regionu nebylo vidět, ze které země je.
					posledniPohled(r).zeme = r.skupina;
					posledniPohled(r).aktualizuj(r);
				}
			}
			gbc.gridy++;
			add(mistoObal, gbc);
		}

		seznam = null;
		final int[] vyska = new int[3];
		int vyskaDalsi = 0;
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
			final List<Uzel> uzlySeznamu = new ArrayList<>(podleZeme.values());
			uzlySeznamu.sort(Comparator.comparing(this::mimoEvropu));
			for (final Uzel u : uzlySeznamu) {
				if (nadpisMimoEvropu == null && mimoEvropu(u)) {
					nadpisMimoEvropu = nadpisOddilu(MIMO_EVROPU);
					ostatni.add(nadpisMimoEvropu, bunka(0, y++, 4));
					mimoEvropuOd = hledatelne.size();
				}
				u.regiony.sort((a, b) -> cs.compare(a.nazev, b.nazev));
				final boolean sRegiony = !u.regiony.isEmpty();
				if (sRegiony) {
					uzly.add(u);
				}
				if (u.celek != null) {
					final int pred = ostatni.getComponentCount();
					y = radekCelku(ostatni, y, u, false, () -> prepniUzel(u.nazev));
					hledatelne.add(new RadekHledani(bezDiakritiky(u.celek.nazev), sRegiony ? u.nazev : null, sRegiony, komponentyOd(ostatni, pred)));
				} else {
					y = radekZemeBezCelku(ostatni, y, u);
				}
				for (final RadekMapy r : u.regiony) {
					y = pridejRegionSeznamu(ostatni, y, r, u);
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
			vyskaDalsi = vse.height;
			seznam = new JScrollPane(obal, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
				private static final long serialVersionUID = 1L;

				@Override
				public Dimension getPreferredSize() {
					return new Dimension(vse.width + getVerticalScrollBar().getPreferredSize().width, Math.min(vse.height, vyska[2]));
				}
			};
			seznam.setBorder(BorderFactory.createEmptyBorder());
			seznam.getVerticalScrollBar().setUnitIncrement(16);
			gbc.gridy++;
			add(seznam, gbc);
		}
		gbc.gridy++;
		final GridBagConstraints vypln = (GridBagConstraints) gbc.clone();

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
		// Zaškrtávátka vlevo a odkazy na weby vpravo na jednom řádku.
		final JPanel pata = new JPanel(new BorderLayout(12, 0));
		pata.setOpaque(false);
		final JPanel volby = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		volby.setOpaque(false);
		if (ovladani != null && ovladani.isObrysDostupny()) {
			volby.add(obrys);
		}
		if (stinovaniDostupne) {
			volby.add(stinovani);
		}
		final JPanel web = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		web.setOpaque(false);
		web.add(maly(WEB));
		web.add(odkaz("osm.paws.cz", URL_PAWS));
		web.add(odkaz("OpenAndroMaps", URL_OAM));
		pata.add(volby, BorderLayout.WEST);
		pata.add(web, BorderLayout.EAST);
		gbc.gridy++;
		add(pata, gbc);
		// Výška seznamů: co zbude z dostupné výšky nad stavovým řádkem po ostatních částech popupu.
		pevnaVyska = getPreferredSize().height;
		rozpocet = rozpocetSekci();
		final int[] pozadovano = { slozkaViditelna ? slozka.getPreferredSize().height : 0, misto == null ? 0 : misto.getPreferredSize().height, Math.min(vyskaDalsi, MAX_VYSKA_SEZNAMU) };
		final int[] prideleno = rozdelVysky(pozadovano, drzenaVyska > 0 ? drzenaVyska - pevnaVyska : rozpocet, MIN_VYSKA_SEKCE);
		System.arraycopy(prideleno, 0, vyska, 0, 3);
		final int zbyva = drzenaVyska - pevnaVyska - prideleno[0] - prideleno[1] - prideleno[2];
		if (drzenaVyska > 0 && zbyva > 0) {
			add(Box.createVerticalStrut(zbyva), vypln);
		}
		final JComponent slozkaSekce = sekce(slozka, prideleno[0]);
		slozkaPosuvnik = slozkaSekce instanceof JScrollPane ? (JScrollPane) slozkaSekce : null;
		slozkaObal.add(slozkaSekce);
		mistoPosuvnik = null;
		if (misto != null) {
			final JComponent mistoSekce = sekce(misto, prideleno[1]);
			mistoPosuvnik = mistoSekce instanceof JScrollPane ? (JScrollPane) mistoSekce : null;
			mistoObal.add(mistoSekce);
		}
		zobrazenaStruktura = struktura();
		zobrazeneRozbaleni = rozbaleni();
		if (uzivatelPise) {
			cekaNaFokus = true;
			SwingUtilities.invokeLater(hledat::requestFocusInWindow);
		}
		revalidate();
		repaint();
	}

	private List<String> souhrnDavky() {
		return ovladani == null ? Collections.emptyList() : ovladani.souhrnDavky();
	}

	/** Souhrn skončené dávky: kolik se stáhlo, co se nepodařilo, a tlačítka Zkusit znovu a Zavřít. */
	private JPanel souhrnDavkyPanel(final List<String> radky) {
		final JPanel p = new JPanel(new BorderLayout(8, 2));
		p.setOpaque(false);
		p.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, IkonyMap.CERVENA), new EmptyBorder(3, 0, 3, 0)));
		final JPanel text = new JPanel(new GridLayout(0, 1));
		text.setOpaque(false);
		for (int i = 0; i < radky.size(); i++) {
			final JLabel l = maly(radky.get(i));
			if (i == 0) {
				l.setForeground(IkonyMap.CERVENA);
				l.setFont(l.getFont().deriveFont(Font.BOLD));
			}
			text.add(l);
		}
		p.add(text, BorderLayout.CENTER);
		final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		tlacitka.setOpaque(false);
		tlacitka.add(odkaz(ZKUSIT_ZNOVU, e -> {
			if (ovladani != null) {
				ovladani.zkusZnovuDavku();
			}
		}));
		tlacitka.add(odkaz(ZAVRIT, e -> {
			if (ovladani != null) {
				ovladani.zavriSouhrnDavky();
			}
		}));
		p.add(tlacitka, BorderLayout.EAST);
		return p;
	}

	/** Nejmenší výška posuvné sekce, aby nezbyl jen proužek. */
	static final int MIN_VYSKA_SEKCE = 80;
	/** Seznam všech map má hledání, stačí mu asi deset řádků; jinak by i s jedinou mapou ve složce vyplnil celou výšku nad stavovým řádkem. */
	static final int MAX_VYSKA_SEZNAMU = 180;

	/** Kolik px je nad stavovým řádkem až po horní lištu okna, -1 když není známé; nastavuje blok Mapy. */
	private transient java.util.function.IntSupplier dostupnaVyska = () -> -1;
	/** Výška popupu bez seznamů z posledního sestavení a rozpočet seznamů, podle kterého se naposledy rozdělovalo. */
	private int pevnaVyska;
	private int rozpocet;
	/** Výška, kterou popup držel při posledním sestavení, -1 když se řídil obsahem. */
	private int drzenaVyska = -1;

	public void setDostupnaVyska(final java.util.function.IntSupplier dostupnaVyska) {
		this.dostupnaVyska = dostupnaVyska;
	}

	/** Okno se mohlo od posledního sestavení zvětšit nebo zmenšit: seznamy se rozdělí znovu, když se rozpočet změnil. */
	public void prepocitejVysku() {
		if (rozpocet != 0 && rozpocetSekci() != rozpocet) {
			naplanujPrestavbu();
		}
	}

	/**
	 * Kolik výšky smějí dohromady mít seznamy map: dostupná výška po horní lištu okna bez ostatních částí popupu, nejméně 260 a nejvýš 520 px; bez znalosti okna půl
	 * obrazovky.
	 */
	int rozpocetSekci() {
		final int dostupna = dostupnaVyska.getAsInt();
		return Math.min(MAX_ROZPOCET, Math.max(MIN_ROZPOCET, dostupna > 0 ? dostupna - pevnaVyska - OKRAJ_POPUPU : obrazovka() / 2));
	}

	private static int obrazovka() {
		return GraphicsEnvironment.isHeadless() ? 1000 : Toolkit.getDefaultToolkit().getScreenSize().height;
	}

	static final int MIN_ROZPOCET = 260;
	/** Ani ve vysokém okně popup nepřeroste přes půl obrazovky; další řádky (třeba po rozbalení země) jsou v posuvníku. */
	static final int MAX_ROZPOCET = 520;
	/** Rezerva na ohraničení popupu. */
	private static final int OKRAJ_POPUPU = 12;

	/**
	 * Rozdělí rozpočet výšky mezi sekce: co se vejde, dostane celou výšku (bez prázdného místa), zbytek se dělí rovným dílem mezi sekce, které se nevejdou,
	 * každá nejméně minimum. Součet může minimy překročit rozpočet.
	 */
	static int[] rozdelVysky(final int[] pozadovano, final int rozpocet, final int minimum) {
		final int[] v = pozadovano.clone();
		int soucet = 0;
		for (final int p : pozadovano) {
			soucet += p;
		}
		if (soucet <= rozpocet) {
			return v;
		}
		final boolean[] pevna = new boolean[v.length];
		int zbyva = rozpocet;
		boolean zmena = true;
		while (zmena) {
			zmena = false;
			int volnych = 0;
			for (int i = 0; i < v.length; i++) {
				if (!pevna[i] && pozadovano[i] > 0) {
					volnych++;
				}
			}
			if (volnych == 0) {
				break;
			}
			final int podil = Math.max(minimum, zbyva / volnych);
			for (int i = 0; i < v.length; i++) {
				if (!pevna[i] && pozadovano[i] > 0 && pozadovano[i] <= podil) {
					pevna[i] = true;
					zbyva -= pozadovano[i];
					zmena = true;
				}
			}
		}
		int volnych = 0;
		for (int i = 0; i < v.length; i++) {
			if (!pevna[i] && pozadovano[i] > 0) {
				volnych++;
			}
		}
		for (int i = 0; i < v.length; i++) {
			if (!pevna[i] && pozadovano[i] > 0) {
				v[i] = Math.min(pozadovano[i], Math.max(minimum, zbyva / volnych));
			}
		}
		return v;
	}

	/** Sekce se seznamem řádků: celá, když se vejde do přidělené výšky, jinak v posuvníku. */
	private static JComponent sekce(final TabulkaRadku tabulka, final int vyska) {
		if (tabulka.getPreferredSize().height <= vyska) {
			return tabulka;
		}
		tabulka.bezRezervyVpravo();
		final JPanel obal = new JPanel(new BorderLayout());
		obal.setOpaque(false);
		obal.add(tabulka, BorderLayout.NORTH);
		final JScrollPane sp = new JScrollPane(obal, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getPreferredSize() {
				return new Dimension(obal.getPreferredSize().width + getVerticalScrollBar().getPreferredSize().width, vyska);
			}
		};
		sp.setBorder(BorderFactory.createEmptyBorder());
		sp.setOpaque(false);
		sp.getViewport().setOpaque(false);
		sp.getVerticalScrollBar().setUnitIncrement(16);
		return sp;
	}

	static String souboru(final int n) {
		return n + " " + (n == 1 ? "soubor" : n < 5 ? "soubory" : "souborů");
	}

	/** Klíč řádku pro zvýraznění obrysu mapy: id z katalogu, u souboru uživatele jeho jméno. */
	static String klicZvyrazneni(final RadekMapy r) {
		return r.vlastni ? JObrysMapSlide.VLASTNI + r.nazev : r.id;
	}

	/** Naposledy sestavený řádek mapy; stejná mapa může být vidět ve dvou oddílech. */
	private RadekView posledniPohled(final RadekMapy r) {
		final List<RadekView> v = pohledy.get(klic(r));
		return v.get(v.size() - 1);
	}

	private int pridejRadek(final JPanel panel, final int y, final RadekMapy r) {
		radek(panel, y, r);
		if (r.skupina == null && r.matice != null && !r.vlastni) {
			final Uzel sama = new Uzel(r.nazev);
			sama.celek = r;
			pridejStahnoutVse(panel, posledniPohled(r).popis, sama);
		}
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

	/** Přidá řádek regionu do seznamu dalších map a zapíše ho pro hledání; vrací další volný řádek mřížky. */
	private int pridejRegionSeznamu(final JPanel panel, final int y, final RadekMapy r, final Uzel uzel) {
		final int pred = panel.getComponentCount();
		radek(panel, y, r);
		posledniPohled(r).nazev.setBorder(new EmptyBorder(0, 2 * ODSAZENI, 0, 0));
		hledatelne.add(new RadekHledani(bezDiakritiky(r.nazev + " " + r.skupina), uzel.nazev, false, komponentyOd(panel, pred)));
		return y + 1;
	}

	private static List<Component> komponentyOd(final JPanel panel, final int pred) {
		return new ArrayList<>(Arrays.asList(panel.getComponents()).subList(pred, panel.getComponentCount()));
	}

	/** Řádek celé země v obou stromech: s regiony je hlavičkou se šipkou, bez nich je odsazený o šířku šipky, aby názvy zemí lícovaly. */
	private int radekCelku(final JPanel panel, final int y, final Uzel u, final boolean rozbaleno, final Runnable prepni) {
		radek(panel, y, u.celek);
		final RadekView v = posledniPohled(u.celek);
		if (u.regiony.isEmpty()) {
			v.nazev.setBorder(new EmptyBorder(0, ODSAZENI, 0, 0));
		} else {
			u.hlavicka = v.nazev;
			nastavRozbaleni(v.nazev, rozbaleno, prepni);
		}
		pridejStahnoutVse(panel, v.popis, u);
		return y + 1;
	}

	/**
	 * Země v seznamu dalších map bez vlastního řádku celku: vypadá jako ostatní země (název s šipkou rozbalení); v buňce Mapa „celá země ve složce“, když je celek
	 * stažený, jinak „jen regiony“, když ji nejde stáhnout celou.
	 */
	private int radekZemeBezCelku(final JPanel panel, final int y, final Uzel u) {
		final Uzel vse = zemeVsechny.get(u.nazev);
		final String popis = vse != null && vse.celek != null && vse.celek.isVeSlozce() ? CELA_VE_SLOZCE
				: u.regiony.stream().allMatch(r -> r.id.startsWith("europe/")) ? JEN_REGIONY : "";
		return radekZemeBezCelku(panel, y, u, popis, false, null);
	}

	/** Řádek země bez celku; v oddílu Ve složce s vlastním popisem (souhrn regionů) a vlastním přepínáním, v seznamu dalších map s hledáním. */
	private int radekZemeBezCelku(final JPanel panel, final int y, final Uzel u, final String popisText, final boolean rozbaleno, final Runnable prepni) {
		final int pred = panel.getComponentCount();
		final GridBagConstraints ci = bunka(0, y, 1);
		ci.insets = new Insets(1, 0, 1, 4);
		final JLabel ikona = new JLabel(IkonyMap.pro(RadekMapy.Stav.NEMAME));
		ikona.putClientProperty(KLIC_RADKU, "země:" + u.nazev);
		panel.add(ikona, ci);
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
		if (CELA_VE_SLOZCE.equals(popisText)) {
			popis.setForeground(IkonyMap.ZELENA);
		}
		popis.setPreferredSize(new Dimension(SIRKA_POPISU, popis.getPreferredSize().height));
		final GridBagConstraints cp = bunka(2, y, 1);
		cp.weightx = 1;
		cp.insets = new Insets(1, 8, 1, 8);
		panel.add(popis, cp);
		pridejStahnoutVse(panel, popis, u);
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

	/** Odkaz „Stáhnout vše“ vedle popisu země, pokud k zemi stáhne nebo aktualizuje víc než jednu položku. */
	private void pridejStahnoutVse(final JPanel panel, final JLabel popis, final Uzel uzel) {
		final Uzel u = zemeVsechny.getOrDefault(uzel.nazev, uzel);
		if (!maStahnoutVse(u)) {
			return;
		}
		final List<String> klice = new ArrayList<>();
		for (final RadekMapy r : u.vsechnyRadky()) {
			if (r.matice != null && !r.vlastni) {
				klice.add(r.id);
			}
		}
		final GridBagConstraints cp = ((GridBagLayout) panel.getLayout()).getConstraints(popis);
		final JButton vse = odkaz(STAHNOUT_VSE, e -> {
			if (ovladani != null) {
				ovladani.stahniVse(u.nazev, u.celek == null ? null : u.celek.id, klice);
			}
		});
		nastavANechNaMiste(() -> {
			vse.setToolTipText(cokoliBezi ? BEZI_STAHOVANI : "Stáhne k zemi mapu, trasy, vrstevnice i stínování, co chybí nebo je zastaralé.");
			vse.setEnabled(!cokoliBezi);
		});
		popis.setPreferredSize(new Dimension(Math.max(40, SIRKA_POPISU - vse.getPreferredSize().width), popis.getPreferredSize().height));
		final JPanel obal = new JPanel(new BorderLayout());
		obal.setOpaque(false);
		panel.remove(popis);
		obal.add(popis, BorderLayout.CENTER);
		obal.add(vse, BorderLayout.EAST);
		panel.add(obal, cp);
	}

	/** Celá země i se staženými mapami leží mimo Evropu. */
	private boolean mimoEvropu(final Uzel u) {
		return zemeVsechny.getOrDefault(u.nazev, u).vsechnyRadky().stream().allMatch(r -> KONTINENTY_MIMO_EVROPU.contains(r.id.substring(0, Math.max(0, r.id.indexOf('/')))));
	}

	/** Země z katalogu podle názvu: celek a všechny regiony bez ohledu na oddíl. */
	private static Map<String, Uzel> zeme(final List<RadekMapy> radky) {
		final Map<String, Uzel> zeme = new HashMap<>();
		for (final RadekMapy r : radky) {
			if (!r.vlastni) {
				final Uzel u = zeme.computeIfAbsent(r.skupina != null ? r.skupina : r.nazev, Uzel::new);
				if (r.skupina != null) {
					u.regiony.add(r);
				} else {
					u.celek = r;
				}
			}
		}
		return zeme;
	}

	/**
	 * „Stáhnout vše“ má smysl, jen když stáhne víc než „Stáhnout“ u jedné položky: počítá se plán, jaký dialog nabídne se všemi částmi, a k němu běžící
	 * stahování země, aby odkaz za stahování nezmizel a popup se nepřestavoval.
	 */
	private static boolean maStahnoutVse(final Uzel u) {
		final List<String> klice = new ArrayList<>();
		final List<MatriceMap.Radek> matice = new ArrayList<>();
		int polozek = 0;
		for (final RadekMapy r : u.vsechnyRadky()) {
			if (r.matice != null && !r.vlastni) {
				klice.add(r.id);
				matice.add(r.matice);
				polozek += r.bezicVrstvy().size() + (r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje ? 1 : 0);
			}
		}
		return polozek + PlanPruvodce.sestavVse(u.celek == null ? null : u.celek.id, klice, EnumSet.allOf(MatriceMap.Cast.class), matice).getPolozky().size() > 1;
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
	/** Výchozí rozbalení oddílu podle počtu map, určené jednou při prvním zobrazení map ve složce; null, dokud nebylo co zobrazit. */
	private Boolean slozkaVychozi;
	/** Oddíl Ve složce, jak je zrovna vidět. */
	private boolean slozkaZobrazenaOtevrena;
	/** Řádky ve složce se stavem, kterým už oddíl nebo zemi samy rozbalily (stahování, chyba); rozbalené zůstanou do zavření popupu. */
	private final Set<String> pozornost = new HashSet<>();
	private boolean slozkaSamaOtevrena;
	private final Set<String> zemeSamyRozbalene = new HashSet<>();

	/** Stahuje se mapa nebo její vrstva, nebo se něco nepovedlo: takový řádek sám rozbalí oddíl i svou zemi, dokud je uživatel nesbalí. */
	private static boolean vyzadujePozornost(final RadekMapy r) {
		final RadekMapy.Stav s = r.celkovyStav();
		// Kontrola aktualizací řádek jen označí, nic se nestahuje, proto oddíl nerozbaluje.
		return s == RadekMapy.Stav.STAHUJE && !r.kontroluje || s == RadekMapy.Stav.CHYBA;
	}

	/** Nový řádek se stahováním nebo chybou (i chyba po stahování) rozbalí oddíl i svou zemi; volbu uživatele to nemění. */
	private void zaznamenejPozornost(final List<RadekMapy> veSlozce) {
		for (final RadekMapy r : veSlozce) {
			if (vyzadujePozornost(r) && pozornost.add(klic(r) + "|" + r.celkovyStav())) {
				slozkaSamaOtevrena = true;
				if (r.skupina != null) {
					zemeSamyRozbalene.add(r.skupina);
				}
			}
		}
	}

	private boolean jeSlozkaOtevrena(final int pocetMap) {
		if (slozkaOtevrena == null) {
			slozkaOtevrena = nactiStavSlozky.get().orElse(null);
		}
		if (slozkaVychozi == null && pocetMap > 0) {
			slozkaVychozi = pocetMap <= SLOZKA_ROZBALENA_DO;
		}
		return slozkaSamaOtevrena || (slozkaOtevrena != null ? slozkaOtevrena : slozkaVychozi == null || slozkaVychozi);
	}

	/** Sbalí nebo rozbalí celý oddíl Ve složce a zapamatuje si to. */
	void prepniSlozku(final boolean otevrit) {
		slozkaOtevrena = otevrit;
		slozkaSamaOtevrena &= otevrit;
		ulozStavSlozky.accept(otevrit);
		prestav();
	}

	/** Klik na zemi ve složce rozbalí nebo sbalí její stažené regiony. */
	void prepniUzelSlozky(final String nazev) {
		final boolean byla = rozbaleneSlozka.remove(nazev);
		if (!zemeSamyRozbalene.remove(nazev) && !byla) {
			rozbaleneSlozka.add(nazev);
		}
		ulozRozbalene.accept(ROZBALENE_SLOZKA, new HashSet<>(rozbaleneSlozka));
		prestav();
	}

	/** Přestaví popup po změně sbalení; výška se určí znovu podle obsahu. */
	private void prestav() {
		sestav();
		zmenaObsahu();
	}

	/** Stažené mapy po zemích: regiony pod zemí se šipkou, souhrn u země bez staženého celku; celý strom v tabulce Ve složce. */
	private int stromSlozky(final JPanel panel, final int zacatek, final List<RadekMapy> mapy) {
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
		final List<Uzel> uzlySlozky = new ArrayList<>(podleZeme.values());
		uzlySlozky.sort(Comparator.comparing(this::mimoEvropu));
		boolean mimoEvropuPridano = false;
		for (final Uzel u : uzlySlozky) {
			if (!mimoEvropuPridano && mimoEvropu(u)) {
				// Soubory uživatele nepatří pod nadpis kontinentu, jsou nad ním.
				for (final RadekMapy r : vlastni) {
					y = pridejRadek(panel, y, r);
				}
				vlastni.clear();
				panel.add(nadpisOddilu(MIMO_EVROPU), bunka(0, y++, 4));
				mimoEvropuPridano = true;
			}
			u.regiony.sort((a, b) -> cs.compare(a.nazev, b.nazev));
			final boolean rozbaleno = rozbaleneSlozka.contains(u.nazev) || zemeSamyRozbalene.contains(u.nazev);
			if (u.celek != null) {
				y = radekCelku(panel, y, u, rozbaleno, () -> prepniUzelSlozky(u.nazev));
			} else {
				y = radekZemeBezCelku(panel, y, u, souhrnRegionu(u.regiony), rozbaleno, () -> prepniUzelSlozky(u.nazev));
			}
			if (rozbaleno) {
				for (final RadekMapy r : u.regiony) {
					radek(panel, y++, r);
					posledniPohled(r).nazev.setBorder(new EmptyBorder(0, 2 * ODSAZENI, 0, 0));
				}
			}
		}
		for (final RadekMapy r : vlastni) {
			y = pridejRadek(panel, y, r);
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

	private void radek(final JPanel panel, final int y, final RadekMapy r) {
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
		final JLabel ikona = new JLabel();
		ikona.putClientProperty(KLIC_RADKU, klic(r));
		ikona.addMouseListener(nadRadkem);
		final GridBagConstraints c = bunka(0, y, 1);
		c.insets = new Insets(1, 0, 1, 4);
		panel.add(ikona, c);

		final JLabel nazev = new JLabel(r.nazev);
		nazev.addMouseListener(nadRadkem);
		// Stejná šířka sloupce názvů ve všech oddílech, aby popisy byly pod sebou.
		nazev.setPreferredSize(new Dimension(sirkaNazvu, nazev.getPreferredSize().height));
		panel.add(nazev, bunka(1, y, 1));

		final JLabel popis = maly("");
		popis.setText(zkrat(popisRadku(r), popis.getFontMetrics(popis.getFont())));
		popis.setPreferredSize(new Dimension(SIRKA_POPISU, popis.getPreferredSize().height));
		popis.addMouseListener(nadRadkem);
		final GridBagConstraints cp = bunka(2, y, 1);
		cp.weightx = 1;
		cp.insets = new Insets(1, 8, 1, 8);
		panel.add(popis, cp);

		final JPanel akce = sloupecAkce(null);
		final GridBagConstraints ca = bunka(3, y, 1);
		ca.anchor = GridBagConstraints.WEST;
		ca.fill = GridBagConstraints.NONE;
		panel.add(akce, ca);
		final boolean maVrstvy = r.matice != null && !r.vlastni;
		final JPanel[] vrstvy = new JPanel[VRSTVY.length];
		for (int i = 0; i < VRSTVY.length; i++) {
			final JPanel bunka = maVrstvy ? bunkaVrstvy(nadRadkem) : prazdnaBunka();
			vrstvy[i] = maVrstvy ? bunka : null;
			final GridBagConstraints cb = bunka(PRVNI_SLOUPEC_VRSTEV + i, y, 1);
			cb.anchor = GridBagConstraints.WEST;
			cb.fill = GridBagConstraints.NONE;
			panel.add(bunka, cb);
		}
		final RadekView v = new RadekView(ikona, nazev, popis, akce, vrstvy, nadRadkem);
		pohledy.computeIfAbsent(klic(r), k -> new ArrayList<>()).add(v);
		v.aktualizuj(r);
	}

	/** Komponenty jednoho řádku mapy v mřížce oddílu; změna stavu mapy se do nich přepíše bez přestavby popupu. */
	private final class RadekView {
		final JLabel ikona;
		final JLabel nazev;
		final JLabel popis;
		final JPanel akce;
		/** Buňky vrstev; null u řádku bez vrstev. */
		final JPanel[] vrstvy;
		/** Procenta stahovaných vrstev, přepisují se při každém průběhu. */
		final JLabel[] procenta = new JLabel[VRSTVY.length];
		final MouseAdapter nadRadkem;
		RadekMapy radek;
		/** Země připsaná za popis regionu, null když se nepíše. */
		String zeme;
		/** Stav, podle kterého jsou naplněné akce a buňky; null před prvním naplněním. */
		String stav;

		RadekView(final JLabel ikona, final JLabel nazev, final JLabel popis, final JPanel akce, final JPanel[] vrstvy, final MouseAdapter nadRadkem) {
			this.ikona = ikona;
			this.nazev = nazev;
			this.popis = popis;
			this.akce = akce;
			this.vrstvy = vrstvy;
			this.nadRadkem = nadRadkem;
		}

		/** Přepíše řádek podle nového stavu mapy; akce a buňky naplní znovu, jen když se změnil stav, jinak přepíše texty. */
		void aktualizuj(final RadekMapy r) {
			radek = r;
			final String tooltip = r.tooltip();
			ikona.setIcon(IkonyMap.pro(r.stav));
			ikona.setToolTipText(tooltip);
			nazev.setToolTipText(tooltip);
			popis.setToolTipText(tooltip);
			popis.setText(r.id != null && r.id.equals(rusenaMapa) ? RUSI_SE : zkrat(popisRadku(r) + (zeme == null ? "" : " · " + zeme), popis.getFontMetrics(popis.getFont())));
			popis.setForeground(r.stav == RadekMapy.Stav.CHYBA ? IkonyMap.CERVENA : SEDY_TEXT);
			final String novy = stavRadku(r);
			if (novy.equals(stav)) {
				for (int i = 0; i < VRSTVY.length; i++) {
					if (procenta[i] != null) {
						final MatriceMap.Bunka b = r.bunka(VRSTVY[i]);
						procenta[i].setText(procenta(b));
						procenta[i].setToolTipText(b.getChyba());
					}
				}
				return;
			}
			stav = novy;
			akce.removeAll();
			final JButton a = JMapyPopup.this.akce(r, mapaBezi);
			if (a != null) {
				a.addMouseListener(nadRadkem);
				akce.add(a);
			}
			akce.revalidate();
			akce.repaint();
			for (int i = 0; i < VRSTVY.length; i++) {
				if (vrstvy[i] != null) {
					naplnBunku(this, i);
					vrstvy[i].revalidate();
					vrstvy[i].repaint();
				}
			}
		}
	}

	/** Akce mapy ve sloupci pevné šířky, aby sloupce vrstev lícovaly i v řádcích bez akce. */
	private static JPanel sloupecAkce(final JButton akce) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SIRKA_AKCE, odkaz(STAHNOUT, e -> {}).getPreferredSize().height));
		if (akce != null) {
			p.add(akce);
		}
		return p;
	}

	private static JPanel prazdnaBunka() {
		final JPanel p = new JPanel();
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SIRKA_BUNKY, odkaz(STAHNOUT, e -> {}).getPreferredSize().height));
		return p;
	}

	/** Prázdná buňka vrstvy v řádku mapy; obsah podle stavu dodá {@link #naplnBunku}. */
	private static JPanel bunkaVrstvy(final MouseAdapter nadRadkem) {
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		p.setOpaque(false);
		p.setPreferredSize(new Dimension(SIRKA_BUNKY, odkaz(STAHNOUT, e -> {}).getPreferredSize().height));
		p.addMouseListener(nadRadkem);
		return p;
	}

	/** Buňka vrstvy v řádku mapy: stav a velikost, akce podle stavu (stáhnout, aktualizovat, zrušit, smazat). */
	private void naplnBunku(final RadekView v, final int sloupec) {
		final MatriceMap.Cast cast = VRSTVY[sloupec];
		final MatriceMap.Bunka b = v.radek.bunka(cast);
		final JPanel p = v.vrstvy[sloupec];
		p.removeAll();
		v.procenta[sloupec] = null;
		if (b.getStav() == MatriceMap.StavBunky.NENI) {
			final JLabel nic = maly(NENI_VRSTVA);
			nic.setBorder(new EmptyBorder(0, 6, 0, 0));
			p.add(nic);
			return;
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
			zakazDokudNeniMisto(aktualizovat, b, vrstvyPlne);
			p.add(aktualizovat);
			break;
		}
		case STAHUJE: {
			p.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.STAHUJE)));
			final JLabel procenta = maly(procenta(b));
			procenta.setToolTipText(b.getChyba());
			v.procenta[sloupec] = procenta;
			p.add(procenta);
			// Za ověřování staženého souboru se položka nezrušuje; zrušit jde jen celá dávka.
			if (!b.isOveruje()) {
				final JButton zrusit = odkaz(ZRUSIT, e -> {
					if (ovladani != null) {
						ovladani.zrusStahovani();
					}
				});
				p.add(zrusit);
			}
			return;
		}
		case CHYBA: {
			p.add(new JLabel(IkonyMap.pro(RadekMapy.Stav.CHYBA)));
			final JButton znovu = odkaz(ZKUSIT_ZNOVU, e -> stahniVrstvu(b));
			znovu.setToolTipText(nazev + ": " + (b.getChyba() == null ? "stažení se nepovedlo" : b.getChyba()));
			zakazDokudNeniMisto(znovu, b, vrstvyPlne);
			p.add(znovu);
			break;
		}
		case KE_STAZENI:
		default: {
			final JButton stahnout = odkaz("↓ " + velikost, e -> stahniVrstvu(b));
			stahnout.setToolTipText(nazev + ": stáhnout" + (velikost.isEmpty() ? "" : ", " + velikost) + (b.getStazeno() > 0 ? " (rozpracováno)" : ""));
			zakazDokudNeniMisto(stahnout, b, vrstvyPlne);
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
			if (cokoliBezi) {
				smazat.setEnabled(false);
				smazat.setToolTipText(BEHEM_STAHOVANI_NEJDE_MAZAT);
			}
			p.add(smazat);
		}
	}

	private void zakazDokudNeniMisto(final JButton b, final MatriceMap.Bunka bunka, final boolean stahujeSe) {
		if (stahujeSe) {
			b.setEnabled(false);
			b.setToolTipText(STAHUJE_SE_JINA);
		} else if (nedostatekMista(bunka)) {
			b.setEnabled(false);
			b.setToolTipText("Na disku je volných jen " + RadekMapy.velikost(Math.max(0, volneMisto - rozdelano)) + ", potřebuje asi " + RadekMapy.velikost(potrebneMisto(bunka)) + ".");
		}
	}

	/** „37 %“ při stahování, „Ověřuji 37 %“ při ověřování staženého souboru. */
	static String procenta(final MatriceMap.Bunka b) {
		if (b.getVelikost() <= 0) {
			return b.isOveruje() ? "Ověřuji…" : "";
		}
		final long stazeno = b.getStazeno();
		if (b.isOveruje() && (stazeno <= 0 || stazeno >= b.getVelikost())) {
			// Průběh nic neříká (začátek nebo hotovo bez hlášení průběhu): bez procent.
			return "Ověřuji…";
		}
		final String p = Math.min(100, stazeno * 100 / b.getVelikost()) + " %";
		return b.isOveruje() ? "Ověřuji " + p : p;
	}

	static String nazevVrstvy(final MatriceMap.Cast cast) {
		switch (cast) {
		case TRASY:
			return TextyMap.TRASY;
		case VRSTEVNICE:
			return TextyMap.VRSTEVNICE;
		case STINOVANI:
		default:
			return TextyMap.STINOVANI_TERENU;
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
			if (r.kontroluje || r.overuje) {
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
				for (final RadekView v : pohledy.getOrDefault(klic(r), Collections.emptyList())) {
					v.popis.setText(RUSI_SE);
				}
			});
			return zrusit;
		case AKTUALNI:
			return null;
		case NOVEJSI:
			if (r.kontroluje) {
				return null;
			}
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
			b.setToolTipText("Na disku je volných jen " + RadekMapy.velikost(Math.max(0, volneMisto - rozdelano)) + ", mapa potřebuje asi " + RadekMapy.velikost(potrebneMisto(r)) + ".");
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

	private static TabulkaRadku panelRadku() {
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
