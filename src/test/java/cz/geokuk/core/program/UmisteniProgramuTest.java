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
		Assert.assertEquals(new File(data, "log"), UmisteniProgramu.log(data, tmp.newFolder("domov")));
	}

	@Test
	public void logDoDomovskeSlozkyKdyzDoDatNejdeZapsat() throws Exception {
		final File data = tmp.newFile("data"); // soubor místo složky, nejde zapsat ani jako správce
		final File domov = tmp.newFolder("domov");
		Assert.assertEquals(new File(new File(domov, ".geokuk"), "log"), UmisteniProgramu.log(data, domov));
	}

	@Test
	public void souborDalkovehoOvladaniJdeSLogemDoDomovskeSlozky() throws Exception {
		final File data = tmp.newFile("data2"); // soubor místo složky, nejde zapsat ani jako správce
		final File domov = tmp.newFolder("domov2");
		Assert.assertEquals(new File(new File(domov, ".geokuk"), "ovladani.properties"), DalkoveOvladani.soubor(UmisteniProgramu.log(data, domov)));
	}

	@Test
	public void souborDalkovehoOvladaniVDatech() throws Exception {
		final File data = tmp.newFolder("data3");
		Assert.assertEquals(new File(data, "ovladani.properties"), DalkoveOvladani.soubor(UmisteniProgramu.log(data, tmp.newFolder("domov3"))));
	}
}
