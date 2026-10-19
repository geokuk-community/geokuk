package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.reader.MapFile;

/**
 * Soubor mapy, který jako přehledová mapa (hrubá mapa světa) dává data jen tam, kde nejsou podrobné mapy: mapsforge by její data četl i na podrobných
 * zoomech a kreslil je vedle podrobné mapy (třeba druhou, hrubou hranici státu).
 */
class SouborMapy extends MapFile {

	/** Do tohoto zoomu se přehledová mapa kreslí všude, výš jen mimo podrobné mapy. */
	static final int PREHLED_DO_ZOOMU = 8;

	private List<? extends MapFile> podrobne = Collections.emptyList();

	SouborMapy(final File soubor) {
		super(soubor);
	}

	/** Mapa je přehledová a podrobné mapy, které ji nad {@link #PREHLED_DO_ZOOMU} nahradí. */
	void setPodrobne(final List<? extends MapFile> podrobne) {
		this.podrobne = podrobne;
	}

	private boolean nahrazena(final BoundingBox oblast, final byte zoom) {
		if (zoom <= PREHLED_DO_ZOOMU) {
			return false;
		}
		for (final MapFile m : podrobne) {
			if (m.supportsArea(oblast, zoom)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean supportsTile(final Tile tile) {
		return super.supportsTile(tile) && !nahrazena(tile.getBoundingBox(), tile.zoomLevel);
	}

	@Override
	public boolean supportsFullTile(final Tile tile) {
		return super.supportsFullTile(tile) && !nahrazena(tile.getBoundingBox(), tile.zoomLevel);
	}

	@Override
	public boolean supportsArea(final BoundingBox oblast, final byte zoom) {
		return super.supportsArea(oblast, zoom) && !nahrazena(oblast, zoom);
	}

	@Override
	public boolean supportsFullArea(final BoundingBox oblast, final byte zoom) {
		return super.supportsFullArea(oblast, zoom) && !nahrazena(oblast, zoom);
	}
}
