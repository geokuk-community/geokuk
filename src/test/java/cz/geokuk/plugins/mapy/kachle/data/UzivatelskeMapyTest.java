package cz.geokuk.plugins.mapy.kachle.data;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.swing.KeyStroke;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.program.FConst;

/** Formát a ověřování souboru uživatelských map. */
public class UzivatelskeMapyTest {

	private static final String URL = "https://tile.example.org/{z}/{x}/{y}.png";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final List<String> chyby = new ArrayList<>();

	@After
	public void uklid() {
		EKaType.setUzivatelske(Collections.emptyList());
	}

	private List<EKaType> zpracuj(final String... radky) throws Exception {
		final Properties p = new Properties();
		p.load(new StringReader(String.join("\n", radky)));
		return UzivatelskeMapy.zpracuj(p, chyby);
	}

	private EKaType jedna(final String... radky) throws Exception {
		final List<EKaType> mapy = zpracuj(radky);
		Assert.assertEquals(Collections.emptyList(), chyby);
		Assert.assertEquals(1, mapy.size());
		return mapy.get(0);
	}

	private void chyba(final String castTextu, final String... radky) throws Exception {
		final List<EKaType> mapy = zpracuj(radky);
		Assert.assertTrue("mapa se měla vynechat", mapy.isEmpty());
		Assert.assertEquals(chyby.toString(), 1, chyby.size());
		Assert.assertTrue(chyby.get(0), chyby.get(0).contains(castTextu));
	}

	private static String url(final EKaType mapa, final int x, final int y, final int z) throws Exception {
		final KaLoc loc = KaLoc.ofJZ(new Mou(x, y), z);
		final String url = new Ka(loc, mapa).getUrl().toString();
		return url.replace(String.valueOf(loc.getFromSzUnsignedX()), "X").replace(String.valueOf(loc.getFromSzUnsignedY()), "Y");
	}

	// Výchozí hodnoty

	@Test
	public void minimalniMapaMaVychoziHodnoty() throws Exception {
		final EKaType m = jedna("topo.nazev=Topo", "topo.url=" + URL);
		Assert.assertEquals("user-topo", m.name());
		Assert.assertEquals("user-topo", m.toString());
		Assert.assertTrue(m.isUzivatelska());
		Assert.assertEquals("Topo", m.getNazev());
		Assert.assertEquals("Topo", m.getPopis());
		Assert.assertEquals(0, m.getMinMoumer());
		Assert.assertEquals(18, m.getMaxMoumer());
		Assert.assertEquals(18, m.getMaxAutoMoumer());
		Assert.assertEquals(0, m.getKlavesa());
		Assert.assertNull(m.getKeyStroke());
		Assert.assertTrue(m.getHlavicky().isEmpty());
		Assert.assertEquals("", m.getAtribuce());
		Assert.assertFalse(m.isHromadneStahovaniPovoleno());
	}

	@Test
	public void vsechnyVlastnosti() throws Exception {
		final EKaType m = jedna("m.nazev=Moje mapa", "m.url=http://mapserver.mapy.cz/x/{z}-{x}-{y}", "m.popis=Popis v nápovědě", "m.min=3", "m.max=19", "m.maxauto=17",
				"m.klavesa=u", "m.zkratka=ctrl U", "m.atribuce=© Autor", "m.hromadne=ano", "m.hlavicka.Referer=https://mapy.com/", "m.hlavicka.User-Agent=Geokuk/{verze} (test)");
		Assert.assertEquals("Moje mapa", m.getNazev());
		Assert.assertEquals("Popis v nápovědě", m.getPopis());
		Assert.assertEquals(3, m.getMinMoumer());
		Assert.assertEquals(19, m.getMaxMoumer());
		Assert.assertEquals(17, m.getMaxAutoMoumer());
		Assert.assertEquals('U', m.getKlavesa());
		Assert.assertEquals(KeyStroke.getKeyStroke("ctrl U"), m.getKeyStroke());
		Assert.assertEquals("© Autor", m.getAtribuce());
		Assert.assertEquals("https://mapy.com/", m.getHlavicky().get("Referer"));
		Assert.assertEquals("Geokuk/" + FConst.VERSION + " (test)", m.getHlavicky().get("User-Agent"));
		Assert.assertEquals(2, m.getHlavicky().size());
		Assert.assertTrue(m.isHromadneStahovaniPovoleno());
	}

