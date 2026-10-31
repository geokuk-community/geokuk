package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HotovePredvykresleniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void bezSouboruNicNeniHotovo() {
		Assert.assertFalse(HotovePredvykresleni.jeHotovo(tmp.getRoot(), "o00000001.aaaaaaaa"));
	}

	@Test
	public void zapsanyKlicJeHotovyJenJeho() throws IOException {
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa");
		Assert.assertTrue(HotovePredvykresleni.jeHotovo(tmp.getRoot(), "o00000001.aaaaaaaa"));
		Assert.assertFalse(HotovePredvykresleni.jeHotovo(tmp.getRoot(), "o00000001.bbbbbbbb"));
	}

	@Test
	public void pamatujeJenPosledniKlice() throws IOException {
		for (int i = 0; i < 5; i++) {
			HotovePredvykresleni.zapis(tmp.getRoot(), "o0000000" + i + ".aaaaaaaa");
		}
		Assert.assertFalse(HotovePredvykresleni.jeHotovo(tmp.getRoot(), "o00000001.aaaaaaaa"));
		Assert.assertTrue(HotovePredvykresleni.jeHotovo(tmp.getRoot(), "o00000002.aaaaaaaa"));
		Assert.assertTrue(HotovePredvykresleni.jeHotovo(tmp.getRoot(), "o00000004.aaaaaaaa"));
	}

	@Test
	public void opakovanyZapisNeplodiRadky() throws IOException {
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa");
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa");
		Assert.assertEquals(1, Files.readAllLines(new File(tmp.getRoot(), HotovePredvykresleni.SOUBOR).toPath()).size());
	}

	@Test
	public void nenaslePoskozenySouborNeshodiCteni() {
		final File neexistujici = new File(tmp.getRoot(), "neni/slozka");
		Assert.assertFalse(HotovePredvykresleni.jeHotovo(neexistujici, "x"));
	}
}
