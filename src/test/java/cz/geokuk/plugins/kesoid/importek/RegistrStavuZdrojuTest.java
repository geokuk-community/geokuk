package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.framework.ProgressModel;

public class RegistrStavuZdrojuTest {

	private final File a = new File("a.db3");
	private final File b = new File("b.db3");
	private final File g = new File("c.gpx");
	private final Set<File> vypnute = new HashSet<>();
	private final RegistrStavuZdroju registr = new RegistrStavuZdroju();
	private final AtomicInteger zmen = new AtomicInteger();

	private void prepis(final File... soubory) {
		registr.prepis(Arrays.asList(soubory), f -> f == g ? TypZdroje.GPX : TypZdroje.GEOGET, File::getName, f -> !vypnute.contains(f), f -> 7);
	}

	private StavPolozky polozka(final File f) {
		return registr.getSnimek().getPolozky().stream().filter(p -> p.getSoubor().equals(f)).findFirst().get();
	}

	@Test
	public void novaPolozkaCekaNaRaduVypnutaJeVypnuta() {
		vypnute.add(b);
		prepis(a, b, g);
		Assert.assertEquals(StavZdroje.CEKA_NA_RADU, polozka(a).getStav());
		Assert.assertEquals(StavZdroje.VYPNUTO, polozka(b).getStav());
		Assert.assertFalse(polozka(b).isZapnuto());
		Assert.assertEquals(TypZdroje.GPX, polozka(g).getTyp());
		Assert.assertEquals(7, polozka(a).getVelikostNaDisku());
		Assert.assertEquals(StavPolozky.NEZNAMO, polozka(a).getWpCelkem());
	}

	@Test
	public void nactenaPolozkaZustaneNactenaPoNovemSkenu() {
		prepis(a, b);
		registr.zacina(a);
		registr.postup(a, 40);
		Assert.assertEquals(40, polozka(a).getPostup());
		registr.hotovo(a, 10, 8);
		prepis(a, b);
		Assert.assertEquals(StavZdroje.NACTENO, polozka(a).getStav());
		Assert.assertEquals(10, polozka(a).getWpCelkem());
		Assert.assertEquals(2, polozka(a).getPocetDuplicit());
		Assert.assertEquals(StavZdroje.CEKA_NA_RADU, polozka(b).getStav());
	}

	@Test
	public void postupNeklesaAJenKdyzSeNacita() {
		prepis(a);
		registr.postup(a, 30);
		Assert.assertEquals("nezačalo", 0, polozka(a).getPostup());
		registr.zacina(a);
		registr.postup(a, 30);
		registr.postup(a, 10);
		registr.postup(a, 100);
		Assert.assertEquals(99, polozka(a).getPostup());
	}

	@Test
	public void vypnutouPolozkuZpozdenyZapisNepreprise() {
		prepis(a);
		registr.zacina(a);
		vypnute.add(a);
		registr.prepisZapnuti(f -> !vypnute.contains(f));
		Assert.assertEquals(StavZdroje.VYPNUTO, polozka(a).getStav());
		registr.postup(a, 50);
		registr.hotovo(a, 1, 1);
		registr.cekaNaZapis(a);
		registr.chyba(a, "x");
		Assert.assertEquals(StavZdroje.VYPNUTO, polozka(a).getStav());
	}

	@Test
	public void zapnutiHnedCekaNaRaduAPoslouchaSeJednou() {
		vypnute.add(a);
		prepis(a, b);
		registr.setPosluchac(zmen::incrementAndGet);
		vypnute.clear();
		registr.prepisZapnuti(f -> !vypnute.contains(f));
		Assert.assertEquals(1, zmen.get());
		Assert.assertEquals(StavZdroje.CEKA_NA_RADU, polozka(a).getStav());
		registr.prepisZapnuti(f -> !vypnute.contains(f));
		Assert.assertEquals("beze změny se neohlašuje", 1, zmen.get());
	}

	@Test
	public void stavTypuPodleZapnutychPolozek() {
		vypnute.add(b);
		prepis(a, b, g);
		StavZdroju s = registr.getSnimek();
		Assert.assertEquals(StavZdroje.NACITA_SE, s.getStavTypu(TypZdroje.GEOGET));
		Assert.assertTrue(s.isNacitaSe());
		Assert.assertFalse(s.isCelyTypZapnut(TypZdroje.GEOGET));
		Assert.assertTrue(s.isTypZapnut(TypZdroje.GEOGET));
		Assert.assertEquals(StavZdroje.VYPNUTO, s.getStavTypu(TypZdroje.GSAK));
		registr.hotovo(a, 1, 1);
		registr.hotovo(g, 1, 1);
		s = registr.getSnimek();
		Assert.assertEquals(StavZdroje.NACTENO, s.getStavTypu(TypZdroje.GEOGET));
		Assert.assertFalse(s.isNacitaSe());
		registr.cekaNaZapis(a);
		Assert.assertEquals(StavZdroje.CEKA_NA_ZAPIS, registr.getSnimek().getStavTypu(TypZdroje.GEOGET));
		registr.chyba(a, "vadné");
		Assert.assertEquals(StavZdroje.CHYBA, registr.getSnimek().getStavTypu(TypZdroje.GEOGET));
		Assert.assertEquals("vadné", polozka(a).getChyba());
	}

	@Test
	public void poradiPodleTypuPakPodleNazvu() {
		prepis(b, g, a);
		final List<String> jmena = new java.util.ArrayList<>();
		registr.getSnimek().getPolozky().forEach(p -> jmena.add(p.getNazev()));
		Assert.assertEquals(Arrays.asList("c.gpx", "a.db3", "b.db3"), jmena);
		Assert.assertEquals("a.db3", polozka(a).getCesta());
	}

	@Test
	public void souboryTypu() {
		prepis(a, b, g);
		final List<File> db = registr.getSoubory(TypZdroje.GEOGET);
		Assert.assertEquals(Arrays.asList(a, b), db);
	}

	@Test
	public void sledovacPostupuVidiProcentaJenVeSvemVlakne() {
		final ProgressModel model = new ProgressModel();
		model.inject(udalost -> {});
		final int[] posledni = { -1 };
		ProgressModel.setSledovacPostupu(p -> posledni[0] = p);
		try {
			final cz.geokuk.framework.Progressor progressor = model.start(200, "x");
			progressor.setProgress(50);
			Assert.assertEquals(25, posledni[0]);
			progressor.addProgress(100);
			Assert.assertEquals(75, posledni[0]);
		} finally {
			ProgressModel.setSledovacPostupu(null);
		}
		posledni[0] = -1;
		model.start(10, "y").setProgress(5);
		Assert.assertEquals("bez sledovače se nic nehlásí", -1, posledni[0]);
	}
}
