package cz.geokuk.plugins.cesty.routovani;

import java.io.File;
import java.util.*;

import org.mapsforge.core.model.*;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.SubFileParameter;

/** Cesty pro pěší z jednoho souboru .map, čtené po dlaždicích základního zoomu s cache. */
final class MapaCest implements AutoCloseable {

	/** Zoom, ve kterém má mapa mít nezjednodušené cesty. */
	static final byte ZOOM_CEST = 14;

	/** Cesty pro pěší v jedné dlaždici. */
	static final class Dlazdice {
		/** Lomené čáry, souřadnice po dvojicích (lat, lon) v mikrostupních. */
		final int[][] cary;
		/** Koeficient délky každé čáry. */
		final float[] koeficienty;
		final int bodu;

		Dlazdice(final int[][] cary, final float[] koeficienty) {
			this.cary = cary;
			this.koeficienty = koeficienty;
			int b = 0;
			for (final int[] c : cary) {
				b += c.length / 2;
			}
			bodu = b;
		}
	}

	private final File soubor;
	private final MapFile mapFile;
	private final BoundingBox hranice;
	/** Základní zoom intervalu se zoomem {@link #ZOOM_CEST}; v něm jsou dlaždice. */
	private final byte zoom;
	private final long maxBoduCache;

	private final LinkedHashMap<Long, Dlazdice> cache = new LinkedHashMap<>(64, 0.75f, true);
	private long boduVCache;

	private MapaCest(final File soubor, final MapFile mapFile, final byte zoom, final long maxBoduCache) {
		this.soubor = soubor;
		this.mapFile = mapFile;
		hranice = mapFile.boundingBox();
		this.zoom = zoom;
		this.maxBoduCache = maxBoduCache;
	}

	/**
	 * Otevře mapu. Mapa bez nezjednodušených cest (nemá zoom {@link #ZOOM_CEST}) se nepoužije.
	 *
	 * @return null, když mapa cesty v potřebném zoomu nemá
	 */
	static MapaCest otevri(final File soubor, final long maxBoduCache) {
		final MapFile mf = new MapFile(soubor);
		final SubFileParameter sp = mf.getMapFileHeader().getSubFileParameter(ZOOM_CEST);
		if (sp == null || sp.zoomLevelMin > ZOOM_CEST || sp.zoomLevelMax < ZOOM_CEST) {
			mf.close();
			return null;
		}
		return new MapaCest(soubor, mf, sp.baseZoomLevel, maxBoduCache);
	}

	File getSoubor() {
		return soubor;
	}

	byte getZoom() {
		return zoom;
	}

	boolean pokryva(final double lat, final double lon) {
		return hranice.contains(lat, lon);
	}

	boolean prekryva(final BoundingBox bb) {
		return hranice.intersects(bb);
	}

	/** Dlaždice zoomu {@link #getZoom()}, z cache nebo přečtená z mapy. */
	Dlazdice dlazdice(final int x, final int y) {
		final long klic = (long) x << 32 | y & 0xffffffffL;
		synchronized (cache) {
			final Dlazdice d = cache.get(klic);
			if (d != null) {
				return d;
			}
		}
		final Dlazdice d = precti(x, y);
		synchronized (cache) {
			if (cache.put(klic, d) == null) {
				boduVCache += d.bodu;
			}
			final Iterator<Dlazdice> it = cache.values().iterator();
			while (boduVCache > maxBoduCache && cache.size() > 1 && it.hasNext()) {
				boduVCache -= it.next().bodu;
				it.remove();
			}
		}
		return d;
	}

	private Dlazdice precti(final int x, final int y) {
		final Tile tile = new Tile(x, y, zoom, 256);
		final List<int[]> cary = new ArrayList<>();
		final List<Float> koef = new ArrayList<>();
		if (mapFile.supportsTile(tile)) {
			final MapReadResult r = mapFile.readMapData(tile);
			if (r == null) {
				// Chyba čtení (mapsforge ji jen zaloguje); prázdná dlaždice by se uložila do cache.
				throw new IllegalStateException("Dlaždici " + tile + " offline mapy " + soubor.getName() + " nejde přečíst.");
			}
			for (final Way w : r.ways) {
				final float k = PeskyProfil.koeficient(w.tags);
				if (k <= 0) {
					continue;
				}
				for (final LatLong[] cast : w.latLongs) {
					if (cast.length < 2) {
						continue;
					}
					final int[] c = new int[cast.length * 2];
					for (int i = 0; i < cast.length; i++) {
						c[2 * i] = (int) Math.round(cast[i].latitude * 1e6);
						c[2 * i + 1] = (int) Math.round(cast[i].longitude * 1e6);
					}
					cary.add(c);
					koef.add(k);
				}
			}
		}
		final float[] k = new float[koef.size()];
		for (int i = 0; i < k.length; i++) {
			k[i] = koef.get(i);
		}
		return new Dlazdice(cary.toArray(new int[0][]), k);
	}

	@Override
	public void close() {
		mapFile.close();
	}
}
