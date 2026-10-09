package cz.geokuk.plugins.kesoid.importek;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.*;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.index2d.BoundingRect;
import cz.geokuk.util.index2d.Indexator;

/** Index bagu složený z indexů zdrojů je po každém přepnutí stejný jako index postavený najednou z waypointů bagu. */
public class IndexPoZdrojichTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final class Model extends KesoidModel {
		final Set<File> vypnute = new HashSet<>();

		Model() {
			final ProgressModel progress = new ProgressModel();
			progress.inject(udalost -> {});
			inject(progress);
			inject(new KesoidPluginManager());
		}

		@Override
		public void fire(final cz.geokuk.framework.Event0<?> udalost) {}

		@Override
		protected cz.geokuk.framework.MyPreferences currPrefe() {
			return cz.geokuk.framework.MyPreferences.current().node("test-index-po-zdrojich");
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
		public boolean maSeNacist(final File zdroj) {
			return !vypnute.contains(zdroj);
		}

		@Override
		public boolean maSeNacist(final KeFile zdroj) {
			return maSeNacist(zdroj.getFile());
		}
	}

	@Test
	public void prepinaniZdroju() throws Exception {
		final File slozka = tmp.newFolder("kese");
		final Random r = new Random(5);
		final List<double[]> mista = new ArrayList<>();
		final File[] soubory = new File[4];
		for (int s = 0; s < soubory.length; s++) {
			final StringBuilder wpty = new StringBuilder();
			for (int i = 0; i < 300; i++) {
				// část keší stojí na stejném místě jako keše z jiných souborů
				final double[] misto = !mista.isEmpty() && r.nextInt(5) == 0 ? mista.get(r.nextInt(mista.size())) : new double[] { 49 + r.nextDouble() * 2, 13 + r.nextDouble() * 5 };
				mista.add(misto);
				wpty.append(ImportKesiTest.kes(String.format("GC%d%04d", s, i), "Geocache", "Traditional Cache", "Kačer", 7, true, false, "1.5", "")
						.replace("lat=\"50.1\" lon=\"14.4\"", String.format(Locale.ROOT, "lat=\"%.6f\" lon=\"%.6f\"", misto[0], misto[1])));
			}
			soubory[s] = new File(slozka, "z" + s + ".gpx");
			Files.write(soubory[s].toPath(), ImportKesiTest.gpx(wpty.toString()).getBytes(StandardCharsets.UTF_8));
		}
		final Model model = new Model();
		final MultiNacitac nacitac = new MultiNacitac(model);

		over(nacti(nacitac, slozka), 1200);
		model.vypnute.add(soubory[2]);
		over(nacti(nacitac, slozka), 900);
		model.vypnute.add(soubory[0]);
		over(nacti(nacitac, slozka), 600);
		model.vypnute.clear();
		over(nacti(nacitac, slozka), 1200);
		model.vypnute.add(soubory[3]);
		over(nacti(nacitac, slozka), 900);
	}

	private static KesBag nacti(final MultiNacitac nacitac, final File slozka) throws IOException {
		nacitac.setRootDirs(true, slozka, null, null, Collections.<File> emptySet());
		return nacitac.nacti(null, new Genom());
	}

	private static void over(final KesBag bag, final int pocet) {
		Assert.assertEquals(pocet, bag.getWpts().size());
		final Indexator<Wpt> najednou = Indexator.postav(BoundingRect.ALL, bag.getWpts(), w -> w.getMou().xx, w -> w.getMou().yy);
		final List<Wpt> ocekavane = najednou.stream().collect(Collectors.toList());
		final List<Wpt> zIndexu = bag.getIndexator().stream().collect(Collectors.toList());
		Assert.assertEquals(pocet, zIndexu.size());
		for (int i = 0; i < pocet; i++) {
			Assert.assertSame("pořadí v indexu na pozici " + i, ocekavane.get(i), zIndexu.get(i));
		}
	}
}
