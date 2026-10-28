package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Skripty týdenní kontroly schématu OpenSAKu: hlásí jen chybějící sloupce, které GeoKuk čte. */
public class OpensakSchemaSkriptyTest {

	private static final String SKRIPTY = ".github/skripty/";
	private static final String POUZITE = "src/test/resources/opensak-pouzite-sloupce.txt";
	private static final String ULOZENE = "src/test/resources/opensak-schema.txt";

	@Rule
	public TemporaryFolder slozka = new TemporaryFolder();

	private static class Vysledek {
		int kod;
		String vystup;
	}

	private static Vysledek spust(final String... prikaz) throws Exception {
		final Process p = new ProcessBuilder(prikaz).redirectErrorStream(true).start();
		final Vysledek v = new Vysledek();
		v.vystup = new String(com.google.common.io.ByteStreams.toByteArray(p.getInputStream()), StandardCharsets.UTF_8);
		Assert.assertTrue(p.waitFor(60, TimeUnit.SECONDS));
		v.kod = p.exitValue();
		return v;
	}

	private File klonOpenSAKu(final String sloupceCaches) throws IOException {
		final File koren = slozka.newFolder();
		final Path db = koren.toPath().resolve("src/opensak/db");
		Files.createDirectories(db);
		Files.write(db.resolve("database.py"), "SCHEMA_VERSION = 29\n".getBytes(StandardCharsets.UTF_8));
		final StringBuilder models = new StringBuilder("class Cache(Base):\n    __tablename__ = \"caches\"\n");
		for (final String sloupec : sloupceCaches.split(",")) {
			models.append("    ").append(sloupec).append(": Mapped[str] = mapped_column()\n");
		}
		models.append("class Waypoint(Base):\n    __tablename__ = \"waypoints\"\n    cache_id: Mapped[int] = mapped_column()\n    latitude: Mapped[float] = mapped_column()\n    longitude: Mapped[float] = mapped_column()\n");
		Files.write(db.resolve("models.py"), models.toString().getBytes(StandardCharsets.UTF_8));
		return koren;
	}

	private Vysledek zmeny(final File klon) throws Exception {
		final Vysledek schema = spust("python3", SKRIPTY + "opensak_schema.py", klon.getPath());
		Assert.assertEquals(schema.vystup, 0, schema.kod);
		final File aktualni = slozka.newFile();
		Files.write(aktualni.toPath(), schema.vystup.getBytes(StandardCharsets.UTF_8));
		return spust("python3", SKRIPTY + "opensak_zmeny.py", POUZITE, ULOZENE, aktualni.getPath());
	}

	@Test
	public void pridanySloupecAVyssiVerzeJsouJenInformace() throws Exception {
		Assume.assumeTrue(new File(SKRIPTY + "opensak_zmeny.py").isFile() && jePython());
		final String vsechny = String.join(",", OpensakDbLoader.pouziteSloupce().get("caches")) + ",novy_sloupec";
		final Vysledek v = zmeny(klonOpenSAKu(vsechny));
		// tabulka user_notes ve vzorovém klonu chybí, proto jen kontrola, že přidaný sloupec chybu nezpůsobí
		Assert.assertTrue(v.vystup, v.vystup.contains("novy_sloupec") && !v.vystup.contains("`caches."));
		Assert.assertTrue(v.vystup, v.vystup.contains("Verze schématu: 29"));
	}

	@Test
	public void chybejiciPouzivanySloupecJeChyba() throws Exception {
		Assume.assumeTrue(new File(SKRIPTY + "opensak_zmeny.py").isFile() && jePython());
		final String bezFoundDate = String.join(",", OpensakDbLoader.pouziteSloupce().get("caches")).replace("found_date,", "");
		final Vysledek v = zmeny(klonOpenSAKu(bezFoundDate));
		Assert.assertEquals(v.vystup, 1, v.kod);
		Assert.assertTrue(v.vystup, v.vystup.contains("`caches.found_date`"));
	}

	@Test
	public void chybaVstupuJeChybaBehuNeChybejiciSloupce() throws Exception {
		Assume.assumeTrue(new File(SKRIPTY + "opensak_zmeny.py").isFile() && jePython());
		final File prazdny = slozka.newFile();
		Assert.assertEquals(2, spust("python3", SKRIPTY + "opensak_zmeny.py", POUZITE, ULOZENE, new File(slozka.getRoot(), "neexistuje.txt").getPath()).kod);
		Assert.assertEquals(2, spust("python3", SKRIPTY + "opensak_zmeny.py", prazdny.getPath(), ULOZENE, ULOZENE).kod);
		Assert.assertEquals(2, spust("python3", SKRIPTY + "opensak_zmeny.py", POUZITE, prazdny.getPath(), ULOZENE).kod);
	}

	private static boolean jePython() {
		try {
			return spust("python3", "--version").kod == 0;
		} catch (final Exception e) {
			return false;
		}
	}
}
