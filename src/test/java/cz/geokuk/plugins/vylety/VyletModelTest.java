package cz.geokuk.plugins.vylety;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.program.MainFrameHolder;
import cz.geokuk.framework.ChybyVDiagnostice;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.kind.kes.Kes;

/** Výlet v souborech lovim.ggt a tedne.ggt musí odpovídat modelu a nesmí ztrácet kódy. */
public class VyletModelTest {

	/** Místo souborů si pamatuje poslední zápis. */
	private static class Soubory extends VyletovyZperzistentnovac {
		volatile List<String> ano = new ArrayList<>(Arrays.asList("GCNEZNAMA", "GC1"));
		volatile List<String> ne = new ArrayList<>();
		int zapisu;

		@Override
		public synchronized void immediatlyZapisVylet(final List<String> ano, final List<String> ne) {
			this.ano = new ArrayList<>(ano);
			this.ne = new ArrayList<>(ne);
			zapisu++;
		}

		@Override
		public Vylet immediatlyNactiVylet(final KesBag vsechny) {
			final Vylet v = new Vylet();
			v.pridejNezname(EVylet.ANO, ano);
			v.pridejNezname(EVylet.NE, ne);
			return v;
		}
	}

	private final Soubory soubory = new Soubory();

	private VyletModel model() {
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

	private void nacti(final VyletModel model) throws Exception {
		model.prevezmiNactenyVylet(model.nactiPoZapisech(null), 0, 0);
	}

	/** Počká, až doběhnou všechny zápisy. */
	private void dopis(final VyletModel model) throws Exception {
		model.nactiPoZapisech(null);
	}

	@Test
	public void neznameKodyZustanou() throws Exception {
		final VyletModel model = model();
		nacti(model);
		model.add(EVylet.NE, kes("GC2"));
		dopis(model);
		Assert.assertEquals(Arrays.asList("GCNEZNAMA", "GC1"), soubory.ano);
		Assert.assertEquals(Collections.singletonList("GC2"), soubory.ne);
	}

	@Test
	public void souborOdpovidaPosledniZmene() throws Exception {
		final VyletModel model = model();
		nacti(model);
		final List<Kesoid> kese = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			kese.add(kes("GCX" + i));
		}
		final Random rnd = new Random(1);
		for (int i = 0; i < 3000; i++) {
			model.add(EVylet.values()[rnd.nextInt(3)], kese.get(rnd.nextInt(kese.size())));
		}
		dopis(model);
		Assert.assertEquals(new HashSet<>(model.vyletKody(EVylet.ANO)), new HashSet<>(soubory.ano));
		Assert.assertEquals(new HashSet<>(model.vyletKody(EVylet.NE)), new HashSet<>(soubory.ne));
	}

	@Test
	public void zmenaPredNactenimSeNeztrati() throws Exception {
		final VyletModel model = model();
		model.add(EVylet.ANO, kes("GC3"));
		dopis(model);
		Assert.assertEquals("před načtením se nezapisuje", 0, soubory.zapisu);
		nacti(model);
		dopis(model);
		Assert.assertEquals(Arrays.asList("GCNEZNAMA", "GC1", "GC3"), soubory.ano);
	}

	@Test
	public void zmenaBehemNacitaniVyvolaNoveNacteni() throws Exception {
		final VyletModel model = model();
		nacti(model);
		final Vylet stary = model.nactiPoZapisech(null);
		model.add(EVylet.ANO, kes("GC4"));
		model.prevezmiNactenyVylet(stary, 0, 0);
		Assert.assertTrue("model nesmí přepsat změnu starším čtením", model.vyletKody(EVylet.ANO).contains("GC4"));
	}

	@Test
	public void chybaKopirovaniDoSchrankySeOhlasi() throws Exception {
		final VyletModel model = model();
		nacti(model);
		model.inject(new MainFrameHolder()); // bez hlavního okna schránka selže
		final String okolnost = "Kopírování odkazů do schránky";
		final int puvodne = ChybyVDiagnostice.pocet(okolnost);
		model.nasypVypetDoGeogetu();
		Assert.assertTrue(ChybyVDiagnostice.pribude(okolnost, puvodne));
	}
}
