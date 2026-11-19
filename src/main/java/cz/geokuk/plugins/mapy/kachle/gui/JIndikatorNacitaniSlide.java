package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import javax.swing.Timer;

import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coord.PrvniNacitaniMapy;

/**
 * Malé točící se kolečko vpravo nahoře v mapě, které se ukáže, když dlaždice nepřicházejí déle než zlomek sekundy. Časovač běží, jen dokud se na nějakou dlaždici
 * čeká nebo kolečko mizí; událostmi myši se kolečko nezabývá.
 */
public class JIndikatorNacitaniSlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	/** Otáčení je plynulé při asi 25 snímcích za sekundu. */
	private static final int TIK_MS = 40;
	private static final Color ZELENA = new Color(0x23, 0x9C, 0x2B);
	private static final float TLOUSTKA = 2.4f;
	/** Okraj kolem kolečka pro překreslení, aby se nezanechaly zbytky stínu. */
	private static final int OKRAJ = 6;

	private final transient IntSupplier cekajici;
	private final transient LongSupplier hodiny;
	private final transient IndikatorNacitani indikator = new IndikatorNacitani();
	private final Timer casovac = new Timer(TIK_MS, e -> tik());
	private boolean kresli;

	/** Počet dlaždic, na které se čeká, dodá vždy aktuální stav kachlovníků. */
	JIndikatorNacitaniSlide(final IntSupplier cekajici, final LongSupplier hodiny) {
		this.cekajici = cekajici;
		this.hodiny = hodiny;
		setOpaque(false);
		setCursor(null);
	}

	JIndikatorNacitaniSlide(final IntSupplier cekajici) {
		this(cekajici, System::currentTimeMillis);
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
		final boolean viditelne = indikator.aktualizuj(hodiny.getAsLong(), cekajici.getAsInt()) && !PrvniNacitaniMapy.probiha();
		if (!indikator.beziAnimace()) {
			casovac.stop();
		}
		if (viditelne || kresli) {
			kresli = viditelne;
			final Rectangle r = IndikatorNacitani.ramecek(getWidth());
			repaint(r.x - OKRAJ, r.y - OKRAJ, r.width + 2 * OKRAJ, r.height + 2 * OKRAJ);
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
		kresliKolecko((Graphics2D) g.create(), IndikatorNacitani.ramecek(getWidth()), indikator.getPruhlednost(), IndikatorNacitani.uhel(hodiny.getAsLong()));
	}

	/** Kolečko s jemným světlým podkladem a stínem, čitelné na světlé i tmavé mapě. */
	static void kresliKolecko(final Graphics2D g2, final Rectangle r, final double pruhlednost, final double uhel) {
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, Math.min(1, pruhlednost))));
			final double polomerOblouku = (r.width - TLOUSTKA) / 2.0;
			final double stred = r.x + r.width / 2.0;
			final double stredY = r.y + r.height / 2.0;
			final double podklad = polomerOblouku + TLOUSTKA / 2 + 3;

			g2.setColor(new Color(0, 0, 0, 40));
			g2.fill(new Ellipse2D.Double(stred - podklad, stredY - podklad + 1.5, 2 * podklad, 2 * podklad));
			g2.setColor(new Color(255, 255, 255, 160));
			g2.fill(new Ellipse2D.Double(stred - podklad, stredY - podklad, 2 * podklad, 2 * podklad));

			g2.setStroke(new BasicStroke(TLOUSTKA, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(new Color(0, 0, 0, 38));
			g2.draw(new Ellipse2D.Double(stred - polomerOblouku, stredY - polomerOblouku, 2 * polomerOblouku, 2 * polomerOblouku));
			g2.setColor(ZELENA);
			// Java měří úhel proti směru hodinových ručiček, kolečko se točí po směru.
			g2.draw(new Arc2D.Double(stred - polomerOblouku, stredY - polomerOblouku, 2 * polomerOblouku, 2 * polomerOblouku, -uhel, -IndikatorNacitani.OBLOUK_STUPNU, Arc2D.OPEN));
		} finally {
			g2.dispose();
		}
	}
}
