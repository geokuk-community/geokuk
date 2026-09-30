package cz.geokuk.core.napoveda;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.program.FConst;
import cz.geokuk.util.pocitadla.PocitadloRoste;

public class DiagnostikaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void textObsahujeVerziAUdalosti() {
		Diagnostika.zaznamenej("první");
		Diagnostika.zaznamenejChybu("chyba stahování");
		for (int i = 0; i < 120; i++) {
			Diagnostika.zaznamenej("událost " + i);
		}
		final String text = Diagnostika.text();
		Assert.assertTrue(text.startsWith("Geokuk " + FConst.VERSION));
		Assert.assertTrue(text.contains("událost 119"));
		Assert.assertFalse(text.contains("první"));
		Assert.assertTrue(text.contains("chyba stahování"));
	}

	@Test
	public void praceSMapouSeZapiseSouhrnne() {
		Diagnostika.zaznamenej("před mapou");
		Diagnostika.zaznamenejVyrez(13, "a");
		Diagnostika.zaznamenejVyrez(13, "b");
		Diagnostika.zaznamenejVyrez(13, "c");
		Diagnostika.zaznamenejVyrez(14, "c");
		Diagnostika.zaznamenej("po mapě");
		final String text = Diagnostika.text();
		Assert.assertTrue(text, text.contains("Mapa: 2× posun, měřítko 13 → 14"));
		Assert.assertTrue(text.indexOf("Mapa: 2×") < text.indexOf("po mapě"));
	}

	@Test
	public void cestaVMenu() {
		final javax.swing.JMenu soubor = new javax.swing.JMenu("Soubor");
		final javax.swing.JMenu podmenu = new javax.swing.JMenu("Mapy");
		final javax.swing.JMenuItem polozka = new javax.swing.JMenuItem("<html>Základní <i>mapa</i>");
		soubor.add(podmenu);
		podmenu.add(polozka);
		Assert.assertEquals("Soubor > Mapy > Základní mapa", Diagnostika.cesta(polozka));
	}

	@Test
	public void textObsahujeServisniHodnoty() {
		final PocitadloRoste pocitadlo = new PocitadloRoste("zk01 zkušební počítadlo", "Jen pro test.");
		pocitadlo.add(7);
		final String text = Diagnostika.text();
		Assert.assertTrue(text.contains("Servisní hodnoty:"));
		Assert.assertTrue(text.contains("zk01 zkušební počítadlo: 7"));
	}

	@Test
	public void domovskaSlozkaSeZkrati() {
		Assert.assertEquals("~" + File.separator + "geokuk", Diagnostika.bezDomova(new File(FConst.HOME_DIR, "geokuk")));
	}

	@Test
	public void konecLoguBezDomova() throws Exception {
		final File log = tmp.newFile("geokuk.log");
		final List<String> radky = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			radky.add("řádek " + i + " " + FConst.HOME_DIR.getAbsolutePath());
		}
		Files.write(log.toPath(), radky, StandardCharsets.UTF_8);
		final Deque<String> konec = Diagnostika.konecLogu(log, 3);
		Assert.assertEquals(Arrays.asList("řádek 27 ~", "řádek 28 ~", "řádek 29 ~"), new ArrayList<>(konec));
	}

	@Test
	public void chybejiciLogJePrazdny() {
		Assert.assertTrue(Diagnostika.konecLogu(new File(tmp.getRoot(), "neni.log"), 3).isEmpty());
	}

	@Test
	public void bezCestUzivateleAParametruAdres() {
		final String text = Diagnostika.soukrome("Chyby v souboru " + new File(FConst.HOME_DIR, "geokuk").getAbsolutePath() + ", https://tile.example.org/1/2/3.png?apikey=TAJNE");
		Assert.assertFalse(text, text.contains(FConst.HOME_DIR.getAbsolutePath()));
		Assert.assertFalse(text, text.contains("TAJNE"));
		Assert.assertTrue(text, text.contains("https://tile.example.org/1/2/3.png?…"));
	}

	@Test
	public void textPoleVHlasceNeniPopisKomponenty() {
		final javax.swing.JTextField pole = new javax.swing.JTextField("https://example.org/");
		Assert.assertEquals("https://example.org/", Diagnostika.textZpravy(pole));
	}

	@Test
	public void kontextoveMenuNeniVListe() {
		final javax.swing.JMenuBar lista = new javax.swing.JMenuBar();
		final javax.swing.JMenu soubor = new javax.swing.JMenu("Soubor");
		final javax.swing.JMenuItem vListe = new javax.swing.JMenuItem("Konec");
		lista.add(soubor);
		soubor.add(vListe);
		final javax.swing.JPopupMenu kontextove = new javax.swing.JPopupMenu();
		final javax.swing.JMenuItem vKontextovem = new javax.swing.JMenuItem("Přidat do cesty");
		kontextove.add(vKontextovem);
		Assert.assertTrue(Diagnostika.jeVListeMenu(vListe));
		Assert.assertFalse(Diagnostika.jeVListeMenu(vKontextovem));
	}

	@Test
	public void polozkaPridanaZnovuSeSledujeJednou() {
		final javax.swing.JMenuBar lista = new javax.swing.JMenuBar();
		final javax.swing.JMenu ikony = new javax.swing.JMenu("Ikony");
		final javax.swing.JMenuItem sada = new javax.swing.JMenuItem("Standardní");
		lista.add(ikony);
		Diagnostika.sledujMenu(lista);
		ikony.add(sada);
		ikony.remove(sada);
		ikony.insert(sada, 0);
		Diagnostika.sledujMenu(lista);
		Assert.assertEquals(1, sada.getActionListeners().length);
	}

	@Test
	public void chybySeVejdouDoOdkazuIPoMnohaUdalostech() throws Exception {
		Diagnostika.zaznamenejChybu("důležitá chyba");
		for (int i = 0; i < 150; i++) {
			Diagnostika.zaznamenej("Otevřeno okno: Přehled problémů – ěščřžýáíé ěščřžýáíé ěščřžýáíé " + i);
		}
		final String odkaz = ZadatProblemAction.odkaz();
		Assert.assertTrue(odkaz.length() <= 6000);
		Assert.assertTrue(java.net.URLDecoder.decode(odkaz, "UTF-8").contains("důležitá chyba"));
	}
}
