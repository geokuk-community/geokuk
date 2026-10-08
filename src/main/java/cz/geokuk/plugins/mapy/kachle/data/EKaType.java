package cz.geokuk.plugins.mapy.kachle.data;

import java.awt.event.KeyEvent;
import java.util.*;

import javax.swing.KeyStroke;

import cz.geokuk.core.coordinates.Mou;

/**
 * Mapový podklad: vestavěný, nebo uživatelský ze souboru ve složce {@value UzivatelskeMapy#SLOZKA}.
 */
public final class EKaType {

	// Mapové podklady, dále neprůhledné
	public static final EKaType BASE_M = new EKaType("BASE_M", false, 0, 19, 19, "Základní", "Základní mapa se silnicemi.", KeyEvent.VK_Z, KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0), new MapyCzUrlBuilder("base-m"));
	public static final EKaType TURIST_M = new EKaType("TURIST_M", false, 0, 19, 19, "Turistická", "Turistická mapa.", KeyEvent.VK_T, KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), new MapyCzUrlBuilder("turist-m"));
	public static final EKaType OPHOTO_M = new EKaType("OPHOTO_M", true, 0, 20, 20, "Letecká", "Letecká ortho foto mapa", KeyEvent.VK_L, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), new MapyCzUrlBuilder("ophoto-m"));
	public static final EKaType WTURIST_WINTER_M = new EKaType("WTURIST_WINTER_M", false, 0, 19, 19, "Turistická zimní", "Zimní turistická mapa.", KeyEvent.VK_M, KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), new MapyCzUrlBuilder("wturist_winter-m"));
	public static final EKaType OPHOTO1415_M = new EKaType("OPHOTO1415_M", true, 0, 20, 20, "Letecká 2015", "Starší fotomapa", 0, null, new MapyCzUrlBuilder("ophoto1415-m"));
	public static final EKaType OPHOTO1012_M = new EKaType("OPHOTO1012_M", true, 0, 19, 19, "Letecká 2012", "Starší fotomapa", 0, null, new MapyCzUrlBuilder("ophoto1012-m"));
	public static final EKaType OPHOTO0406_M = new EKaType("OPHOTO0406_M", true, 0, 19, 19, "Letecká 2006", "Starší fotomapa", KeyEvent.VK_6, null, new MapyCzUrlBuilder("ophoto0406-m"));
	public static final EKaType OPHOTO0203_M = new EKaType("OPHOTO0203_M", true, 0, 18, 18, "Letecká 2003", "Starší fotomapa", KeyEvent.VK_3, null, new MapyCzUrlBuilder("ophoto0203-m"));
	public static final EKaType ZEMEPIS_M = new EKaType("ZEMEPIS_M", false, 0, 18, 18, "Zeměpisná", "Zeměpisná mapa", KeyEvent.VK_G, KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), new MapyCzUrlBuilder("zemepis-m"));
	public static final EKaType BASE_M_TRAF_DOWN = new EKaType("BASE_M_TRAF_DOWN", false, 0, 19, 19, "Dopravní", "Dopravní mapa taková vyšedlá.", 0, null, new MapyCzUrlBuilder("base-m-traf-down"));
	public static final EKaType ARMY2_M = new EKaType("ARMY2_M", true, 0, 15, 15, "Historická", "Historická mapa z let 1836-52", KeyEvent.VK_H, KeyStroke.getKeyStroke(KeyEvent.VK_H, 0), new MapyCzUrlBuilder("army2-m"));

	public static final EKaType OPEN_STREET = new EKaType("OPEN_STREET", false, 0, 19, 19, "OpenStreetMap", "OpenStreetMap.", KeyEvent.VK_O, KeyStroke.getKeyStroke(KeyEvent.VK_O, 0), new OpenStreatMapUrlBuilder("https://tile.openstreetmap.org/", ".png"));

