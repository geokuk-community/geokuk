package cz.geokuk.util.gui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.IntFunction;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/**
 * Posuvník s pevnými předvolbami (třeba Auto, 1×, 1,5×…) a popiskem vybrané hodnoty. Hodnota, která mezi předvolbami není (vlastní z nastavení), se přidá
 * jako další poloha, aby se volbou nic neztratilo.
 */
public class JPosuvnikPredvoleb extends JPanel {

	private static final long serialVersionUID = 1L;

	private final List<Integer> hodnoty;
	private final JSlider slider;
	private final JLabel popisek = new JLabel();
	private final IntFunction<String> popis;

	/**
	 * @param predvolby
	 *            vzestupně seřazené hodnoty
	 * @param znacka
	 *            krátký text pod polohou posuvníku
	 * @param popis
	 *            text vedle posuvníku pro vybranou hodnotu
	 */
	public JPosuvnikPredvoleb(final int[] predvolby, final int hodnota, final IntFunction<String> znacka, final IntFunction<String> popis) {
		super(new FlowLayout(FlowLayout.LEFT, 4, 0));
		this.popis = popis;
		hodnoty = hodnoty(predvolby, hodnota);
		slider = new JSlider(0, hodnoty.size() - 1, hodnoty.indexOf(hodnota));
		slider.setSnapToTicks(true);
		slider.setMajorTickSpacing(1);
		slider.setPaintTicks(true);
		final Hashtable<Integer, JLabel> znacky = new Hashtable<>();
		for (int i = 0; i < hodnoty.size(); i++) {
			znacky.put(i, new JLabel(znacka.apply(hodnoty.get(i))));
		}
		slider.setLabelTable(znacky);
		slider.setPaintLabels(true);
		slider.addChangeListener(e -> obnovPopisek());
		final List<String> popisy = new ArrayList<>();
		for (final int h : hodnoty) {
			popisy.add(popis.apply(h));
		}
		pevnaSirka(popisek, popisy);
		obnovPopisek();
		add(slider);
		add(popisek);
	}

	/** Popisek široký podle nejdelšího textu: při změně hodnoty se okno nepřizpůsobuje a delší text by posuvník ořízl. */
	public static void pevnaSirka(final JLabel popisek, final Iterable<String> texty) {
		final String puvodni = popisek.getText();
		final Dimension d = new Dimension();
		for (final String t : texty) {
			popisek.setText(t);
			d.width = Math.max(d.width, popisek.getPreferredSize().width);
			d.height = Math.max(d.height, popisek.getPreferredSize().height);
		}
		popisek.setText(puvodni);
		popisek.setPreferredSize(d);
		popisek.setMinimumSize(d);
	}

	static List<Integer> hodnoty(final int[] predvolby, final int hodnota) {
		final List<Integer> v = new ArrayList<>();
		boolean pridana = false;
		for (final int p : predvolby) {
			if (!pridana && hodnota < p) {
				v.add(hodnota);
				pridana = true;
			}
			if (p == hodnota) {
				pridana = true;
			}
			v.add(p);
		}
		if (!pridana) {
			v.add(hodnota);
		}
		return v;
	}

	private void obnovPopisek() {
		popisek.setText(popis.apply(getHodnota()));
	}

	public int getHodnota() {
		return hodnoty.get(slider.getValue());
	}

	public void setHodnota(final int hodnota) {
		final int i = hodnoty.indexOf(hodnota);
		if (i >= 0) {
			slider.setValue(i);
		}
	}

	public JSlider getSlider() {
		return slider;
	}
}
