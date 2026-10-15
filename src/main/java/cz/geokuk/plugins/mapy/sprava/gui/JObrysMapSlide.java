package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.ObrysPokrytiEvent;
import cz.geokuk.plugins.mapy.kachle.OfflineMapaChangedEvent;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;

/** Obrys území, které pokrývají offline mapy ve složce; jen na obrazovce, do tisku a exportu nejde. */
public class JObrysMapSlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	static final Color CARA = new Color(0x2E, 0x9E, 0x4F, 210);
	static final Color VYPLN = new Color(0x2E, 0x9E, 0x4F, 28);

	private transient KachleModel kachleModel;
	private transient List<PolygonPokryti> obrysy = Collections.emptyList();
	private transient SwingWorker<List<PolygonPokryti>, Void> cteni;
	private boolean znovu;

	public JObrysMapSlide() {
		setOpaque(false);
		setCursor(null);
		setVisible(false);
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void onEvent(final ObrysPokrytiEvent event) {
		setVisible(event.isZapnuto());
		if (event.isZapnuto()) {
			nacti();
		}
	}

	public void onEvent(final OfflineMapaChangedEvent event) {
		if (isVisible()) {
			nacti();
		}
	}

	public void onEvent(final KachleUmisteniSouboruChangedEvent event) {
		if (isVisible()) {
			nacti();
		}
	}

	private void nacti() {
		if (cteni != null) {
			znovu = true;
			return;
		}
		final File slozka = kachleModel == null ? null : kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
		cteni = new SwingWorker<List<PolygonPokryti>, Void>() {
			@Override
			protected List<PolygonPokryti> doInBackground() {
				return SouboryMap.obrysy(slozka, KatalogMap.vestaveny());
			}

			@Override
			protected void done() {
				cteni = null;
				try {
					setObrysy(get());
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException e) {
					FExceptionDumper.dump(e.getCause(), EExceptionSeverity.WORKARROUND, "Obrys pokrytí offline map");
				}
				if (znovu) {
					znovu = false;
					nacti();
				}
			}
		};
		cteni.execute();
	}

	void setObrysy(final List<PolygonPokryti> obrysy) {
		this.obrysy = obrysy;
		repaint();
	}

	@Override
	protected void paintComponent(final Graphics g0) {
		if (!isSoordInitialized() || obrysy.isEmpty()) {
			return;
		}
		final Graphics2D g = (Graphics2D) g0.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			for (final PolygonPokryti p : obrysy) {
				final Path2D cesta = cesta(getSoord(), p);
				g.setColor(VYPLN);
				g.fill(cesta);
				g.setColor(CARA);
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
		boolean prvni = true;
		for (final Wgs w : kruh) {
			final Point b = soord.transform(w.toMou());
			if (prvni) {
				cesta.moveTo(b.x, b.y);
				prvni = false;
			} else {
				cesta.lineTo(b.x, b.y);
			}
		}
		if (!prvni) {
			cesta.closePath();
		}
	}
}
