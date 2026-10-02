package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CancellationException;

import javax.swing.JComponent;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.*;
import cz.geokuk.util.pocitadla.PocitadloMalo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JKachle extends JComponent {

	private static final boolean ZOBRAZOVAT_NA_KACHLICH_DIAGNOSTICKE_INFORMACE = false;
	// Staré mapy.cz. Takto to tam bylo:
	// (2) ad mapy.cz: jejich vnitřní XY souřadnice jsou odvozeninou UTM souřadnic.
	// čtverec s nejzápadnějčím bodem ČR je na http://m4.mapserver.mapy.cz/turist/13_79D8000_8250000
	// sX = dec(79D8000) = 127762432
	// sY = dec(8250000) = 136642560
	// 13 je měřítko mapy
	//
	// X_UTM=(sX*0,03125)-3700000 = 292576
	// Y_UTM=(sY*0,03125)+1300000 = 5570080
	//
	// a pak už jen stačí převést UTM na WGS :-) a máš souřadnice levého dolního rohu každé dlaždice těch jejich bitmap.

	// http://www.mapy.cz/#x=127762400@y=136642528@z=13@mm=TP@sa=s@st=s@ssq=50.246422,12.090604@sss=1@ssp=1
	public static int KACHLE_WIDTH = 256;
	public static int KACHLE_HEIGHT = 256;

	private static final long serialVersionUID = -5445121736003161730L;

	static final long PRVNI_POKUS_PO_CHYBE_MS = 15_000;
	private static final long POSLEDNI_POKUS_PO_CHYBE_MS = 300_000;

	private static final PocitadloMalo pocitJKAchle = new PocitadloMalo("#JKachle", "Počet kompomnent JKachle přes hlavní okno, okno v rohu, rendry, stahování, prostě všude.");
	private final JKachlovnik jKachlovnik;
	// private static int cictac;
	private final Ka ka;

	private Kanceler kanceler;
	private Image image;

	private boolean jeTamUzCelyObrazek;

	/** Chyba posledního získání dlaždice, null když chyba není. */
	private Throwable chyba;
	/** Kdy se chybná dlaždice zkusí znovu; odstup se s každou další chybou zdvojnásobí. */
	private long dalsiPokus;
	private int chybVRade;
	private final Set<DiagnosticsData> diagnosticsDatas = Collections.synchronizedSet(new LinkedHashSet<>());

	private String diagnosticesFazeStr;
	// Point mou = new Point(); // souřadnice roho

	private static int ordinalIndexOf(final String str, final char c, int n) {
		int pos = str.indexOf(c, 0);
		while (n-- > 0 && pos != -1) {
			pos = str.indexOf(c, pos + 1);
		}
		return pos;
	}

	private static String toHex(final int cc) {
		String s = Integer.toHexString(cc);
		s = "00000000".substring(0, 8 - s.length()) + s;
		s = s.substring(0, 4) + " " + s.substring(4);
		return s;
	}

	public JKachle(final JKachlovnik jKachlovnik, final Ka ka) {
		this.jKachlovnik = jKachlovnik;
		this.ka = ka;
		setSize(KACHLE_WIDTH, KACHLE_HEIGHT);
		pocitJKAchle.inc();

	}

	public KaLoc getKaLoc() {
		return ka.getLoc();
	}

	public boolean jeTamUzCelyObrazek() {
		return jeTamUzCelyObrazek;
	}

	public synchronized boolean jeChybna() {
		return chyba != null;
	}

	/** Dlaždici, kterou se nepodařilo získat (třeba při výpadku sítě), zkusí získat znovu, když už je na to čas. */
	void zkusZnovuPoChybe(final KachleModel kachleModel, final Priority priorita) {
		synchronized (this) {
			if (chyba == null || System.currentTimeMillis() < dalsiPokus) {
				return;
			}
			chyba = null;
			jeTamUzCelyObrazek = false;
		}
		uzTeNepotrebuju();
		ziskejObsah(kachleModel, priorita);
	}

	/**
	 * Už nebudu výsledek potřebovat, tak všechnoi můžeme stornovat
	 */
	public void uzTeNepotrebuju() {
		try {
			if (kanceler != null) {
				kanceler.cancel();
			}
		} catch (final CancellationException e) {
			log.debug("Stahování dlaždice zrušeno");
		}
	}

	public synchronized void waitNaDotazeniDlazdice() throws InterruptedException {
		while (!jeTamUzCelyObrazek()) {
			wait();
		}
	}

	/**
	 * Získá obsah
	 *
	 * @param kachleModel
	 * @param priorita
	 */
	public void ziskejObsah(final KachleModel kachleModel, final Priority priorita) {
		final KaOneReq req = new KaOneReq(ka, kastat -> {

			if (priorita == Priority.STAHOVANI) {
				log.debug("Získán obsah: {} {}", ka, kastat.getImg() != null ? "ANO" : kastat.getThr().getMessage());
			}
			synchronized (JKachle.this) { // paintování spoléhá na stálost údajů
				if (kastat.getImg() != null) {
					image = kastat.getImg(); // přepíšeme, jen když jde něco lepšího
					chyba = null;
					chybVRade = 0;
				} else {
					chyba = kastat.getThr();
					dalsiPokus = System.currentTimeMillis() + Math.min(PRVNI_POKUS_PO_CHYBE_MS << Math.min(chybVRade, 5), POSLEDNI_POKUS_PO_CHYBE_MS);
					chybVRade++;
				}
				jeTamUzCelyObrazek = true;
				ziskanPlnyObrazek(image);
				if (jKachlovnik != null) {
					jKachlovnik.kachleZpracovana(this);
				}
				JKachle.this.notifyAll();
			}
			repaint(); // prý můžeme volat z libovolného vlákna
		}, priorita);

		final DiagnosticsData.Listener diagListener = (diagnosticsData, diagnosticesFazeStr) -> {
			JKachle.this.diagnosticesFazeStr = diagnosticesFazeStr;
			for (DiagnosticsData dd = diagnosticsData; dd != null; dd = dd.getParent()) {
				diagnosticsDatas.add(dd);
			}
		};
		final String nazevKachlovniku = jKachlovnik == null ? null : jKachlovnik.nazevKachlovniku;
		kanceler = kachleModel.getZiskavac().ziskejObsah(req, DiagnosticsData.create(null, nazevKachlovniku, diagListener));
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		pocitJKAchle.dec();
	}

	@Override
	// Synchronizuje se, prtotže se může během zpracování změnit obrázek ,který se vykresluje
	protected synchronized void paintComponent(final Graphics aG) {
		super.paintComponent(aG);
		// if (true) return;
		final Graphics2D g = (Graphics2D) aG.create();
		// if (isVykreslovatOkamzite()) {
		// // Pokud rendruji do KMZ č souboru, a ne naobrazovku tak mám možná otočeno a nestojím o žádné uříznuití.
		// g.setClip(null);
		// }
		if (image != null) {
			g.drawImage(image, 0, 0, null);
		}
		if (image == null && chyba != null && !ZOBRAZOVAT_NA_KACHLICH_DIAGNOSTICKE_INFORMACE) {
			g.setColor(Color.GRAY);
			drawPsanicko(g);
			vypisChybu(g);
		} else if (image == null || ZOBRAZOVAT_NA_KACHLICH_DIAGNOSTICKE_INFORMACE) {
			g.setColor(Color.blue);
			drawPsanicko(g);
			g.setColor(Color.RED);
			vypisPozici(g);

			int y = 75;
			if (diagnosticesFazeStr != null) {
				g.setColor(Color.MAGENTA);
				g.drawString(diagnosticesFazeStr, 5, y);
				y += 15;
			}

			for (final DiagnosticsData ss : diagnosticsDatas.toArray(new DiagnosticsData[0])) {
				final Object dato = ss.getDato();
				if (dato instanceof URL) {
					final String s = dato.toString();
					final int index = ordinalIndexOf(s, '/', 2) + 1;
					g.setColor(Color.YELLOW);
					g.drawString(s.substring(0, index), 5, y);
					y += 15;
					g.drawString(s.substring(index), 10, y);
				} else if (dato instanceof Throwable) {
					g.setColor(Color.RED);
					g.drawString(dato.toString(), 5, y);
				} else {
					g.setColor(Color.CYAN);
					g.drawString((ss.getNazev() == null ? "" : ss.getNazev() + " = ") + dato, 5, y);
				}
				y += 15;
			}

		}
		super.paintComponent(aG);
	}

	/*
	 * Rendrovací kachlovník může použít pro dopaintování.
	 */
	protected void ziskanPlnyObrazek(final Image img) {

	}

	private void vypisChybu(final Graphics2D g) {
		final String[] radky = { "Dlaždici se nepodařilo načíst:", popisChyby(chyba), "Zkusí se znovu." };
		final FontMetrics fm = g.getFontMetrics();
		int y = getHeight() / 2 - fm.getHeight();
		for (final String radek : radky) {
			final int sirka = fm.stringWidth(radek);
			g.setColor(new Color(255, 255, 255, 200));
			g.fillRect((getWidth() - sirka) / 2 - 3, y - fm.getAscent(), sirka + 6, fm.getHeight());
			g.setColor(Color.BLACK);
			g.drawString(radek, (getWidth() - sirka) / 2, y);
			y += fm.getHeight();
		}
	}

	static String popisChyby(final Throwable t) {
		if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException || t instanceof java.net.NoRouteToHostException) {
			return "bez připojení k serveru mapy";
		}
		if (t instanceof java.net.SocketTimeoutException) {
			return "server mapy neodpovídá";
		}
		if (t instanceof KachloDownloader.ChybaServeru) {
			return "server mapy vrátil chybu " + ((KachloDownloader.ChybaServeru) t).getKod();
		}
		if (t instanceof java.io.IOException) {
			return "chyba při stahování";
		}
		return "chyba";
	}

	private void drawPsanicko(final Graphics2D g) {
		final int kraj = 3;
		final int x1 = kraj;
		final int y1 = kraj;
		final int x2 = getSize().width - 1 - kraj;
		final int y2 = getSize().height - 1 - kraj;
		g.drawLine(x1, y1, x1, y2);
		g.drawLine(x1, y1, x2, y1);
		g.drawLine(x1, y2, x2, y2);
		g.drawLine(x2, y1, x2, y2);
		g.drawLine(x1, y1, x2, y2);
		g.drawLine(x1, y2, x2, y1);
	}

	private void vypisPozici(final Graphics2D g) {
		final KaLoc p = ka.getLoc();
		if (p != null) {
			final Mou mou = p.getMouSZ(); // souřadnice SZ kachle
			final int xx = mou.xx;
			final int yy = mou.yy;
			g.setColor(Color.WHITE);
			g.drawString("x = " + toHex(xx), 5, 15);
			g.drawString("y = " + toHex(yy), 5, 30);
			g.drawString("z = " + p.getMoumer(), 5, 45);

			final Wgs wgs = mou.toWgs();
			g.drawString("lat = " + wgs.lat, 100, 15);
			g.drawString("lon = " + wgs.lon, 100, 30);

			g.drawString("[" + p.getSignedX() + "," + p.getSignedY() + "]", 140, 50);
			g.drawString("[" + p.getFromSzUnsignedX() + "," + p.getFromSzUnsignedY() + "]", 140, 65);

		}
	}

}
