package cz.geokuk.plugins.mapy.sprava.gui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogVrstev;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Mazání vrstev a map: smaže jen soubory dané vrstvy nebo mapy (i s kontrolním součtem a rozpracovanými soubory) a vždy po potvrzení. */
public class SmazaniSouboruTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static Set<String> jmena(final List<File> f) {
		final Set<String> j = new TreeSet<>();
		for (final File x : f) {
			j.add(x.getName());
		}
		return j;
	}

	private File vytvor(final File slozka, final String jmeno, final int bajtu) throws Exception {
		final File f = new File(slozka, jmeno);
		Files.write(f.toPath(), new byte[bajtu]);
		return f;
	}

	@Test
	public void souboryVrstvyVcetneSouctuARozpracovanych() throws Exception {
		final File slozka = tmp.newFolder("layers");
		final KatalogVrstev katalog = VrstvyProGui.katalogCesko();
		final KatalogovaVrstva trasy = katalog.podleId("tourist/europe/czech-republic").get();
		final String cesta = trasy.getSoubory().get(0).getCesta();
		final String vrstevnice = katalog.podleId("contours/europe/czech-republic").get().getSoubory().get(0).getCesta();
		new File(slozka, cesta).getParentFile().mkdirs();
		final File[] vytvorene = { vytvor(slozka, cesta, 100), vytvor(slozka, cesta + ".sha256", 64), vytvor(slozka, cesta + ".stahuje", 10),
				vytvor(slozka, cesta + ".stahuje.info", 10), vytvor(slozka, cesta + ".stara", 10) };
		vytvor(slozka, vrstevnice, 100);
		vytvor(slozka, "cizi.map", 100);
		final List<File> soubory = SmazaniSouboru.souboryVrstvy(slozka, trasy);
		Assert.assertEquals(5, soubory.size());
		Assert.assertEquals(new TreeSet<>(Arrays.asList(vytvorene[0].getName(), vytvorene[1].getName(), vytvorene[2].getName(), vytvorene[3].getName(), vytvorene[4].getName())), jmena(soubory));
		Assert.assertTrue("bez složky nic", SmazaniSouboru.souboryVrstvy(null, trasy).isEmpty());
		Assert.assertTrue(SmazaniSouboru.smaz(soubory).isEmpty());
		Assert.assertTrue("cizí a jiná vrstva zůstaly", new File(slozka, "cizi.map").isFile() && new File(slozka, vrstevnice).isFile());
		Assert.assertTrue("smazat už smazané nevadí", SmazaniSouboru.smaz(soubory).isEmpty());
	}

	/** Smazání vrstvy tras smaže i její starší verzi (*.routes.map). */
	@Test
	public void souboryVrstvyVcetneStarsiVerze() throws Exception {
		final File slozka = tmp.newFolder("layers");
		final KatalogovaVrstva trasy = VrstvyProGui.katalogCesko().podleId("tourist/europe/czech-republic").get();
		new File(slozka, "europe").mkdirs();
		final File stara = vytvor(slozka, "europe/czech-republic.routes.map", 100);
		final File soucet = vytvor(slozka, "europe/czech-republic.routes.map.sha256", 64);
		vytvor(slozka, "europe/slovakia.routes.map", 100);
		Assert.assertEquals(Arrays.asList(stara, soucet), SmazaniSouboru.souboryVrstvy(slozka, trasy));
	}

	@Test
	public void souboryMapyAPopis() throws Exception {
		final File slozka = tmp.newFolder("mapy");
		final KatalogovaMapa bayern = KatalogMap.vestaveny().podleId("europe/germany/bayern").get();
		vytvor(slozka, bayern.getNazevSouboru().toUpperCase(), 2_000_000);
		vytvor(slozka, "jina.map", 5);
		final List<File> soubory = SmazaniSouboru.souboryMapy(slozka, bayern);
		Assert.assertEquals(1, soubory.size());
		final String popis = SmazaniSouboru.popis(soubory);
		Assert.assertTrue(popis, popis.contains(bayern.getNazevSouboru().toUpperCase()));
		Assert.assertTrue(popis, popis.contains("2 MB"));
		Assert.assertTrue(popis, popis.contains("změněno"));
		Assert.assertTrue(popis, popis.endsWith("Celkem 2 MB"));
		Assert.assertTrue(SmazaniSouboru.souboryMapy(slozka, KatalogMap.vestaveny().podleId("europe/czech-republic").get()).isEmpty());
	}

	/** Bez potvrzení se nic nesmaže; po potvrzení se smaže, mimo EDT. */
	@Test(timeout = 20000)
	public void smazaniPoPotvrzeni() throws Exception {
		final File slozka = tmp.newFolder("vrstvy2");
		final File a = vytvor(slozka, "a.map", 10);
		final File b = vytvor(slozka, "b.map", 10);
		final JBlokMap[] blok = new JBlokMap[1];
		final List<String> texty = new ArrayList<>();
		final boolean[] ano = { false };
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			blok[0] = new JBlokMap();
			blok[0].setZdrojSlozky(() -> slozka);
			blok[0].potvrzeni = (titulek, text) -> {
				texty.add(titulek + "|" + text);
				return ano[0];
			};
			blok[0].smazSPotvrzenim("Smazat testovací", Arrays.asList(a, b));
		});
		Assert.assertTrue(a.exists() && b.exists());
		Assert.assertTrue(texty.get(0), texty.get(0).contains("a.map") && texty.get(0).contains("b.map") && texty.get(0).contains("nejde vrátit"));
		ano[0] = true;
		javax.swing.SwingUtilities.invokeAndWait(() -> blok[0].smazSPotvrzenim("Smazat testovací", Arrays.asList(a, b)));
		while (a.exists() || b.exists()) {
			Thread.sleep(20);
		}
		javax.swing.SwingUtilities.invokeAndWait(() -> blok[0].smazSPotvrzenim("Smazat nic", new ArrayList<>()));
		Assert.assertEquals("prázdný seznam se ani neptá", 2, texty.size());
	}

	/** Země stažená s jedním regionem: nabídne se jen smazání regionu; celá země až když jsou stažené všechny regiony; vždy jen jedna z kopií. */
	@Test
	public void souboryDvojitychMap() throws Exception {
		final File slozka = tmp.newFolder("dvojite");
		final KatalogMap katalog = KatalogMap.vestaveny();
		final KatalogovaMapa celek = katalog.podleId("europe/germany").get();
		vytvor(slozka, celek.getNazevSouboru(), 3);
		final List<KatalogovaMapa> vsechny = katalog.regiony(celek);
		Assert.assertTrue(vsechny.size() > 1);
		vytvor(slozka, vsechny.get(0).getNazevSouboru(), 2);

		final cz.geokuk.plugins.mapy.sprava.PokrytiMap.DvojitaMapa cast = cz.geokuk.plugins.mapy.sprava.PokrytiMap.dvojiteMapy(slozka, katalog).get(0);
		Assert.assertFalse("stažen jediný region, zbytek země by se smazáním celku ztratil", cast.isRegionyPokryvajiCelek());
		Assert.assertEquals(Arrays.asList(vsechny.get(0).getNazevSouboru()), nazvy(SmazaniSouboru.souboryDvojite(slozka, cast, true)));
		Assert.assertTrue(SmazaniSouboru.souboryDvojite(slozka, cast, false).isEmpty());

		for (final KatalogovaMapa r : vsechny) {
			final File f = new File(slozka, r.getNazevSouboru());
			if (!f.exists()) {
				vytvor(slozka, r.getNazevSouboru(), 2);
			}
		}
		final cz.geokuk.plugins.mapy.sprava.PokrytiMap.DvojitaMapa cela = cz.geokuk.plugins.mapy.sprava.PokrytiMap.dvojiteMapy(slozka, katalog).get(0);
		Assert.assertTrue(cela.isRegionyPokryvajiCelek());
		Assert.assertEquals(Arrays.asList(celek.getNazevSouboru()), nazvy(SmazaniSouboru.souboryDvojite(slozka, cela, false)));
		Assert.assertEquals(vsechny.size(), SmazaniSouboru.souboryDvojite(slozka, cela, true).size());
		Assert.assertFalse(nazvy(SmazaniSouboru.souboryDvojite(slozka, cela, true)).contains(celek.getNazevSouboru()));
	}

	private static List<String> nazvy(final List<File> f) {
		final List<String> n = new ArrayList<>();
		for (final File x : f) {
			n.add(x.getName());
		}
		return n;
	}
}
