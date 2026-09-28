package cz.geokuk.core.napoveda;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.FConst;

public class DiagnostikaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void textObsahujeVerziAUdalosti() {
		Diagnostika.zaznamenej("první");
		Diagnostika.zaznamenejChybu("chyba stahování");
		for (int i = 0; i < 60; i++) {
			Diagnostika.zaznamenej("událost " + i);
		}
		final String text = Diagnostika.text();
		Assert.assertTrue(text.startsWith("Geokuk " + FConst.VERSION));
		Assert.assertTrue(text.contains("událost 59"));
		Assert.assertFalse(text.contains("první"));
		Assert.assertTrue(text.contains("chyba stahování"));
	}

	@Test
	public void domovskaSlozkaSeZkrati() {
		Assert.assertEquals("~" + File.separator + "geokuk", Diagnostika.bezDomova(new File(FConst.HOME_DIR, "geokuk")));
	}

	@Test
	public void konecLoguBezDomova() throws Exception {
		final File log = tmp.newFile("geokuk.log");
		final List<String> radky = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			radky.add("řádek " + i + " " + FConst.HOME_DIR.getAbsolutePath());
		}
		Files.write(log.toPath(), radky, StandardCharsets.UTF_8);
		final Deque<String> konec = Diagnostika.konecLogu(log, 3);
		Assert.assertEquals(Arrays.asList("řádek 27 ~", "řádek 28 ~", "řádek 29 ~"), new ArrayList<>(konec));
	}

	@Test
	public void chybejiciLogJePrazdny() {
		Assert.assertTrue(Diagnostika.konecLogu(new File(tmp.getRoot(), "neni.log"), 3).isEmpty());
	}
}