	@Test
	public void mezeryKolemHodnotSeOrezou() throws Exception {
		final EKaType m = jedna("m.nazev =  Topo  ", "m.url = " + URL + "  ", "m.max = 15 ");
		Assert.assertEquals("Topo", m.getNazev());
		Assert.assertEquals(15, m.getMaxMoumer());
	}

	@Test
	public void maxautoVychoziJeMax() throws Exception {
		Assert.assertEquals(12, jedna("m.nazev=M", "m.url=" + URL, "m.max=12").getMaxAutoMoumer());
	}

	@Test
	public void krajniMeritka() throws Exception {
		final EKaType m = jedna("m.nazev=M", "m.url=" + URL, "m.min=0", "m.max=22", "m.maxauto=0");
		Assert.assertEquals(0, m.getMinMoumer());
		Assert.assertEquals(22, m.getMaxMoumer());
		Assert.assertEquals(0, m.getMaxAutoMoumer());
		Assert.assertEquals(5, jedna("n.nazev=N", "n.url=" + URL, "n.min=5", "n.max=5").getMaxMoumer());
	}

	@Test
	public void fitMoumerDrziRozsah() throws Exception {
		final EKaType m = jedna("m.nazev=M", "m.url=" + URL, "m.min=4", "m.max=10");
		Assert.assertEquals(4, m.fitMoumer(1));
		Assert.assertEquals(7, m.fitMoumer(7));
		Assert.assertEquals(10, m.fitMoumer(20));
	}

	// Adresy dlaždic

	@Test
	public void adresaSeSklada() throws Exception {
		Assert.assertEquals("https://tile.example.org/13/X/Y.png", url(jedna("m.nazev=M", "m.url=" + URL), 0x40000000, 0x20000000, 13));
	}

	@Test
	public void adresaVeTvaruMapyCz() throws Exception {
		Assert.assertEquals("http://mapserver.mapy.cz/turist-m/13-X-Y", url(jedna("m.nazev=M", "m.url=http://mapserver.mapy.cz/turist-m/{z}-{x}-{y}"), 0x40000000, 0x20000000, 13));
	}

	@Test
	public void zastupneZnakyVLibovolnemPoradiIVDotazu() throws Exception {
		Assert.assertEquals("https://t.example.org/tile?y=Y&x=X&z=13&key=abc", url(jedna("m.nazev=M", "m.url=https://t.example.org/tile?y={y}&x={x}&z={z}&key=abc"), 0x40000000, 0x20000000, 13));
	}

