package cz.geokuk.util.exception;

import java.io.File;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Repozitář výjimek nesmí růst donekonečna. */
public class FileBasedExceptionDumperRepositoryTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void staraSpusteniSeSmazou() throws Exception {
		final File dir = tmp.newFolder("excrep");
		// předchozí spuštění: 1 až 50, každé s jedním souborem
		for (int i = 1; i <= 50; i++) {
			final File hluboko = new File(dir, i + "/x/00");
			Assert.assertTrue(hluboko.mkdirs());
			Assert.assertTrue(new File(hluboko, "exc" + i + "x1.html").createNewFile());
		}
		zapisCisloSpusteni(dir, 50);

		new FileBasedExceptionDumperRepository(dir);

		Assert.assertFalse("dávné spuštění se smaže", new File(dir, "1").exists());
		Assert.assertFalse(new File(dir, "31").exists());
		Assert.assertTrue("posledních dvacet zůstane", new File(dir, "32").exists());
		Assert.assertTrue(new File(dir, "50/x/00/exc50x1.html").exists());
	}

	@Test
	public void cizaSlozkaZustane() throws Exception {
		final File dir = tmp.newFolder("excrep");
		Assert.assertTrue(new File(dir, "neco").mkdirs());
		zapisCisloSpusteni(dir, 100);

		new FileBasedExceptionDumperRepository(dir);

		Assert.assertTrue(new File(dir, "neco").exists());
	}

	private static void zapisCisloSpusteni(final File dir, final int cislo) throws Exception {
		try (java.io.PrintWriter w = new java.io.PrintWriter(new File(dir, "runNumber.txt"), "UTF-8")) {
			w.print(cislo);
		}
	}
}
