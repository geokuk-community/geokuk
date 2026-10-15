package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;
import javax.swing.Timer;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.ObrysPokrytiEvent;
import cz.geokuk.plugins.mapy.kachle.OfflineMapaChangedEvent;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;

/**
 * V přehledu (do zoomu {@link #NEJVETSI_ZOOM}) lehce zašedne území, které offline mapy ve složce nepokrývají; v podrobnějším
 * zoomu ukazuje pokrytí sama mapa. Jen na obrazovce, do tisku a exportu nejde.
 */
public class JObrysMapSlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	static final int NEJVETSI_ZOOM = 9;
	static final Color MIMO = new Color(0x50, 0x50, 0x50, 70);
	static final Color CARA = new Color(0x40, 0x40, 0x40, 160);

	private transient KachleModel kachleModel;
	private transient List<PolygonPokryti> obrysy = Collections.emptyList();
	private transient SwingWorker<List<PolygonPokryti>, Void> cteni;
	private boolean znovu;
	/** Otisk složky při posledním čtení; s online podkladem složku nikdo jiný nehlídá. */
	private String otisk;
	private final Timer hlidani = new Timer(2000, e -> nacti(false));
	transient BiFunction<File, KatalogMap, List<PolygonPokryti>> ctecka = SouboryMap::obrysy;

	public JObrysMapSlide() {
		setOpaque(false);
		setCursor(null);
		setVisible(false);
	}

	@Override
	public void setVisible(final boolean viditelny) {
		super.setVisible(viditelny);
		if (viditelny) {
			hlidani.start();
		} else {
			hlidani.stop();
		}
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void onEvent(final ObrysPokrytiEvent event) {
		setVisible(event.isZapnuto());
		if (event.isZapnuto()) {
			nacti(true);
		}
	}

	public void onEvent(final OfflineMapaChangedEvent event) {
		if (isVisible()) {
			nacti(true);
		}
	}

	public void onEvent(final KachleUmisteniSouboruChangedEvent event) {
		if (isVisible()) {
			nacti(true);
		}
	}

	public void onEvent(final SpravaMapEvent event) {
		if (isVisible() && event.getStav().getFaze() == StavMapy.Faze.HOTOVO) {
			nacti(true);
		}
	}

	/** Obrysy se čtou mimo EDT; bez vynucení jen když se mapy ve složce změnily. */
	void nacti(final boolean vynutit) {
		if (cteni != null) {
			znovu |= vynutit;
			return;
		}
		final File slozka = kachleModel == null ? null : kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
		final String predchozi = vynutit ? null : otisk;
		final BiFunction<File, KatalogMap, List<PolygonPokryti>> cti = ctecka;
		cteni = new SwingWorker<List<PolygonPokryti>, Void>() {
			private String novy;

			@Override
			protected List<PolygonPokryti> doInBackground() {
				novy = SouboryMap.otisk(slozka);
				return novy.equals(predchozi) ? null : cti.apply(slozka, KatalogMap.vestaveny());
			}

			@Override
			protected void done() {
				cteni = null;
				try {
					final List<PolygonPokryti> nove = get();
					if (nove != null) {
						otisk = novy;
						setObrysy(nove);
					}
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					FExceptionDumper.dump(e.getCause(), EExceptionSeverity.WORKARROUND, "Obrys pokrytí offline map");
				}
				if (znovu) {
					znovu = false;
					nacti(true);
				}
			}
		};
		cteni.execute();
	}

	boolean isCteni() {
		return cteni != null;
	}

	void setObrysy(final List<PolygonPokryti> obrysy) {
		this.obrysy = obrysy;
		repaint();
	}

	@Override
	protected void paintComponent(final Graphics g0) {
		if (!isSoordInitialized() || obrysy.isEmpty() || getSoord().getMoumer() > NEJVETSI_ZOOM) {
			return;
		}
		final Graphics2D g = (Graphics2D) g0.create();
		try {
			final Area mimo = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
			final List<Path2D> cesty = new ArrayList<>(obrysy.size());
			for (final PolygonPokryti p : obrysy) {
				final Path2D cesta = cesta(getSoord(), p);
				cesty.add(cesta);
				mimo.subtract(new Area(cesta));
			}
			g.setColor(MIMO);
			g.fill(mimo);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(CARA);
			for (final Path2D cesta : cesty) {
				g.draw(cesta);
			}
		} finally {
			g.dispose();
		}
	}

	/** Obrys v pixelech výřezu; díry se z výplně vyříznou. */
	static Path2D cesta(final Coord soord, final PolygonPokryti p) {
		final Path2D cesta = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		for (final List<Wgs> kruh : p.getObrysy()) {
			pridej(cesta, soord, kruh);
		}
		for (final List<Wgs> kruh : p.getDiry()) {
			pridej(cesta, soord, kruh);
		}
		return cesta;
	}

	private static void pridej(final Path2D cesta, final Coord soord, final List<Wgs> kruh) {
		Point posledni = null;
		for (final Wgs w : kruh) {
			final Point b = soord.transform(w.toMou());
			if (posledni == null) {
				cesta.moveTo(b.x, b.y);
				posledni = b;
			} else if (Math.abs(b.x - posledni.x) > 1 || Math.abs(b.y - posledni.y) > 1) { // při oddálení padne mnoho bodů obrysu na stejné místo
				cesta.lineTo(b.x, b.y);
				posledni = b;
			}
		}
		if (posledni != null) {
			cesta.closePath();
		}
	}
}
