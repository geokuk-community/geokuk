package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import cz.geokuk.plugins.mapy.pruvodce.NabidkaZemi.Zeme;
import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky;
import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;

/** Průvodce nastavením map: země, podklad, vrstvy, shrnutí. Každý krok jde přeskočit i vrátit; nic se nezmění, dokud uživatel nepotvrdí poslední krok. */
public final class JPruvodceMap extends JDialog {

	private static final long serialVersionUID = 1L;

	static final String NADPIS = "Nastavení map";
	static final String[] KROKY = { "Země", "Podklad", "Vrstvy", "Hotovo" };
	static final String PRESKOCIT = "Přeskočit";
	static final String ZPET = "◂ Zpět";
	static final String DAL = "Dál ▸";
	static final String JEN_PREPNOUT = "Přepnout bez stahování";
	static final String STAHNOUT_A_PREPNOUT = "Stáhnout a přepnout";
	static final String PREPNOUT = "Přepnout";
	static final String VICE_ZEMI = "Vybrat víc zemí";
	static final String VSECHNY_ZEME = "Zobrazit všechny země";

	private static final Color SEDY = new Color(90, 90, 90);
	private static final Color CERVENY = new Color(0xC6, 0x28, 0x28);

	private final transient PruvodceKontext kontext;
	private final transient List<Zeme> zeme;
	private final transient List<Zeme> regiony;
	private final transient List<Zeme> okoli;
	private boolean vsechnyZeme;
	private String dotaz = "";
	private transient DefaultComboBoxModel<Zeme> modelJine;
	private transient JPanel seznamZemi;
	/** Nabídka se právě plní: události combo boxu se ignorují, aby se plněním neměnil výběr. */
	private boolean plniNabidku;
	private final transient Set<Zeme> vybrane = new LinkedHashSet<>();
	private boolean viceZemi;
	private PodkladPruvodce podklad;
	private final transient Set<Cast> vrstvy = EnumSet.copyOf(PruvodceVolba.vychoziVrstvy());
	private int krok;
	private final JPanel stred = new JPanel(new BorderLayout());
	private final JPanel kroky = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
	private final JLabel souhrn = new JLabel(" ");
	private final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

	public JPruvodceMap(final Frame rodic, final PruvodceKontext kontext) {
		super(rodic, NADPIS, true);
		this.kontext = kontext;
		this.zeme = NabidkaZemi.sestav(kontext.getKatalog());
		this.regiony = NabidkaZemi.regiony(kontext.getKatalog());
		final List<Zeme> vse = new ArrayList<>(zeme);
		vse.addAll(regiony);
		this.okoli = NabidkaZemi.okoli(vse);
		predvyber();
		vsechnyZeme = !vybrane.isEmpty() && !okoli.containsAll(vybrane) && !zeme.subList(0, Math.min(2, zeme.size())).containsAll(vybrane);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		final JPanel obsah = new JPanel(new BorderLayout(0, 10));
		obsah.setBorder(new EmptyBorder(12, 16, 12, 16));
		final JPanel hlava = new JPanel(new BorderLayout());
		final JLabel nadpis = new JLabel(NADPIS);
		nadpis.setFont(nadpis.getFont().deriveFont(Font.BOLD, 16f));
		hlava.add(nadpis, BorderLayout.WEST);
		final JButton preskocit = new JButton(PRESKOCIT);
		preskocit.addActionListener(e -> dispose());
		hlava.add(preskocit, BorderLayout.EAST);
		kroky.setOpaque(false);
		final JPanel nahore = new JPanel(new BorderLayout(0, 6));
		nahore.add(hlava, BorderLayout.NORTH);
		nahore.add(kroky, BorderLayout.SOUTH);
		obsah.add(nahore, BorderLayout.NORTH);
		obsah.add(stred, BorderLayout.CENTER);
		final JPanel pata = new JPanel(new BorderLayout(12, 0));
		souhrn.setForeground(SEDY);
		pata.add(souhrn, BorderLayout.CENTER);
		pata.add(tlacitka, BorderLayout.EAST);
		obsah.add(pata, BorderLayout.SOUTH);
		setContentPane(obsah);
		zobraz(0);
		setMinimumSize(new Dimension(640, 420));
		pack();
		setLocationRelativeTo(rodic);
	}

	/** Předvyplněná země podle jazykového nastavení systému, jinak Česko. */
	private void predvyber() {
		final String podleSystemu = new Locale("", Locale.getDefault().getCountry()).getDisplayCountry(new Locale("cs"));
		Zeme vychozi = zeme.stream().filter(z -> z.getNazev().equalsIgnoreCase(podleSystemu)).findFirst().orElse(null);
		if (vychozi == null && !zeme.isEmpty()) {
			vychozi = zeme.get(0);
		}
		if (vychozi != null) {
			vybrane.add(vychozi);
		}
	}

