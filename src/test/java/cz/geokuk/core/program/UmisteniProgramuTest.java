package cz.geokuk.core.program;

import java.io.File;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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
}
