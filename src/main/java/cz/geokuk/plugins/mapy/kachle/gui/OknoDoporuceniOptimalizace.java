package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Doporuceni;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

/** Okno při startu s doporučením optimalizace; neblokuje program a po odpočtu se samo zavře, odpočet stojí, když uživatel čte. */
public class OknoDoporuceniOptimalizace extends JDialog {

	private static final long serialVersionUID = 1L;

	static final int ODPOCET_S = 30;

	/** Rozhodnutí uživatele, aby se dalo uložit. */
	public interface Volba {
		/** Spustit optimalizaci. */
		void optimalizovat();

		/** Zvoleno „Spustit bez optimalizace“. */
		void bezOptimalizace();

		/** Vypršel odpočet. */
		void vyprseni();
	}

	/** Odpočet po sekundách, který se dá pozastavit a zrušit. */
	static final class Odpocet {
		private int zbyva;
		private boolean zruseno;

		Odpocet(final int sekund) {
			zbyva = sekund;
		}

		/** Uplynula sekunda; vrací true, když odpočet právě vypršel. */
		boolean tik(final boolean pozastaveno) {
			if (zruseno || pozastaveno) {
				return false;
			}
			return --zbyva <= 0;
		}

		void zrus() {
			zruseno = true;
		}

		boolean zruseno() {
			return zruseno;
		}

		int zbyva() {
			return zbyva;
		}
	}

	private final Timer casovac;
	private final Odpocet odpocet = new Odpocet(ODPOCET_S);

	/** Obsah okna; zvlášť, aby šel nakreslit bez zobrazení. */
	static JPanel panel(final StavSouboruCache stav, final Doporuceni doporuceni, final long volne, final JLabel odpocet, final JButton optimalizovat, final JButton bez) {
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
		p.add(tlacitka, BorderLayout.SOUTH);
		return p;
	}

	private OknoDoporuceniOptimalizace(final Window vlastnik, final StavSouboruCache stav, final Doporuceni doporuceni, final long volne, final Volba volba) {
		super(vlastnik, OptimalizaceTexty.NAZEV, ModalityType.MODELESS);
		final boolean kriticky = PripominkaOptimalizace.kriticky(stav);
		final JLabel popisek = new JLabel(OptimalizaceTexty.odpocet(ODPOCET_S, kriticky));
		final JButton optimalizovat = new JButton("Optimalizovat teď");
		final JButton bez = new JButton("Spustit bez optimalizace");
		final JPanel panel = panel(stav, doporuceni, volne, popisek, optimalizovat, bez);
		setContentPane(panel);
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		casovac = new Timer(1000, e -> {
			final boolean pozastaveno = uzivatelCte();
			if (odpocet.tik(pozastaveno)) {
				zavri(volba::vyprseni);
			} else if (pozastaveno) {
				popisek.setText(OptimalizaceTexty.odpocetPozastaven(kriticky));
			} else {
				popisek.setText(OptimalizaceTexty.odpocet(odpocet.zbyva(), kriticky));
			}
		});
		// Klik do okna odpočet zruší, okno zůstane, dokud uživatel nevybere.
		final MouseAdapter zastav = new MouseAdapter() {
			@Override
			public void mousePressed(final MouseEvent e) {
				odpocet.zrus();
				casovac.stop();
				popisek.setText("");
			}
		};
		for (final java.awt.Component c : new java.awt.Component[] { panel, popisek }) {
			c.addMouseListener(zastav);
		}
		optimalizovat.addActionListener(e -> zavri(volba::optimalizovat));
		bez.addActionListener(e -> zavri(volba::bezOptimalizace));
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(final java.awt.event.WindowEvent e) {
				zavri(volba::bezOptimalizace);
			}
		});
		pack();
		setLocationRelativeTo(vlastnik);
		setAutoRequestFocus(false);
	}

	/** Myš je nad oknem nebo má okno fokus. */
	private boolean uzivatelCte() {
		final PointerInfo mys = MouseInfo.getPointerInfo();
		return isFocused() || mys != null && isShowing() && new Rectangle(getLocationOnScreen(), getSize()).contains(mys.getLocation());
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