// Nefunkční mapy k 16.11.2019
//	OPEN_STREAT(false, 0, 18, 18, "Openstreetmap", "Openstreetmap.", KeyEvent.VK_O, KeyStroke.getKeyStroke('o'), new OpenStreatMapUrlBuilder("https://b.tile.openstreetmap.org/")),
//    // http://otile{switch:1,2,3,4}.mqcdn.com/tiles/1.0.0/osm/{zoom}/{x}/{y}.png
//	MAPBOX(false, 0, 18, 18, "Map box", "Open streat map box.", KeyEvent.VK_O, KeyStroke.getKeyStroke('o'), new OpenStreatMapUrlBuilder("http://otile1.mqcdn.com/tiles/1.0.0/osm/")),
	public static final EKaType TUR_FREEMAP_SK_T = new EKaType("TUR_FREEMAP_SK_T", false, 0, 18, 18, "Slovensko turistická", "Freemap Slovakia - turistická mapa", 0, null, new OpenStreatMapUrlBuilder("https://outdoor.tiles.freemap.sk/", ".png"));
	public static final EKaType TUR_FREEMAP_SK_F = new EKaType("TUR_FREEMAP_SK_F", false, 0, 18, 18, "Slovensko ortofoto", "Freemap Slovakia - letecká mapa", 0, null, new OpenStreatMapUrlBuilder("https://ortofoto.tiles.freemap.sk/", ".jpg"));
