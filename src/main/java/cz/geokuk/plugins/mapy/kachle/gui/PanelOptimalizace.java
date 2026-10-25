package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;

import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.OptimalizaceChyba;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Prubeh;
import cz.geokuk.plugins.mapy.kachle.podklady.OptimalizaceDatabaze.Vysledek;
import cz.geokuk.plugins.mapy.kachle.podklady.StavSouboruCache;

/** Obsah dialogu Optimalizace databáze: přehled, průběh a výsledek. */
class PanelOptimalizace extends JPanel {

	private static final long serialVersionUID = 1L;

	enum Faze {
		PRIPRAVENO, MALO_MISTA, NELZE, BEZI, HOTOVO, CHYBA
	}

	private final OptimalizaceDatabaze optimalizace;
	private final JLabel uvod = new JLabel(OptimalizaceTexty.UVOD);
	private final JPanel prehled = new JPanel(new GridBagLayout());
	private final JTextArea zprava = new JTextArea();
	private final JProgressBar prubeh = new JProgressBar(0, 100);
	private final JButton optimalizovat = new JButton("Optimalizovat");
	private final JButton zrusit = new JButton("Zrušit");
	private final JButton zavrit = new JButton("Zavřít");
	private final AtomicBoolean zruseno = new AtomicBoolean();
	private Faze faze;

	PanelOptimalizace(final OptimalizaceDatabaze optimalizace, final Runnable zavritOkno) {
		super(new BorderLayout(0, 10));
		this.optimalizace = optimalizace;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
		prehled.setOpaque(false);
		zprava.setEditable(false);
		zprava.setOpaque(false);
		zprava.setLineWrap(true);
		zprava.setWrapStyleWord(true);
		zprava.setColumns(40);
		final JPanel stred = new JPanel();
		stred.setLayout(new BoxLayout(stred, BoxLayout.Y_AXIS));
		for (final JComponent c : new JComponent[] { uvod, prehled, prubeh, zprava }) {
			c.setAlignmentX(LEFT_ALIGNMENT);
			stred.add(c);
			stred.add(Box.createVerticalStrut(8));
		}
		add(stred, BorderLayout.CENTER);
		final JPanel tlacitka = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		tlacitka.add(optimalizovat);
		tlacitka.add(zrusit);
		tlacitka.add(zavrit);
		add(tlacitka, BorderLayout.SOUTH);
		optimalizovat.addActionListener(e -> spust());
		zrusit.addActionListener(e -> {
			zruseno.set(true);
			zrusit.setEnabled(false);
		});
		zavrit.addActionListener(e -> zavritOkno.run());
		pripravPodleStavu();
	}

	Faze faze() {
		return faze;
	}

	boolean bezi() {
		return faze == Faze.BEZI;
	}

	JButton tlacitkoOptimalizovat() {
		return optimalizovat;
	}

	/** Načte stav souboru a nastaví přehled; cache se tím neotevírá. */
	void pripravPodleStavu() {
		final StavSouboruCache stav = optimalizace == null ? null : optimalizace.stav();
		if (stav == null) {
			nastav(Faze.NELZE, OptimalizaceTexty.NELZE);
			return;
		}
		final long volne = optimalizace.volneMisto();
		naplnPrehled(OptimalizaceTexty.prehled(stav, volne));
		if (volne >= 0 && volne < OptimalizaceDatabaze.potrebneMisto(stav)) {
			nastav(Faze.MALO_MISTA, OptimalizaceTexty.maloMista(stav, volne));
		} else {
			nastav(Faze.PRIPRAVENO, "");
		}
	}

	private void naplnPrehled(final String[][] radky) {
		prehled.removeAll();
		final GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		for (int i = 0; i < radky.length; i++) {
			c.gridy = i;
			c.gridx = 0;
			c.insets = new Insets(1, 0, 1, 16);
			final JLabel popisek = new JLabel(radky[i][0]);
			popisek.setFont(popisek.getFont().deriveFont(Font.PLAIN));
			prehled.add(popisek, c);
			c.gridx = 1;
			c.weightx = 1;
			c.insets = new Insets(1, 0, 1, 0);
			final JLabel hodnota = new JLabel(radky[i][1]);
			hodnota.setFont(hodnota.getFont().deriveFont(Font.BOLD));
			prehled.add(hodnota, c);
		}
	}

	void nastav(final Faze nova, final String text) {
		faze = nova;
		zprava.setText(text);
		zprava.setVisible(!text.isEmpty());
		prubeh.setVisible(nova == Faze.BEZI);
		optimalizovat.setVisible(nova == Faze.PRIPRAVENO);
		zrusit.setVisible(nova == Faze.BEZI);
		zrusit.setEnabled(nova == Faze.BEZI);
		zavrit.setVisible(nova != Faze.BEZI);
		prehled.setVisible(nova != Faze.NELZE);
		revalidate();
		final Window okno = SwingUtilities.getWindowAncestor(this);
		if (okno != null) {
			okno.pack();
		}
	}

	/** Spustí optimalizaci na pozadí; cache je po dobu běhu zavřená. */
	void spust() {
		final StavSouboruCache pred = optimalizace.stav();
		zruseno.set(false);
		prubeh.setIndeterminate(true);
		nastav(Faze.BEZI, "Probíhá optimalizace, může to chvíli trvat.");
		new SwingWorker<Vysledek, long[]>() {
			@Override
			protected Vysledek doInBackground() {
				return optimalizace.spust(new Prubeh() {
					@Override
					public void zpracovano(final long hotovo, final long celkem) {
						publish(new long[] { hotovo, celkem });
					}

					@Override
					public boolean zruseno() {
						return zruseno.get();
					}
				});
			}

			@Override
			protected void process(final List<long[]> chunks) {
				final long[] posledni = chunks.get(chunks.size() - 1);
				prubeh.setIndeterminate(false);
				prubeh.setValue(posledni[1] == 0 ? 0 : (int) (posledni[0] * 100 / posledni[1]));
				zprava.setText(String.format("Zpracováno %d z %d dlaždic.", posledni[0], posledni[1]));
			}

			@Override
			protected void done() {
				try {
					if (get() == Vysledek.ZRUSENO) {
						pripravPodleStavu();
						nastav(faze, OptimalizaceTexty.ZRUSENO);
					} else {
						final StavSouboruCache po = optimalizace.stav();
						nastav(Faze.HOTOVO, OptimalizaceTexty.hotovo(pred.velikost(), po == null ? pred.velikost() : po.velikost()));
					}
				} catch (final ExecutionException e) {
					final String text = e.getCause() instanceof OptimalizaceChyba ? e.getCause().getMessage() : String.valueOf(e.getCause());
					nastav(Faze.CHYBA, OptimalizaceTexty.chyba(text));
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		}.execute();
	}
}
