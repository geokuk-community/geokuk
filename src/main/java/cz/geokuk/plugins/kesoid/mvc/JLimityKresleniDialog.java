package cz.geokuk.plugins.kesoid.mvc;

import java.awt.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

import javax.swing.*;

import cz.geokuk.framework.AfterInjectInit;
import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.plugins.kesoid.LimityKresleni;

/** Kolik waypointů ve výřezu se ještě kreslí jako ikony a jako tečky. */
public class JLimityKresleniDialog extends JMyDialog0 implements AfterInjectInit {

	private static final long serialVersionUID = 1L;

	/** Společná stupnice obou posuvníků: jemně u malých hodnot, hrubě u velkých. */
	static final int[] STUPNICE = stupnice();

	private KesoidModel kesoidModel;
	JSlider jIkon;
	JSlider jTecek;
	final JLabel jHodnotaIkon = hodnota();
	final JLabel jHodnotaTecek = hodnota();
	/** Zobrazené hodnoty; uložená hodnota mimo stupnici zůstane, dokud uživatel jezdcem nepohne. */
	private int ikon = LimityKresleni.VYCHOZI_IKON;
	private int tecek = LimityKresleni.VYCHOZI_TECEK;
	private boolean nastavuji;

	public JLimityKresleniDialog() {
		setTitle("Limity kreslení keší");
		init();
	}

	private static int[] stupnice() {
		final List<Integer> s = new ArrayList<>();
		pridej(s, 30_000, 100_000, 10_000);
		pridej(s, 125_000, 300_000, 25_000);
		pridej(s, 350_000, 1_000_000, 50_000);
		pridej(s, 1_100_000, LimityKresleni.MAX, 100_000);
		return s.stream().mapToInt(Integer::intValue).toArray();
	}

	private static void pridej(final List<Integer> s, final int od, final int doHodnoty, final int krok) {
		for (int v = od; v <= doHodnoty; v += krok) {
			s.add(v);
		}
	}

	/** Stupeň nejbližší hodnotě. */
	static int stupen(final int hodnota) {
		int nejblizsi = 0;
		for (int i = 1; i < STUPNICE.length; i++) {
			if (Math.abs(STUPNICE[i] - hodnota) < Math.abs(STUPNICE[nejblizsi] - hodnota)) {
				nejblizsi = i;
			}
		}
		return nejblizsi;
	}

	static String text(final int hodnota) {
		return String.format(new Locale("cs"), "%,d", hodnota);
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
		jIkon = posuvnik();
		jTecek = posuvnik();
		jIkon.setToolTipText("Při více waypointech ve výřezu se v automatickém zobrazení kreslí tečky a nekreslí se popisky.");
		jTecek.setToolTipText("Při více waypointech ve výřezu se nekreslí ani tečky, je potřeba mapu přiblížit.");
		jIkon.addChangeListener(e -> posunutIkon());
		jTecek.addChangeListener(e -> posunutTecek());
		final JButton jVychozi = new JButton("Výchozí");
		jVychozi.addActionListener(e -> kesoidModel.setLimityKresleni(LimityKresleni.VYCHOZI));
		if (LimityKresleni.zadanoVlastnostmi()) {
			jIkon.setEnabled(false);
			jTecek.setEnabled(false);
			jVychozi.setEnabled(false);
		}

		final JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.anchor = GridBagConstraints.WEST;
		radek(panel, c, 0, "Nejvíc ikon ve výřezu:", jIkon, jHodnotaIkon);
		radek(panel, c, 1, "Nejvíc teček ve výřezu:", jTecek, jHodnotaTecek);
		c.gridx = 1;
		c.gridy = 2;
		c.gridwidth = 2;
		final JLabel poznamka = new JLabel("Teček je vždy aspoň tolik jako ikon; nejméně " + text(LimityKresleni.MIN_TECEK) + ".");
		final Color seda = UIManager.getColor("Label.disabledForeground");
		poznamka.setForeground(seda != null ? seda : Color.GRAY);
		panel.add(poznamka, c);
		c.gridy = 3;
		c.anchor = GridBagConstraints.EAST;
		panel.add(jVychozi, c);
		add(panel, BorderLayout.CENTER);
	}

