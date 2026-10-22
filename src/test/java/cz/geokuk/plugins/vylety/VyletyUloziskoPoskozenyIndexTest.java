package cz.geokuk.plugins.vylety;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Poškozený vylety.txt nesmí vést k přepsání uložených výletů. */
public class VyletyUloziskoPoskozenyIndexTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void ulozeneVyletyZustanou() throws Exception {
		final File ano = new File(tmp.getRoot(), "lovim.ggt");
		final File ne = new File(tmp.getRoot(), "tedne.ggt");
		zapis(ano, "GC1\n");
		zapis(ne, "");
		final VyletyUlozisko u = new VyletyUlozisko(ano, ne);
		final String druhy = u.novy("Druhý");
		u.aktivuj(druhy);
		zapis(ano, "GC2\r\n");
		u.zrcadli(Collections.singletonList("GC2"), Collections.<String>emptyList());

		zapis(new File(tmp.getRoot(), "vylety.txt"), "jmeno.v1=\\u12\n");
		u.novy("Třetí");

		Assert.assertEquals("GC1", obsah(new File(tmp.getRoot(), "ulozene/v1/lovim.ggt")));
		Assert.assertEquals("GC2", obsah(new File(tmp.getRoot(), "ulozene/" + druhy + "/lovim.ggt")));
		Assert.assertEquals(druhy, u.aktivniId());
		Assert.assertEquals(3, u.seznam().size());
		final String[] odlozene = tmp.getRoot().list((d, n) -> n.startsWith("vylety-poskozeny-"));
		Assert.assertNotNull(odlozene);
		Assert.assertEquals(1, odlozene.length);
	}

	@Test
	public void aktivniBezShodyDostaneVlastniSlozku() throws Exception {
		final File ano = new File(tmp.getRoot(), "lovim.ggt");
		final File ne = new File(tmp.getRoot(), "tedne.ggt");
		zapis(ano, "GC1\n");
		zapis(ne, "");
		final VyletyUlozisko u = new VyletyUlozisko(ano, ne);
		u.novy("Druhý");
		zapis(ano, "GC9\n");
		zapis(new File(tmp.getRoot(), "vylety.txt"), "poradi=x\n");

		final String aktivni = u.aktivniId();
		Assert.assertEquals("GC1", obsah(new File(tmp.getRoot(), "ulozene/v1/lovim.ggt")));
		Assert.assertEquals("GC9", obsah(new File(tmp.getRoot(), "ulozene/" + aktivni + "/lovim.ggt")));
		Assert.assertEquals(3, u.seznam().size());
	}

	private static void zapis(final File f, final String text) throws Exception {
		Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	private static String obsah(final File f) throws Exception {
		return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
	}
}
