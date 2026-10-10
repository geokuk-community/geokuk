package cz.geokuk.core.napoveda;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JButton;

import cz.geokuk.core.program.FConst;
import cz.geokuk.util.process.BrowserOpener;

/** Téma nápovědy dialogu → stránka (a kotva) uživatelské wiki na GitHubu. */
public final class NapovedaWiki {

	private static final String PREDPONA_TEMATU = "Dialog/";
	private static final Map<String, String[]> STRANKY = new HashMap<>();

	static {
		stranka("ErrorList", "Hlášení-problémů", "přehled-problémů");
		stranka("Service", "Hlášení-problémů", null);
		stranka("UmisteniSouboru", "Nastavení-a-umístění-souborů", "soubor--umístění-souborů");
		stranka("Render", "Tisk-a-rendrování-map", null);
		stranka("ZdrojeKesoidu", "Načtení-keší", "přehled-zdrojů");
		stranka("InformaceOSobe", "Načtení-keší", "moje-keše");
		stranka("FiltrKesoidu", "Filtry-a-zobrazení-keší", "filtr");
		stranka("VyberFenotypu", "Filtry-a-zobrazení-keší", null);
		stranka("DebugIkon", "Filtry-a-zobrazení-keší", "sady-ikon");
		stranka("PopiskyKesoidu", "Filtry-a-zobrazení-keší", "popisky");
		stranka("ZvyraznovaciKruhy", "Filtry-a-zobrazení-keší", "kruhy");
		stranka("HledatVKesoidech", "Hledání", "hledání-keše");
		stranka("HledatAdresu", "Hledání", "hledání-adresy");
		stranka("JintNaSouradnice", "Hledání", "zadání-souřadnic");
		stranka("StahovaniMapovychDlazdic", "Mapové-podklady-a-cache", "hromadné-stažení-map-na-výlet");
		stranka("LimityKresleni", "Filtry-a-zobrazení-keší", "ikony-nebo-tečky");
		stranka("PametProgramu", "Časté-potíže", "načtení-keší-je-pomalé-nebo-programu-dochází-paměť");
		stranka("ZobrazeniProgramu", "Časté-potíže", "program-je-pomalý-nebo-mapa-a-menu-sekají-windows");
	}

	private NapovedaWiki() {}

	private static void stranka(final String tema, final String stranka, final String kotva) {
		STRANKY.put(tema, new String[] { stranka, kotva });
	}

	/** Tlačítko do dialogu, které otevře stránku wiki k tématu a dialog nezavře. */
	public static JButton tlacitko(final String tema) {
		final JButton b = new JButton("Nápověda");
		b.addActionListener(e -> BrowserOpener.displayURL(url(tema)));
		return b;
	}

	/** Adresa stránky wiki pro téma, bez tématu nebo pro neznámé téma úvodní stránka. */
	public static URL url(final String tema) {
		final String klic = tema != null && tema.startsWith(PREDPONA_TEMATU) ? tema.substring(PREDPONA_TEMATU.length()) : tema;
		final String[] stranka = klic == null ? null : STRANKY.get(klic);
		try {
			final URI zaklad = new URI(FConst.WEB_PAGE_WIKI);
			if (stranka == null) {
				return zaklad.toURL();
			}
			return new URL(new URI(zaklad.getScheme(), zaklad.getHost(), zaklad.getPath() + "/" + stranka[0], stranka[1]).toASCIIString());
		} catch (final URISyntaxException | MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}
}
