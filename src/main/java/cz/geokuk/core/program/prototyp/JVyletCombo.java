package cz.geokuk.core.program.prototyp;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

/** Rozbalovač aktivního výletu ve stavovém řádku: výběr, nový, přejmenovat, duplikovat, smazat. */
public class JVyletCombo extends JPanel {

	private static final long serialVersionUID = 1L;

	private final VyletyModel model;
	private final JLabel nazev = new JLabel();
	private final JLabel pocty = new JLabel();
	private final JPopupMenu menu = new JPopupMenu();

	public JVyletCombo(final VyletyModel model) {
		super(new FlowLayout(FlowLayout.CENTER, 5, 0));
		this.model = model;
		setBorder(BorderFactory.createEtchedBorder());
		add(new JLabel("Výlet:"));

		final JPanel pole = new JPanel(new BorderLayout(6, 0));
		pole.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor") != null ? UIManager.getColor("Component.borderColor") : Color.GRAY),
				BorderFactory.createEmptyBorder(0, 6, 0, 4)));
		nazev.setPreferredSize(new Dimension(150, nazev.getPreferredSize().height + 2));
		pole.add(nazev, BorderLayout.CENTER);
		pole.add(new JLabel("▾"), BorderLayout.EAST);
		pole.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		pole.setToolTipText("Aktivní výlet, kliknutím vyberete jiný nebo spravujete výlety");
		pole.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				ukazMenu(pole);
			}
		});
		add(pole);
		pocty.setToolTipText("Počet keší, které chci lovit / budu ignorovat.");
		pocty.setPreferredSize(new Dimension(pocty.getFontMetrics(pocty.getFont()).stringWidth("9999 / 9999"), pocty.getFontMetrics(pocty.getFont()).getHeight()));
		add(pocty);
		model.addPosluchac(this::obnov);
		obnov();
	}

	private void obnov() {
		nazev.setText(model.getAktivni());
		pocty.setText(model.getLovim() + " / " + model.getIgnoruji());
	}

	/** Menu se sestaví při otevření, aby odpovídalo aktuálnímu seznamu výletů. */
	public void ukazMenu(final Component pole) {
		menu.removeAll();
		final ButtonGroup skupina = new ButtonGroup();
		for (final String jmeno : model.getJmena()) {
			final JRadioButtonMenuItem polozka = new JRadioButtonMenuItem(jmeno, jmeno.equals(model.getAktivni()));
			polozka.addActionListener(e -> model.aktivuj(jmeno));
			skupina.add(polozka);
			menu.add(polozka);
		}
		menu.addSeparator();
		pridej("Nový výlet…", () -> zeptejSeNaJmeno("Nový výlet", "", model::novy));
		pridej("Přejmenovat…", () -> zeptejSeNaJmeno("Přejmenovat výlet", model.getAktivni(), model::prejmenuj));
		pridej("Duplikovat…", () -> zeptejSeNaJmeno("Duplikovat výlet", model.getAktivni() + " (kopie)", model::duplikuj));
		pridej("Smazat…", this::smaz);
		menu.pack();
		menu.show(pole, 0, -menu.getPreferredSize().height);
	}

	private void pridej(final String text, final Runnable akce) {
		final JMenuItem m = new JMenuItem(text);
		m.addActionListener(e -> akce.run());
		menu.add(m);
	}

	private void zeptejSeNaJmeno(final String titulek, final String vychozi, final java.util.function.Consumer<String> akce) {
		final String jmeno = (String) JOptionPane.showInputDialog(this, "Název výletu:", titulek, JOptionPane.PLAIN_MESSAGE, null, null, vychozi);
		if (jmeno == null) {
			return;
		}
		if (model.jeJmenoVolne(jmeno)) {
			akce.accept(jmeno);
		} else {
			JOptionPane.showMessageDialog(this, "Výlet s tímto názvem už existuje nebo je název prázdný.", titulek, JOptionPane.WARNING_MESSAGE);
		}
	}

	private void smaz() {
		final String jmeno = model.getAktivni();
		if (JOptionPane.showConfirmDialog(this, "Smazat výlet „" + jmeno + "“ včetně jeho seznamů Lovím a Ignoruji?", "Smazat výlet", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
			model.smaz();
		}
	}
}
