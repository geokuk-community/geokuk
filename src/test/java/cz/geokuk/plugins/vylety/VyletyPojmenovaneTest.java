package cz.geokuk.plugins.vylety;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;

/** Pojmenované výlety: aktivní je vždy v lovim.ggt a tedne.ggt, ostatní vedle v ulozene/. */
public class VyletyPojmenovaneTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File slozka;
	private File ano;
	private File ne;
	private VyletovyZperzistentnovac soubory;

	@Before
	public void setUp() throws Exception {
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
	}

	private static void zapis(final File f, final String... kody) throws Exception {
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), Arrays.asList(kody), StandardCharsets.UTF_8);
	}

	private static List<String> radky(final File f) throws Exception {
		return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
	}

	private File ulozeny(final String id, final String soubor) {
		return new File(new File(new File(slozka, "ulozene"), id), soubor);
	}

	private static VyletModel model(final VyletovyZperzistentnovac soubory) {
		final VyletModel model = new VyletModel();
		model.inject(soubory);
		model.inject(event -> {});
		model.initAfterInject();
		return model;
	}

	private static Kesoid kes(final String kod) {
		final Kes kes = new Kes();
		kes.setIdentifier(kod);
		return kes;
	}

	@Test
	public void bezIndexuJeJedenVychoziVyletAnicNeniNaDisku() throws Exception {
		zapis(ano, "GC1", "GC2");
		zapis(ne, "GC3");
		final List<VyletInfo> seznam = soubory.seznamVyletu();
		Assert.assertEquals(1, seznam.size());
		Assert.assertEquals("Výchozí", seznam.get(0).getJmeno());
		Assert.assertTrue(seznam.get(0).isAktivni());
		Assert.assertEquals(2, seznam.get(0).getPocetLovim());
		Assert.assertEquals(1, seznam.get(0).getPocetTedne());
		Assert.assertFalse(new File(slozka, "ulozene").exists());
		Assert.assertFalse(new File(slozka, "vylety.txt").exists());
		Assert.assertEquals(Arrays.asList("GC1", "GC2"), radky(ano));
	}

	@Test
	public void novyVyletJePrazdnyAVychoziSeStavaUlozenymVyletem() throws Exception {
		zapis(ano, "GC1");
		zapis(ne, "GC2");
		final String id = soubory.novyVylet("Šumava");
		Assert.assertEquals(Arrays.asList("GC1"), radky(ulozeny("v1", "lovim.ggt")));
		Assert.assertEquals(Arrays.asList("GC2"), radky(ulozeny("v1", "tedne.ggt")));
		Assert.assertTrue(radky(ulozeny(id, "lovim.ggt")).isEmpty());
		Assert.assertEquals("aktivní zůstává Výchozí, jeho soubory se nemění", Arrays.asList("GC1"), radky(ano));
		Assert.assertEquals(2, soubory.seznamVyletu().size());
		Assert.assertEquals("v1", soubory.aktivniVylet());
	}

	@Test
	public void aktivaceKopirujeObsahDoLovimATedneAPuvodniVyletZustane() throws Exception {
		zapis(ano, "GC1");
		zapis(ne, "GC2");
		final String id = soubory.novyVylet("Šumava");
		zapis(ulozeny(id, "lovim.ggt"), "GCS1", "GCS2");
		zapis(ulozeny(id, "tedne.ggt"), "GCS3");

		soubory.aktivujVylet(id);

		Assert.assertEquals(Arrays.asList("GCS1", "GCS2"), radky(ano));
		Assert.assertEquals(Arrays.asList("GCS3"), radky(ne));
		Assert.assertEquals(id, soubory.aktivniVylet());
		Assert.assertEquals(Arrays.asList("GC1"), radky(ulozeny("v1", "lovim.ggt")));
		Assert.assertEquals(Arrays.asList("GC2"), radky(ulozeny("v1", "tedne.ggt")));
		Assert.assertFalse("deník aktivace se po dokončení maže", new File(slozka, "aktivace.txt").exists());
	}

	@Test
	public void zmenaVLovimUlozenaPriOdchoduSeNeztrati() throws Exception {
		zapis(ano, "GC1");
		final String id = soubory.novyVylet("Šumava");
		zapis(ano, "GC1", "GCZVENKU"); // GeoGet skript přidal keš
		soubory.aktivujVylet(id);
		soubory.aktivujVylet("v1");
		Assert.assertEquals(Arrays.asList("GC1", "GCZVENKU"), radky(ano));
	}

	@Test
	public void kazdaZmenaSeZapisujeDoObouMist() throws Exception {
		zapis(ano, "GC1");
		final String id = soubory.novyVylet("Šumava");
		soubory.aktivujVylet(id);
		soubory.immediatlyZapisVylet(Arrays.asList("GCA", "GCB"), Collections.singletonList("GCC"));
		Assert.assertEquals(Arrays.asList("GCA", "GCB"), radky(ano));
		Assert.assertEquals(Collections.singletonList("GCC"), radky(ne));
		Assert.assertEquals(Arrays.asList("GCA", "GCB"), radky(ulozeny(id, "lovim.ggt")));
		Assert.assertEquals(Collections.singletonList("GCC"), radky(ulozeny(id, "tedne.ggt")));
		Assert.assertEquals("neaktivní výlet se nemění", Arrays.asList("GC1"), radky(ulozeny("v1", "lovim.ggt")));
	}

	@Test
	public void bezIndexuZapisZapisujeJenLovimATedne() throws Exception {
		soubory.immediatlyZapisVylet(Arrays.asList("GC1"), Collections.<String>emptyList());
		Assert.assertEquals(Arrays.asList("GC1"), radky(ano));
		Assert.assertFalse(new File(slozka, "ulozene").exists());
	}

	@Test
	public void pretrzenaAktivaceSeDokonciPriDalsimCteni() throws Exception {
		zapis(ano, "GC1");
		final String id = soubory.novyVylet("Šumava");
		zapis(ulozeny(id, "lovim.ggt"), "GCS1");
		// pád po zápisu deníku a prvního souboru, index ještě ukazuje na Výchozí
		zapis(new File(slozka, "aktivace.txt"), id);
		zapis(ano, "GCS1");
		Assert.assertEquals("v1", Files.readAllLines(new File(slozka, "vylety.txt").toPath()).stream().filter(r -> r.startsWith("aktivni=")).findFirst().get().substring(8));

		soubory.immediatlyNactiVylet(null);

		Assert.assertEquals(id, soubory.aktivniVylet());
		Assert.assertEquals(Arrays.asList("GCS1"), radky(ano));
		Assert.assertTrue("tedne.ggt se dokončí z uloženého výletu", radky(ne).isEmpty());
		Assert.assertFalse(new File(slozka, "aktivace.txt").exists());
		Assert.assertEquals("uložený obsah Výchozího zůstal", Arrays.asList("GC1"), radky(ulozeny("v1", "lovim.ggt")));
	}

	@Test
	public void pretrzenaAktivacePoZapisuDenikuSeDokonciZUlozenehoVyletu() throws Exception {
		zapis(ano, "GC1");
		zapis(ne, "GCT1");
		final String id = soubory.novyVylet("Šumava");
		zapis(ulozeny(id, "lovim.ggt"), "GCS1", "GCS2");
		zapis(ulozeny(id, "tedne.ggt"), "GCS3");
		zapis(new File(slozka, "aktivace.txt"), id); // pád hned po zápisu deníku, lovim i tedne mají ještě starý obsah

		soubory.immediatlyNactiVylet(null);

		Assert.assertEquals(Arrays.asList("GCS1", "GCS2"), radky(ano));
		Assert.assertEquals(Arrays.asList("GCS3"), radky(ne));
		Assert.assertEquals(id, soubory.aktivniVylet());
	}

	@Test
	public void chybaZrcadleniNezablokujeZapisLovimATedne() throws Exception {
		zapis(ano, "GC1");
		final String id = soubory.novyVylet("Šumava");
		soubory.aktivujVylet(id);
		final File zrcadlo = ulozeny(id, "lovim.ggt");
		Assert.assertTrue(zrcadlo.delete());
		Assert.assertTrue("zápis zrcadla musí selhat", zrcadlo.mkdirs());
		try {
			soubory.immediatlyZapisVylet(Arrays.asList("GCA"), Arrays.asList("GCB"));
			Assert.fail("chyba zrcadlení se má ohlásit");
		} catch (final RuntimeException e) {
			// ohlášeno až po zápisu lovim.ggt a tedne.ggt
		}
		Assert.assertEquals(Arrays.asList("GCA"), radky(ano));
		Assert.assertEquals(Arrays.asList("GCB"), radky(ne));
	}

	@Test
	public void souborMimoUtf8NezrusiSeznam() throws Exception {
		Files.write(ano.toPath(), new byte[] { 'G', 'C', '1', '\n', (byte) 0xE8, '\n' });
		Assert.assertEquals(2, soubory.seznamVyletu().get(0).getPocetLovim());
	}

	@Test
	public void pokozenyIndexJeJedenVychoziVylet() throws Exception {
		zapis(ano, "GC1");
		zapis(new File(slozka, "vylety.txt"), "aktivni=v9", "poradi=v1,v9", "jmeno.v1=Výchozí");
		final List<VyletInfo> seznam = soubory.seznamVyletu();
		Assert.assertEquals(1, seznam.size());
		Assert.assertEquals("v1", seznam.get(0).getId());
		zapis(new File(slozka, "vylety.txt"), "nesmysl \\u12");
		Assert.assertEquals(1, soubory.seznamVyletu().size());
	}

	@Test
	public void smazaniAktivnihoAktivujeVychoziAVychoziNejdeSmazat() throws Exception {
		zapis(ano, "GC1");
		final String id = soubory.novyVylet("Šumava");
		soubory.aktivujVylet(id);
		soubory.immediatlyZapisVylet(Arrays.asList("GCS"), Collections.<String>emptyList());

		soubory.smazVylet(id);

		Assert.assertEquals("v1", soubory.aktivniVylet());
		Assert.assertEquals(Arrays.asList("GC1"), radky(ano));
		Assert.assertFalse(ulozeny(id, "lovim.ggt").exists());
		Assert.assertEquals(1, soubory.seznamVyletu().size());
		try {
			soubory.smazVylet("v1");
			Assert.fail("Výchozí výlet nejde smazat");
		} catch (final IllegalArgumentException e) {
			Assert.assertEquals(1, soubory.seznamVyletu().size());
		}
	}

	@Test
	public void prejmenovaniAValidaceJmen() throws Exception {
		final String id = soubory.novyVylet("Šumava");
		soubory.prejmenujVylet(id, "  Krkonoše ");
		Assert.assertEquals("Krkonoše", soubory.seznamVyletu().get(1).getJmeno());
		soubory.prejmenujVylet("v1", "Základ");
		Assert.assertEquals("Základ", soubory.seznamVyletu().get(0).getJmeno());

		Assert.assertNotNull(soubory.validujJmenoVyletu("", null));
		Assert.assertNotNull(soubory.validujJmenoVyletu("   ", null));
		Assert.assertNotNull(soubory.validujJmenoVyletu(null, null));
		Assert.assertNull(soubory.validujJmenoVyletu(new String(new char[60]).replace('\0', 'x'), null));
		Assert.assertNotNull(soubory.validujJmenoVyletu(new String(new char[61]).replace('\0', 'x'), null));
		Assert.assertNotNull(soubory.validujJmenoVyletu("a\nb", null));
		Assert.assertNotNull("duplicita bez ohledu na velikost", soubory.validujJmenoVyletu("KRKONOŠE", null));
		Assert.assertNull("vlastní jméno při přejmenování nevadí", soubory.validujJmenoVyletu("krkonoše", id));
		try {
			soubory.novyVylet("krkonoše");
			Assert.fail("duplicitní jméno");
		} catch (final IllegalArgumentException e) {
			Assert.assertEquals(2, soubory.seznamVyletu().size());
		}
	}

	@Test
	public void jmenoSDiakritikouPrezijeZapisIndexu() throws Exception {
		soubory.novyVylet("Říčany – žluťoučký kůň");
		Assert.assertEquals("Říčany – žluťoučký kůň", soubory.seznamVyletu().get(1).getJmeno());
	}

	@Test
	public void chybejiciUlozenySouborAktivaceNezmeni() throws Exception {
		zapis(ano, "GC1");
		final String id = soubory.novyVylet("Šumava");
		Assert.assertTrue(ulozeny(id, "tedne.ggt").delete());
		try {
			soubory.aktivujVylet(id);
			Assert.fail("poškozený výlet nejde aktivovat");
		} catch (final RuntimeException e) {
			Assert.assertEquals("v1", soubory.aktivniVylet());
			Assert.assertEquals(Arrays.asList("GC1"), radky(ano));
		}
	}

	@Test
	public void modelAktivaceZaZmenamiVyletu() throws Exception {
		zapis(ano, "GC1");
		final VyletModel model = model(soubory);
		model.prevezmiNactenyVylet(model.nactiPoZapisech(null), 0, 0);
		final String id = model.novy("Šumava", false).get();
		model.add(EVylet.ANO, kes("GCNOVA"));

		model.aktivovat(id).get();

		Assert.assertTrue("změna před aktivací je ve Výchozím výletu", radky(ulozeny("v1", "lovim.ggt")).contains("GCNOVA"));
		Assert.assertTrue("aktivní výlet je prázdný", radky(ano).isEmpty());
		Assert.assertEquals(2, model.getVylety().size());
		Assert.assertTrue(model.getVylety().get(1).isAktivni());
	}

	@Test
	public void modelOdmitneNeplatneJmenoVBudoucnosti() throws Exception {
		final VyletModel model = model(soubory);
		try {
			model.novy("", true).get();
			Assert.fail("prázdné jméno");
		} catch (final ExecutionException e) {
			Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
		}
		Assert.assertEquals(1, model.getVylety().size());
	}
}
