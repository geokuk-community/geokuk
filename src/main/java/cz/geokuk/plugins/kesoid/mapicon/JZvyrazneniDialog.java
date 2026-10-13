package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import cz.geokuk.framework.AfterInjectInit;
import cz.geokuk.framework.JMyDialog0;
import cz.geokuk.plugins.kesoid.genetika.*;
import cz.geokuk.plugins.kesoid.mvc.*;

/** Úprava pravidel zvýraznění keší a správa vlastních předvoleb. */
public class JZvyrazneniDialog extends JMyDialog0 implements AfterInjectInit {

	private static final long serialVersionUID = 1L;

	static final String VLASTNI = "Vlastní";
	private static final int VELIKOST_VZORKU = 14;

	private KesoidModel kesoidModel;
	private Genom genom;

	final DefaultListModel<Zvyrazneni.Pravidlo> pravidla = new DefaultListModel<>();
	JList<Zvyrazneni.Pravidlo> jPravidla;
	private JButton jUpravit;
	private JButton jOdebrat;
	private JButton jNahoru;
	private JButton jDolu;
	private JButton jSmazatPredvolbu;

	public JZvyrazneniDialog() {
		setTitle("Zvýraznění keší");
		init();
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	@Override
	public void initAfterInject() {
		zobraz(kesoidModel.getZvyrazneni());
	}

	public void onEvent(final IkonyNactenyEvent event) {
		genom = event.getBag().getGenom();
		if (jPravidla != null) {
			jPravidla.repaint();
		}
	}

	public void onEvent(final ZvyrazneniEvent event) {
		aktualizujTlacitka();
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return "ZvyrazneniKesi";
	}

	@Override
	protected void initComponents() {
		jPravidla = new JList<>(pravidla);
		jPravidla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		jPravidla.setCellRenderer(new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				final Zvyrazneni.Pravidlo p = (Zvyrazneni.Pravidlo) value;
				setText(popis(p));
				setIcon(vzorek(p.getBarva()));
				return this;
			}
		});
		jPravidla.addListSelectionListener(e -> aktualizujTlacitka());
		jPravidla.setVisibleRowCount(8);
		final JScrollPane scroll = new JScrollPane(jPravidla);
		scroll.setPreferredSize(new Dimension(460, 200));

		final JButton jPridat = new JButton("Přidat…");
		jPridat.addActionListener(e -> pridej());
		jUpravit = new JButton("Upravit…");
		jUpravit.addActionListener(e -> uprav());
		jOdebrat = new JButton("Odebrat");
		jOdebrat.addActionListener(e -> odeber());
		jNahoru = new JButton("Nahoru");
		jNahoru.addActionListener(e -> posun(-1));
		jDolu = new JButton("Dolů");
		jDolu.addActionListener(e -> posun(1));
		final JPanel tlacitka = new JPanel(new GridLayout(0, 1, 0, 4));
		for (final JButton b : Arrays.asList(jPridat, jUpravit, jOdebrat, jNahoru, jDolu)) {
			tlacitka.add(b);
		}
		final JPanel vpravo = new JPanel(new BorderLayout());
		vpravo.add(tlacitka, BorderLayout.NORTH);

		final JLabel nadpis = new JLabel("Pravidla – když keš vyhovuje víc pravidlům, platí první:");
		nadpis.setLabelFor(jPravidla);

		final JButton jPouzit = new JButton("Použít");
		jPouzit.addActionListener(e -> kesoidModel.setZvyrazneni(sestav(VLASTNI)));
		final JButton jUlozit = new JButton("Uložit jako předvolbu…");
		jUlozit.addActionListener(e -> ulozPredvolbu());
		jSmazatPredvolbu = new JButton("Smazat předvolbu");
		jSmazatPredvolbu.addActionListener(e -> smazPredvolbu());
		final JPanel dole = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		dole.add(jPouzit);
		dole.add(jUlozit);
		dole.add(jSmazatPredvolbu);

