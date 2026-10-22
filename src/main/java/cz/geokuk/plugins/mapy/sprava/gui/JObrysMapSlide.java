package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.*;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
import cz.geokuk.plugins.mapy.kachle.ZvyrazneniMapyEvent;
import cz.geokuk.plugins.mapy.sprava.HlavickaMapy;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;
import cz.geokuk.plugins.mapy.sprava.SpravaMapEvent;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;

/**
 * Obrys území offline map jen tenkou čarou a jen na žádost: trvale do zoomu {@link #NEJVETSI_ZOOM}, když ho uživatel zapnul; obrys mapy, nad jejímž
 * řádkem v popupu je myš; obrys mapy, která se právě stahuje. Jen na obrazovce, do tisku a exportu nejde.
 */
public class JObrysMapSlide extends JSingleSlide0 {

	private static final long serialVersionUID = 1L;

	static final int NEJVETSI_ZOOM = 7;
	/** Klíč řádku popupu u souboru, který není v katalogu. */
	static final String VLASTNI = "soubor:";
	static final Color CARA = new Color(0x40, 0x40, 0x40, 160);

	private transient KachleModel kachleModel;
	private transient List<PolygonPokryti> obrysy = Collections.emptyList();
	private transient SwingWorker<List<PolygonPokryti>, Void> cteni;
	private boolean znovu;
	/** Uživatel zapnul trvalý obrys všech map ve složce. */
	boolean trvaly;
	/** Řádek popupu, nad kterým je myš (id mapy z katalogu nebo „soubor:“ a jméno); null, když žádný. */
	private String zvyrazneno;
	transient PolygonPokryti pokrytiZvyrazneni;
	/** Id mapy, která se právě stahuje; null, když žádná. */
	String stahovana;

	/** Otisk složky při posledním čtení; s online podkladem složku nikdo jiný nehlídá. */
	private String otisk;
	private final Timer hlidani = new Timer(2000, e -> nacti(false));
	transient BiFunction<File, KatalogMap, List<PolygonPokryti>> ctecka = SouboryMap::obrysy;

	public JObrysMapSlide() {
		setOpaque(false);
		setCursor(null);
		setVisible(false);
	}

	private void obnovViditelnost() {
		final boolean viditelny = trvaly || zvyrazneno != null || stahovana != null;
		setVisible(viditelny);
		if (trvaly) {
			hlidani.start();
		} else {
			hlidani.stop();
		}
		repaint();
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void onEvent(final ObrysPokrytiEvent event) {
		trvaly = event.isZapnuto();
		obnovViditelnost();
		if (trvaly) {
			nacti(true);
		}
	}

	public void onEvent(final ZvyrazneniMapyEvent event) {
		zvyrazni(event.getKlic());
	}

	void zvyrazni(final String klic) {
		zvyrazneno = klic;
		pokrytiZvyrazneni = null;
		obnovViditelnost();
		if (klic == null) {
			return;
		}
		if (!klic.startsWith(VLASTNI)) {
			pokrytiZvyrazneni = KatalogMap.vestaveny().podleId(klic).map(KatalogovaMapa::getPokryti).orElse(null);
			return;
		}
		final File slozka = slozkaMap();
		new SwingWorker<PolygonPokryti, Void>() {
			@Override
			protected PolygonPokryti doInBackground() throws IOException {
				final HlavickaMapy h = HlavickaMapy.precti(new File(slozka, klic.substring(VLASTNI.length())));
				return PolygonPokryti.obdelnik(h.getJih(), h.getZapad(), h.getSever(), h.getVychod());
			}

			@Override
			protected void done() {
				try {
					final PolygonPokryti p = get();
					if (klic.equals(zvyrazneno)) {
						pokrytiZvyrazneni = p;
						repaint();
					}
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (final ExecutionException | RuntimeException e) {
					// Nečitelná mapa nemá co zvýraznit.
				}
			}
		}.execute();
	}

	private File slozkaMap() {
		return kachleModel == null ? null : kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
	}

	public void onEvent(final OfflineMapaChangedEvent event) {
		if (trvaly) {
			nacti(true);
		}
	}

	public void onEvent(final cz.geokuk.plugins.mapy.sprava.StavyMapEvent event) {
		if (trvaly) {
			nacti(false);
		}
	}

	public void onEvent(final KachleUmisteniSouboruChangedEvent event) {
		if (trvaly) {
			nacti(true);
		}
	}

	public void onEvent(final SpravaMapEvent event) {
		final StavMapy.Faze faze = event.getStav().getFaze();
		final String id = event.getMapa().getId();
		if (faze == StavMapy.Faze.STAHUJE) {
			stahovana = id;
			obnovViditelnost();
		} else if (id.equals(stahovana)) {
			stahovana = null;
			obnovViditelnost();
		}
		if (trvaly && faze == StavMapy.Faze.HOTOVO) {
			nacti(true);
		}
	}

	/** Obrysy se čtou mimo EDT; bez vynucení jen když se mapy ve složce změnily. */
	void nacti(final boolean vynutit) {
		if (cteni != null) {
			znovu |= vynutit;
			return;
		}
		final File slozka = slozkaMap();
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
		if (!isSoordInitialized()) {
			return;
		}
		final List<PolygonPokryti> kresli = new ArrayList<>();
		if (trvaly && getSoord().getMoumer() <= NEJVETSI_ZOOM) {
			kresli.addAll(obrysy);
		}
		if (pokrytiZvyrazneni != null) {
			kresli.add(pokrytiZvyrazneni);
		}
		final PolygonPokryti stahovane = stahovana == null ? null : KatalogMap.vestaveny().podleId(stahovana).map(KatalogovaMapa::getPokryti).orElse(null);
		if (stahovane != null) {
			kresli.add(stahovane);
		}
		if (kresli.isEmpty()) {
			return;
		}
		final Graphics2D g = (Graphics2D) g0.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(CARA);
			for (final PolygonPokryti p : kresli) {
				g.draw(cestaZCache(p));
			}
		} finally {
			g.dispose();
		}
	}

	/** Obrysy přepočtené do pixelů pro {@link #cestyPro}; platí, dokud se nezmění výřez, aby se při dílčím překreslení nepřevádělo tisíce vrcholů znovu. */
	private final transient Map<PolygonPokryti, Path2D> cesty = new IdentityHashMap<>();
	private transient Coord cestyPro;

	Path2D cestaZCache(final PolygonPokryti p) {
		if (!getSoord().equals(cestyPro)) {
			cesty.clear();
			cestyPro = getSoord();
		}
		return cesty.computeIfAbsent(p, k -> cesta(cestyPro, k));
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
