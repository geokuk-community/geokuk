package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore;
import org.mapsforge.map.layer.renderer.*;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import lombok.extern.slf4j.Slf4j;

/**
 * Vykresluje dlaždice z otevřených souborů .map jedním tématem. Je bezpečný pro více vláken. Zavře se, až doběhnou rozdělaná vykreslení.
 */
@Slf4j
final class OfflineRenderer {

	static final int VELIKOST_DLAZDICE = 256;

	private static final GraphicFactory GRAFIKA = AwtGraphicFactory.INSTANCE;

	private final MultiMapDataStore data;
	private final RenderThemeFuture tema;
	private final DisplayModel displayModel;
	private final DatabaseRenderer renderer;
	private final String klic;
	/** Proč se nepoužilo zvolené téma, null když se použilo. */
	private final String chybaTematu;

	private int rozdelanych;
	private boolean zavrit;
	private boolean zavreny;

	private OfflineRenderer(final MultiMapDataStore data, final RenderThemeFuture tema, final DisplayModel displayModel, final String klic, final String chybaTematu) {
		this.data = data;
		this.tema = tema;
		this.displayModel = displayModel;
		this.klic = klic;
		this.chybaTematu = chybaTematu;
		renderer = new DatabaseRenderer(data, GRAFIKA, null, new MapDataStoreLabelStore(data, tema, 1f, displayModel, GRAFIKA), true, false, null);
	}

	/**
	 * Otevře mapy a načte téma; když téma načíst nejde, použije výchozí.
	 *
	 * @throws IOException
	 *             když nejde otevřít některý soubor mapy
	 */
	static OfflineRenderer otevri(final List<File> mapy, final TemaOfflineMapy tema) throws IOException {
		if (mapy.isEmpty()) {
			throw new IOException("Žádný soubor mapy.");
		}
		final MultiMapDataStore data = new MultiMapDataStore(MultiMapDataStore.DataPolicy.DEDUPLICATE);
		final StringBuilder otisk = new StringBuilder();
		try {
			for (final File f : mapy) {
				final MapFile mapFile;
				try {
					mapFile = new MapFile(f);
				} catch (final RuntimeException e) {
					throw new IOException("Soubor mapy " + f + " nejde otevřít: " + e.getMessage(), e);
				}
				data.addMapDataStore(mapFile, false, false);
				otisk.append(f.getAbsolutePath()).append(':').append(f.length()).append(':').append(mapFile.getMapFileInfo().mapDate).append('\n');
			}
		} catch (final IOException | RuntimeException e) {
			data.close();
			throw e;
		}
		final DisplayModel displayModel = new DisplayModel();
		displayModel.setFixedTileSize(VELIKOST_DLAZDICE);
		String chybaTematu = null;
		TemaOfflineMapy pouzite = tema;
		RenderThemeFuture rtf;
		try {
			rtf = nactiTema(tema, displayModel);
		} catch (final IOException e) {
			chybaTematu = e.getMessage();
			log.warn("Téma offline mapy {} nejde použít, kreslí se výchozím: {}", tema, chybaTematu);
			pouzite = TemaOfflineMapy.VYCHOZI;
			rtf = nactiTema(pouzite, displayModel);
		}
		otisk.append(pouzite.otisk()).append('\n').append(VELIKOST_DLAZDICE);
		return new OfflineRenderer(data, rtf, displayModel, klic(otisk.toString()), chybaTematu);
	}

	private static RenderThemeFuture nactiTema(final TemaOfflineMapy tema, final DisplayModel displayModel) throws IOException {
		final RenderThemeFuture rtf = new RenderThemeFuture(GRAFIKA, tema.vytvor(), displayModel);
		rtf.run();
		try {
			rtf.get();
		} catch (final ExecutionException e) {
			final Throwable pricina = e.getCause() != null && e.getCause().getCause() != null ? e.getCause().getCause() : e.getCause();
			throw new IOException("Téma " + tema + " nejde načíst: " + pricina, pricina);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Načítání tématu přerušeno");
		}
		return rtf;
	}

	/** Klíč dlaždic v cache: písmeno o a 8 šestnáctkových číslic, vejde se do sloupce typu. */
	static String klic(final String otisk) {
		final CRC32 crc = new CRC32();
		crc.update(otisk.getBytes(StandardCharsets.UTF_8));
		return String.format("o%08x", crc.getValue());
	}

	String getKlic() {
		return klic;
	}

	String getChybaTematu() {
		return chybaTematu;
	}

	static Tile dlazdice(final KaLoc loc) {
		return new Tile(loc.getFromSzUnsignedX(), loc.getFromSzUnsignedY(), (byte) loc.getMoumer(), VELIKOST_DLAZDICE);
	}

	/** Dlaždice zasahuje do některé z map. */
	boolean pokryva(final KaLoc loc) {
		return data.supportsTile(dlazdice(loc));
	}

	/** Začátek vykreslování; false, když už je renderer zavřený. */
	synchronized boolean zacni() {
		if (zavrit) {
			return false;
		}
		rozdelanych++;
		return true;
	}

	synchronized void skonci() {
		rozdelanych--;
		if (zavrit && rozdelanych == 0) {
			zavriTed();
		}
	}

	/** Zavře mapy, jakmile doběhnou rozdělaná vykreslení. */
	synchronized void zavri() {
		zavrit = true;
		if (rozdelanych == 0) {
			zavriTed();
		}
	}

	synchronized boolean isZavreny() {
		return zavreny;
	}

	private void zavriTed() {
		if (zavreny) {
			return;
		}
		zavreny = true;
		tema.decrementRefCount();
		data.close();
	}

	/** Vykreslí dlaždici; volat mezi {@link #zacni()} a {@link #skonci()}. */
	ImageWithData vyrendruj(final KaLoc loc) throws IOException {
		final RendererJob job = new RendererJob(dlazdice(loc), data, tema, displayModel, 1f, false, false);
		final TileBitmap bitmapa = renderer.executeJob(job);
		if (bitmapa == null) {
			throw new IOException("Dlaždici " + loc + " offline mapy nejde vykreslit.");
		}
		final BufferedImage obrazek = AwtGraphicFactory.getBitmap(bitmapa);
		final ByteArrayOutputStream png = new ByteArrayOutputStream(64 * 1024);
		ImageIO.write(obrazek, "png", png);
		return new ImageWithData(obrazek, png.toByteArray());
	}
}