		final JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
		panel.add(nadpis, BorderLayout.NORTH);
		panel.add(scroll, BorderLayout.CENTER);
		panel.add(vpravo, BorderLayout.EAST);
		panel.add(dole, BorderLayout.SOUTH);
		add(panel, BorderLayout.CENTER);
		aktualizujTlacitka();
	}

	void zobraz(final Zvyrazneni zvyrazneni) {
		pravidla.clear();
		for (final Zvyrazneni.Pravidlo p : zvyrazneni.getPravidla()) {
			pravidla.addElement(p);
		}
		aktualizujTlacitka();
	}

	/** Zvýraznění z pravidel v dialogu; se stejnými pravidly jako některá předvolba je to ta předvolba. */
	Zvyrazneni sestav(final String nazev) {
		final Zvyrazneni z = new Zvyrazneni(nazev, Collections.list(pravidla.elements()));
		for (final Zvyrazneni p : predvolby()) {
			if (p.stejnaPravidla(z)) {
				return p;
			}
		}
		return z;
	}

	private List<Zvyrazneni> predvolby() {
		final List<Zvyrazneni> vse = new ArrayList<>(Zvyrazneni.VESTAVENA);
		vse.addAll(kesoidModel.getPredvolbyZvyrazneni());
		return vse;
	}

	private void aktualizujTlacitka() {
		if (jPravidla == null) {
			return;
		}
		final int i = jPravidla.getSelectedIndex();
		jUpravit.setEnabled(i >= 0);
		jOdebrat.setEnabled(i >= 0);
		jNahoru.setEnabled(i > 0);
		jDolu.setEnabled(i >= 0 && i < pravidla.size() - 1);
		jSmazatPredvolbu.setEnabled(kesoidModel != null && kesoidModel.getPredvolbyZvyrazneni().contains(kesoidModel.getZvyrazneni()));
	}

	private void pridej() {
		final Zvyrazneni.Pravidlo p = new JEditorPravidla(genom, null).zobraz(this, "Nové pravidlo");
		if (p != null) {
			pravidla.addElement(p);
			jPravidla.setSelectedIndex(pravidla.size() - 1);
		}
	}

	private void uprav() {
		final int i = jPravidla.getSelectedIndex();
		if (i < 0) {
			return;
		}
		final Zvyrazneni.Pravidlo p = new JEditorPravidla(genom, pravidla.get(i)).zobraz(this, "Upravit pravidlo");
		if (p != null) {
			pravidla.set(i, p);
		}
	}

	private void odeber() {
		final int i = jPravidla.getSelectedIndex();
		if (i >= 0) {
			pravidla.remove(i);
			jPravidla.setSelectedIndex(Math.min(i, pravidla.size() - 1));
		}
	}

	private void posun(final int smer) {
		final int i = jPravidla.getSelectedIndex();
		final int j = i + smer;
		if (i < 0 || j < 0 || j >= pravidla.size()) {
			return;
		}
		final Zvyrazneni.Pravidlo p = pravidla.remove(i);
		pravidla.add(j, p);
		jPravidla.setSelectedIndex(j);
	}

	private void ulozPredvolbu() {
		final Zvyrazneni aktualni = kesoidModel.getZvyrazneni();
		final String vychozi = aktualni.isVestavene() || VLASTNI.equals(aktualni.getNazev()) ? "" : aktualni.getNazev();
		final String zadany = (String) JOptionPane.showInputDialog(this, "Název předvolby:", "Uložit jako předvolbu", JOptionPane.PLAIN_MESSAGE, null, null, vychozi);
		if (zadany == null) {
			return;
		}
		final Zvyrazneni nova = new Zvyrazneni(zadany, Collections.list(pravidla.elements()));
		if (nova.getNazev().isEmpty()) {
			return;
		}
		for (final Zvyrazneni v : Zvyrazneni.VESTAVENA) {
			if (v.getNazev().equals(nova.getNazev())) {
				JOptionPane.showMessageDialog(this, "Tak se jmenuje vestavěná předvolba, zvolte jiný název.", "Uložit jako předvolbu", JOptionPane.WARNING_MESSAGE);
				return;
			}
		}
		final List<Zvyrazneni> vlastni = new ArrayList<>();
		boolean nahrazeno = false;
		for (final Zvyrazneni z : kesoidModel.getPredvolbyZvyrazneni()) {
			if (z.getNazev().equals(nova.getNazev())) {
				vlastni.add(nova);
				nahrazeno = true;
			} else {
				vlastni.add(z);
			}
		}
		if (!nahrazeno) {
			vlastni.add(nova);
		}
		kesoidModel.setPredvolbyZvyrazneni(vlastni);
		kesoidModel.setZvyrazneni(nova);
	}

	private void smazPredvolbu() {
		final Zvyrazneni aktualni = kesoidModel.getZvyrazneni();
		final List<Zvyrazneni> vlastni = new ArrayList<>(kesoidModel.getPredvolbyZvyrazneni());
		if (vlastni.remove(aktualni)) {
			kesoidModel.setPredvolbyZvyrazneni(vlastni);
			kesoidModel.setZvyrazneni(new Zvyrazneni(VLASTNI, aktualni.getPravidla()));
		}
	}

	/** Podmínka pravidla slovy, například „Druh kešoidu: Keš, Terén: 5“. */
	String popis(final Zvyrazneni.Pravidlo p) {
		if (p.getAlely().isEmpty()) {
			return "Všechny waypointy";
		}
		final SortedMap<Integer, String> casti = new TreeMap<>();
		int nezname = Integer.MAX_VALUE / 2;
		for (final String qualName : p.getAlely()) {
			final Optional<Alela> alela = genom == null ? Optional.empty() : genom.locateQualAlela(qualName);
			if (alela.isPresent()) {
				casti.put(alela.get().getGen().getIndex(), alela.get().getGen().getDisplayName() + ": " + alela.get().getDisplayName());
			} else {
				casti.put(nezname++, qualName);
			}
		}
		return String.join(", ", casti.values());
	}

	static Icon vzorek(final Color barva) {
		return new Icon() {
			@Override
			public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
				g.setColor(barva);
				g.fillRoundRect(x, y, VELIKOST_VZORKU, VELIKOST_VZORKU, 5, 5);
				g.setColor(Color.GRAY);
				g.drawRoundRect(x, y, VELIKOST_VZORKU - 1, VELIKOST_VZORKU - 1, 5, 5);
			}

			@Override
			public int getIconWidth() {
				return VELIKOST_VZORKU;
			}

			@Override
			public int getIconHeight() {
				return VELIKOST_VZORKU;
			}
		};
	}
}
