package cz.geokuk.plugins.mapy.sprava;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.gui.RadaOfflineMapy;

/** Rada u hranice nenabízí území, které už máme, ani celek spolu s jeho regiony. */
public class RadaSousedniMapyTest {

	private static final String HLAVA = "{\"verze\": 1, \"zdroj\": \"server\", \"licence\": \"ODbL 1.0\", \"atribuce\": \"© OSM\", "
			+ "\"mapy\": \"https://example.org/maps/v5/\", \"polygony\": \"https://example.org/maps/poly/\", \"polozky\": [";
	// Střed (50, 10) je v zemi Doma; země Cizi s regiony A a B leží východně, země Stat1 s regionem C severně.
	private static final KatalogMap KATALOG = katalog("{\"id\": \"europe/doma\", \"nazev\": \"Doma\", \"velikost\": 50, \"obdelnik\": [49, 9, 51, 11]},"
			+ "{\"id\": \"europe/cizi\", \"nazev\": \"Cizí\", \"velikost\": 90, \"obdelnik\": [49, 11, 51, 13]},"
			+ "{\"id\": \"europe/cizi/a\", \"nazev\": \"Cizí A\", \"skupina\": \"Cizí\", \"velikost\": 40, \"obdelnik\": [49, 11, 51, 12]},"
			+ "{\"id\": \"europe/cizi/b\", \"nazev\": \"Cizí B\", \"skupina\": \"Cizí\", \"velikost\": 50, \"obdelnik\": [49, 12, 51, 13]}");
	private static final RadaOfflineMapy.Vyrez VYREZ = new RadaOfflineMapy.Vyrez(49.5, 9.5, 50.5, 12.5);
	private static final Wgs STRED = new Wgs(50, 10);

	private static KatalogMap katalog(final String polozky) {
		try {
			return KatalogMap.nacti(HLAVA + polozky + "]}", id -> null);
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<String> sousedi(final Function<KatalogovaMapa, StavMapy> stavy) {
		final List<String> id = new ArrayList<>();
		for (final RadaOfflineMapy s : RadaOfflineMapy.pro(KATALOG, stavy, STRED, null, VYREZ).sousedni) {
			id.add(s.mapa.getId());
		}
		return id;
	}

	private static StavMapy mame(final KatalogovaMapa m) {
		return StavMapyProGui.stav(m, StavMapy.Mistni.MAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null);
	}

	/** Celek se nenabízí spolu s regiony; nabídnou se regiony. */
	@Test
	public void celekSpolecneSRegionyNe() {
		Assert.assertEquals(java.util.Arrays.asList("europe/cizi/a", "europe/cizi/b"), sousedi(StavMapy::novy));
	}

	/** Stažená země = její regiony se nenabízí; stažený region = nenabízí se ani celek. */
	@Test
	public void uzemiKtereUzMame() {
		Assert.assertTrue("celek stažený, regiony se nenabízí", sousedi(m -> m.getId().equals("europe/cizi") ? mame(m) : StavMapy.novy(m)).isEmpty());
		final List<String> bezA = sousedi(m -> m.getId().equals("europe/cizi/a") ? mame(m) : StavMapy.novy(m));
		Assert.assertEquals("stažený region: zbytek země se nabídne po regionech, celek ne", java.util.Collections.singletonList("europe/cizi/b"), bezA);
	}
}