	@Test
	public void adresaVMalemMeritku() throws Exception {
		final EKaType m = jedna("m.nazev=M", "m.url=" + URL);
		final String url = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 2), m).getUrl().toString();
		Assert.assertTrue(url, url.matches("https://tile\\.example\\.org/2/[0-3]/[0-3]\\.png"));
	}

	@Test
	public void httpIHttpsSeSmi() throws Exception {
		jedna("m.nazev=M", "m.url=http://t.example.org/{z}/{x}/{y}");
		chyby.clear();
		jedna("n.nazev=N", "n.url=https://t.example.org/{z}/{x}/{y}");
	}

	// Chyby

	@Test
	public void chybiNazev() throws Exception {
		chyba("m.nazev", "m.url=" + URL);
	}

	@Test
	public void prazdnyNazev() throws Exception {
		chyba("m.nazev", "m.nazev= ", "m.url=" + URL);
	}

	@Test
	public void chybiUrl() throws Exception {
		chyba("m.url", "m.nazev=M");
	}

	@Test
	public void urlBezZastupnychZnaku() throws Exception {
		chyba("m.url", "m.nazev=M", "m.url=https://t.example.org/{x}/{y}.png");
		chyby.clear();
		chyba("m.url", "m.nazev=M", "m.url=https://t.example.org/{z}/{y}.png");
		chyby.clear();
		chyba("m.url", "m.nazev=M", "m.url=https://t.example.org/{z}/{x}.png");
	}

	@Test
	public void urlJinehoProtokolu() throws Exception {
		chyba("m.url", "m.nazev=M", "m.url=ftp://t.example.org/{z}/{x}/{y}");
		chyby.clear();
		chyba("m.url", "m.nazev=M", "m.url=file:///{z}/{x}/{y}");
	}

	@Test
	public void urlSMezerou() throws Exception {
		chyba("m.url", "m.nazev=M", "m.url=https://t.example.org/{z} /{x}/{y}");
	}

	@Test
	public void neplatneOznaceni() throws Exception {
		for (final String id : new String[] { "Topo", "mapa_1", "-topo", "mapička" }) {
			chyby.clear();
			Assert.assertTrue(zpracuj(id + ".nazev=M", id + ".url=" + URL).isEmpty());
			Assert.assertEquals(id, 2, chyby.size());
			Assert.assertTrue(chyby.get(0), chyby.get(0).contains("označení"));
		}
	}

	@Test
	public void klicBezVlastnosti() throws Exception {
		Assert.assertTrue(zpracuj("topo=neco").isEmpty());
		Assert.assertEquals(1, chyby.size());
	}

	@Test
	public void neznamaVlastnost() throws Exception {
		final List<EKaType> mapy = zpracuj("m.nazev=M", "m.url=" + URL, "m.barva=modrá");
		Assert.assertEquals("platné vlastnosti mapu nezruší", 1, mapy.size());
		Assert.assertEquals(1, chyby.size());
		Assert.assertTrue(chyby.get(0), chyby.get(0).contains("m.barva"));
	}

	@Test
	public void neplatneJmenoHlavicky() throws Exception {
		zpracuj("m.nazev=M", "m.url=" + URL, "m.hlavicka.Špatné=x", "m.hlavicka.=x");
		Assert.assertEquals(chyby.toString(), 2, chyby.size());
	}

	@Test
	public void meritkaMimoRozsah() throws Exception {
		for (final String[] meritka : new String[][] { { "min", "-1" }, { "max", "23" }, { "max", "30" } }) {
			chyby.clear();
			chyba("měřítka", "m.nazev=M", "m.url=" + URL, "m." + meritka[0] + "=" + meritka[1]);
		}
	}

	@Test
	public void minVetsiNezMax() throws Exception {
		chyba("měřítka", "m.nazev=M", "m.url=" + URL, "m.min=10", "m.max=5");
	}

	@Test
	public void maxautoMimoMinMax() throws Exception {
		chyba("měřítka", "m.nazev=M", "m.url=" + URL, "m.max=10", "m.maxauto=11");
		chyby.clear();
		chyba("měřítka", "m.nazev=M", "m.url=" + URL, "m.min=5", "m.maxauto=4");
	}

	@Test
	public void meritkoNeniCislo() throws Exception {
		chyba("celá čísla", "m.nazev=M", "m.url=" + URL, "m.max=deset");
		chyby.clear();
		chyba("celá čísla", "m.nazev=M", "m.url=" + URL, "m.min=1.5");
	}

	@Test
	public void neplatnaKlavesa() throws Exception {
		for (final String klavesa : new String[] { "ab", "č", "+" }) {
			chyby.clear();
			chyba("klavesa", "m.nazev=M", "m.url=" + URL, "m.klavesa=" + klavesa);
		}
	}

	@Test
	public void hromadneJenAnoNeboNe() throws Exception {
		Assert.assertFalse(jedna("m.nazev=M", "m.url=" + URL, "m.hromadne=ne").isHromadneStahovaniPovoleno());
		chyba("hromadne", "n.nazev=N", "n.url=" + URL, "n.hromadne=true");
	}

	@Test
	public void platneZkratky() throws Exception {
		Assert.assertEquals(KeyStroke.getKeyStroke('u'), jedna("m.nazev=M", "m.url=" + URL, "m.zkratka=u").getKeyStroke());
		Assert.assertEquals(KeyStroke.getKeyStroke("F5"), jedna("n.nazev=N", "n.url=" + URL, "n.zkratka=F5").getKeyStroke());
		Assert.assertEquals(KeyStroke.getKeyStroke("alt shift U"), jedna("o.nazev=O", "o.url=" + URL, "o.zkratka=alt shift U").getKeyStroke());
	}

	@Test
	public void neplatnaZkratka() throws Exception {
		chyba("zkratka", "m.nazev=M", "m.url=" + URL, "m.zkratka=ctrl nesmysl");
	}

	@Test
	public void zkratkaKazdeVestaveneMapySeOdmitne() throws Exception {
		for (final EKaType vestavena : EKaType.vestavene()) {
			if (vestavena.getKeyStroke() != null) {
				chyby.clear();
				chyba(vestavena.getNazev(), "m.nazev=M", "m.url=" + URL, "m.zkratka=" + vestavena.getKeyStroke().getKeyChar());
			}
		}
	}

	@Test
	public void zkratkaJineUzivatelskeMapySeOdmitne() throws Exception {
		final List<EKaType> mapy = zpracuj("a.nazev=A", "a.url=" + URL, "a.zkratka=F5", "b.nazev=B", "b.url=" + URL, "b.zkratka=F5");
		Assert.assertEquals(1, mapy.size());
		Assert.assertEquals("user-a", mapy.get(0).name());
		Assert.assertEquals(1, chyby.size());
		Assert.assertTrue(chyby.get(0), chyby.get(0).contains("b.zkratka") && chyby.get(0).contains("A"));
	}

	// Více map

	@Test
	public void viceMapSeradenychPodleOznaceni() throws Exception {
		final List<EKaType> mapy = zpracuj("zeta.nazev=Z", "zeta.url=" + URL, "alfa.nazev=A", "alfa.url=" + URL, "m-2.nazev=M", "m-2.url=" + URL);
		Assert.assertEquals(Collections.emptyList(), chyby);
		Assert.assertEquals(Arrays.asList("user-alfa", "user-m-2", "user-zeta"), Arrays.asList(mapy.get(0).name(), mapy.get(1).name(), mapy.get(2).name()));
	}

	@Test
	public void chybnaMapaNerusiOstatni() throws Exception {
		final List<EKaType> mapy = zpracuj("dobra.nazev=D", "dobra.url=" + URL, "spatna.nazev=S", "spatna.url=ftp://x", "druha.nazev=D2", "druha.url=" + URL);
		Assert.assertEquals(2, mapy.size());
		Assert.assertEquals(1, chyby.size());
	}

	@Test
	public void oznaceniShodneSVestavenouNekoliduje() throws Exception {
		final EKaType m = jedna("turist-m.nazev=Turistická", "turist-m.url=" + URL);
		Assert.assertEquals("user-turist-m", m.name());
		Assert.assertNotSame(EKaType.TURIST_M, m);
	}

	@Test
	public void prazdnySouborNicNevytvori() throws Exception {
		Assert.assertTrue(zpracuj("# jen komentář", "").isEmpty());
		Assert.assertTrue(chyby.isEmpty());
	}

	// Registr podkladů

	@Test
	public void uzivatelskeJsouZaVestavenymi() throws Exception {
		final List<EKaType> mapy = zpracuj("a.nazev=A", "a.url=" + URL, "b.nazev=B", "b.url=" + URL);
		EKaType.setUzivatelske(mapy);
		final List<EKaType> vse = Arrays.asList(EKaType.values());
		Assert.assertEquals(EKaType.vestavene(), vse.subList(0, EKaType.vestavene().size()));
		Assert.assertEquals(mapy, vse.subList(EKaType.vestavene().size(), vse.size()));
		Assert.assertSame(mapy.get(1), EKaType.podleJmena("user-b"));
		Assert.assertNull(EKaType.podleJmena("b"));
	}

	@Test
	public void vestaveneMapyNemajiVAdreseKlic() throws Exception {
		for (final EKaType ka : EKaType.vestavene()) {
			final String url = new Ka(KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 10), ka).getUrl().toString();
			Assert.assertFalse(url, url.toLowerCase().contains("key"));
		}
	}

	@Test
	public void vestaveneNejsouUzivatelske() {
		for (final EKaType ka : EKaType.vestavene()) {
			Assert.assertFalse(ka.name(), ka.isUzivatelska());
			Assert.assertTrue(ka.getHlavicky().isEmpty());
			Assert.assertSame(ka, EKaType.podleJmena(ka.name()));
		}
		Assert.assertNull(EKaType.podleJmena("neexistuje"));
	}

	// Soubor

	@Test
	public void nacteSouborVUtf8() throws Exception {
		final File soubor = tmp.newFile(UzivatelskeMapy.SOUBOR);
		Files.write(soubor.toPath(), ("čeština.nazev=x\nmapa.nazev=Žluťoučká mapa\nmapa.url=" + URL + "\nmapa.atribuce=© Kůň\n").getBytes(StandardCharsets.UTF_8));
		final List<String> chybySouboru = UzivatelskeMapy.nacti(soubor);
		Assert.assertEquals(1, chybySouboru.size());
		final EKaType m = EKaType.podleJmena("user-mapa");
		Assert.assertEquals("Žluťoučká mapa", m.getNazev());
		Assert.assertEquals("© Kůň", m.getAtribuce());
	}

	@Test
	public void chybejiciSouborNicNezmeni() throws Exception {
		Assert.assertTrue(UzivatelskeMapy.nacti(new File(tmp.getRoot(), "neni.properties")).isEmpty());
		Assert.assertEquals(EKaType.vestavene().size(), EKaType.values().length);
	}

	@Test
	public void nactenimSeNahradiPredchoziUzivatelske() throws Exception {
		final File soubor = tmp.newFile(UzivatelskeMapy.SOUBOR);
		Files.write(soubor.toPath(), ("a.nazev=A\na.url=" + URL + "\n").getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapy.nacti(soubor);
		Files.write(soubor.toPath(), ("b.nazev=B\nb.url=" + URL + "\n").getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapy.nacti(soubor);
		Assert.assertNull(EKaType.podleJmena("user-a"));
		Assert.assertNotNull(EKaType.podleJmena("user-b"));
	}

	@Test
	public void poskozenySouborSeOhlasi() throws Exception {
		final File soubor = tmp.newFile(UzivatelskeMapy.SOUBOR);
		Files.write(soubor.toPath(), "a.nazev=\\uZZZZ\n".getBytes(StandardCharsets.UTF_8));
		final List<String> chybySouboru = UzivatelskeMapy.nacti(soubor);
		Assert.assertEquals(1, chybySouboru.size());
		Assert.assertTrue(chybySouboru.get(0), chybySouboru.get(0).contains("nelze přečíst"));
	}

	@Test
	public void prikladJePlatny() throws Exception {
		final String priklad = new String(Files.readAllBytes(Paths.get("priklady", UzivatelskeMapy.SOUBOR)), StandardCharsets.UTF_8);
		final List<EKaType> mapy = zpracuj(priklad.replaceAll("(?m)^#([a-z])", "$1"));
		Assert.assertEquals(Collections.emptyList(), chyby);
		Assert.assertEquals(7, mapy.size());
		for (final EKaType m : mapy) {
			Assert.assertFalse(m.name(), m.getAtribuce().isEmpty());
		}
	}

	@Test
	public void prikladPopisujeVsechnyVlastnosti() throws Exception {
		final String priklad = new String(Files.readAllBytes(Paths.get("priklady", UzivatelskeMapy.SOUBOR)), StandardCharsets.UTF_8);
		for (final String vlastnost : new String[] { "nazev", "url", "popis", "min", "max", "maxauto", "klavesa", "zkratka", "atribuce", "hromadne", "hlavicka" }) {
			Assert.assertTrue(vlastnost, priklad.contains("<označení>." + vlastnost));
		}
	}
}
