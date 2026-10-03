package cz.geokuk.plugins.vylety;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;

/** Výlet s fotkami a waypointy, jejichž jména mají diakritiku. */
public class VyletKodovaniTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private VyletovyZperzistentnovac soubory(final File ano, final File ne) {
		final KesoidUmisteniSouboru umisteni = new KesoidUmisteniSouboru();
		umisteni.setAnoGgtFile(new Filex(ano, false, true));
		umisteni.setNeGgtFile(new Filex(ne, false, true));
		final VyletovyZperzistentnovac soubory = new VyletovyZperzistentnovac();
		soubory.inject(new KesoidModel() {
			@Override
			public KesoidUmisteniSouboru getUmisteniSouboru() {
				return umisteni;
			}
		});
		return soubory;
	}

	@Test
	public void vyletZeStarsiVerzeVCp1250SeNacte() throws Exception {
		final File ano = tmp.newFile("lovim.ggt");
		Files.write(ano.toPath(), "GC1\r\nJiří.jpg\r\n".getBytes(java.nio.charset.Charset.forName("windows-1250")));
		final Vylet v = soubory(ano, new File(tmp.getRoot(), "tedne.ggt")).immediatlyNactiVylet(null);
		Assert.assertEquals(Arrays.asList("GC1", "Jiří.jpg"), v.kody(EVylet.ANO));
	}

	@Test
	public void vyletSeZapiseVUtf8() throws Exception {
		final File ano = new File(tmp.getRoot(), "lovim.ggt");
		final File ne = new File(tmp.getRoot(), "tedne.ggt");
		final VyletovyZperzistentnovac soubory = soubory(ano, ne);
		soubory.immediatlyZapisVylet(Arrays.asList("Jiří.jpg"), Collections.singletonList("Žlutý kůň"));
		Assert.assertTrue(new String(Files.readAllBytes(ano.toPath()), StandardCharsets.UTF_8).startsWith("Jiří.jpg"));
		Assert.assertEquals(Collections.singletonList("Žlutý kůň"), soubory.immediatlyNactiVylet(null).kody(EVylet.NE));
	}
}
