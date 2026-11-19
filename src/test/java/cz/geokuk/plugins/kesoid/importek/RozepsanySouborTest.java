package cz.geokuk.plugins.kesoid.importek;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RozepsanySouborTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void cerstveZmenenySouborJeRozepsany() throws Exception {
		final File f = tmp.newFile("a.gpx");
		Files.setLastModifiedTime(f.toPath(), FileTime.fromMillis(100_000));
		Assert.assertTrue(RozepsanySoubor.jeRozepsany(f, 100_000 + RozepsanySoubor.STARI_MS - 1));
	}

	@Test
	public void starySouborRozepsanyNeni() throws Exception {
		final File f = tmp.newFile("a.gpx");
		Files.setLastModifiedTime(f.toPath(), FileTime.fromMillis(100_000));
		Assert.assertFalse(RozepsanySoubor.jeRozepsany(f, 100_000 + RozepsanySoubor.STARI_MS));
	}

	@Test
	public void chybejiciSouborRozepsanyNeni() {
		Assert.assertFalse(RozepsanySoubor.jeRozepsany(new File(tmp.getRoot(), "neni.gpx")));
	}
}
