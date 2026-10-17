package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;

/** Souhrn před stažením všeho k zemi: co se stáhne, kolik to zabere a kolik je volného místa; části jdou odškrtnout. */
public final class JStahnoutVse extends JDialog {

	private static final long serialVersionUID = 1L;

	private static final Color SEDY = new Color(90, 90, 90);
	private static final Color CERVENA = new Color(180, 30, 30);

	private final transient PruvodceKontext kontext;
	private final transient java.util.function.BiFunction<Set<Cast>, List<MatriceMap.Radek>, PlanPruvodce> planOd;
	private final transient List<String> klice;
	/** Dostáhnout vrstvy: základní mapy se nestahují, část Offline mapa se nenabízí. */
	private final boolean jenVrstvy;
	private final Map<Cast, JCheckBox> casti = new EnumMap<>(Cast.class);
	private final JLabel souhrn = new JLabel(" ");
	private final JButton stahnout = new JButton("Stáhnout");

	/**
	 * @param celekKlic
	 *            klíč celé země, null když země má jen regiony
	 * @param klice
	 *            klíče řádků země (celek a regiony)
	 */
	public JStahnoutVse(final Frame rodic, final PruvodceKontext kontext, final String nazevZeme, final String celekKlic, final List<String> klice) {
		this(rodic, kontext, "Stáhnout vše – " + nazevZeme, nazevZeme, (casti, matice) -> PlanPruvodce.sestavVse(celekKlic, klice, casti, matice), klice);
	}

	/** Souhrn k jedné zemi, nebo „Dostáhnout vrstvy“ ke všem staženým mapám (bez části Offline mapa). */
	public static JStahnoutVse dostahnoutVrstvy(final Frame rodic, final PruvodceKontext kontext) {
		return new JStahnoutVse(rodic, kontext, "Dostáhnout vrstvy", "Vrstvy ke staženým mapám", PlanPruvodce::sestavDostahnout, Collections.emptyList());
	}

	private JStahnoutVse(final Frame rodic, final PruvodceKontext kontext, final String titulek, final String nadpisOkna,
			final java.util.function.BiFunction<Set<Cast>, List<MatriceMap.Radek>, PlanPruvodce> planOd, final List<String> klice) {
		super(rodic, titulek, true);
		this.kontext = kontext;
		this.planOd = planOd;
		this.jenVrstvy = klice.isEmpty();
		this.klice = klice;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		final JPanel obsah = new JPanel(new BorderLayout(0, 10));
		obsah.setBorder(new EmptyBorder(12, 16, 12, 16));
		final JLabel nadpis = new JLabel(nadpisOkna);
		nadpis.setFont(nadpis.getFont().deriveFont(Font.BOLD, 15f));
		obsah.add(nadpis, BorderLayout.NORTH);
		final JPanel seznam = new JPanel(new GridLayout(0, 1, 0, 2));
		final List<MatriceMap.Radek> matice = kontext.matice();
		final Set<Cast> cileCasti = EnumSet.allOf(Cast.class);
		for (final Cast c : cileCasti) {
			if (c == Cast.MAPA && jenVrstvy) {
				continue;
			}
			final PlanPruvodce jen = planOd.apply(EnumSet.of(c), matice);
			final JCheckBox box = new JCheckBox(popis(c, jen));
			box.setEnabled(!jen.jeHotovo());
			box.setSelected(!jen.jeHotovo() && (c != Cast.STINOVANI || maOfflineMapu(matice) || (casti.get(Cast.MAPA) != null && casti.get(Cast.MAPA).isSelected())));
			box.addActionListener(e -> prepocti());
			casti.put(c, box);
			seznam.add(box);
		}
		obsah.add(seznam, BorderLayout.CENTER);
		final JPanel pata = new JPanel(new BorderLayout(0, 8));
		souhrn.setForeground(SEDY);
		pata.add(souhrn, BorderLayout.NORTH);
		final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		final JButton zrusit = new JButton("Zrušit");
		zrusit.addActionListener(e -> dispose());
		stahnout.addActionListener(e -> {
			final PlanPruvodce plan = plan();
			dispose();
			if (!plan.jeHotovo()) {
				SpousteniPruvodce.spust(kontext, plan);
			}
		});
		tlacitka.add(zrusit);
		tlacitka.add(stahnout);
		pata.add(tlacitka, BorderLayout.SOUTH);
		obsah.add(pata, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(stahnout);
		setContentPane(obsah);
		prepocti();
		pack();
		setLocationRelativeTo(rodic);
	}

	private static String nazevCasti(final Cast c) {
		switch (c) {
		case MAPA:
			return "Offline mapa";
		case TRASY:
			return "Turistické trasy";
		case VRSTEVNICE:
			return "Vrstevnice";
		case STINOVANI:
		default:
			return "Stínování terénu";
		}
	}

	static String souboru(final int n) {
		return n + " " + (n == 1 ? "soubor" : n < 5 ? "soubory" : "souborů");
	}

	private static String popis(final Cast c, final PlanPruvodce jen) {
		if (jen.jeHotovo()) {
			return nazevCasti(c) + " – nic ke stažení";
		}
		final int n = jen.getPolozky().size();
		final long v = jen.getCelkovaVelikost();
		return nazevCasti(c) + " – " + souboru(n) + (v > 0 ? ", " + JPruvodceMap.velikost(v) : "");
	}

	/** Stínování je jen nad offline mapou: předvybere se, jen když je mapa země už stažená, nebo se teprve stahuje. */
	private boolean maOfflineMapu(final List<MatriceMap.Radek> matice) {
		return matice.stream().filter(r -> klice.isEmpty() || klice.contains(r.getKlic())).anyMatch(r -> r.getBunka(Cast.MAPA).jeStazeno());
	}

	private PlanPruvodce plan() {
		final Set<Cast> vybrane = EnumSet.noneOf(Cast.class);
		casti.forEach((c, box) -> {
			if (box.isSelected() && box.isEnabled()) {
				vybrane.add(c);
			}
		});
		return planOd.apply(vybrane, kontext.matice());
	}

	private void prepocti() {
		final PlanPruvodce plan = plan();
		final long volne = kontext.volneMisto();
		final long potrebuje = plan.getCelkovaVelikost();
		final boolean malo = volne >= 0 && potrebuje > volne;
		if (plan.jeHotovo()) {
			souhrn.setText("Nic není vybráno.");
		} else {
			souhrn.setText("Celkem " + souboru(plan.getPolozky().size()) + ", " + JPruvodceMap.velikost(potrebuje) + (volne >= 0 ? " · volno na disku " + JPruvodceMap.velikost(volne) : ""));
		}
		souhrn.setForeground(malo ? CERVENA : SEDY);
		if (malo) {
			souhrn.setText(souhrn.getText() + " – chybí " + JPruvodceMap.velikost(potrebuje - volne));
		}
		stahnout.setEnabled(!plan.jeHotovo() && !malo);
	}
}
