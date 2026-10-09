package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mapsforge.core.mapelements.MapElementContainer;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.layer.labels.MapDataStoreLabelStore;
import org.mapsforge.map.reader.MapFile;

/** Popisky z paměti musí patřit té dlaždici, pro kterou se chtějí, i když má jiná dlaždice stejný hash. */
public class PopiskyOfflineMapyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static List<String> popisy(final List<MapElementContainer> prvky) {
		final List<String> s = new ArrayList<>();
		for (final MapElementContainer p : prvky) {
			s.add(p.getClass().getSimpleName() + p.getPoint());
		}
		Collections.sort(s);
		return s;
	}

	@Test
	public void dlazdiceSeStejnymHashemMajiSvePopisky() throws Exception {
		final File slozka = tmp.newFolder();
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final OfflineRenderer.NacteneTema tema = OfflineRenderer.nactiTema(TemaOfflineMapy.VYCHOZI);
		final MapFile data = new MapFile(new File(slozka, "kukov.map"));
		try {
			final MapDataStoreLabelStore bezPameti = new MapDataStoreLabelStore(data, tema.future, 1f, tema.displayModel, AwtGraphicFactory.INSTANCE);
			final byte z = 19;
			final Tile roh = OfflineRenderer.dlazdice(cz.geokuk.plugins.mapy.kachle.data.KaLoc.ofJZ(new cz.geokuk.core.coordinates.Wgs(50.010, 14.390).toMou(), z));
			Tile a = null;
			Tile b = null;
			final Map<Integer, Tile> podleHashe = new HashMap<>();
			hledej: for (int x = roh.tileX; x < roh.tileX + 46; x++) {
				for (int y = roh.tileY; y < roh.tileY + 48; y++) {
					final Tile t = new Tile(x, y, z, 256);
					final Tile jina = podleHashe.putIfAbsent(t.hashCode(), t);
					if (jina == null) {
						continue;
					}
					final List<String> pa = popisy(bezPameti.getVisibleItems(jina, jina));
					final List<String> pb = popisy(bezPameti.getVisibleItems(t, t));
					if (!pa.isEmpty() && !pb.isEmpty() && !pa.equals(pb)) {
						a = jina;
						b = t;
						break hledej;
					}
				}
			}
			Assert.assertNotNull("v testovací mapě je dvojice dlaždic se stejným hashem a různými popisky", a);
			final PopiskyOfflineMapy sPameti = new PopiskyOfflineMapy(data, tema.future, tema.displayModel, AwtGraphicFactory.INSTANCE);
			Assert.assertEquals(popisy(bezPameti.getVisibleItems(a, a)), popisy(sPameti.getVisibleItems(a, a)));
			Assert.assertEquals(popisy(bezPameti.getVisibleItems(b, b)), popisy(sPameti.getVisibleItems(b, b)));
		} finally {
			data.close();
			tema.uvolni();
		}
	}
}
