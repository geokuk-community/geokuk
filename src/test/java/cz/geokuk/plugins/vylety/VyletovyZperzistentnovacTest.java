package cz.geokuk.plugins.vylety;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;

/** Výlet v souborech lovim.ggt a tedne.ggt, jak je čte a píše GeoGet. */
public class VyletovyZperzistentnovacTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File ano;
	private File ne;
	private VyletovyZperzistentnovac soubory;

	@Before
	public void setUp() throws Exception {
		final File slozka = tmp.newFolder("vylety");
		ano = new File(slozka, "lovim.ggt");
		ne = new File(slozka, "tedne.ggt");
		final KesoidUmisteniSouboru umisteni = new KesoidUmisteniSouboru();
		umisteni.setAnoGgtFile(new Filex(ano, false, true));
		umisteni.setNeGgtFile(new Filex(ne, false, true));
		soubory = new VyletovyZperzistentnovac();
		soubory.inject(new KesoidModel() {
			@Override
			public KesoidUmisteniSouboru getUmisteniSouboru() {
				return umisteni;
			}
		});
	}

	private List<String> radky(final File f) throws Exception {
		return Files.readAllLines(f.toPath(), Charset.defaultCharset());
	}

	@Test
	public void zapisJedenKodNaRadek() throws Exception {
		soubory.immediatlyZapisVylet(Arrays.asList("GC1", "GC2"), Collections.singletonList("GC3"));
		Assert.assertEquals(Arrays.asList("GC1", "GC2"), radky(ano));
		Assert.assertEquals(Collections.singletonList("GC3"), radky(ne));
	}

	@Test
	public void zapsanyVyletSeNacteZpet() throws Exception {
		soubory.immediatlyZapisVylet(Arrays.asList("GC1", "GC2"), Collections.singletonList("GC3"));
		final Vylet v = soubory.immediatlyNactiVylet(null);
		Assert.assertEquals(Arrays.asList("GC1", "GC2"), v.kody(EVylet.ANO));
		Assert.assertEquals(Collections.singletonList("GC3"), v.kody(EVylet.NE));
	}

	@Test
	public void prazdneRadkyAMezeryAOpakovaneKody() throws Exception {
		Files.write(ano.toPath(), Arrays.asList("", "  GC1  ", "GC2", "GC1", "   "), Charset.defaultCharset());
		Assert.assertEquals(Arrays.asList("GC1", "GC2"), soubory.immediatlyNactiVylet(null).kody(EVylet.ANO));
	}

	@Test
	public void chybejiciSouboryJsouPrazdnyVylet() {
		final Vylet v = soubory.immediatlyNactiVylet(null);
		Assert.assertTrue(v.kody(EVylet.ANO).isEmpty());
		Assert.assertTrue(v.kody(EVylet.NE).isEmpty());
	}

	@Test
	public void prazdnyVyletPrepiseSoubor() throws Exception {
		soubory.immediatlyZapisVylet(Collections.singletonList("GC1"), Collections.emptyList());
		soubory.immediatlyZapisVylet(Collections.emptyList(), Collections.emptyList());
		Assert.assertTrue(radky(ano).isEmpty());
		Assert.assertTrue(radky(ne).isEmpty());
	}
}
