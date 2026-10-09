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

/** Bag s převzatými skupinami zdrojů je po každém přepnutí stejný jako bag načtený znovu od začátku. */
public class PrevzetiSkupinTest {

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
			return cz.geokuk.framework.MyPreferences.current().node("test-prevzeti-skupin");
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
		final File[] soubory = new File[5];
		for (int s = 0; s < soubory.length; s++) {
			final StringBuilder wpty = new StringBuilder();
			for (int i = 0; i < 300; i++) {
				// z4 má část keší z0 (stejná jména), z0 a z4 tak tvoří jednu skupinu
				final int zdroj = s == 4 && i < 50 ? 0 : s;
				// část keší stojí na stejném místě jako keše z jiných souborů
				final double[] misto = !mista.isEmpty() && r.nextInt(5) == 0 ? mista.get(r.nextInt(mista.size())) : new double[] { 49 + r.nextDouble() * 2, 13 + r.nextDouble() * 5 };
				mista.add(misto);
				final StringBuilder tagy = new StringBuilder("<gpxg:GeogetExtension xmlns:gpxg=\"http://geoget.ararat.cz/GpxExtensions/v2\"><gpxg:Tags>");
				tagy.append("<gpxg:Tag Category=\"BestOf\">").append(r.nextInt(20)).append("</gpxg:Tag>");
				tagy.append("<gpxg:Tag Category=\"favorites\">").append(r.nextInt(500)).append("</gpxg:Tag>");
				if (s == 3 && i % 7 == 0) {
					// uživatelský gen přibude do genomu až se zdrojem z3
					tagy.append("<gpxg:Tag Category=\"geokuk_barva\">").append(i % 2 == 0 ? "modra" : "cervena").append("</gpxg:Tag>");
				}
				tagy.append("</gpxg:Tags></gpxg:GeogetExtension>");
				wpty.append(ImportKesiTest.kes(String.format("GC%d%04d", zdroj, i), "Geocache", "Traditional Cache", "Kačer", 7, true, false, "1.5", tagy.toString())
						.replace("lat=\"50.1\" lon=\"14.4\"", String.format(Locale.ROOT, "lat=\"%.6f\" lon=\"%.6f\"", misto[0], misto[1])));
			}
			soubory[s] = new File(slozka, "z" + s + ".gpx");
			Files.write(soubory[s].toPath(), ImportKesiTest.gpx(wpty.toString()).getBytes(StandardCharsets.UTF_8));
		}
		final Model model = new Model();
		final MultiNacitac nacitac = new MultiNacitac(model);
		// Skupiny zdrojů se převezmou jen se stejným genomem, jako v programu.
		final Genom genom = new Genom();

		over(nacti(nacitac, slozka, genom), slozka, model);
		model.vypnute.add(soubory[2]);
		over(nacti(nacitac, slozka, genom), slozka, model);
		model.vypnute.clear();
		over(nacti(nacitac, slozka, genom), slozka, model);
		model.vypnute.add(soubory[2]);
		over(nacti(nacitac, slozka, genom), slozka, model);
		model.vypnute.add(soubory[3]);
		over(nacti(nacitac, slozka, genom), slozka, model);
		model.vypnute.remove(soubory[3]);
		over(nacti(nacitac, slozka, genom), slozka, model);
		model.vypnute.add(soubory[1]);
		over(nacti(nacitac, slozka, genom), slozka, model);
	}

	private static KesBag nacti(final MultiNacitac nacitac, final File slozka, final Genom genom) throws IOException {
		nacitac.setRootDirs(true, slozka, null, null, Collections.<File> emptySet());
		return nacitac.nacti(null, genom);
	}

	/** Bag po přepnutí odpovídá bagu načtenému znovu od začátku; index odpovídá waypointům bagu v jejich pořadí. */
	private static void over(final KesBag bag, final File slozka, final Model model) throws IOException {
		final Model cisty = new Model();
		cisty.vypnute.addAll(model.vypnute);
		final KesBag znovu = nacti(new MultiNacitac(cisty), slozka, new Genom());

		Assert.assertEquals(jmena(znovu.getWpts()), jmena(bag.getWpts()));
		final List<String> kesoidy = bag.getKesoidy().stream().map(k -> k.getIdentifier()).sorted().collect(Collectors.toList());
		Assert.assertEquals("kešoidy bez duplicit", kesoidy.size(), new HashSet<>(kesoidy).size());
		Assert.assertEquals(znovu.getKesoidy().stream().map(k -> k.getIdentifier()).sorted().collect(Collectors.toList()), kesoidy);
		Assert.assertEquals(znovu.getMaximalniBestOf(), bag.getMaximalniBestOf());
		Assert.assertEquals(znovu.getMaximalniFavorit(), bag.getMaximalniFavorit());
		Assert.assertEquals(znovu.getMaximalniHodnoceni(), bag.getMaximalniHodnoceni());
		// Gen přibylý se zdrojem v genomu zůstane i po jeho vypnutí, každý waypoint pak má jeho výchozí alelu; nový genom ho nezná.
		final Map<String, Integer> pocty = pocty(bag);
		final Map<String, Integer> poctyZnovu = pocty(znovu);
		for (final Map.Entry<String, Integer> e : pocty.entrySet()) {
			if (!poctyZnovu.containsKey(e.getKey()) && e.getKey().startsWith("~~:")) {
				Assert.assertEquals(e.getKey(), pocet(bag), (int) e.getValue());
				poctyZnovu.put(e.getKey(), e.getValue());
			}
		}
		Assert.assertEquals(poctyZnovu, pocty);

		final int pocet = bag.getWpts().size();
		final Indexator<Wpt> najednou = Indexator.postav(BoundingRect.ALL, bag.getWpts(), w -> w.getMou().xx, w -> w.getMou().yy);
		final List<Wpt> ocekavane = najednou.stream().collect(Collectors.toList());
		final List<Wpt> zIndexu = bag.getIndexator().stream().collect(Collectors.toList());
		Assert.assertEquals(pocet, zIndexu.size());
		for (int i = 0; i < pocet; i++) {
			Assert.assertSame("pořadí v indexu na pozici " + i, ocekavane.get(i), zIndexu.get(i));
		}
	}

	private static int pocet(final KesBag bag) {
		return bag.getWpts().size();
	}

	private static List<String> jmena(final List<Wpt> wpty) {
		return wpty.stream().map(Wpt::getName).sorted().collect(Collectors.toList());
	}

	private static Map<String, Integer> pocty(final KesBag bag) {
		final Map<String, Integer> pocty = new TreeMap<>();
		bag.getPoctyAlel().getMap().forEach((alela, pocet) -> {
			if (pocet != 0) {
				pocty.put(alela.qualName(), pocet);
			}
		});
		return pocty;
	}
}
