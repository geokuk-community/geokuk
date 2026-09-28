package cz.geokuk.plugins.mapy.kachle.data;

import java.awt.event.KeyEvent;
import java.util.*;

import javax.swing.KeyStroke;

/**
 * Mapový podklad: vestavěný, nebo uživatelský ze souboru {@value UzivatelskeMapy#SOUBOR}.
 */
public final class EKaType {

	// Mapové podklady, dále neprůhledné
	public static final EKaType BASE_M = new EKaType("BASE_M", false, 0, 19, 19, "Základní", "Základní mapa se silnicemi.", KeyEvent.VK_Z, KeyStroke.getKeyStroke('z'), new MapyCzUrlBuilder("base-m"));
	public static final EKaType TURIST_M = new EKaType("TURIST_M", false, 0, 19, 19, "Turistická", "Turistická mapa.", KeyEvent.VK_T, KeyStroke.getKeyStroke('t'), new MapyCzUrlBuilder("turist-m"));
	public static final EKaType OPHOTO_M = new EKaType("OPHOTO_M", true, 0, 20, 20, "Letecká", "Letecká ortho foto mapa", KeyEvent.VK_L, KeyStroke.getKeyStroke('f'), new MapyCzUrlBuilder("ophoto-m"));
	public static final EKaType WTURIST_WINTER_M = new EKaType("WTURIST_WINTER_M", false, 0, 19, 19, "Turistická zimní", "Zimní turistická mapa.", KeyEvent.VK_M, KeyStroke.getKeyStroke('w'), new MapyCzUrlBuilder("wturist_winter-m"));
	public static final EKaType OPHOTO1415_M = new EKaType("OPHOTO1415_M", true, 0, 20, 20, "Letecká 2015", "Starší fotomapa", 0, null, new MapyCzUrlBuilder("ophoto1415-m"));
	public static final EKaType OPHOTO1012_M = new EKaType("OPHOTO1012_M", true, 0, 19, 19, "Letecká 2012", "Starší fotomapa", 0, null, new MapyCzUrlBuilder("ophoto1012-m"));
	public static final EKaType OPHOTO0406_M = new EKaType("OPHOTO0406_M", true, 0, 19, 19, "Letecká 2006", "Starší fotomapa", KeyEvent.VK_6, null, new MapyCzUrlBuilder("ophoto0406-m"));
	public static final EKaType OPHOTO0203_M = new EKaType("OPHOTO0203_M", true, 0, 18, 18, "Letecká 2003", "Starší fotomapa", KeyEvent.VK_3, null, new MapyCzUrlBuilder("ophoto0203-m"));
	public static final EKaType ZEMEPIS_M = new EKaType("ZEMEPIS_M", false, 0, 18, 18, "Zeměpisná", "Zeměpisná mapa", KeyEvent.VK_G, KeyStroke.getKeyStroke('g'), new MapyCzUrlBuilder("zemepis-m"));
	public static final EKaType BASE_M_TRAF_DOWN = new EKaType("BASE_M_TRAF_DOWN", false, 0, 19, 19, "Dopravní", "Dopravní mapa taková vyšedlá.", 0, null, new MapyCzUrlBuilder("base-m-traf-down"));
	public static final EKaType ARMY2_M = new EKaType("ARMY2_M", true, 0, 15, 15, "Historická", "Historická mapa z let 1836-52", KeyEvent.VK_H, KeyStroke.getKeyStroke('h'), new MapyCzUrlBuilder("army2-m"));

	public static final EKaType OPEN_STREET = new EKaType("OPEN_STREET", false, 0, 19, 19, "Openstreetmap", "Openstreetmap.", KeyEvent.VK_O, KeyStroke.getKeyStroke('o'), new OpenStreatMapUrlBuilder("https://tile.openstreetmap.org/", ".png"));

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

	// Nefunguje, jakási ochrana přes kukačku
	// HIKING_SK_TOPO (true, false, 0, 18, 18, "Slovensko turistická ", "mapy.hiking.sk - topo", 0, null, new OpenStreatMapUrlBuilder("http://mapy.hiking.sk/layers/topo/")),

	// Použit testovací apikey
	public static final EKaType OPEN_CYKLO = new EKaType("OPEN_CYKLO", false, 0, 22, 22, "Open cyclo", "Open cycle map", 0, null, new OpenStreatMapUrlBuilder("https://c.tile.thunderforest.com/cycle/",".png?apikey=6a53e8b25d114a5e9216df5bf9b5e9c8"));


	private static final List<EKaType> VESTAVENE = Collections.unmodifiableList(Arrays.asList(BASE_M, TURIST_M, OPHOTO_M, WTURIST_WINTER_M, OPHOTO1415_M, OPHOTO1012_M, OPHOTO0406_M, OPHOTO0203_M, ZEMEPIS_M, BASE_M_TRAF_DOWN, ARMY2_M, OPEN_STREET, TUR_FREEMAP_SK_T, TUR_FREEMAP_SK_F, OPEN_CYKLO));
	private static volatile List<EKaType> uzivatelske = Collections.emptyList();

	// super("Turistické trasy");
	// putValue(SHORT_DESCRIPTION, "Turistické trasy, červená, modrá, zelená, žlutá.");
	// putValue(MNEMONIC_KEY, KeyEvent.VK_U);
	// putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('u'));

	private final String jmeno;
	private Map<String, String> hlavicky = Collections.emptyMap();
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

	static EKaType uzivatelska(final String id, final String nazev, final String popis, final int minMoumer, final int maxMoumer, final int maxAutoMoumer, final int klavesa,
			final KeyStroke keyStroke, final Map<String, String> hlavicky, final KachleUrlBuilder urlBuilder) {
		final EKaType mapa = new EKaType(UzivatelskeMapy.PREFIX + id, false, minMoumer, maxMoumer, maxAutoMoumer, nazev, popis, klavesa, keyStroke, urlBuilder);
		mapa.hlavicky = Collections.unmodifiableMap(new TreeMap<>(hlavicky));
		return mapa;
	}

	/** Vestavěné podklady a za nimi uživatelské. */
	public static EKaType[] values() {
		final List<EKaType> vse = new ArrayList<>(VESTAVENE);
		vse.addAll(uzivatelske);
		return vse.toArray(new EKaType[vse.size()]);
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

	public KachleUrlBuilder getUrlBuilder() {
		return urlBuilder;
	}

}
