package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.util.function.IntSupplier;

import javax.swing.Timer;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coord.PrvniNacitaniMapy;

/**
 * Tenký proužek u horního okraje mapy, který se ukáže, když dlaždice nepřicházejí déle než zlomek sekundy. Časovač běží, jen dokud se na nějakou dlaždici čeká.
 */
public class JIndikatorNacitaniSlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	private static final int TIK_MS = 100;
	private static final Color BARVA = new Color(0x23, 0x9C, 0x2B, 150);

	private final transient IntSupplier cekajici;
	private final transient IndikatorNacitani indikator = new IndikatorNacitani();
	private final Timer casovac = new Timer(TIK_MS, e -> tik());
	private boolean kresli;

	/** Počet dlaždic, na které se čeká, dodá vždy aktuální stav kachlovníků. */
	JIndikatorNacitaniSlide(final IntSupplier cekajici) {
		this.cekajici = cekajici;
		setOpaque(false);
		setCursor(null);
	}

	public JIndikatorNacitaniSlide(final JKachlovnik... kachlovniky) {
		this(() -> {
			int n = 0;
			for (final JKachlovnik k : kachlovniky) {
				n += k.pocetCekajicichViditelnych();
			}
			return n;
		});
		for (final JKachlovnik k : kachlovniky) {
			k.setPriNovychDlazdicich(this::probud);
		}
	}

	/** Po nové zakázce na dlaždice; spustí časovač. */
	void probud() {
		if (!casovac.isRunning()) {
			casovac.start();
		}
	}

	void tik() {
		final boolean ukaz = indikator.aktualizuj(System.currentTimeMillis(), cekajici.getAsInt()) && !PrvniNacitaniMapy.probiha();
		if (!indikator.cekaSe()) {
			casovac.stop();
		}
		if (ukaz || kresli) {
			kresli = ukaz;
			repaint(0, 0, getWidth(), IndikatorNacitani.VYSKA + 1);
		}
	}

	boolean bezi() {
		return casovac.isRunning();
	}

	boolean isKresli() {
		return kresli;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		if (!kresli) {
			return;
		}
		final Rectangle p = IndikatorNacitani.prouzek(System.currentTimeMillis(), getWidth());
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setColor(BARVA);
			g2.fillRect(p.x, p.y, p.width, p.height);
		} finally {
			g2.dispose();
		}
	}
}
