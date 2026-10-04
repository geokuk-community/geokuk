package cz.geokuk.plugins.kesoid.mvc;

import java.awt.*;

import javax.swing.*;

import cz.geokuk.framework.AfterInjectInit;
import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.plugins.kesoid.LimityKresleni;

/** Kolik waypointů ve výřezu se ještě kreslí jako ikony a jako tečky. */
public class JLimityKresleniDialog extends JMyDialog0 implements AfterInjectInit {

	private static final long serialVersionUID = 1L;
	private static final int KROK = 10_000;

	private KesoidModel kesoidModel;
	JSpinner jIkon;
	JSpinner jTecek;
	private boolean nastavuji;

	public JLimityKresleniDialog() {
		setTitle("Limity kreslení keší");
		init();
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	@Override
	public void initAfterInject() {
		zobraz(kesoidModel.getLimityKresleni());
	}

	public void onEvent(final LimityKresleniEvent event) {
		zobraz(event.getLimity());
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return null;
	}

	@Override
	protected void initComponents() {
		jIkon = new JSpinner(new SpinnerNumberModel(LimityKresleni.VYCHOZI_IKON, LimityKresleni.MIN, LimityKresleni.MAX, KROK));
		jTecek = new JSpinner(new SpinnerNumberModel(LimityKresleni.VYCHOZI_TECEK, LimityKresleni.MIN, LimityKresleni.MAX, KROK));
		jIkon.setToolTipText("Při více waypointech ve výřezu se v automatickém zobrazení kreslí tečky a nekreslí se popisky.");
		jTecek.setToolTipText("Při více waypointech ve výřezu se nekreslí ani tečky, je potřeba mapu přiblížit.");
		jIkon.addChangeListener(e -> uloz());
		jTecek.addChangeListener(e -> uloz());
		final JButton jVychozi = new JButton("Výchozí");
		jVychozi.addActionListener(e -> kesoidModel.setLimityKresleni(LimityKresleni.VYCHOZI));

		final JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 3, 3, 3);
		c.anchor = GridBagConstraints.WEST;
		radek(panel, c, 0, "Nejvíc ikon ve výřezu:", jIkon);
		radek(panel, c, 1, "Nejvíc teček ve výřezu:", jTecek);
		c.gridx = 1;
		c.gridy = 2;
		c.anchor = GridBagConstraints.EAST;
		panel.add(jVychozi, c);
		add(panel, BorderLayout.CENTER);
	}

	private static void radek(final JPanel panel, final GridBagConstraints c, final int y, final String popis, final JSpinner spinner) {
		c.gridx = 0;
		c.gridy = y;
		final JLabel label = new JLabel(popis);
		label.setLabelFor(spinner);
		panel.add(label, c);
		c.gridx = 1;
		panel.add(spinner, c);
	}

	private void uloz() {
		if (nastavuji || kesoidModel == null) {
			return;
		}
		kesoidModel.setLimityKresleni(LimityKresleni.of((Integer) jIkon.getValue(), (Integer) jTecek.getValue()));
		zobraz(kesoidModel.getLimityKresleni());
	}

	private void zobraz(final LimityKresleni limity) {
		nastavuji = true;
		try {
			jIkon.setValue(limity.getIkon());
			jTecek.setValue(limity.getTecek());
		} finally {
			nastavuji = false;
		}
	}
}
