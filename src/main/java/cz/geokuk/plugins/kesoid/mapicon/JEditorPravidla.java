package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import cz.geokuk.plugins.kesoid.genetika.*;

/** Jedno pravidlo zvýraznění: u každé vlastnosti libovolná hodnota, nebo jedna konkrétní (všechny zvolené platí zároveň), a barva čtverce. */
class JEditorPravidla extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String LIBOVOLNE = "(libovolné)";

	private final Map<Gen, JComboBox<Object>> vyber = new LinkedHashMap<>();
	private Color barva;
	private final JButton jBarva = new JButton("Změnit…");
	private final JLabel jVzorek = new JLabel();

	JEditorPravidla(final Genom genom, final Zvyrazneni.Pravidlo puvodni) {
		super(new GridBagLayout());
		barva = puvodni == null ? new Color(255, 255, 0) : puvodni.getBarva();
		final Set<String> zvolene = puvodni == null ? Collections.emptySet() : puvodni.getAlely();
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(2, 4, 2, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		int radek = 0;
		if (genom != null) {
			for (final Gen gen : genom.getGeny()) {
				// Geny bez českého názvu jsou vnitřní pomůcky sad ikon.
				if (gen == genom.GEN_postavenikMysi || gen == genom.GEN_Vybranost || gen.getAlely().size() < 2 || gen.getVychoziAlela().qualName().endsWith(":" + gen.getDisplayName())) {
					continue;
				}
				final JComboBox<Object> combo = new JComboBox<>();
				combo.addItem(LIBOVOLNE);
				for (final Alela alela : gen.getAlely()) {
					combo.addItem(alela);
					if (zvolene.contains(alela.qualName())) {
						combo.setSelectedItem(alela);
					}
				}
				combo.setRenderer(new DefaultListCellRenderer() {
					private static final long serialVersionUID = 1L;

					@Override
					public Component getListCellRendererComponent(final JList<?> list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
						super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
						if (value instanceof Alela) {
							setText(((Alela) value).getDisplayName());
						}
						return this;
					}
				});
				vyber.put(gen, combo);
				radek(c, radek++, gen.getDisplayName() + ":", combo);
			}
		}
		jBarva.addActionListener(e -> zvolBarvu());
		final JPanel barvaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		barvaPanel.add(jVzorek);
		barvaPanel.add(jBarva);
		jBarva.getAccessibleContext().setAccessibleName("Změnit barvu čtverce");
		radek(c, radek, "Barva čtverce:", barvaPanel);
		ukazBarvu();
	}

	private void radek(final GridBagConstraints c, final int y, final String popis, final JComponent komponenta) {
		c.gridx = 0;
		c.gridy = y;
		c.weightx = 0;
		final JLabel label = new JLabel(popis);
		label.setLabelFor(komponenta);
		add(label, c);
		c.gridx = 1;
		c.weightx = 1;
		add(komponenta, c);
	}

	private void zvolBarvu() {
		final Color nova = JColorChooser.showDialog(this, "Barva čtverce", barva);
		if (nova != null) {
			barva = nova;
			ukazBarvu();
		}
	}

	private void ukazBarvu() {
		jVzorek.setIcon(JZvyrazneniDialog.vzorek(barva));
	}

	/** Pravidlo podle voleb v editoru. */
	Zvyrazneni.Pravidlo getPravidlo() {
		final List<String> alely = new ArrayList<>();
		for (final JComboBox<Object> combo : vyber.values()) {
			if (combo.getSelectedItem() instanceof Alela) {
				alely.add(((Alela) combo.getSelectedItem()).qualName());
			}
		}
		return new Zvyrazneni.Pravidlo(barva, alely);
	}

	/** Ukáže editor v dialogu, vrátí upravené pravidlo, nebo null po zrušení. */
	Zvyrazneni.Pravidlo zobraz(final Component rodic, final String titulek) {
		final JScrollPane scroll = new JScrollPane(this);
		scroll.setBorder(null);
		final Dimension d = getPreferredSize();
		scroll.setPreferredSize(new Dimension(d.width + 30, Math.min(d.height + 10, 520)));
		final int volba = JOptionPane.showConfirmDialog(rodic, scroll, titulek, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		return volba == JOptionPane.OK_OPTION ? getPravidlo() : null;
	}
}
