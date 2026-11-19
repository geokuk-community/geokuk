package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

/** Identita varianty dlaždic je stejná pro jinou verzi téhož tématu a jiná pro jiné vrstvy, velikost, písmo, předponu i zdroj tématu. */
public class OfflineRendererIdentitaTest {

	private static final String HLAVICKA = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<rendertheme xmlns=\"http://mapsforge.org\" version=\"5\" map-background=\"#ffffff\">\n";
	private static final String MENU = "<stylemenu id=\"menu\" defaultlang=\"en\" defaultvalue=\"a\">\n<layer id=\"a\" visible=\"true\"><name lang=\"en\" value=\"A\"/><cat id=\"x\"/></layer>\n"
			+ "<layer id=\"b\" visible=\"true\"><name lang=\"en\" value=\"B\"/><cat id=\"y\"/></layer>\n</stylemenu>\n";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File tema(final String nazev, final String pravidlo) throws Exception {
		final File f = new File(tmp.getRoot(), nazev);
		Files.write(f.toPath(), (HLAVICKA + MENU + pravidlo + "</rendertheme>\n").getBytes(StandardCharsets.UTF_8));
		return f;
	}

	private static OfflineRenderer.NacteneTema nacti(final TemaOfflineMapy t, final double meritko, final double pismo) throws Exception {
		final OfflineRenderer.NacteneTema n = OfflineRenderer.nactiTema(t, null, meritko, pismo);
		Assert.assertNull("téma se musí načíst, ne nahradit výchozím: " + n.chyba, n.chyba);
		return n;
	}

	@Test
	public void jinaVerzeSouboruTemaMaStejnouIdentituAleJinyOtisk() throws Exception {
		final File f = tema("moje.xml", "<rule e=\"way\" k=\"*\" v=\"*\" cat=\"x\"><line stroke=\"#000000\" stroke-width=\"1.0\"/></rule>\n");
		final OfflineRenderer.NacteneTema pred = nacti(TemaOfflineMapy.zeSouboru(f, null), 1, 1);
		Files.write(f.toPath(), (HLAVICKA + MENU + "<rule e=\"way\" k=\"*\" v=\"*\" cat=\"x\"><line stroke=\"#ff0000\" stroke-width=\"2.0\"/></rule>\n</rendertheme>\n").getBytes(StandardCharsets.UTF_8));
		Assert.assertTrue(f.setLastModified(f.lastModified() + 5000));
		final OfflineRenderer.NacteneTema po = nacti(TemaOfflineMapy.zeSouboru(f, null), 1, 1);
		Assert.assertNotEquals("změněný soubor má jiný otisk obsahu", pred.otiskPouziteho, po.otiskPouziteho);
		Assert.assertEquals(OfflineRenderer.identita(pred, false), OfflineRenderer.identita(po, false));
		Assert.assertEquals(pred.identita, po.identita);
	}

	@Test
	public void jinyZdrojVrstvyVelikostPismoAPredponaMajiJinouIdentitu() throws Exception {
		final File f = tema("moje.xml", "<rule e=\"way\" k=\"*\" v=\"*\" cat=\"x\"><line stroke=\"#000000\" stroke-width=\"1.0\"/></rule>\n");
		final File jine = tema("jine.xml", "<rule e=\"way\" k=\"*\" v=\"*\" cat=\"x\"><line stroke=\"#000000\" stroke-width=\"1.0\"/></rule>\n");
		final OfflineRenderer.NacteneTema zaklad = nacti(TemaOfflineMapy.zeSouboru(f, null), 1, 1);
		final String id = OfflineRenderer.identita(zaklad, false);
		Assert.assertEquals("stejné nastavení", id, OfflineRenderer.identita(nacti(TemaOfflineMapy.zeSouboru(f, null), 1, 1), false));
		Assert.assertNotEquals("překryv", id, OfflineRenderer.identita(zaklad, true));
		Assert.assertNotEquals("velikost dlaždice", id, OfflineRenderer.identita(nacti(TemaOfflineMapy.zeSouboru(f, null), 1.5, 1), false));
		Assert.assertNotEquals("písmo", id, OfflineRenderer.identita(nacti(TemaOfflineMapy.zeSouboru(f, null), 1, 1.25), false));
		Assert.assertNotEquals("jiný soubor tématu", id, OfflineRenderer.identita(nacti(TemaOfflineMapy.zeSouboru(jine, null), 1, 1), false));
		final TemaOfflineMapy sVrstvou = TemaOfflineMapy.zeSouboru(f, null).sVrstvami(VolbaVrstev.AUTOR.seStylem("b", "a"));
		final OfflineRenderer.NacteneTema jinyStyl = nacti(sVrstvou, 1, 1);
		Assert.assertNotEquals("jiný styl vrstev", id, OfflineRenderer.identita(jinyStyl, false));
		Assert.assertTrue("identita je osm šestnáctkových číslic", id.matches("[0-9a-f]{8}"));
	}

	@Test
	public void identitaSeNesePresOblastniKlic() throws Exception {
		final OfflineRenderer.NacteneTema vychozi = OfflineRenderer.nactiTema(TemaOfflineMapy.VYCHOZI, null, 1, 1);
		Assert.assertEquals(OfflineRenderer.identita(vychozi, false), OfflineRenderer.identita(OfflineRenderer.nactiTema(TemaOfflineMapy.VYCHOZI, null, 1, 1), false));
		final OblastniKlic klic = new OblastniKlic("o00000001", java.util.Collections.emptyList(), null, OfflineRenderer.identita(vychozi, false));
		Assert.assertEquals(OfflineRenderer.identita(vychozi, false), klic.getIdentita());
		Assert.assertNull(OblastniKlic.bezSouboru("o00000001").getIdentita());
	}
}
