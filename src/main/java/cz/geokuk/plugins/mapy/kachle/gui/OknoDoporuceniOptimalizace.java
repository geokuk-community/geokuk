package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

/** Okno při startu s doporučením optimalizace; neblokuje program a po odpočtu se samo zavře. */
public class OknoDoporuceniOptimalizace extends JDialog {

	private static final long serialVersionUID = 1L;

	static final int ODPOCET_S = 10;

	/** Rozhodnutí uživatele, aby se dalo uložit. */
	public interface Volba {
		/** Spustit optimalizaci. */
		void optimalizovat();

		/** Zavřeno bez odložení, příště se zeptá znovu. */
		void bezOptimalizace();

		/** Odloženo o týden. */
		void pripomenout();
	}

	private final Timer casovac;
	private int zbyva = ODPOCET_S;

	/** Obsah okna; zvlášť, aby šel nakreslit bez zobrazení. */
	static JPanel panel(final StavSouboruCache stav, final Doporuceni doporuceni, final long volne, final JLabel odpocet, final JButton optimalizovat, final JButton bez,
			final JButton pripomenout) {
		final JPanel p = new JPanel(new BorderLayout(0, 10));
		p.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
		final JTextArea text = new JTextArea(OptimalizaceTexty.doporuceni(stav, doporuceni == Doporuceni.MALO_MISTA, volne));
		text.setEditable(false);
		text.setOpaque(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setColumns(46);
		text.setRows(4);
		final JPanel horni = new JPanel(new BorderLayout(0, 8));
		horni.setOpaque(false);
		horni.add(text, BorderLayout.CENTER);
		horni.add(odpocet, BorderLayout.SOUTH);
		p.add(horni, BorderLayout.CENTER);
		final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		if (doporuceni == Doporuceni.DOPORUCIT) {
			tlacitka.add(optimalizovat);
		}
		tlacitka.add(bez);
		tlacitka.add(pripomenout);
		p.add(tlacitka, BorderLayout.SOUTH);
		return p;
	}

	private OknoDoporuceniOptimalizace(final Window vlastnik, final StavSouboruCache stav, final Doporuceni doporuceni, final long volne, final Volba volba) {
		super(vlastnik, OptimalizaceTexty.NAZEV, ModalityType.MODELESS);
		final JLabel odpocet = new JLabel(OptimalizaceTexty.odpocet(ODPOCET_S));
		final JButton optimalizovat = new JButton("Optimalizovat teď");
		final JButton bez = new JButton("Spustit bez optimalizace");
		final JButton pripomenout = new JButton("Připomenout za týden");
		final JPanel panel = panel(stav, doporuceni, volne, odpocet, optimalizovat, bez, pripomenout);
		setContentPane(panel);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		casovac = new Timer(1000, e -> {
			if (--zbyva <= 0) {
				zavri(volba::bezOptimalizace);
			} else {
				odpocet.setText(OptimalizaceTexty.odpocet(zbyva));
			}
		});
		// Klik do okna odpočet zruší, okno zůstane, dokud uživatel nevybere.
		final MouseAdapter zastav = new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				casovac.stop();
				odpocet.setText("");
			}
		};
		for (final java.awt.Component c : new java.awt.Component[] { panel, odpocet }) {
			c.addMouseListener(zastav);
		}
		optimalizovat.addActionListener(e -> zavri(volba::optimalizovat));
		bez.addActionListener(e -> zavri(volba::bezOptimalizace));
		pripomenout.addActionListener(e -> zavri(volba::pripomenout));
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(final java.awt.event.WindowEvent e) {
				zavri(volba::bezOptimalizace);
			}
		});
		pack();
		setLocationRelativeTo(vlastnik);
		setFocusableWindowState(false);
	}

	private void zavri(final Runnable akce) {
		casovac.stop();
		dispose();
		akce.run();
	}

	/** Zobrazí okno a spustí odpočet; volat ve vlákně Swingu. */
	public static void zobraz(final Window vlastnik, final StavSouboruCache stav, final Doporuceni doporuceni, final long volne, final Volba volba) {
		final OknoDoporuceniOptimalizace okno = new OknoDoporuceniOptimalizace(vlastnik, stav, doporuceni, volne, volba);
		okno.setVisible(true);
		okno.casovac.start();
	}
}