	private List<String> klice() {
		final List<String> k = new ArrayList<>();
		for (final Zeme z : vybrane) {
			k.addAll(z.getKlice());
		}
		return k;
	}

	private PruvodceVolba volba() {
		return new PruvodceVolba(klice(), podklad, vrstvy);
	}

	private PlanPruvodce plan(final PodkladPruvodce p, final Set<Cast> v) {
		return PlanPruvodce.sestav(new PruvodceVolba(klice(), p, v), kontext.matice());
	}

	// ----------------------------------------------------------------------------------------------------------------

	void zobraz(final int novy) {
		krok = novy;
		kroky.removeAll();
		for (int i = 0; i < KROKY.length; i++) {
			final JLabel l = new JLabel((i + 1) + " " + KROKY[i]);
			l.setFont(l.getFont().deriveFont(i == krok ? Font.BOLD : Font.PLAIN));
			l.setForeground(i == krok ? Color.BLACK : SEDY);
			kroky.add(l);
		}
		stred.removeAll();
		tlacitka.removeAll();
		souhrn.setForeground(SEDY);
		souhrn.setText(" ");
		if (krok > 0) {
			final JButton zpet = new JButton(ZPET);
			zpet.addActionListener(e -> zobraz(krok - 1));
			tlacitka.add(zpet);
		}
		switch (krok) {
		case 0:
			stred.add(krokZeme(), BorderLayout.NORTH);
			break;
		case 1:
			stred.add(krokPodklad(), BorderLayout.NORTH);
			break;
		case 2:
			stred.add(krokVrstvy(), BorderLayout.NORTH);
			break;
		case 3:
		default:
			stred.add(krokHotovo(), BorderLayout.NORTH);
			break;
		}
		if (krok < 3) {
			final JButton dal = new JButton(DAL);
			dal.setEnabled(krok == 0 ? !vybrane.isEmpty() : krok != 1 || podklad != null);
			dal.addActionListener(e -> zobraz(krok + 1));
			dalsi = dal;
			tlacitka.add(dal);
			getRootPane().setDefaultButton(dal);
		}
		kroky.revalidate();
		stred.revalidate();
		tlacitka.revalidate();
		revalidate();
		repaint();
	}

	private JButton dalsi;

	private static JLabel nadpisKroku(final String text) {
		final JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
		l.setBorder(new EmptyBorder(0, 0, 8, 0));
		return l;
	}

	// 1 – země ------------------------------------------------------------------------------------------------------