//	TUR_FREEMAP_SK_A(false, 0, 18, 18, "Slovensko automapa", "turistika.freemap.sk - automapa", 0, null, new OpenStreatMapUrlBuilder("http://c.freemap.sk/A/")),
//	TUR_FREEMAP_SK_C(false, 0, 18, 18, "Slovensko cyklomapa", "turistika.freemap.sk - cyklomapa", 0, null, new OpenStreatMapUrlBuilder("http://c.freemap.sk/C/")),
//	TUR_FREEMAP_SK_K(false, 0, 18, 18, "Slovensko lyžařská  ", "turistika.freemap.sk - lyžařská mapa", 0, null, new OpenStreatMapUrlBuilder("http://c.freemap.sk/K/")),
//

	public static final EKaType CUZK_ORTO = omezeneUzemi(new EKaType("CUZK_ORTO", true, 6, 20, 20, "ČR ortofoto (ČÚZK)", "Ortofoto České republiky, otevřená data ČÚZK", 0, null,
			new UzivatelskyUrlBuilder("https://ags.cuzk.gov.cz/arcgis1/rest/services/ORTOFOTO_WM/MapServer/tile/{z}/{y}/{x}")));
	public static final EKaType CUZK_ZTM = omezeneUzemi(new EKaType("CUZK_ZTM", false, 6, 19, 19, "ČR Základní topografická mapa (ČÚZK)", "Základní topografická mapa České republiky, otevřená data ČÚZK",
			0, null, new UzivatelskyUrlBuilder("https://ags.cuzk.gov.cz/arcgis1/rest/services/ZTM_WM/MapServer/tile/{z}/{y}/{x}")));
	// Mimo rozsah služby (fullExtent v EPSG:3857) vrací server 503; uvnitř je 503 výpadek nebo přetížení, tedy chyba.
	public static final EKaType SK_ZBGIS_ORTO = omezeneUzemi(new EKaType("SK_ZBGIS_ORTO", true, 7, 19, 19, "SR ortofoto (ZBGIS)", "Ortofoto Slovenska, ZBGIS, GKÚ Bratislava", 0, null,
			new UzivatelskyUrlBuilder("https://zbgis.skgeodesy.sk/zbgis/rest/services/Ortofoto/MapServer/tile/{z}/{y}/{x}")), new double[] { 1_860_379, 5_965_455, 2_523_588, 6_483_011 }, 404, 503);

	// Nefunguje, jakási ochrana přes kukačku
	// HIKING_SK_TOPO (true, false, 0, 18, 18, "Slovensko turistická ", "mapy.hiking.sk - topo", 0, null, new OpenStreatMapUrlBuilder("http://mapy.hiking.sk/layers/topo/")),

	/**
	 * Servery, jejichž provozovatel hromadné (automatické) stahování dlaždic zakazuje nebo důrazně nedoporučuje: OpenStreetMap (Tile Usage Policy), Mapy.cz
	 * (podmínky Seznam.cz), Waymarked Trails. Platí i pro jejich subdomény a pro uživatelské mapy.
	 */
	static final List<String> SERVERY_BEZ_HROMADNEHO_STAHOVANI = Collections.unmodifiableList(Arrays.asList("tile.openstreetmap.org", "mapy.cz", "mapy.com", "tile.waymarkedtrails.org"));

	private static final List<EKaType> VESTAVENE = Collections.unmodifiableList(Arrays.asList(BASE_M, TURIST_M, OPHOTO_M, WTURIST_WINTER_M, OPHOTO1415_M, OPHOTO1012_M, OPHOTO0406_M, OPHOTO0203_M, ZEMEPIS_M, BASE_M_TRAF_DOWN, ARMY2_M, OPEN_STREET, TUR_FREEMAP_SK_T, TUR_FREEMAP_SK_F, CUZK_ORTO, CUZK_ZTM, SK_ZBGIS_ORTO));
	private static volatile List<EKaType> uzivatelske = Collections.emptyList();

	private final String jmeno;
	private Map<String, String> hlavicky = Collections.emptyMap();
	private String atribuce = "";
	private boolean hromadne;
	/** Kódy HTTP, kterými server odpovídá mimo území podkladu; prázdné u podkladů pro celý svět. */
	private Set<Integer> kodyMimoUzemi = Collections.emptySet();
	/** Rozsah dat v EPSG:3857 (minX, minY, maxX, maxY); kódy mimo území platí jen pro dlaždice mimo něj. Null = kódy platí všude. */
	private double[] rozsah;
	private final int minMoumer;
	private final int maxMoumer;
	private final int maxAutoMoumer;
	private final String nazev;
	private final String popis;
	private final int klavesa;
	private final KeyStroke keyStroke;
	private final KachleUrlBuilder urlBuilder;

	/**
	 * @param podklad
	 *            true, pokud se jedná o neprůhledný podlad, false jinak.
	 * @param jeMozneNavrsitTexty
	 *            zde je možné ještě aplikovat samostatnou vrstvu s texty. Zadejte true, pokud mapa texty neobsahuje (napříkald fotomapy).
	 * @param minMoumer
	 *            Minimáklní měřítko, ke kterému jsou podklady. Věřme, že to bude dne světšinou 0. (U starých seznamových map to bylo 3 nebo 4).
	 * @param maxMoumer
	 *            Maximální měřítko, pro který jsou kachle.
	 * @param maxAutoMoumer
	 *            už přesně nevím, nutno analyzovat, nastavujem to stejnějako maxMoumer
	 * @param nazev
	 *            Název mapy, tak se objeví v menu.
	 * @param popis
	 *            Bližší popis mapy, objeví se jako tooltip v menu.
	 * @param klavesa
	 *            Hot-key, která mapu vyvolá.
	 * @param keyStroke
	 *            Písmeno z nazev, které lze použít pro výběr při rozbaleném menu.
	 * @param urlBuilder
	 *            Implementace třídy, která sestaví URL pro zobrazení mapy.
	 */
	private EKaType(final String jmeno, final boolean jeMozneNavrsitTexty, final int minMoumer, final int maxMoumer, final int maxAutoMoumer, final String nazev, final String popis, final int klavesa,
			final KeyStroke keyStroke, final KachleUrlBuilder urlBuilder) {
		this.jmeno = jmeno;
		this.minMoumer = minMoumer;
		this.maxMoumer = maxMoumer;
		this.maxAutoMoumer = maxAutoMoumer;
		this.nazev = nazev;
		this.popis = popis;
		this.klavesa = klavesa;
		this.keyStroke = keyStroke;
		this.urlBuilder = urlBuilder;

	}

	static EKaType omezeneUzemi(final EKaType mapa) {
		return omezeneUzemi(mapa, 404);
	}

	static EKaType omezeneUzemi(final EKaType mapa, final Integer... kody) {
		return omezeneUzemi(mapa, null, kody);
	}

	static EKaType omezeneUzemi(final EKaType mapa, final double[] rozsah, final Integer... kody) {
		mapa.kodyMimoUzemi = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(kody)));
		mapa.rozsah = rozsah;
		return mapa;
	}

	static EKaType uzivatelska(final String id, final String nazev, final String popis, final int minMoumer, final int maxMoumer, final int maxAutoMoumer, final int klavesa,
			final KeyStroke keyStroke, final Map<String, String> hlavicky, final String atribuce, final boolean hromadne, final KachleUrlBuilder urlBuilder) {
		final EKaType mapa = new EKaType(UzivatelskeMapy.PREFIX + id, false, minMoumer, maxMoumer, maxAutoMoumer, nazev, popis, klavesa, keyStroke, urlBuilder);
		mapa.hlavicky = Collections.unmodifiableMap(new TreeMap<>(hlavicky));
		mapa.atribuce = atribuce;
		mapa.hromadne = hromadne;
		return mapa;
	}

	/** Vestavěné podklady a za nimi uživatelské. */
	public static EKaType[] values() {
		final List<EKaType> vse = new ArrayList<>(VESTAVENE);
		vse.addAll(uzivatelske);
		return vse.toArray(new EKaType[vse.size()]);
	}

	static List<EKaType> vestavene() {
		return VESTAVENE;
	}

	/** Podklad podle jména, null když takový není. */
	public static EKaType podleJmena(final String jmeno) {
		for (final EKaType ka : values()) {
			if (ka.jmeno.equals(jmeno)) {
				return ka;
			}
		}
		return null;
	}

	static void setUzivatelske(final List<EKaType> mapy) {
		uzivatelske = Collections.unmodifiableList(new ArrayList<>(mapy));
	}

	public String name() {
		return jmeno;
	}

	/** Hlavičky požadavku navíc, jen u uživatelských map. */
	public Map<String, String> getHlavicky() {
		return hlavicky;
	}

	public boolean isUzivatelska() {
		return jmeno.startsWith(UzivatelskeMapy.PREFIX);
	}

	@Override
	public String toString() {
		return jmeno;
	}

	public int fitMoumer(int moumer) {
		if (moumer < minMoumer) {
			moumer = minMoumer;
		}
		if (moumer > maxMoumer) {
			moumer = maxMoumer;
		}
		return moumer;
	}

	public KeyStroke getKeyStroke() {
		return keyStroke;
	}

	public int getKlavesa() {
		return klavesa;
	}

	public int getMaxAutoMoumer() {
		return maxAutoMoumer;
	}

	public int getMaxMoumer() {
		return maxMoumer;
	}

	public int getMinMoumer() {
		return minMoumer;
	}

	public String getNazev() {
		return nazev;
	}

	public String getPopis() {
		return popis;
	}

	/** Atribuce zobrazená vpravo dole v mapě. */
	public String getAtribuce() {
		if (isUzivatelska()) {
			return atribuce;
		}
		if (urlBuilder instanceof MapyCzUrlBuilder) {
			return "© Seznam.cz, a.s. a další";
		}
		if (this == TUR_FREEMAP_SK_T) {
			return "© Freemap Slovakia, © OpenStreetMap contributors";
		}
		if (this == TUR_FREEMAP_SK_F) {
			return "© GKÚ, NLC, © ČÚZK";
		}
		if (this == CUZK_ORTO || this == CUZK_ZTM) {
			return "© ČÚZK, CC BY 4.0";
		}
		if (this == SK_ZBGIS_ORTO) {
			return "© GKÚ Bratislava, NLC, CC BY 4.0";
		}
		return "© OpenStreetMap contributors";
	}

	/** Z vestavěných jde hromadně stahovat jen Freemap, uživatelské mapy jen když to uživatel zapne a server to nezakazuje. */
	public boolean isHromadneStahovaniPovoleno() {
		if (jeServerBezHromadnehoStahovani(hostitel())) {
			return false;
		}
		return isUzivatelska() ? hromadne : this != OPEN_STREET && this != CUZK_ORTO && this != CUZK_ZTM && this != SK_ZBGIS_ORTO && !(urlBuilder instanceof MapyCzUrlBuilder);
	}

	static boolean jeServerBezHromadnehoStahovani(final String hostitel) {
		// Koncová tečka plně kvalifikovaného jména adresu nemění.
		final String h = hostitel.toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
		for (final String server : SERVERY_BEZ_HROMADNEHO_STAHOVANI) {
			if (h.equals(server) || h.endsWith("." + server)) {
				return true;
			}
		}
		return false;
	}

	/** Hostitel adresy dlaždic, prázdný když ji nejde sestavit. */
	private String hostitel() {
		try {
			return urlBuilder.buildUrl(new Ka(KaLoc.ofJZ(new Mou(0, 0), 0), this)).getHost();
		} catch (final java.net.MalformedURLException e) {
			return "";
		}
	}

	/** Podklad pokrývá jen část světa; mimo ni je dlaždice prázdná, ne chybná. */
	public boolean isOmezeneUzemi() {
		return !kodyMimoUzemi.isEmpty();
	}

	/** Odpověď serveru na dlaždici znamená, že je mimo území podkladu. */
	public boolean jeMimoUzemi(final int kodHttp, final KaLoc loc) {
		if (!kodyMimoUzemi.contains(kodHttp)) {
			return false;
		}
		if (rozsah == null) {
			return true;
		}
		final double svet = 20_037_508.342789244;
		final double velikost = 2 * svet / (1L << loc.getMoumer());
		final double minX = -svet + loc.getFromSzUnsignedX() * velikost;
		final double maxY = svet - loc.getFromSzUnsignedY() * velikost;
		return minX + velikost <= rozsah[0] || minX >= rozsah[2] || maxY <= rozsah[1] || maxY - velikost >= rozsah[3];
	}

	public KachleUrlBuilder getUrlBuilder() {
		return urlBuilder;
	}

}
