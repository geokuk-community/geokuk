package cz.geokuk.core.program;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

public class KontrolaUmisteniTest {

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
	public void beznaSlozkaNeniSynchronizovana() {
		Assert.assertNull(KontrolaUmisteni.synchronizovanaSluzba(new File("/home/kacer/GeoKuk"), Collections.singletonMap("OneDrive", "/home/kacer/OneDrive")));
	}

	@Test
	public void staraDataZVerze600() throws Exception {
		final File home = java.nio.file.Files.createTempDirectory("home").toFile();
		final File stara = new File(home, "geokuk");
		final File data = new File(home, "GeoKuk/data");
		new File(stara, "cesty").mkdirs();
		new File(stara, "imagesMy").mkdirs();
		new File(stara, "podslozka").mkdirs();
		java.nio.file.Files.write(new File(stara, "podslozka/moje.GPX").toPath(), new byte[] { 1 });
		java.nio.file.Files.write(new File(stara, "cesty/vylet.gpx").toPath(), new byte[] { 1 });
		java.nio.file.Files.write(new File(stara, "imagesMy/ikona.png").toPath(), new byte[] { 1 });
		java.nio.file.Files.write(new File(stara, "lovim.ggt").toPath(), "GC1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		new File(stara, "tedne.ggt").createNewFile(); // prázdný se nehlásí
		final String text = KontrolaUmisteni.staraData(stara, data, new File(data, "gpx"));
		Assert.assertNotNull(text);
		Assert.assertTrue(text, text.contains("soubory s kešemi"));
		Assert.assertTrue(text, text.contains("cesty ze složky"));
		Assert.assertTrue(text, text.contains("vlastní ikony"));
		Assert.assertTrue(text, text.contains("lovim.ggt"));
		Assert.assertFalse(text, text.contains("tedne.ggt"));
		Assert.assertFalse(text, text.contains("ikony ostatních"));

		// Po zkopírování už není co hlásit.
		new File(data, "gpx").mkdirs();
		java.nio.file.Files.write(new File(data, "gpx/moje.gpx").toPath(), new byte[] { 1 });
		new File(data, "cesty").mkdirs();
		java.nio.file.Files.write(new File(data, "cesty/vylet.gpx").toPath(), new byte[] { 1 });
		new File(data, "ikony/moje").mkdirs();
		java.nio.file.Files.write(new File(data, "ikony/moje/ikona.png").toPath(), new byte[] { 1 });
		new File(data, "vylety").mkdirs();
		java.nio.file.Files.write(new File(data, "vylety/lovim.ggt").toPath(), new byte[] { 1 });
		Assert.assertNull(KontrolaUmisteni.staraData(stara, data, new File(data, "gpx")));
	}

	@Test
	public void bezStareSlozkyNicNehlasi() throws Exception {
		final File home = java.nio.file.Files.createTempDirectory("home").toFile();
		Assert.assertNull(KontrolaUmisteni.staraData(new File(home, "geokuk"), new File(home, "data"), new File(home, "data/gpx")));
		new File(home, "geokuk").mkdirs();
		Assert.assertNull("prázdná stará složka", KontrolaUmisteni.staraData(new File(home, "geokuk"), new File(home, "data"), new File(home, "data/gpx")));
	}

	private static void soubor(final File f) throws java.io.IOException {
		f.getParentFile().mkdirs();
		java.nio.file.Files.write(f.toPath(), new byte[] { 1 });
	}

	/** Keše se načítají ze složky z nastavení, ne vždy z data/gpx. */
	@Test
	public void staraDataPodleSlozkySKesemi() throws Exception {
		final File home = java.nio.file.Files.createTempDirectory("home").toFile();
		final File stara = new File(home, "geokuk");
		final File data = new File(home, "GeoKuk/data");
		soubor(new File(stara, "moje.gpx"));
		Assert.assertNull("keše se čtou přímo ze staré složky", KontrolaUmisteni.staraData(stara, data, stara));
		final File vlastni = new File(home, "kese");
		final String text = KontrolaUmisteni.staraData(stara, data, vlastni);
		Assert.assertNotNull(text);
		Assert.assertTrue(text, text.contains("do " + vlastni));
		soubor(new File(vlastni, "moje.gpx"));
		Assert.assertNull(KontrolaUmisteni.staraData(stara, data, vlastni));
	}

	/** GPX jen v cestách nebo ve složce s programem uvnitř staré složky nejsou keše ke zkopírování. */
	@Test
	public void staraDataVynechaCestyASlozkuProgramu() throws Exception {
		final File home = java.nio.file.Files.createTempDirectory("home").toFile();
		final File stara = new File(home, "geokuk");
		final File data = new File(stara, "GeoKuk/data");
		soubor(new File(stara, "cesty/vylet.gpx"));
		soubor(new File(data, "gpx/moje.gpx"));
		final String text = KontrolaUmisteni.staraData(stara, data, new File(home, "jinde"));
		Assert.assertNotNull(text);
		Assert.assertTrue(text, text.contains("cesty ze složky"));
		Assert.assertFalse(text, text.contains("soubory s kešemi"));
	}
}
