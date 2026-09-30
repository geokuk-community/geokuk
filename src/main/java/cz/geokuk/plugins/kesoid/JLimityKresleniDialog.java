package cz.geokuk.plugins.kesoid;

import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.*;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.plugins.kesoid.LimityKresleni.Vrstva;

/**
 * Limity kreslení jednotlivých vrstev. Změna se projeví hned, v dialogu je vidět,
 * kolik je ve výřezu waypointů a jak dlouho vrstvy kreslily.
 */
public class JLimityKresleniDialog extends JMyDialog0 {

	private static final long serialVersionUID = 1L;

	private static final int[] PRESETY = { 10_000, 30_000, 60_000, 100_000, 200_000, 500_000 };

	private final Map<Vrstva, JSpinner> spinnery = new EnumMap<>(Vrstva.class);
	private final Map<Vrstva, JLabel> casy = new EnumMap<>(Vrstva.class);
	private JLabel veVyrezu;
	private Timer obnova;

	public JLimityKresleniDialog() {
		setTitle("Limity kreslení");
		init();
	}

	@Override
	public void dispose() {
		if (obnova != null) {
			obnova.stop();
		}
		super.dispose();
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return null;
	}

	@Override
	protected void initComponents() {
		final JPanel tabulka = new JPanel(new GridBagLayout());
		tabulka.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;

		c.gridy = 0;
		c.gridx = 0;
		tabulka.add(new JLabel("Vrstva"), c);
		c.gridx = 1;
		tabulka.add(new JLabel("Kreslit do (waypointů ve výřezu)"), c);
		c.gridx = 2;
		tabulka.add(new JLabel("Poslední kreslení"), c);

		for (final Vrstva v : Vrstva.values()) {
			c.gridy++;
			c.gridx = 0;
			tabulka.add(new JLabel(v.nazev), c);
			final JSpinner spinner = new JSpinner(new SpinnerNumberModel(LimityKresleni.get(v), 0, 10_000_000, 5_000));
			spinner.setEditor(new JSpinner.NumberEditor(spinner, "#,##0"));
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(9);
			spinner.addChangeListener(e -> nastav(v, ((Number) spinner.getValue()).intValue()));
			spinnery.put(v, spinner);
			c.gridx = 1;
			tabulka.add(spinner, c);
			final JLabel cas = new JLabel("–");
			cas.setPreferredSize(new Dimension(110, cas.getPreferredSize().height));
			casy.put(v, cas);
			c.gridx = 2;
			tabulka.add(cas, c);
		}

		final JPanel presety = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		presety.add(new JLabel("Všem vrstvám:"));
		for (final int limit : PRESETY) {
			final JButton b = new JButton(String.format("%,d", limit));
			b.setMargin(new Insets(1, 4, 1, 4));
			b.addActionListener(e -> nastavVsem(limit));
			presety.add(b);
		}
		final JButton vychozi = new JButton("Výchozí");
		vychozi.setMargin(new Insets(1, 4, 1, 4));
		vychozi.setToolTipText("Všem vrstvám limit " + String.format("%,d", FConst.MAX_POC_WPT_NA_MAPE) + ".");
		vychozi.addActionListener(e -> nastavVsem(FConst.MAX_POC_WPT_NA_MAPE));
		presety.add(vychozi);
		c.gridy++;
		c.gridx = 0;
		c.gridwidth = 3;
		c.insets = new Insets(8, 4, 2, 4);
		tabulka.add(presety, c);

		veVyrezu = new JLabel(" ");
		c.gridy++;
		c.insets = new Insets(8, 4, 2, 4);
		tabulka.add(veVyrezu, c);

		add(tabulka, BorderLayout.CENTER);

		obnova = new Timer(250, e -> obnovStatistiku());
		obnova.start();
		obnovStatistiku();
	}

	private void nastav(final Vrstva v, final int limit) {
		if (LimityKresleni.get(v) == limit) {
			return;
		}
		LimityKresleni.set(v, limit);
		final JFrame frame = Dlg.parentFrame();
		if (frame != null) {
			frame.repaint();
		}
	}

	private void nastavVsem(final int limit) {
		for (final Vrstva v : Vrstva.values()) {
			spinnery.get(v).setValue(limit);
		}
	}

	private void obnovStatistiku() {
		veVyrezu.setText(String.format("Ve výřezu je %,d waypointů.", LimityKresleni.getPosledniPocetVeVyrezu()));
		for (final Vrstva v : Vrstva.values()) {
			final long ns = LimityKresleni.getPosledniKresleniNs(v);
			casy.get(v).setText(ns == 0 ? "nekreslí se" : String.format("%.1f ms", ns / 1e6));
		}
	}
}
