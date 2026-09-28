package cz.geokuk.plugins.mapy.kachle.data;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.swing.KeyStroke;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.program.FConst;

public class UzivatelskeMapyTest {

	private static List<EKaType> zpracuj(final String obsah, final List<String> chyby) throws Exception {
		final Properties p = new Properties();
		p.load(new StringReader(obsah));
		return UzivatelskeMapy.zpracuj(p, chyby);
	}

	@Test
	public void platnaMapa() throws Exception {
		final List<String> chyby = new ArrayList<>();
		final List<EKaType> mapy = zpracuj("topo.nazev=Topo\ntopo.url=https://tile.example.org/{z}/{x}/{y}.png\ntopo.max=17\n", chyby);
		Assert.assertEquals(Collections.emptyList(), chyby);
		Assert.assertEquals(1, mapy.size());
		final EKaType topo = mapy.get(0);
		Assert.assertEquals("user-topo", topo.name());
		Assert.assertEquals("Topo", topo.getNazev());
		Assert.assertTrue(topo.isUzivatelska());
		Assert.assertEquals(0, topo.getMinMoumer());
		Assert.assertEquals(17, topo.getMaxMoumer());
		final KaLoc loc = KaLoc.ofJZ(new Mou(0x40000000, 0x20000000), 13);
		Assert.assertEquals("https://tile.example.org/13/" + loc.getFromSzUnsignedX() + "/" + loc.getFromSzUnsignedY() + ".png", new Ka(loc, topo).getUrl().toString());
	}

	@Test
	public void chybnaMapaSeVynecha() throws Exception {
		final List<String> chyby = new ArrayList<>();
		final List<EKaType> mapy = zpracuj("a.nazev=A\na.url=ftp://x/{z}/{x}/{y}\nb.url=https://x/{z}/{x}/{y}\nc.nazev=C\nc.url=https://x/{z}/{x}/{y}\nc.max=30\nD.nazev=D\ne.barva=modra\n", chyby);
		Assert.assertTrue(mapy.isEmpty());
		Assert.assertEquals(chyby.toString(), 5, chyby.size());
	}

	@Test
	public void vseCoUmiVestavene() throws Exception {
		final List<String> chyby = new ArrayList<>();
		final List<EKaType> mapy = zpracuj("m.nazev=M\nm.url=http://mapserver.mapy.cz/x/{z}-{x}-{y}\nm.min=3\nm.max=19\nm.maxauto=17\nm.klavesa=u\nm.zkratka=ctrl U\n"
				+ "m.hlavicka.Referer=https://mapy.com/\nm.hlavicka.User-Agent=Geokuk/{verze}\n", chyby);
		Assert.assertEquals(Collections.emptyList(), chyby);
		final EKaType m = mapy.get(0);
		Assert.assertEquals(3, m.getMinMoumer());
		Assert.assertEquals(17, m.getMaxAutoMoumer());
		Assert.assertEquals('U', m.getKlavesa());
		Assert.assertEquals(KeyStroke.getKeyStroke("ctrl U"), m.getKeyStroke());
		Assert.assertEquals("https://mapy.com/", m.getHlavicky().get("Referer"));
		Assert.assertEquals("Geokuk/" + FConst.VERSION, m.getHlavicky().get("User-Agent"));
	}

	@Test
	public void zkratkaVestaveneMapySeOdmitne() throws Exception {
		final List<String> chyby = new ArrayList<>();
		Assert.assertTrue(zpracuj("m.nazev=M\nm.url=https://x/{z}/{x}/{y}\nm.zkratka=t\n", chyby).isEmpty());
		Assert.assertTrue(chyby.toString(), chyby.get(0).contains("Turistická"));
	}

	@Test
	public void prikladJePlatny() throws Exception {
		final String priklad = new String(Files.readAllBytes(Paths.get("priklady", UzivatelskeMapy.SOUBOR)), StandardCharsets.UTF_8);
		final List<String> chyby = new ArrayList<>();
		final List<EKaType> mapy = zpracuj(priklad.replaceAll("(?m)^#([a-z])", "$1"), chyby);
		Assert.assertEquals(Collections.emptyList(), chyby);
		Assert.assertEquals(7, mapy.size());
	}

	@Test
	public void vestavenePodkladyNejsouUzivatelske() {
		Assert.assertSame(EKaType.TURIST_M, EKaType.podleJmena("TURIST_M"));
		Assert.assertFalse(EKaType.TURIST_M.isUzivatelska());
		Assert.assertNull(EKaType.podleJmena("neexistuje"));
	}
}
