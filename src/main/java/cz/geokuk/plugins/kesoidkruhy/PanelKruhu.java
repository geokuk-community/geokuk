package cz.geokuk.plugins.kesoidkruhy;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Hashtable;

import javax.swing.*;
import javax.swing.event.ChangeListener;

/** Ovládací prvky nastavení zvýrazňovacích kruhů: jednotkovost, velikost, poloměr, průhlednost a barva. Zapnutí kruhů panel nemění. */
final class PanelKruhu extends Box {

	private static final long serialVersionUID = 1L;

	final JLabel pruhlednostLabel = new JLabel();
	final JSlider velikostSlider = new JSlider();
	final JSlider polomerSlider = new JSlider(0, PolomerKruhu.PRESETY.length - 1, PolomerKruhu.pozice(PolomerKruhu.VYCHOZI_METRU));
	final JTextField polomerPole = new JTextField(9);
	final JSlider pruhlednostSlider = new JSlider(0, 100, 0);
	final JColorChooser barvaChooser = new JColorChooser(Color.WHITE);
	final JCheckBox jednotkoveKruhy = new JCheckBox("Jednotkové kruhy");

	private KruhyModel kruhyModel;
	/** Ovládací prvky se právě nastavují z modelu, jejich změny se do modelu nevracejí. */
	private boolean nacitaSe;
	/** Poslední platný poloměr jednotkových kruhů v metrech. */
	private int polomer = PolomerKruhu.VYCHOZI_METRU;

	PanelKruhu() {
		super(BoxLayout.Y_AXIS);
		pruhlednostLabel.setText(textPruhlednosti(0));
		velikostSlider.setToolTipText("Velikost zvýrazňovacího kruhu v pixelech.");
		polomerSlider.setToolTipText("Poloměr jednotkových kruhů; posuvník se přichytává k běžným hodnotám, jinou hodnotu zadejte do pole.");
		polomerPole.setToolTipText("Poloměr jednotkových kruhů od 1 m do 50 km, například 161, 333 m nebo 1,5 km. Potvrdí se Enterem.");
		polomerPole.setText(PolomerKruhu.format(polomer));
		polomerSlider.setSnapToTicks(true);
		polomerSlider.setMajorTickSpacing(1);
		polomerSlider.setPaintTicks(true);
		polomerSlider.setLabelTable(popiskyPosuvnikuPolomeru());
		polomerSlider.setPaintLabels(true);
		pruhlednostSlider.setToolTipText("Průhlednost zvýrazňovacího kruhu: 0 % je plně krycí, 100 % neviditelný.");
		barvaChooser.setToolTipText("Nastavení barvy zvýrazňovacího kruhu");
		jednotkoveKruhy.setToolTipText("Poloměr kruhů v metrech místo pixelů");
		add(jednotkoveKruhy);
		add(new JLabel("Velikost kruhů (pixely)"));
		add(velikostSlider);
		add(Box.createVerticalStrut(10));
		add(new JLabel("Poloměr jednotkových kruhů"));
		final JPanel radekPolomeru = new JPanel(new BorderLayout(8, 0));
		radekPolomeru.add(polomerSlider, BorderLayout.CENTER);
		final JPanel poleVyska = new JPanel(new BorderLayout());
		poleVyska.add(polomerPole, BorderLayout.NORTH);
		radekPolomeru.add(poleVyska, BorderLayout.EAST);
		add(radekPolomeru);
		add(Box.createVerticalStrut(10));
		add(pruhlednostLabel);
		add(pruhlednostSlider);
		add(Box.createVerticalStrut(10));
		add(barvaChooser);
		uplatniJednotkovost();
	}

