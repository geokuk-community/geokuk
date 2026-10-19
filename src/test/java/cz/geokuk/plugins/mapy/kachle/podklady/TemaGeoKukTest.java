package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TemaGeoKukTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private BufferedImage vyrendruj(final File slozka, final TemaOfflineMapy tema) throws IOException {
		final OfflineMapy mapy = new OfflineMapy(() -> {});
		try {
			mapy.kontrolaSlozkyNs = 0;
			mapy.nastav(slozka, tema);
			final OfflineRenderer r = mapy.pouzij();
			try {
				return r.vyrendruj(OfflineMapyTest.STRED_Z15);
			} finally {
				r.skonci();
			}
		} finally {
			mapy.zavri();
		}
	}

	/** Bez zvoleného tématu je GeoKuk, uložené původní téma zůstane. */
	@Test
	public void geokukJeVychozi() {
		Assert.assertSame(TemaOfflineMapy.GEOKUK, TemaOfflineMapy.zTextu(""));
		Assert.assertSame(TemaOfflineMapy.GEOKUK, TemaOfflineMapy.zTextu(null));
		Assert.assertSame(TemaOfflineMapy.GEOKUK, TemaOfflineMapy.zTextu(TemaOfflineMapy.GEOKUK.naText()));
		Assert.assertSame(TemaOfflineMapy.VYCHOZI, TemaOfflineMapy.zTextu(TemaOfflineMapy.VYCHOZI.naText()));
		Assert.assertEquals("GeoKuk", TemaOfflineMapy.GEOKUK.getNazev());
		Assert.assertTrue(TemaOfflineMapy.GEOKUK.isKresliVrstvyTras());
		Assert.assertFalse(TemaOfflineMapy.TRASY.isKresliVrstvyTras());
		Assert.assertEquals(TemaOfflineMapy.GEOKUK, TemaOfflineMapy.VESTAVENA.get(0));
		Assert.assertTrue(TemaOfflineMapy.VESTAVENA.contains(TemaOfflineMapy.VYCHOZI));
	}

	/** Uložené přibalené téma, které program nemá, se nahradí tématem GeoKuk. */
	@Test
	public void neznamePribaleneTemaJeGeoKuk() {
		Assert.assertSame(TemaOfflineMapy.GEOKUK, TemaOfflineMapy.zTextu("zdroj:jine.xml"));
		Assert.assertEquals(TemaOfflineMapy.GEOKUK.naText(), TemaOfflineMapy.zTextu("zdroj:jine.xml").naText());
	}

	/** Téma se načte celé (i se symboly z programu) a kreslí jinak než původní. */
	@Test
	public void geokukSeNacteAKresliJinakNezPuvodni() throws Exception {
		final OfflineRenderer.NacteneTema nactene = OfflineRenderer.nactiTema(TemaOfflineMapy.GEOKUK);
		try {
			Assert.assertNull(nactene.chyba, nactene.chyba);
		} finally {
			nactene.uvolni();
		}
		final File slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final BufferedImage turisticka = vyrendruj(slozka, TemaOfflineMapy.GEOKUK);
		final BufferedImage puvodni = vyrendruj(slozka, TemaOfflineMapy.VYCHOZI);
		Assert.assertNotNull(turisticka);
		Assert.assertFalse(java.util.Arrays.equals(turisticka.getRGB(0, 0, 256, 256, null, 0, 256), puvodni.getRGB(0, 0, 256, 256, null, 0, 256)));
	}

	private static String text() throws IOException {
		return text("geokuk/GeoKuk.xml");
	}

	private static String text(final String zdroj) throws IOException {
		try (java.io.InputStream in = TemaOfflineMapy.class.getResourceAsStream(zdroj)) {
			return new String(com.google.common.io.ByteStreams.toByteArray(in), java.nio.charset.StandardCharsets.UTF_8);
		}
	}

	/** Odvozené téma nese původ a licenci; hlavička se nesmí při úpravách ztratit. */
	@Test
	public void temaMaHlavickuSPuvodemALicenci() throws IOException {
		final String zacatek = text().substring(0, text().indexOf("<rendertheme"));
		Assert.assertTrue(zacatek, zacatek.contains("mapsforge-themes 0.25.0"));
		Assert.assertTrue(zacatek, zacatek.contains("LGPL"));
		Assert.assertTrue(zacatek, zacatek.contains("mapsforge.org"));
		Assert.assertTrue(zacatek, zacatek.contains("Upraveno pro GeoKuk"));
	}

	/** Každý symbol a vzor, na který téma odkazuje, existuje v knihovně témat nebo vedle tématu; mapsforge chybějící jen zaloguje. */
	@Test
	public void vsechnySymbolyTemaExistuji() throws IOException {
		Assert.assertTrue(symbolyTematu("geokuk/GeoKuk.xml", "geokuk/") > 50);
		Assert.assertTrue(symbolyTematu("trasy.xml", "") > 0);
	}

	/** Ověří symboly tématu a vrátí, kolik jich téma má. */
	private static int symbolyTematu(final String zdroj, final String slozka) throws IOException {
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"(jar|file):([^\"]+)\"").matcher(text(zdroj));
		int pocet = 0;
		while (m.find()) {
			pocet++;
			final String cesta = m.group(1).equals("jar") ? "/assets/" + m.group(2) : slozka + m.group(2);
			Assert.assertNotNull(zdroj + ": chybí symbol " + m.group(0), TemaOfflineMapy.class.getResource(cesta));
		}
		return pocet;
	}

	/** Symboly file: se čtou z programu vedle tématu, ne ze složky, odkud se program spustil. */
	@Test
	public void symbolyVedleTematuSeCtouZProgramu() throws IOException {
		final org.mapsforge.map.rendertheme.XmlThemeResourceProvider zdroje = TemaOfflineMapy.GEOKUK.vytvor(new TemaOfflineMapy.VyberVrstev(VolbaVrstev.AUTOR))
				.getResourceProvider();
		try (java.io.InputStream in = zdroje.createInputStream("", "file:symbols/cliff.svg")) {
			Assert.assertNotNull(in);
		}
		Assert.assertNull(zdroje.createInputStream("", "file:symbols/neni.svg"));
		Assert.assertNull("jar: hledá mapsforge sám", zdroje.createInputStream("", "jar:symbols/peak.svg"));
		try (java.io.InputStream in = TemaOfflineMapy.TRASY.vytvor(new TemaOfflineMapy.VyberVrstev(VolbaVrstev.AUTOR)).getResourceProvider().createInputStream("",
				"file:geokuk/symbols/cliff.svg")) {
			Assert.assertNotNull(in);
		}
	}

	/** Překryv kreslí i skály a body z vrstvy tourist; jméno trasy z pole ref jen u turistických tras (ref je jinak třeba číslo silnice). */
	@Test
	public void prekryvKresliPrvkyVrstvyTouristAJmenaTras() throws IOException {
		final String xml = text("trasy.xml");
		Assert.assertTrue(xml.contains("k=\"natural\" v=\"cliff\""));
		Assert.assertTrue(xml.contains("k=\"information\" v=\"guidepost\""));
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<rule e=\"way\" k=\"([^\"]+)\"[^>]*>\\s*<pathText k=\"ref\"").matcher(xml);
		Assert.assertTrue(m.find());
		for (final String k : m.group(1).split("\\|")) {
			Assert.assertTrue(k, k.startsWith("fmrelhiking"));
		}
		Assert.assertFalse("ref jen v jednom pravidle", m.find());
		Assert.assertEquals(1, xml.split("k=\"ref\"", -1).length - 1);
	}

	/** Klíč cache dlaždic vestavěného tématu vychází z obsahu souboru, takže nová verze tématu dlaždice vykreslí znovu. */
	@Test
	public void otiskVestavenehoTematuJeZObsahu() throws IOException {
		final java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Assert.assertEquals(TemaOfflineMapy.GEOKUK.naText() + ':' + Long.toHexString(crc.getValue()), TemaOfflineMapy.GEOKUK.otiskSouboru());
	}

	/** Názvy vodních toků se vykreslí i při kolizi s názvy ulic. */
	@Test
	public void nazvyVodnichTokuSeVzdyKresli() throws IOException {
		final String xml = text();
		Assert.assertTrue(xml.contains("<pathText display=\"always\" fill=\"#4F7FB8\" font-size=\"11\""));
		Assert.assertTrue(xml.contains("<pathText display=\"always\" fill=\"#4F7FB8\" font-size=\"10\""));
	}
}
