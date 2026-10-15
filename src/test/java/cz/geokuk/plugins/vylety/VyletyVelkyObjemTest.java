package cz.geokuk.plugins.vylety;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.swing.SwingUtilities;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;

/**
 * Pojmenované výlety s tisíci keší: lovim.ggt z GeoGetu s 8 000 kódy, 15 výletů, v každém stovky přidaných keší a desítky přepnutí sem a tam. Obsah každého výletu
 * v souborech musí být po každém přepnutí přesně ten, který do něj uživatel dal. Počet keší na výlet mění {@code scenare.vylet.kesi} (0 test přeskočí).
 */
public class VyletyVelkyObjemTest {

	private static final int KESI = Integer.getInteger("scenare.vylet.kesi", 500);
	private static final int VYLETU = 15;
	private static final int ZGEOGETU = 8000;

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File slozka;
	private File ano;
	private File ne;
	private VyletovyZperzistentnovac soubory;
	private VyletModel model;

	@Before
	public void setUp() throws Exception {
		Assume.assumeTrue("scenare.vylet.kesi=0 test vypíná", KESI > 0);
		slozka = tmp.newFolder("vylety");
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
		model = new VyletModel();
		model.inject(soubory);
		model.inject(event -> {});
		model.initAfterInject();
	}

	private static void zapis(final File f, final String predpona, final int pocet) throws Exception {
		final List<String> kody = new ArrayList<>();
		for (int i = 0; i < pocet; i++) {
			kody.add(predpona + i);
		}
		Files.write(f.toPath(), kody, StandardCharsets.UTF_8);
	}

	private static Set<String> radky(final File f) throws Exception {
		final List<String> r = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
		final Set<String> mnozina = new TreeSet<>(r);
		Assert.assertEquals(f + ": žádný kód dvakrát", mnozina.size(), r.size());
		return mnozina;
	}

	private static Kesoid kes(final String kod) {
		final Kes kes = new Kes();
		kes.setIdentifier(kod);
		return kes;
	}

	private void dopis() throws Exception {
		model.nactiPoZapisech(null);
	}

	private void prepni(final String id) throws Exception {
		model.aktivovat(id).get();
		for (int i = 0; i < 400 && model.isPrepina(); i++) {
			Thread.sleep(25);
		}
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse("výlet se do 10 s přepnul", model.isPrepina());
	}

	@Test(timeout = 180000)
	public void vyletySTisiciKesiSeNemiNiPriPrepinani() throws Exception {
		zapis(ano, "GCA", ZGEOGETU);
		zapis(ne, "GCN", ZGEOGETU / 2);
		model.prevezmiNactenyVylet(model.nactiPoZapisech(null), 0, 0);
		for (int i = 0; i < 20; i++) {
			model.add(EVylet.ANO, kes("GCPRIDANA" + i));
		}
		dopis();
		final Set<String> vychoziAno = radky(ano);
		final Set<String> vychoziNe = radky(ne);
		Assert.assertEquals("keše z GeoGetu i přidané", ZGEOGETU + 20, vychoziAno.size());

		final Map<String, Set<String>> ocekavaneAno = new LinkedHashMap<>();
		final Map<String, Set<String>> ocekavaneNe = new LinkedHashMap<>();
		final String vychozi = soubory.aktivniVylet();
		ocekavaneAno.put(vychozi, vychoziAno);
		ocekavaneNe.put(vychozi, vychoziNe);
		for (int v = 0; v < VYLETU; v++) {
			final String id = model.novy("Výlet " + v, true).get();
			for (int i = 0; i < 400 && model.isPrepina(); i++) {
				Thread.sleep(25);
			}
			SwingUtilities.invokeAndWait(() -> {});
			Assert.assertFalse(model.isPrepina());
			final Set<String> a = new TreeSet<>();
			final Set<String> n = new TreeSet<>();
			for (int i = 0; i < KESI; i++) {
				final String kodA = "GCV" + v + "_" + i;
				model.add(EVylet.ANO, kes(kodA));
				a.add(kodA);
				if (i % 3 == 0) {
					final String kodN = "GCW" + v + "_" + i;
					model.add(EVylet.NE, kes(kodN));
					n.add(kodN);
				}
			}
			dopis();
			Assert.assertEquals("výlet " + v + " hned po přidávání", a, radky(ano));
			Assert.assertEquals(n, radky(ne));
			ocekavaneAno.put(id, a);
			ocekavaneNe.put(id, n);
		}

		final List<String> ids = new ArrayList<>(ocekavaneAno.keySet());
		final Random rnd = new Random(7);
		for (int k = 0; k < 80; k++) {
			final String id = ids.get(rnd.nextInt(ids.size()));
			prepni(id);
			Assert.assertEquals("po přepnutí " + k + " na " + id, id, soubory.aktivniVylet());
			Assert.assertEquals("lovim.ggt po přepnutí " + k, ocekavaneAno.get(id), radky(ano));
			Assert.assertEquals("tedne.ggt po přepnutí " + k, ocekavaneNe.get(id), radky(ne));
		}
		prepni(vychozi);
		Assert.assertEquals("Výchozí výlet z GeoGetu je celý", vychoziAno, radky(ano));
		Assert.assertEquals(VYLETU + 1, model.getVylety().size());
	}
}
