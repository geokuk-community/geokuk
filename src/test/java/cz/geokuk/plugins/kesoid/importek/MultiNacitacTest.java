package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.file.KeFile;

/** Načtení keší ze složky: podsložky, zip, soubory jiných typů. */
public class MultiNacitacTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static class Model extends KesoidModel {
		final Set<File> blokovane = new HashSet<>();

		Model() {
			final ProgressModel progress = new ProgressModel();
			progress.inject(udalost -> {});
			inject(progress);
			inject(new KesoidPluginManager());
		}

		// Bez EventFireru by události z vlákna načítání spadly na EDT.
		@Override
		public void fire(final cz.geokuk.framework.Event0<?> udalost) {}

		@Override
		protected cz.geokuk.framework.MyPreferences currPrefe() {
			return cz.geokuk.framework.MyPreferences.current().node("test-multinacitac");
		}

		@Override
		public GccomNick getGccomNick() {
			return new GccomNick("Ja", 42);
		}

		@Override
		public GsakParametryNacitani getGsakParametryNacitani() {
			return new GsakParametryNacitani();
		}

		@Override
		public boolean maSeNacist(final KeFile zdroj) {
			return !blokovane.contains(zdroj.getFile());
		}
	}

	private static String gpx(final String... kody) {
		return ImportKesiTest.gpx(Arrays.stream(kody).map(k -> ImportKesiTest.kes(k, "Geocache", "Traditional Cache", "Kačer", 7, true, false, "1.5", "")).toArray(String[]::new));
	}

	private static void zapis(final File f, final String obsah) throws IOException {
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), obsah.getBytes(StandardCharsets.UTF_8));
	}

	private static Set<String> kody(final KesBag bag) {
		return bag.getKesoidy().stream().map(Kesoid::getIdentifier).collect(Collectors.toCollection(TreeSet::new));
	}

	private KesBag nacti(final Model model, final File slozka) throws IOException {
		final MultiNacitac nacitac = new MultiNacitac(model);
		nacitac.setRootDirs(true, slozka, null, null, Collections.emptySet());
		return nacitac.nacti(null, new Genom());
	}

	@Test
	public void slozkaPodslozkyAZip() throws Exception {
		final File slozka = tmp.newFolder("kese");
		zapis(new File(slozka, "a.gpx"), gpx("GC1A"));
		zapis(new File(slozka, "pod/b.GPX"), gpx("GC2B"));
		zapis(new File(slozka, "pod/c.geokuk"), "*geokuk:exportversion=2\n"
				+ ":GC3C|Traditional Cache|Small|2|3|false|false|false|false|Kačer|2020-01-01|CZ|Praha|0|0|0|0||\n-GC|Geocache|50.1|14.4|Keš C\n/\n");
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(slozka, "d.zip")))) {
			zip.putNextEntry(new ZipEntry("uvnitr/d.gpx"));
			zip.write(gpx("GC4D", "GC5E").getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("readme.txt"));
			zip.write("nic".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		zapis(new File(slozka, "poznamka.txt"), "není keš");
		Assert.assertEquals(new TreeSet<>(Arrays.asList("GC1A", "GC2B", "GC3C", "GC4D", "GC5E")), kody(nacti(new Model(), slozka)));
	}

	@Test
	public void vadnySouborNezastaviOstatni() throws Exception {
		final File slozka = tmp.newFolder("kese");
		zapis(new File(slozka, "a.gpx"), gpx("GC1A"));
		zapis(new File(slozka, "vadny.gpx"), "<gpx><wpt lat=");
		zapis(new File(slozka, "prazdny.geokuk"), "");
		zapis(new File(slozka, "falesny.zip"), "PK není zip");
		Assert.assertEquals(Collections.singleton("GC1A"), kody(nacti(new Model(), slozka)));
	}

	@Test
	public void blokovanyZdrojSeNenacte() throws Exception {
		final File slozka = tmp.newFolder("kese");
		zapis(new File(slozka, "a.gpx"), gpx("GC1A"));
		final File b = new File(slozka, "b.gpx");
		zapis(b, gpx("GC2B"));
		final Model model = new Model();
		model.blokovane.add(b);
		Assert.assertEquals(Collections.singleton("GC1A"), kody(nacti(model, slozka)));
	}
}
