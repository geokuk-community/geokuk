package cz.geokuk.plugins.refbody;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Referenční body z geohome.ini GeoGetu. */
public class RefbodyModelTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void bodySePrectouVKodovaniGeogetu() throws Exception {
		final File ini = tmp.newFile("geohome.ini");
		Files.write(ini.toPath(), "50.08 14.42 Domů v Praze\nšpatný řádek\n49.19 16.6  Brno střed\n".getBytes(Charset.forName("CP1250")));
		final List<RefbodyModel.RefBod> body = RefbodyModel.precti(ini);
		Assert.assertEquals(2, body.size());
		Assert.assertEquals("Domů v Praze", body.get(0).nazev);
		Assert.assertEquals(50.08, body.get(0).wgs.lat, 1e-9);
		Assert.assertEquals(14.42, body.get(0).wgs.lon, 1e-9);
		Assert.assertEquals("Brno střed", body.get(1).nazev);
	}

	@Test
	public void souborVUtf8SeZnackouBom() throws Exception {
		final File ini = tmp.newFile("geohome.ini");
		final byte[] text = "50.0197 14.42 Domů\n".getBytes(StandardCharsets.UTF_8);
		final byte[] sBom = new byte[text.length + 3];
		sBom[0] = (byte) 0xEF;
		sBom[1] = (byte) 0xBB;
		sBom[2] = (byte) 0xBF;
		System.arraycopy(text, 0, sBom, 3, text.length);
		Files.write(ini.toPath(), sBom);
		final List<RefbodyModel.RefBod> body = RefbodyModel.precti(ini);
		Assert.assertEquals(1, body.size());
		Assert.assertEquals("Domů", body.get(0).nazev);
		Assert.assertEquals(50.0197, body.get(0).wgs.lat, 1e-9);
	}

	@Test
	public void souborVUtf8BezZnackyBom() throws Exception {
		final File ini = tmp.newFile("geohome.ini");
		Files.write(ini.toPath(), "49.19 16.6 Brno střed\n".getBytes(StandardCharsets.UTF_8));
		final List<RefbodyModel.RefBod> body = RefbodyModel.precti(ini);
		Assert.assertEquals(1, body.size());
		Assert.assertEquals("Brno střed", body.get(0).nazev);
	}

	/** Složka GeoGetu bez geohome.ini nebo neexistující složka: žádné body, žádná chyba. */
	@Test
	public void chybejiciSouborDaPrazdnySeznam() {
		Assert.assertTrue(RefbodyModel.precti(new File(tmp.getRoot(), "neni/geohome.ini")).isEmpty());
	}
}
