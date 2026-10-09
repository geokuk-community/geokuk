package cz.geokuk.plugins.mapy.kachle.podklady;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * Popisky dlaždic offline mapy s pamětí: každá dlaždice potřebuje popisky celého okolí 3×3, sousední dlaždice je tak čtou opakovaně. Dlaždice se ukládají
 * pod celým klíčem {@link Tile}, ne jen pod jeho hashem.
 */
final class PopiskyOfflineMapy extends MapDataStoreLabelStore {

	private static final int MAX_DLAZDIC = 2000;

	private final Cache<Tile, List<MapElementContainer>> popisky = CacheBuilder.newBuilder().maximumSize(MAX_DLAZDIC).softValues().build();

	PopiskyOfflineMapy(final MapDataStore data, final RenderThemeFuture tema, final DisplayModel displayModel, final GraphicFactory grafika) {
		super(data, tema, 1f, displayModel, grafika);
	}

	@Override
	public List<MapElementContainer> getVisibleItems(final Tile vlevoNahore, final Tile vpravoDole) {
		final List<MapElementContainer> vysledek = new ArrayList<>();
		for (int x = vlevoNahore.tileX; x <= vpravoDole.tileX; x++) {
			for (int y = vlevoNahore.tileY; y <= vpravoDole.tileY; y++) {
				final Tile dlazdice = new Tile(x, y, vlevoNahore.zoomLevel, vlevoNahore.tileSize);
				try {
					vysledek.addAll(popisky.get(dlazdice, () -> super.getVisibleItems(dlazdice, dlazdice)));
				} catch (final ExecutionException | UncheckedExecutionException e) {
					throw new IllegalStateException("Popisky dlaždice " + dlazdice + " nejde načíst", e.getCause());
				}
			}
		}
		return vysledek;
	}

	@Override
	public void clear() {
		popisky.invalidateAll();
		super.clear();
	}
}
