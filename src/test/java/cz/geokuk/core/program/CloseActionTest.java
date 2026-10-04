package cz.geokuk.core.program;

import java.io.File;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.profile.ProfileModel;

public class CloseActionTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Do nezapisovatelné složky (start už varoval) se nastavení neukládá a ukončení se na nic neptá. */
	@Test(timeout = 30_000)
	public void zNezapisovatelneSlozkyKonecBezDotazu() throws Exception {
		final File data = tmp.newFile("data"); // soubor místo složky, do data nejde zapsat ani jako správce
		final CloseAction akce = new CloseAction();
		akce.inject(new ProfileModel() {
			@Override
			public void ulozNastaveni() {
				throw new IllegalStateException("Nastavení se nepodařilo uložit");
			}
		});
		Assert.assertTrue(akce.ulozNastaveniNeboPresto(data));
	}
}
