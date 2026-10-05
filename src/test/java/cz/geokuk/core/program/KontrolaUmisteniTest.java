package cz.geokuk.core.program;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.UzivatelskeMapy;

public class KontrolaUmisteniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final Map<String, String> NIC = Collections.emptyMap();

	@Test
	public void poznaSynchronizovaneSlozky() {
		Assert.assertEquals("OneDrive", KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/OneDrive - Firma/Plocha/GeoKuk"), NIC));
		Assert.assertEquals("Dropbox", KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/Dropbox/GeoKuk"), NIC));
		Assert.assertEquals("Google Disk", KontrolaUmisteni.synchronizovanaSluzba(new File("/G/Můj disk/GeoKuk"), NIC));
		Assert.assertEquals("OneDrive", KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/Dokumenty/GeoKuk"),
				Collections.singletonMap("OneDrive", "/home/kacer/Dokumenty")));
	}

	@Test
	public void pripraviSlozkuUzivatelskychMap() {
		final File mapy = UzivatelskeMapy.slozka();
		mapy.delete();
		Assume.assumeFalse("složka už obsahuje soubory", mapy.exists());
		KontrolaUmisteni.pripravSlozky();
		Assert.assertTrue(mapy.isDirectory());
		Assert.assertEquals(new File(FConst.DATA_DIR, "mapy"), mapy);
	}

	@Test
	public void beznaSlozkaNeniSynchronizovana() {
		Assert.assertNull(KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/GeoKuk"), Collections.singletonMap("OneDrive", "/home/kacer/OneDrive")));
	}

	@Test
	public void ukazkyMapSeDoplniAObnovi() throws Exception {
		final File slozka = tmp.newFolder("mapy-priklady");
		final File osm = new File(slozka, "osm.mapa");
		Files.write(osm.toPath(), "stará verze".getBytes(StandardCharsets.UTF_8));
		final File vlastni = new File(slozka, "moje.mapa");
		Files.write(vlastni.toPath(), "moje".getBytes(StandardCharsets.UTF_8));
		KontrolaUmisteni.doplnUkazkyMap(slozka);
		for (final String jmeno : KontrolaUmisteni.UKAZKY_MAP) {
			Assert.assertArrayEquals(jmeno, Files.readAllBytes(new File("priklady/mapy", jmeno).toPath()), Files.readAllBytes(new File(slozka, jmeno).toPath()));
		}
		Assert.assertEquals("moje", new String(Files.readAllBytes(vlastni.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void seznamUkazekOdpovidaSlozcePriklady() {
		final String[] soubory = new File("priklady/mapy").list();
		Arrays.sort(soubory);
		Assert.assertEquals(Arrays.asList(soubory), KontrolaUmisteni.UKAZKY_MAP);
	}
}
