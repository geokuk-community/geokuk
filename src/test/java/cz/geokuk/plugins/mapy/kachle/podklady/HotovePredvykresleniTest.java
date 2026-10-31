package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

public class HotovePredvykresleniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final List<KaLoc> VZOREK = Arrays.asList(KaLoc.ofXyz(4, 5, 3), KaLoc.ofXyz(260, 170, 9));

	private File soubor() {
		return new File(tmp.getRoot(), HotovePredvykresleni.SOUBOR);
	}

	@Test
	public void bezSouboruNicNeniZapsano() {
		Assert.assertNull(HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000001.aaaaaaaa"));
	}

	@Test
	public void zapsanyKlicVraciSvujVzorekAJenSvuj() throws IOException {
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa", VZOREK);
		Assert.assertEquals(VZOREK, HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000001.aaaaaaaa"));
		Assert.assertNull(HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000001.bbbbbbbb"));
	}

	@Test
	public void pamatujeJenPosledniKlice() throws IOException {
		for (int i = 0; i < 5; i++) {
			HotovePredvykresleni.zapis(tmp.getRoot(), "o0000000" + i + ".aaaaaaaa", VZOREK);
		}
		Assert.assertNull(HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000001.aaaaaaaa"));
		Assert.assertNotNull(HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000002.aaaaaaaa"));
		Assert.assertNotNull(HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000004.aaaaaaaa"));
	}

	@Test
	public void opakovanyZapisNeplodiRadky() throws IOException {
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa", VZOREK);
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa", VZOREK);
		Assert.assertEquals(1, Files.readAllLines(soubor().toPath()).size());
	}

	@Test
	public void chybejiciSlozkaNeshodiCteni() {
		Assert.assertNull(HotovePredvykresleni.vzorek(new File(tmp.getRoot(), "neni/slozka"), "x"));
	}

	@Test
	public void poskozenyObsahNeplati() throws IOException {
		final String klic = "o00000001.aaaaaaaa";
		for (final String obsah : new String[] { "", klic, klic + " ", klic + " 3/4", klic + " a/b/c", klic + " 3/4/5 x", klic + " 99999999999/1/1", "\n\n  \n" }) {
			Files.write(soubor().toPath(), obsah.getBytes("UTF-8"));
			Assert.assertNull("obsah: [" + obsah + "]", HotovePredvykresleni.vzorek(tmp.getRoot(), klic));
		}
	}

	@Test
	public void necitelnySouborNeplati() throws IOException {
		final String klic = "o00000001.aaaaaaaa";
		Files.write(soubor().toPath(), new byte[] { (byte) 0xff, (byte) 0xfe, (byte) 0xc3, 0x28, ' ', '3', '/', '1', '/', '1' });
		Assert.assertNull("nevalidní UTF-8", HotovePredvykresleni.vzorek(tmp.getRoot(), klic));
		Files.delete(soubor().toPath());
		Files.createDirectory(soubor().toPath());
		Assert.assertNull("místo souboru složka", HotovePredvykresleni.vzorek(tmp.getRoot(), klic));
	}

	@Test
	public void zapisPrepisePoskozenySoubor() throws IOException {
		Files.write(soubor().toPath(), new byte[] { (byte) 0xff, (byte) 0xc3, 0x28 });
		HotovePredvykresleni.zapis(tmp.getRoot(), "o00000001.aaaaaaaa", VZOREK);
		Assert.assertEquals(VZOREK, HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000001.aaaaaaaa"));
	}

	@Test
	public void poskozenyRadekNezneplatiPlatny() throws IOException {
		Files.write(soubor().toPath(), Arrays.asList("rozbite", "o00000001.aaaaaaaa 3/4/5"));
		Assert.assertEquals(Arrays.asList(KaLoc.ofXyz(4, 5, 3)), HotovePredvykresleni.vzorek(tmp.getRoot(), "o00000001.aaaaaaaa"));
	}
}
