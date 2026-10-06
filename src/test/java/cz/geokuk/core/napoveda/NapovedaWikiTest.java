package cz.geokuk.core.napoveda;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class NapovedaWikiTest {

	private static final String WIKI = "https://github.com/geokuk-community/geokuk/wiki";

	/** Témata, která vracejí dialogy z getTemaNapovedyDialogu. */
	private static final List<String> TEMATA_DIALOGU = Arrays.asList("ErrorList", "Service", "UmisteniSouboru", "Render", "ZdrojeKesoidu", "InformaceOSobe", "FiltrKesoidu", "VyberFenotypu",
			"DebugIkon", "HledatVKesoidech", "JintNaSouradnice", "HledatAdresu", "StahovaniMapovychDlazdic", "PopiskyKesoidu", "ZvyraznovaciKruhy");

	@Test
	public void bezTematuJeUvodniStranka() {
		Assert.assertEquals(WIKI, NapovedaWiki.url(null).toString());
		Assert.assertEquals("neznámé téma", WIKI, NapovedaWiki.url("Dialog/Neexistuje").toString());
	}

	@Test
	public void kazdeTemaDialoguMaStrankuWiki() {
		for (final String tema : TEMATA_DIALOGU) {
			final URL url = NapovedaWiki.url("Dialog/" + tema);
			Assert.assertTrue(tema + " → " + url, url.toString().startsWith(WIKI + "/"));
			Assert.assertEquals(url, NapovedaWiki.url(tema));
		}
	}

	@Test
	public void adresaJeVeTvaruProProhlizec() {
		Assert.assertEquals(WIKI + "/Filtry-a-zobrazen%C3%AD-ke%C5%A1%C3%AD#filtr", NapovedaWiki.url("Dialog/FiltrKesoidu").toString());
		Assert.assertEquals(WIKI + "/Hl%C3%A1%C5%A1en%C3%AD-probl%C3%A9m%C5%AF#p%C5%99ehled-probl%C3%A9m%C5%AF", NapovedaWiki.url("Dialog/ErrorList").toString());
		Assert.assertEquals("stránka bez kotvy", WIKI + "/Tisk-a-rendrov%C3%A1n%C3%AD-map", NapovedaWiki.url("Dialog/Render").toString());
	}
}