	private static JSlider posuvnik() {
		final JSlider s = new JSlider(0, STUPNICE.length - 1, 0);
		final Hashtable<Integer, JLabel> popisky = new Hashtable<>();
		for (final int v : new int[] { 30_000, 100_000, 300_000, 1_000_000, 2_000_000 }) {
			popisky.put(stupen(v), new JLabel(v >= 1_000_000 ? v / 1_000_000 + " mil." : v / 1000 + " tis."));
		}
		s.setLabelTable(popisky);
		s.setPaintLabels(true);
		s.setMajorTickSpacing(1);
		s.setPaintTicks(true);
		s.setSnapToTicks(true);
		s.setPreferredSize(new Dimension(420, s.getPreferredSize().height));
		return s;
	}

	private static JLabel hodnota() {
		final JLabel l = new JLabel();
		l.setFont(l.getFont().deriveFont(Font.BOLD));
		l.setHorizontalAlignment(SwingConstants.RIGHT);
		final FontMetrics fm = l.getFontMetrics(l.getFont());
		l.setPreferredSize(new Dimension(fm.stringWidth(text(LimityKresleni.MAX)) + 8, fm.getHeight()));
		return l;
	}

	private static void radek(final JPanel panel, final GridBagConstraints c, final int y, final String popis, final JSlider posuvnik, final JLabel hodnota) {
		c.gridx = 0;
		c.gridy = y;
		final JLabel label = new JLabel(popis);
		label.setLabelFor(posuvnik);
		panel.add(label, c);
		c.gridx = 1;
		panel.add(posuvnik, c);
		c.gridx = 2;
		panel.add(hodnota, c);
	}

	/** Ikony nad tečky posunou i tečky. */
	private void posunutIkon() {
		if (nastavuji) {
			return;
		}
		ikon = STUPNICE[jIkon.getValue()];
		jHodnotaIkon.setText(text(ikon));
		if (jTecek.getValue() < jIkon.getValue()) {
			jTecek.setValue(jIkon.getValue());
		}
		ulozPoPusteni();
	}

	/** Tečky nejdou pod nejnižší limit; tečky pod ikony posunou dolů i ikony. */
	private void posunutTecek() {
		if (nastavuji) {
			return;
		}
		final int min = stupen(LimityKresleni.MIN_TECEK);
		if (jTecek.getValue() < min) {
			jTecek.setValue(min);
			return;
		}
		tecek = STUPNICE[jTecek.getValue()];
		jHodnotaTecek.setText(text(tecek));
		if (jIkon.getValue() > jTecek.getValue()) {
			jIkon.setValue(jTecek.getValue());
		}
		// Ikony uložené mimo stupnici můžou být na stejném stupni, a přesto víc než zvolené tečky.
		if (ikon > tecek) {
			ikon = tecek;
			jHodnotaIkon.setText(text(ikon));
		}
		ulozPoPusteni();
	}

	/** Během tažení se jen ukazuje hodnota, uloží se po puštění jezdce. */
	private void ulozPoPusteni() {
		if (kesoidModel == null || jIkon.getValueIsAdjusting() || jTecek.getValueIsAdjusting()) {
			return;
		}
		kesoidModel.setLimityKresleni(LimityKresleni.of(ikon, tecek));
	}

	private void zobraz(final LimityKresleni limity) {
		nastavuji = true;
		try {
			ikon = limity.getIkon();
			tecek = limity.getTecek();
			jIkon.setValue(stupen(ikon));
			jTecek.setValue(stupen(tecek));
			jHodnotaIkon.setText(text(ikon));
			jHodnotaTecek.setText(text(tecek));
		} finally {
			nastavuji = false;
		}
	}
}
