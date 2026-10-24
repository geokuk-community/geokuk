package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.map.reader.MapFile;

import cz.geokuk.plugins.mapy.sprava.*;

/** Zapojení katalogů do oblastního klíče při otevření rendereru: katalogová mapa a vrstva dostanou obrys, soubor uživatele ne. */
public class OblastniKlicKatalogTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File mapa;
	private File vrstva;
	private BoundingBox bbox;

	@Before
	public void setUp() throws Exception {
		mapa = new File(tmp.newFolder("mapy"), "kukov.map");
		Files.copy(OfflineMapyTest.class.getResourceAsStream("/offline-mapy/kukov.map"), mapa.toPath());
		final File vrstvy = tmp.newFolder("mapy", "layers", "test");
		vrstva = new File(vrstvy, "kukov.tourist.map");
		Files.copy(OfflineMapyTest.class.getResourceAsStream("/offline-mapy-trasy/vrstva.tourist.map"), vrstva.toPath());
		final MapFile m = new MapFile(mapa);
		bbox = m.getMapFileInfo().boundingBox;
		m.close();
	}

	private OblastniKlic otevri(final KatalogMap katalog, final KatalogVrstev vrstvy) throws Exception {
		final OfflineRenderer r = OfflineRenderer.otevri(Collections.singletonList(mapa), Arrays.asList(vrstva), null, OfflineRenderer.nactiTema(TemaOfflineMapy.VYCHOZI), false, katalog, vrstvy);
		try {
			return r.getOblastniKlic();
		} finally {
			r.zavri();
		}
	}

	private KatalogMap katalog() throws Exception {
		return KatalogyProOfflineTest.mapa("test/kukov", bbox.minLatitude, bbox.minLongitude, bbox.maxLatitude, bbox.maxLongitude);
	}

	@Test
	public void katalogovaMapaAVrstvaMajiObrys() throws Exception {
		final KatalogMap katalog = katalog();
		Assert.assertEquals(2, otevri(katalog, KatalogyProOfflineTest.vrstva(katalog, "test/kukov", "tourist")).pocetObrysu());
	}

	@Test
	public void bezKatalogovehoZaznamuObdelnik() throws Exception {
		final KatalogMap jina = KatalogyProOfflineTest.mapa("test/jina", bbox.minLatitude, bbox.minLongitude, bbox.maxLatitude, bbox.maxLongitude);
		Assert.assertEquals("mapa ani vrstva v katalogu nejsou", 0, otevri(jina, KatalogyProOfflineTest.vrstva(jina, "test/jina", "tourist")).pocetObrysu());
	}

	/** Mapa v katalogu bez vrstvy: obrys má jen mapa. */
	@Test
	public void jenMapaMaObrys() throws Exception {
		final KatalogMap katalog = katalog();
		final KatalogMap jina = KatalogyProOfflineTest.mapa("test/jina", bbox.minLatitude, bbox.minLongitude, bbox.maxLatitude, bbox.maxLongitude);
		Assert.assertEquals(1, otevri(katalog, KatalogyProOfflineTest.vrstva(jina, "test/jina", "tourist")).pocetObrysu());
	}

	/** Soubor uživatele téhož jména, ale oříznutý nebo větší než katalogová mapa, obrys nedostane. */
	@Test
	public void souborJinehoRozsahuNeniKatalogovaMapa() throws Exception {
		final KatalogMap velka = KatalogyProOfflineTest.mapa("test/kukov", bbox.minLatitude - 3, bbox.minLongitude - 3, bbox.maxLatitude + 3, bbox.maxLongitude + 3);
		Assert.assertEquals(0, otevri(velka, KatalogyProOfflineTest.vrstva(velka, "test/kukov", "tourist")).pocetObrysu());
	}
}
