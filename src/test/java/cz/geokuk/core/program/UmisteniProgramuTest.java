package cz.geokuk.core.program;

import java.io.File;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.ovladani.DalkoveOvladani;

public class UmisteniProgramuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void logVDatech() throws Exception {
		final File data = tmp.newFolder("data");
		Assert.assertEquals(new File(data, "log"), UmisteniProgramu.log(data, tmp.newFolder("temp")));
	}

	@Test
	public void logDoDocasneSlozkyKdyzDoDatNejdeZapsat() throws Exception {
		final File data = tmp.newFile("data"); // soubor místo složky, nejde zapsat ani jako správce
		final File temp = tmp.newFolder("temp");
		Assert.assertEquals(new File(new File(temp, "GeoKuk"), "log"), UmisteniProgramu.log(data, temp));
	}

	@Test
	public void souborDalkovehoOvladaniJdeSLogemDoDocasneSlozky() throws Exception {
		final File data = tmp.newFile("data2"); // soubor místo složky, nejde zapsat ani jako správce
		final File temp = tmp.newFolder("temp2");
		Assert.assertEquals(new File(new File(temp, "GeoKuk"), "ovladani.properties"), DalkoveOvladani.soubor(UmisteniProgramu.log(data, temp)));
	}

	@Test
	public void souborDalkovehoOvladaniVDatech() throws Exception {
		final File data = tmp.newFolder("data3");
		Assert.assertEquals(new File(data, "ovladani.properties"), DalkoveOvladani.soubor(UmisteniProgramu.log(data, tmp.newFolder("temp3"))));
	}

	@Test
	public void logVeSdileneDocasneSlozceJenKdyzPatriNam() throws Exception {
		org.junit.Assume.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		final File data = tmp.newFile("data4");
		final File temp = tmp.newFolder("temp4");
		final File geokuk = new File(temp, "GeoKuk");
		Assert.assertTrue(geokuk.mkdir());
		java.nio.file.Files.setPosixFilePermissions(geokuk.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx"));
		final String domov = System.getProperty("user.home");
		Assert.assertEquals(new File(new File(domov, ".geokuk"), "log"), UmisteniProgramu.log(data, temp));
	}

	@Test
	public void logNeJeVCiziDocasneSlozce() throws Exception {
		org.junit.Assume.assumeTrue(java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
		final File data = tmp.newFile("data5");
		final File temp = tmp.newFolder("temp5");
		final File geokuk = new File(temp, "GeoKuk");
		Assert.assertTrue(geokuk.mkdir());
		try {
			// Změnit vlastníka smí jen root.
			java.nio.file.Files.setOwner(geokuk.toPath(), geokuk.toPath().getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("nobody"));
		} catch (final java.io.IOException e) {
			org.junit.Assume.assumeNoException(e);
		}
		final File log = UmisteniProgramu.log(data, temp);
		Assert.assertEquals(new File(new File(System.getProperty("user.home"), ".geokuk"), "log"), log);
	}
}
