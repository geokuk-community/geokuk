package cz.geokuk.core.program;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Přehledová mapa z jaru: chybí → doplní, smazaná → nedoplní, starší → nahradí, vlastní soubor uživatele → nechá. */
public class PrehledovaMapaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final byte[] NOVA = "mapa verze 2".getBytes(StandardCharsets.UTF_8);
	private static final byte[] STARA = "mapa verze 1".getBytes(StandardCharsets.UTF_8);

	private File slozka;
	private File mapa;

	@Before
	public void setUp() {
		slozka = new File(tmp.getRoot(), "offline-mapy");
		mapa = new File(slozka, PrehledovaMapa.SOUBOR);
	}

	@Test
	public void chybiDoplni() throws Exception {
		Assert.assertEquals(PrehledovaMapa.otisk(NOVA), PrehledovaMapa.doplni(slozka, NOVA, null));
		Assert.assertArrayEquals(NOVA, Files.readAllBytes(mapa.toPath()));
		Assert.assertFalse("bez dočasného souboru", new File(slozka, PrehledovaMapa.SOUBOR + ".tmp").exists());
	}

	@Test
	public void smazanaNedoplni() throws Exception {
		Assert.assertNull(PrehledovaMapa.doplni(slozka, NOVA, PrehledovaMapa.otisk(STARA)));
		Assert.assertFalse(mapa.exists());
	}

	@Test
	public void starsiNahradi() throws Exception {
		slozka.mkdirs();
		Files.write(mapa.toPath(), STARA);
		Assert.assertEquals(PrehledovaMapa.otisk(NOVA), PrehledovaMapa.doplni(slozka, NOVA, PrehledovaMapa.otisk(STARA)));
		Assert.assertArrayEquals(NOVA, Files.readAllBytes(mapa.toPath()));
	}

	/** Mapa ze zipu starší verze, než si program začal otisk pamatovat. */
	@Test
	public void starsiBezOtiskuNahradi() throws Exception {
		slozka.mkdirs();
		Files.write(mapa.toPath(), STARA);
		Assert.assertEquals(PrehledovaMapa.otisk(NOVA), PrehledovaMapa.doplni(slozka, NOVA, null));
		Assert.assertArrayEquals(NOVA, Files.readAllBytes(mapa.toPath()));
	}

	@Test
	public void stejnaZeZipuJenZapamatuje() throws Exception {
		slozka.mkdirs();
		Files.write(mapa.toPath(), NOVA);
		Assert.assertEquals(PrehledovaMapa.otisk(NOVA), PrehledovaMapa.doplni(slozka, NOVA, null));
		Assert.assertNull(PrehledovaMapa.doplni(slozka, NOVA, PrehledovaMapa.otisk(NOVA)));
	}

	@Test
	public void vlastniSouborUzivateleNecha() throws Exception {
		slozka.mkdirs();
		final byte[] vlastni = "moje mapa".getBytes(StandardCharsets.UTF_8);
		Files.write(mapa.toPath(), vlastni);
		Assert.assertNull(PrehledovaMapa.doplni(slozka, NOVA, PrehledovaMapa.otisk(STARA)));
		Assert.assertArrayEquals(vlastni, Files.readAllBytes(mapa.toPath()));
	}

	@Test
	public void mapaJeVJaru() {
		Assert.assertNotNull(PrehledovaMapa.class.getResource("/offline-mapy/" + PrehledovaMapa.SOUBOR));
	}
}
