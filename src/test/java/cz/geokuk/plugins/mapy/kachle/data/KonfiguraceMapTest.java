package cz.geokuk.plugins.mapy.kachle.data;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.FConst;
import cz.geokuk.core.coordinates.Mou;

public class KonfiguraceMapTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final KaLoc loc = KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13);

	@Test
	public void kazdaVrstvaMaAdresu() throws Exception {
		for (final EKaType typ : EKaType.values()) {
			new URL(KonfiguraceMap.url(typ, loc));
		}
		final String zxy = loc.getMoumer() + "-" + loc.getFromSzUnsignedX() + "-" + loc.getFromSzUnsignedY();
		Assert.assertEquals("http://mapserver.mapy.cz/turist-m/" + zxy, KonfiguraceMap.url(EKaType.TURIST_M, loc));
		Assert.assertEquals("https://tile.openstreetmap.org/" + zxy.replace('-', '/') + ".png", KonfiguraceMap.url(EKaType.OPEN_STREET, loc));
	}

	@Test
	public void hlavickyPodleServeru() {
		Assert.assertEquals("https://en.mapy.com/", KonfiguraceMap.hlavicky("mapserver.mapy.cz").get("Referer"));
		Assert.assertEquals("Geokuk/" + FConst.VERSION + " (+https://github.com/geokuk-community/geokuk)", KonfiguraceMap.hlavicky("tile.openstreetmap.org").get("User-Agent"));
		Assert.assertTrue(KonfiguraceMap.hlavicky("outdoor.tiles.freemap.sk").isEmpty());
	}

	@Test
	public void mistniSouborMaPrednost() throws Exception {
		final File stazena = zapis("stazena.properties", "TURIST_M.url=https://a/{z}/{x}/{y}\nBASE_M.url=https://b/{z}/{x}/{y}\n");
		final File mistni = zapis("mistni.properties", "TURIST_M.url=https://c/{z}/{x}/{y}\nhlavicka.mapy.cz.Referer=\n");
		final Properties p = KonfiguraceMap.nacti(stazena, mistni);
		Assert.assertEquals("https://c/{z}/{x}/{y}", p.getProperty("TURIST_M.url"));
		Assert.assertEquals("https://b/{z}/{x}/{y}", p.getProperty("BASE_M.url"));
		Assert.assertTrue(KonfiguraceMap.hlavicky(p, "mapserver.mapy.cz").isEmpty());
	}

	@Test(expected = IOException.class)
	public void neplatnaAdresaSeOdmitne() throws Exception {
		KonfiguraceMap.precti("TURIST_M.url=file:///etc/passwd\n".getBytes(StandardCharsets.UTF_8));
	}

	private File zapis(final String jmeno, final String obsah) throws IOException {
		final File f = tmp.newFile(jmeno);
		Files.write(f.toPath(), obsah.getBytes(StandardCharsets.UTF_8));
		return f;
	}
}