	private JComponent krokZeme() {
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		modelJine = null;
		seznamZemi = null;
		p.add(nadpisKroku("Kde budete mapy používat?"));
		p.add(hledaniZemi());
		final JCheckBox vice = new JCheckBox(VICE_ZEMI, viceZemi);
		if (!viceZemi) {
			final ButtonGroup skupina = new ButtonGroup();
			final List<Zeme> horni = zeme.subList(0, Math.min(2, zeme.size()));
			for (final Zeme z : horni) {
				final JRadioButton r = new JRadioButton(z.getNazev(), vybrane.contains(z));
				r.addActionListener(e -> vyber(z));
				skupina.add(r);
				p.add(r);
			}
			final JPanel jina = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			final JRadioButton rJina = new JRadioButton("Jiná země:");
			skupina.add(rJina);
			final DefaultComboBoxModel<Zeme> model = new DefaultComboBoxModel<>();
			modelJine = model;
			plniNabidku = true;
			naplnJine(horni);
			plniNabidku = false;
			final JComboBox<Zeme> combo = new JComboBox<>(model);
			combo.setRenderer(new DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;

				@Override
				public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean sel, final boolean focus) {
					return super.getListCellRendererComponent(list, value == null ? "" : ((Zeme) value).getNazev(), index, sel, focus);
				}
			});
			final boolean jinaVybrana = vybrane.size() == 1 && !horni.contains(vybrane.iterator().next());
			rJina.setSelected(jinaVybrana);
			if (jinaVybrana) {
				combo.setSelectedItem(vybrane.iterator().next());
			}
			combo.setEnabled(jinaVybrana);
			rJina.addActionListener(e -> {
				combo.setEnabled(true);
				vyber((Zeme) combo.getSelectedItem());
			});
			combo.addActionListener(e -> {
				if (rJina.isSelected() && !plniNabidku) {
					vyber((Zeme) combo.getSelectedItem());
				}
			});
			for (final JRadioButton r : Collections.list(skupina.getElements()).stream().map(b -> (JRadioButton) b).toArray(JRadioButton[]::new)) {
				if (r != rJina) {
					r.addActionListener(e -> combo.setEnabled(false));
				}
			}
			jina.add(rJina);
			jina.add(combo);
			p.add(jina);
		} else {
			final JPanel seznam = new JPanel();
			seznam.setLayout(new BoxLayout(seznam, BoxLayout.Y_AXIS));
			seznamZemi = seznam;
			naplnSeznam();
			final JScrollPane sp = new JScrollPane(seznam);
			sp.setPreferredSize(new Dimension(420, 200));
			sp.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(sp);
		}
		p.add(Box.createVerticalStrut(8));
		vice.addActionListener(e -> {
			viceZemi = vice.isSelected();
			if (!viceZemi && vybrane.size() > 1) {
				final Zeme prvni = vybrane.iterator().next();
				vybrane.clear();
				vybrane.add(prvni);
			}
			zobraz(0);
		});
		p.add(vice);
		p.add(Box.createVerticalStrut(10));
		final JLabel napoveda = new JLabel("Výběr jde později změnit: Mapy > Průvodce nastavením map.");
		napoveda.setForeground(SEDY);
		p.add(napoveda);
		for (final Component c : p.getComponents()) {
			if (c instanceof JComponent) {
				((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
			}
		}
		return p;
	}

	/** Nabídka zemí a regionů: bez hledání Česko a okolí, nebo všechny země; hledání prohledá země i regiony. */
	private List<Zeme> nabidka() {
		if (!dotaz.trim().isEmpty()) {
			final List<Zeme> vse = new ArrayList<>(zeme);
			vse.addAll(regiony);
			return vse.stream().filter(z -> NabidkaZemi.vyhovuje(z, dotaz)).collect(java.util.stream.Collectors.toList());
		}
		return vsechnyZeme ? zeme : okoli;
	}

	private JComponent hledaniZemi() {
		final JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		r.setBorder(new EmptyBorder(0, 0, 6, 0));
		final JTextField hledat = new JTextField(dotaz, 16);
		hledat.setToolTipText("Hledat zemi nebo region…");
		hledat.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(final javax.swing.event.DocumentEvent e) {
				zmenaHledani();
			}

			@Override
			public void removeUpdate(final javax.swing.event.DocumentEvent e) {
				zmenaHledani();
			}

			@Override
			public void changedUpdate(final javax.swing.event.DocumentEvent e) {
				zmenaHledani();
			}

			private void zmenaHledani() {
				dotaz = hledat.getText();
				obnovNabidku();
			}
		});
		r.add(new JLabel("Hledat zemi nebo region:"));
		r.add(hledat);
		final JCheckBox vsechny = new JCheckBox(VSECHNY_ZEME, vsechnyZeme);
		vsechny.addActionListener(e -> {
			vsechnyZeme = vsechny.isSelected();
			obnovNabidku();
		});
		r.add(vsechny);
		return r;
	}

	private void obnovNabidku() {
		if (modelJine != null) {
			final Object vybrano = modelJine.getSelectedItem();
			plniNabidku = true;
			try {
				naplnJine(zeme.subList(0, Math.min(2, zeme.size())));
				modelJine.setSelectedItem(vybrano != null && modelJine.getIndexOf(vybrano) >= 0 ? vybrano : null);
			} finally {
				plniNabidku = false;
			}
		}
		if (seznamZemi != null) {
			naplnSeznam();
			seznamZemi.revalidate();
			seznamZemi.repaint();
		}
	}

	private void naplnJine(final List<Zeme> horni) {
		modelJine.removeAllElements();
		nabidka().stream().filter(z -> !horni.contains(z)).forEach(modelJine::addElement);
	}

	private void naplnSeznam() {
		seznamZemi.removeAll();
		final List<Zeme> zobrazene = new ArrayList<>(nabidka());
		vybrane.stream().filter(z -> !zobrazene.contains(z)).forEach(zobrazene::add);
		for (final Zeme z : zobrazene) {
			final JCheckBox c = new JCheckBox(z.getNazev(), vybrane.contains(z));
			c.addActionListener(e -> {
				if (c.isSelected()) {
					vybrane.add(z);
				} else {
					vybrane.remove(z);
				}
				aktualizujDal();
			});
			seznamZemi.add(c);
		}
	}

	private void vyber(final Zeme z) {
		vybrane.clear();
		if (z != null) {
			vybrane.add(z);
		}
		aktualizujDal();
	}

	private void aktualizujDal() {
		if (dalsi != null) {
			dalsi.setEnabled(!vybrane.isEmpty());
		}
	}

	// 2 – podklad ---------------------------------------------------------------------------------------------------

	private JComponent krokPodklad() {
		final JPanel p = new JPanel(new BorderLayout());
		final List<String> klice = klice();
		p.add(nadpisKroku("Jaká mapa má být základem?"), BorderLayout.NORTH);
		final JPanel mrizka = new JPanel(new GridLayout(0, 3, 10, 10));
		final ButtonGroup skupina = new ButtonGroup();
		if (podklad == null || !podklad.jeDostupny(klice)) {
			podklad = PodkladPruvodce.TURISTICKA_OFFLINE;
		}
		for (final PodkladPruvodce pp : PodkladPruvodce.values()) {
			if (!pp.jeDostupny(klice)) {
				continue;
			}
			final long mb = pp.isOffline() ? plan(pp, EnumSet.noneOf(Cast.class)).getCelkovaVelikost() : 0;
			final String popis = pp.isOffline() ? (mb > 0 ? "offline · " + velikost(mb) : "offline · staženo") : "online";
			final JToggleButton b = new JToggleButton("<html><b>" + pp.getNazev() + "</b><br><span style='color:#5a5a5a'>" + popis + "</span></html>", pp == podklad);
			b.setHorizontalAlignment(SwingConstants.LEFT);
			b.setPreferredSize(new Dimension(190, 54));
			b.addActionListener(e -> {
				podklad = pp;
				zobrazSouhrnStazeni(false);
			});
			skupina.add(b);
			mrizka.add(b);
		}
		p.add(mrizka, BorderLayout.CENTER);
		zobrazSouhrnStazeni(false);
		return p;
	}

	/** „Stáhne se zatím: X MB“ pod kroky podklad a vrstvy. */
	private void zobrazSouhrnStazeni(final boolean zVrstvami) {
		if (podklad == null) {
			return;
		}
		final long b = plan(podklad, zVrstvami ? vrstvy : EnumSet.noneOf(Cast.class)).getCelkovaVelikost();
		souhrn.setText((zVrstvami ? "Stáhne se celkem: " : "Stáhne se zatím: ") + (b > 0 ? velikost(b) : "nic"));
	}

	// 3 – vrstvy ----------------------------------------------------------------------------------------------------

	private JComponent krokVrstvy() {
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.add(nadpisKroku("Co ještě přidat do mapy?"));
		final List<MatriceMap.Radek> matice = kontext.matice();
		for (final Cast c : new Cast[] { Cast.TRASY, Cast.VRSTEVNICE, Cast.STINOVANI }) {
			final boolean mamePro = klice().stream().anyMatch(k -> matice.stream().anyMatch(r -> r.getKlic().equals(k) && r.getBunka(c).getStav() != StavBunky.NENI));
			final String duvod = PlanPruvodce.duvodNedostupnosti(podklad, c);
			final long mb = PlanPruvodce.sestav(new PruvodceVolba(klice(), podklad, EnumSet.of(c)), matice).getPolozky().stream().filter(x -> x.getCast() == c).mapToLong(Polozka::getVelikost).sum();
			final boolean dostupna = duvod == null && mamePro;
			final String text = nazev(c) + (dostupna ? (mb > 0 ? " – " + velikost(mb) : " – staženo") : "");
			final JCheckBox cb = new JCheckBox(text, dostupna && vrstvy.contains(c));
			cb.setEnabled(dostupna);
			final JLabel poznamka = new JLabel(dostupna ? " " : "  " + (duvod != null ? duvod : "Pro vybranou zemi zatím není."));
			poznamka.setForeground(SEDY);
			poznamka.setFont(poznamka.getFont().deriveFont(Font.PLAIN, 11f));
			cb.addActionListener(e -> {
				if (cb.isSelected()) {
					vrstvy.add(c);
				} else {
					vrstvy.remove(c);
				}
				zobrazSouhrnStazeni(true);
			});
			cb.setAlignmentX(Component.LEFT_ALIGNMENT);
			poznamka.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(cb);
			p.add(poznamka);
			p.add(Box.createVerticalStrut(6));
			if (!dostupna) {
				vrstvy.remove(c);
			}
		}
		zobrazSouhrnStazeni(true);
		return p;
	}

	static String nazev(final Cast c) {
		switch (c) {
		case TRASY:
			return "Trasy";
		case VRSTEVNICE:
			return "Vrstevnice";
		case STINOVANI:
		default:
			return "Stínování terénu";
		}
	}

	// 4 – shrnutí ---------------------------------------------------------------------------------------------------

	private JComponent krokHotovo() {
		final PlanPruvodce plan = PlanPruvodce.sestav(volba(), kontext.matice());
		final JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.add(nadpisKroku(plan.jeHotovo() ? "Vše potřebné už máte stažené" : "Stáhne se:"));
		for (final Polozka pol : plan.getPolozky()) {
			final JLabel l = new JLabel("   " + pol.getNazev() + (pol.getVelikost() > 0 ? " – " + velikost(pol.getVelikost()) : ""));
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(l);
		}
		p.add(Box.createVerticalStrut(10));
		final long volne = kontext.volneMisto();
		final long potrebuje = plan.jeHotovo() ? 0 : plan.getCelkovaVelikost() + SpravaMapModel.REZERVA_MISTA;
		final boolean misto = volne < 0 || potrebuje <= volne;
		final JLabel soucet = new JLabel(plan.jeHotovo() ? " " : "Celkem " + velikost(plan.getCelkovaVelikost()) + (volne >= 0 ? " · volno na disku " + velikost(volne) : ""));
		soucet.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(soucet);
		final JLabel prepne = new JLabel("Přepne se na: " + podklad.getNazev() + (podklad.isOffline() ? " (offline)" : "") + vrstvyText());
		prepne.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.add(prepne);
		if (!misto) {
			final JLabel malo = new JLabel("Na disku není dost místa (potřeba asi " + velikost(potrebuje) + ").");
			malo.setForeground(CERVENY);
			malo.setAlignmentX(Component.LEFT_ALIGNMENT);
			p.add(Box.createVerticalStrut(6));
			p.add(malo);
		}
		if (!plan.jeHotovo()) {
			final JButton jen = new JButton(JEN_PREPNOUT);
			final boolean cekaNaMapu = !plan.prepnoutHned();
			jen.setEnabled(!cekaNaMapu);
			if (cekaNaMapu) {
				jen.setToolTipText("Offline mapa se musí nejdřív stáhnout, jinak by zůstala prázdná.");
			}
			jen.addActionListener(e -> {
				dispose();
				kontext.prepni(volba());
			});
			tlacitka.add(jen);
		}
		final JButton hotovo = new JButton(plan.jeHotovo() ? PREPNOUT : STAHNOUT_A_PREPNOUT);
		hotovo.setEnabled(misto);
		hotovo.addActionListener(e -> {
			if (!plan.jeHotovo() && potrebujePotvrzeni(plan) && !potvrzeni.test(plan)) {
				return;
			}
			dispose();
			if (plan.jeHotovo()) {
				kontext.prepni(volba());
			} else {
				SpousteniPruvodce.spust(kontext, plan);
			}
		});
		tlacitka.add(hotovo);
		getRootPane().setDefaultButton(hotovo);
		return p;
	}

	/** Od kolika základních map a bajtů se před stažením ptáme, jestli to uživatel opravdu chce. */
	static final int POTVRZENI_MAP = 5;
	static final long POTVRZENI_BAJTU = 10_000_000_000L;

	static boolean potrebujePotvrzeni(final PlanPruvodce plan) {
		return plan.getPolozky().stream().filter(p -> p.getCast() == Cast.MAPA).count() > POTVRZENI_MAP || plan.getCelkovaVelikost() > POTVRZENI_BAJTU;
	}

	/** Zeptá se před velkým stahováním; v testech jde nahradit. */
	transient java.util.function.Predicate<PlanPruvodce> potvrzeni = plan -> {
		final long map = plan.getPolozky().stream().filter(p -> p.getCast() == Cast.MAPA).count();
		return JOptionPane.showConfirmDialog(this, "Opravdu stáhnout " + map + " " + (map == 1 ? "mapu" : map < 5 ? "mapy" : "map") + ", " + velikost(plan.getCelkovaVelikost()) + "?", "Nastavení map", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
	};

	private String vrstvyText() {
		final List<String> v = new ArrayList<>();
		for (final Cast c : vrstvy) {
			if (PlanPruvodce.jeVrstvaDostupna(podklad, c)) {
				v.add(nazev(c).toLowerCase(Locale.ROOT));
			}
		}
		return v.isEmpty() ? "" : " + " + String.join(", ", v);
	}

	static String velikost(final long bajtu) {
		if (bajtu >= 1_000_000_000L) {
			return String.format(new Locale("cs"), "%.1f GB", bajtu / 1e9);
		}
		return String.format(new Locale("cs"), "%d MB", Math.max(1, Math.round(bajtu / 1e6)));
	}
}
