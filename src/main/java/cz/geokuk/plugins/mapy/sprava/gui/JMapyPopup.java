package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.LongSupplier;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

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
	static final String WEB = "Další offline mapy:";
	static final String STAHNOUT = "Stáhnout";
	static final String POKRACOVAT = "Pokračovat";
	static final String AKTUALIZOVAT = "Aktualizovat";
	static final String STAHNOUT_ZNOVU = "Stáhnout znovu";
	static final String ZKUSIT_ZNOVU = "Zkusit znovu";
	static final String ZRUSIT = "Zrušit";
	static final String ZKONTROLOVAT = "Zkontrolovat aktualizace";
	static final String ZKONTROLOVAT_TIP = "Zjistí na serveru, jestli jsou pro stažené mapy novější data.";
	static final String OTEVRIT_SLOZKU = "Otevřít složku";
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

	/** Delší popis (třeba text chyby) se zkrátí, celý je v tooltipu. */
	static final int SIRKA_POPISU = 240;

	/** Nejvyšší výška seznamu dalších map, pak se posouvá. */
	static final int VYSKA_SEZNAMU = 220;

	private static final Color SEDY_TEXT = new Color(90, 90, 90);
	private static final Color ODKAZ = new Color(0x1A5FB4);

	private OvladaniMap ovladani;
	/** Klíč řádku, jehož obrys je teď v mapě zvýrazněný; null, když žádný. */
	private String zvyraznena;
	private List<RadekMapy> radky = Collections.emptyList();
	/** Ikona, název a popis podle id řádku, aby šly při stahování obnovit bez přestavění popupu. */
	private final Map<String, JLabel[]> popisy = new HashMap<>();
	private final JCheckBox obrys = new JCheckBox(OBRYS);
	private JScrollPane seznam;
	private Runnable poZmeneObsahu = () -> {};
	/** Mapa, jejíž stahování uživatel zrušil a čeká se na potvrzení; null, když nic. */
	private String rusenaMapa;
	private final OdhadStahovani odhad = new OdhadStahovani();
	/** Čas pro odhad stahování; v testech nahrazený. */
	LongSupplier hodiny = () -> System.nanoTime() / 1_000_000;
	/** Volné místo na disku se složkou offline map v bajtech, -1 neznámé. */
	private long volneMisto = -1;

	public JMapyPopup() {
		super(new GridBagLayout());
		setBorder(new CompoundBorder(new LineBorder(Color.GRAY), new EmptyBorder(6, 8, 6, 8)));
		obrys.setFont(obrys.getFont().deriveFont(Font.PLAIN, 11f));
		obrys.setFocusable(false);
		obrys.addActionListener(e -> {
			if (ovladani != null) {
				ovladani.setObrysPokryti(obrys.isSelected());
			}
		});
		addHierarchyListener(e -> {
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
				zvyrazni(null);
			}
		});
		sestav();
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
		sestav();
	}

	public void setObrysZapnuty(final boolean zapnuty) {
		obrys.setSelected(zapnuty);
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
			}
			if (sirsi) {
				revalidate();
				poZmeneObsahu.run();
			}
			return;
		}
		rusenaMapa = null;
		final int posun = seznam == null ? 0 : seznam.getVerticalScrollBar().getValue();
		sestav();
		if (seznam != null) {
			seznam.getVerticalScrollBar().setValue(posun);
		}
		poZmeneObsahu.run();
	}

	List<RadekMapy> getRadky() {
		return radky;
	}

	private List<String> struktura(final List<RadekMapy> radky) {
		final List<String> s = new ArrayList<>();
		for (final RadekMapy r : radky) {
			s.add(klic(r) + "|" + r.stav + "|" + r.proZobrazeneMisto + "|" + r.rozpracovano + "|" + r.kontroluje + "|" + r.overuje + "|" + (r.chyba != null) + "|" + r.zkontrolovano + "|" + nedostatekMista(r));
		}
		return s;
	}

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
		}
		return s;
	}

	/** Mapa ke stažení nebo k aktualizaci se na disk nevejde (s toutéž rezervou jako ve stahovači). */
	private boolean nedostatekMista(final RadekMapy r) {
		return volneMisto >= 0 && potrebneMisto(r) > volneMisto;
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
		final FontMetrics fm = getFontMetrics(new JLabel().getFont());
		sirkaNazvu = 0;
		for (final RadekMapy r : radky) {
			sirkaNazvu = Math.max(sirkaNazvu, fm.stringWidth(r.nazev) + 2);
		}
		final boolean stahujeSe = radky.stream().anyMatch(r -> r.stav == RadekMapy.Stav.STAHUJE && !r.kontroluje);
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

		final List<RadekMapy> veSlozce = new ArrayList<>();
		final List<RadekMapy> proMisto = new ArrayList<>();
		final List<RadekMapy> dalsi = new ArrayList<>();
		for (final RadekMapy r : radky) {
			if (r.isVeSlozce()) {
				veSlozce.add(r);
			} else if (r.proZobrazeneMisto || r.stav == RadekMapy.Stav.STAHUJE) {
				proMisto.add(r);
			} else {
				dalsi.add(r);
			}
		}

		gbc.gridy++;
		final JPanel oddilSlozka = new JPanel(new BorderLayout());
		oddilSlozka.setOpaque(false);
		oddilSlozka.add(nadpisOddilu(VE_SLOZCE), BorderLayout.WEST);
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
		for (final RadekMapy r : veSlozce) {
			radek(slozka, y++, r, stahujeSe);
		}
		gbc.gridy++;
		add(slozka, gbc);

		if (!proMisto.isEmpty()) {
			gbc.gridy++;
			add(nadpisOddilu(PRO_MISTO), gbc);
			final JPanel misto = panelRadku();
			y = 0;
			for (final RadekMapy r : proMisto) {
				radek(misto, y++, r, stahujeSe);
			}
			gbc.gridy++;
			add(misto, gbc);
		}

		seznam = null;
		if (!dalsi.isEmpty()) {
			gbc.gridy++;
			add(nadpisOddilu(DALSI), gbc);
			final JPanel ostatni = panelRadku();
			y = 0;
			String skupina = null;
			for (final RadekMapy r : dalsi) {
				if (r.skupina != null && !r.skupina.equals(skupina)) {
					final JLabel zeme = maly(r.skupina);
					zeme.setFont(zeme.getFont().deriveFont(Font.BOLD));
					zeme.setBorder(new EmptyBorder(3, 0, 0, 0));
					ostatni.add(zeme, bunka(0, y++, 4));
				}
				skupina = r.skupina;
				radek(ostatni, y++, r, stahujeSe);
			}
			final JPanel obal = new JPanel(new BorderLayout());
			obal.setOpaque(false);
			obal.add(ostatni, BorderLayout.NORTH);
			seznam = new JScrollPane(obal, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
				private static final long serialVersionUID = 1L;

				@Override
				public Dimension getPreferredSize() {
					final Dimension d = super.getPreferredSize();
					d.height = Math.min(d.height, VYSKA_SEZNAMU);
					d.width += getVerticalScrollBar().getPreferredSize().width;
					return d;
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

		gbc.gridy++;
		gbc.insets = new Insets(6, 0, 0, 0);
		add(new JSeparator(), gbc);
		gbc.insets = new Insets(2, 0, 0, 0);
		if (ovladani != null && ovladani.isObrysDostupny()) {
			gbc.gridy++;
			add(obrys, gbc);
		}
		gbc.gridy++;
		final JPanel web = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		web.setOpaque(false);
		web.add(maly(WEB));
		web.add(odkaz("osm.paws.cz", URL_PAWS));
		web.add(odkaz("OpenAndroMaps", URL_OAM));
		add(web, gbc);
		revalidate();
		repaint();
	}

	/** Klíč řádku pro zvýraznění obrysu mapy: id z katalogu, u souboru uživatele jeho jméno. */
	static String klicZvyrazneni(final RadekMapy r) {
		return r.vlastni ? JObrysMapSlide.VLASTNI + r.nazev : r.id;
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
		ca.anchor = GridBagConstraints.EAST;
		ca.fill = GridBagConstraints.NONE;
		if (akce != null) {
			panel.add(akce, ca);
		} else {
			panel.add(Box.createVerticalStrut(odkaz(STAHNOUT, e -> {}).getPreferredSize().height), ca);
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
		final JPanel p = new JPanel(new GridBagLayout());
		p.setOpaque(false);
		return p;
	}

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
