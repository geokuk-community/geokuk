package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TemaTuristickaTest {

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

	/** Bez zvoleného tématu je Turistická, uložené původní téma zůstane. */
	@Test
	public void turistickaJeVychozi() {
		Assert.assertSame(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.zTextu(""));
		Assert.assertSame(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.zTextu(null));
		Assert.assertSame(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.zTextu(TemaOfflineMapy.TURISTICKA.naText()));
		Assert.assertSame(TemaOfflineMapy.VYCHOZI, TemaOfflineMapy.zTextu(TemaOfflineMapy.VYCHOZI.naText()));
		Assert.assertEquals("Turistická", TemaOfflineMapy.TURISTICKA.getNazev());
		Assert.assertEquals(TemaOfflineMapy.TURISTICKA, TemaOfflineMapy.VESTAVENA.get(0));
		Assert.assertTrue(TemaOfflineMapy.VESTAVENA.contains(TemaOfflineMapy.VYCHOZI));
	}

	/** Téma se načte celé (i se symboly z programu) a kreslí jinak než původní. */
	@Test
	public void turistickaSeNactePokresliJinakNezPuvodni() throws Exception {
		final OfflineRenderer.NacteneTema nactene = OfflineRenderer.nactiTema(TemaOfflineMapy.TURISTICKA);
		try {
			Assert.assertNull(nactene.chyba, nactene.chyba);
		} finally {
			nactene.uvolni();
		}
		final File slozka = tmp.newFolder("offline-mapy");
		OfflineMapyTest.zkopirujMapu(slozka, "kukov.map");
		final BufferedImage turisticka = vyrendruj(slozka, TemaOfflineMapy.TURISTICKA);
		final BufferedImage puvodni = vyrendruj(slozka, TemaOfflineMapy.VYCHOZI);
		Assert.assertNotNull(turisticka);
		Assert.assertFalse(java.util.Arrays.equals(turisticka.getRGB(0, 0, 256, 256, null, 0, 256), puvodni.getRGB(0, 0, 256, 256, null, 0, 256)));
	}

	private static String text() throws IOException {
		try (java.io.InputStream in = TemaOfflineMapy.class.getResourceAsStream("turisticka.xml")) {
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

	/** Každý symbol a vzor, na který téma odkazuje, existuje v knihovně témat; mapsforge chybějící jen zaloguje. */
	@Test
	public void vsechnySymbolyTemaExistuji() throws IOException {
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("src=\"jar:([^\"]+)\"").matcher(text());
		int pocet = 0;
		while (m.find()) {
			pocet++;
			Assert.assertNotNull("chybí symbol " + m.group(1), TemaOfflineMapy.class.getResource("/assets/" + m.group(1)));
		}
		Assert.assertTrue(pocet > 50);
	}

	/** Názvy vodních toků se vykreslí i při kolizi s názvy ulic. */
	@Test
	public void nazvyVodnichTokuSeVzdyKresli() throws IOException {
		final String xml = text();
		Assert.assertTrue(xml.contains("<pathText display=\"always\" fill=\"#4F7FB8\" font-size=\"11\""));
		Assert.assertTrue(xml.contains("<pathText display=\"always\" fill=\"#4F7FB8\" font-size=\"10\""));
	}
}