	private static Hashtable<Integer, JLabel> popiskyPosuvnikuPolomeru() {
		final Hashtable<Integer, JLabel> popisky = new Hashtable<>();
		for (int i = 0; i < PolomerKruhu.PRESETY.length; i++) {
			final int m = PolomerKruhu.PRESETY[i];
			final JLabel l = new JLabel(m >= 1000 ? m / 1000 + " km" : String.valueOf(m));
			l.setFont(l.getFont().deriveFont(10f));
			popisky.put(i, l);
		}
		return popisky;
	}

	void inject(final KruhyModel kruhyModel) {
		this.kruhyModel = kruhyModel;
	}

	void onEvent(final KruhyPreferencesChangeEvent event) {
		nacitaSe = true;
		try {
			jednotkoveKruhy.getModel().setSelected(event.kruhy.isJednotkovaVelikost());
			velikostSlider.getModel().setValue(event.kruhy.getVelikost());
			polomer = event.kruhy.getPolomerMetru();
			polomerSlider.getModel().setValue(PolomerKruhu.pozice(polomer));
			polomerPole.setText(PolomerKruhu.format(polomer));
			final Color barva = event.kruhy.getBarva();
			barvaChooser.getSelectionModel().setSelectedColor(new Color(barva.getRGB()));
			final int pruhlednost = KruhyZDialogu.pruhlednostProcent(barva.getAlpha());
			pruhlednostSlider.getModel().setValue(pruhlednost);
			pruhlednostLabel.setText(textPruhlednosti(pruhlednost));
			uplatniJednotkovost();
		} finally {
			nacitaSe = false;
		}
	}

	void registerEvents() {
		final ChangeListener chlist = e -> {
			if (nacitaSe) {
				return;
			}
			uplatniJednotkovost();
			uloz();
		};
		velikostSlider.getModel().addChangeListener(chlist);
		pruhlednostSlider.getModel().addChangeListener(chlist);
		barvaChooser.getSelectionModel().addChangeListener(chlist);
		jednotkoveKruhy.getModel().addChangeListener(chlist);
		polomerSlider.getModel().addChangeListener(e -> {
			if (nacitaSe) {
				return;
			}
			polomer = PolomerKruhu.metru(polomerSlider.getValue());
			polomerPole.setText(PolomerKruhu.format(polomer));
			uloz();
		});
		polomerPole.addActionListener(e -> potvrdPolomerZPole());
		polomerPole.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(final FocusEvent e) {
				potvrdPolomerZPole();
			}
		});
	}

	/** Neplatná hodnota se vrátí na poslední platnou, platná nastaví posuvník na nejbližší pozici bez přichycení hodnoty. */
	void potvrdPolomerZPole() {
		final int metru = PolomerKruhu.parsuj(polomerPole.getText());
		if (metru == 0) {
			polomerPole.setText(PolomerKruhu.format(polomer));
			return;
		}
		polomerPole.setText(PolomerKruhu.format(metru));
		if (metru == polomer) {
			return;
		}
		polomer = metru;
		nacitaSe = true;
		try {
			polomerSlider.setValue(PolomerKruhu.pozice(metru));
		} finally {
			nacitaSe = false;
		}
		uloz();
	}

	/** Při jednotkových kruzích se používá posuvník poloměru, jinak posuvník pixelů. */
	private void uplatniJednotkovost() {
		final boolean jednotkove = jednotkoveKruhy.isSelected();
		velikostSlider.setEnabled(!jednotkove);
		polomerSlider.setEnabled(jednotkove);
		polomerPole.setEnabled(jednotkove);
	}

	private void uloz() {
		final int pruhlednost = pruhlednostSlider.getValue();
		pruhlednostLabel.setText(textPruhlednosti(pruhlednost));
		kruhyModel.setData(KruhyZDialogu.zmen(kruhyModel.getData(), barvaChooser.getSelectionModel().getSelectedColor(), pruhlednost, velikostSlider.getValue(),
		        jednotkoveKruhy.isSelected(), polomer));
	}

	private static String textPruhlednosti(final int procent) {
		return "Průhlednost kruhů: " + procent + " %";
	}
}
