package cz.geokuk.util.gui;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Výběr písma: rodina, řez, velikost a náhled. */
public class JVyberPisma extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String[] REZY = { "obyčejné", "tučné", "kurzíva", "tučná kurzíva" };

	private static final int[] STYLY = { Font.PLAIN, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC };

	private static final String NAHLED = "Žluťoučký kůň 123";

	final DefaultListModel<String> rodiny = new DefaultListModel<>();
	final JList<String> seznamRodin = new JList<>(rodiny);
	final JComboBox<String> rez = new JComboBox<>(REZY);
	final JSpinner velikost = new JSpinner(new SpinnerNumberModel(12, 4, 72, 1));
	final JLabel nahled = new JLabel(NAHLED, SwingConstants.CENTER) {
		private static final long serialVersionUID = 1L;

		@Override
		protected void paintComponent(final Graphics g) {
			// Podklad může být průhledný, pod ním je běžné pozadí dialogu.
			if (pozadiNahledu != null) {
				g.setColor(pozadiNahledu);
				g.fillRect(0, 0, getWidth(), getHeight());
			}
			super.paintComponent(g);
		}
	};
	private Color pozadiNahledu;

	private Font pismo;
	private boolean nastavuji;

	public JVyberPisma() {
		this(new Font(Font.DIALOG, Font.PLAIN, 12));
	}

	public JVyberPisma(final Font pocatecni) {
		super(new BorderLayout(4, 4));
		setBorder(BorderFactory.createTitledBorder("Písmo"));
		for (final String rodina : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
			rodiny.addElement(rodina);
		}
		seznamRodin.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		seznamRodin.setVisibleRowCount(8);

		final JPanel volby = new JPanel(new FlowLayout(FlowLayout.LEFT));
		volby.add(rez);
		volby.add(new JLabel("velikost"));
		volby.add(velikost);

		nahled.setPreferredSize(new Dimension(220, 50));
		nahled.setBorder(BorderFactory.createEtchedBorder());

		add(new JScrollPane(seznamRodin), BorderLayout.CENTER);
		final JPanel dole = new JPanel(new BorderLayout());
		dole.add(volby, BorderLayout.NORTH);
		dole.add(nahled, BorderLayout.SOUTH);
		add(dole, BorderLayout.SOUTH);

		setVybranePismo(pocatecni);
		seznamRodin.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				podleOvladacu();
			}
		});
		rez.addActionListener(e -> podleOvladacu());
		velikost.addChangeListener(e -> podleOvladacu());
	}

	public Font getVybranePismo() {
		return pismo;
	}

	/** Nastaví ovládací prvky podle písma; posluchače upozorní jen při změně. */
	public void setVybranePismo(final Font font) {
		if (font == null) {
			return;
		}
		nastavuji = true;
		try {
			if (!rodiny.contains(font.getName())) {
				rodiny.addElement(font.getName());
			}
			seznamRodin.setSelectedValue(font.getName(), true);
			rez.setSelectedIndex(Arrays.binarySearch(STYLY, font.getStyle() & (Font.BOLD | Font.ITALIC)));
			velikost.setValue(Math.max(4, Math.min(72, font.getSize())));
		} finally {
			nastavuji = false;
		}
		zmen(font);
	}

	/** Barvy ukázky písma; null ponechá výchozí barvu. */
	public void setBarvyNahledu(final Color popredi, final Color pozadi) {
		nahled.setForeground(popredi);
		pozadiNahledu = pozadi;
		nahled.repaint();
	}

	public void addChangeListener(final ChangeListener l) {
		listenerList.add(ChangeListener.class, l);
	}

	public void removeChangeListener(final ChangeListener l) {
		listenerList.remove(ChangeListener.class, l);
	}

	private void podleOvladacu() {
		final String rodina = seznamRodin.getSelectedValue();
		if (nastavuji || rodina == null) {
			return;
		}
		zmen(new Font(rodina, STYLY[rez.getSelectedIndex()], (Integer) velikost.getValue()));
	}

	private void zmen(final Font novy) {
		nahled.setFont(novy);
		if (Objects.equals(pismo, novy)) {
			return;
		}
		pismo = novy;
		final ChangeEvent udalost = new ChangeEvent(this);
		for (final ChangeListener l : listenerList.getListeners(ChangeListener.class)) {
			l.stateChanged(udalost);
		}
	}
}
