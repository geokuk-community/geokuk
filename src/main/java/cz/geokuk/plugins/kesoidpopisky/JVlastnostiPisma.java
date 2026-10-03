package cz.geokuk.plugins.kesoidpopisky;

import java.awt.Color;

import javax.swing.*;
import javax.swing.event.ChangeListener;

import cz.geokuk.util.gui.JVyberPisma;

public class JVlastnostiPisma extends JPanel {

	private static final long serialVersionUID = 6845731953052027169L;

	public static final String VLASTNOSI_PISMA_MODEL_PROPERTY = "vlastnostiPismaModel";

	private final VlastnostiPismaModel vlastnostiPismaModel;

	private JSpinner xspinner;
	private JSpinner yspinner;
	private JColorChooser foregroundChooser;
	JVyberPisma fontChooser;
	private JColorChooser backgroudChooser;

	public JVlastnostiPisma() {
		this(new VlastnostiPismaModel());
	}

	public JVlastnostiPisma(final VlastnostiPismaModel model) {
		assert model != null;
		vlastnostiPismaModel = model;
		initComponents();
		registerEvents();
	}

	/**
	 * Sets the model containing the selected color.
	 *
	 * @param newModel
	 *            the new <code>ColorSelectionModel</code> object
	 *
	 * @beaninfo bound: true hidden: true description: The model which contains the currently selected color.
	 */
	// Nedáváme, museli bychom pak testovat, zda se správně mění model asprávně reagovat na lsitenery.

	public VlastnostiPismaModel getVlastnostiPismaModel() {
		return vlastnostiPismaModel;
	}

	protected void initComponents() {
		xspinner = new JSpinner();
		yspinner = new JSpinner();
		foregroundChooser = new JColorChooser(Color.BLACK);
		backgroudChooser = new JColorChooser(Color.WHITE);
		fontChooser = new JVyberPisma();

		xspinner.setToolTipText("Posun popisku vůči ikoně v horizontálním směru");
		yspinner.setToolTipText("Posun popisku vůči ikoně ve vertkálním směru");
		foregroundChooser.setToolTipText("Barva písma včetně průhlednosti");
		backgroudChooser.setToolTipText("Barva podkladu včetně průhlednosti");
		fontChooser.setToolTipText("Font popisků na mapě");

		// Barvy na kartách pod sebou, vedle sebe by dialog byl širší než obrazovka.
		final JTabbedPane barvy = new JTabbedPane();
		barvy.addTab("Barva písma", foregroundChooser);
		barvy.addTab("Barva podkladu", backgroudChooser);

		final JPanel posun = new JPanel();
		posun.setBorder(BorderFactory.createTitledBorder("Posun popisku"));
		for (final JSpinner spinner : new JSpinner[] { xspinner, yspinner }) {
			((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(4);
		}
		posun.add(new JLabel("vodorovně"));
		posun.add(xspinner);
		posun.add(new JLabel("svisle"));
		posun.add(yspinner);

		final Box vpravo = Box.createVerticalBox();
		vpravo.add(fontChooser);
		vpravo.add(posun);

		final Box box = Box.createHorizontalBox();
		box.add(barvy);
		box.add(vpravo);
		add(box);
	}

	/**
	 *
	 */
	private void registerEvents() {
		final ChangeListener chlistenerGui2Model = e -> {
			final VlastnostiPismaModel m = getVlastnostiPismaModel();
			m.setForeground(foregroundChooser.getSelectionModel().getSelectedColor());
			m.setPosuX((Integer) xspinner.getValue());
			m.setPosuY((Integer) yspinner.getValue());
			m.setBackground(backgroudChooser.getSelectionModel().getSelectedColor());
			m.setFont(fontChooser.getVybranePismo());
		};

		final ChangeListener chlistenerModel2Gui = e -> {
			final VlastnostiPismaModel m = getVlastnostiPismaModel();
			foregroundChooser.getSelectionModel().setSelectedColor(m.getForeground());
			backgroudChooser.getSelectionModel().setSelectedColor(m.getBackground());
			fontChooser.setVybranePismo(m.getFont());
			xspinner.setValue(m.getPosuX());
			yspinner.setValue(m.getPosuY());
		};

		foregroundChooser.getSelectionModel().addChangeListener(chlistenerGui2Model);
		xspinner.addChangeListener(chlistenerGui2Model);
		yspinner.addChangeListener(chlistenerGui2Model);
		backgroudChooser.getSelectionModel().addChangeListener(chlistenerGui2Model);
		fontChooser.addChangeListener(chlistenerGui2Model);

		getVlastnostiPismaModel().addChangeListener(chlistenerModel2Gui);

	}

}
