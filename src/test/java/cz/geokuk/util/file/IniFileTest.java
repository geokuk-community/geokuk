package cz.geokuk.util.file;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IniFileTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void sectionsAndKeysIgnoreCase() {
		final IniFile ini = IniFile.parse("; comment\r\n[Boot]\r\nDataDir = c:\\GeoGet\\data \r\n[geoget]\nlastDB=geoget\nlastDB=druha\n");
		Assert.assertEquals("c:\\GeoGet\\data", ini.get("boot", "datadir"));
		Assert.assertEquals("first occurrence wins", "geoget", ini.get("GEOGET", "lastdb"));
		Assert.assertNull(ini.get("geoget", "chybi"));
		Assert.assertNull(ini.get("chybi", "lastDB"));
	}

	@Test
	public void keyOutsideSectionIsIgnored() {
		Assert.assertNull(IniFile.parse("lastDB=x\n[geoget]\n").get("geoget", "lastDB"));
	}

	@Test
	public void readsUtf16WithBom() throws Exception {
		final File f = tmp.newFile("geoget.ini");
		final byte[] text = "[geoget]\r\nlastDB=Výlet\r\n".getBytes(StandardCharsets.UTF_16LE);
		final byte[] obsah = new byte[text.length + 2];
		obsah[0] = (byte) 0xFF;
		obsah[1] = (byte) 0xFE;
		System.arraycopy(text, 0, obsah, 2, text.length);
		Files.write(f.toPath(), obsah);
		Assert.assertEquals("Výlet", IniFile.read(f).get("geoget", "lastDB"));
	}

	@Test
	public void readsWindows1250() throws Exception {
		final File f = tmp.newFile("geoget.ini");
		Files.write(f.toPath(), "[geoget]\r\nlastDB=Výlet\r\n".getBytes(TextovySoubor.CP1250));
		Assert.assertEquals("Výlet", IniFile.read(f).get("geoget", "lastDB"));
	}

	@Test
	public void missingFileIsEmpty() throws Exception {
		Assert.assertNull(IniFile.read(new File(tmp.getRoot(), "chybi.ini")).get("boot", "datadir"));
	}
}
