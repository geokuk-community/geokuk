package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.zip.CRC32;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.core.model.Tile;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.datastore.MultiMapDataStore;
import org.mapsforge.map.layer.hills.DemFolderFS;
import org.mapsforge.map.layer.hills.DiffuseLightShadingAlgorithm;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.hills.MemoryCachingHgtReaderTileSource;
import org.mapsforge.map.layer.renderer.*;
import org.mapsforge.map.model.DisplayModel;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.XmlRenderThemeStyleMenu;
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture;

import cz.geokuk.plugins.mapy.kachle.data.KaLoc;
import lombok.extern.slf4j.Slf4j;

/**
 * Vykresluje dlaždice z otevřených souborů .map jedním tématem. Je bezpečný pro více vláken. Zavře se, až doběhnou rozdělaná vykreslení.
 */
@Slf4j
final class OfflineRenderer {

	/** Velikost dlaždice v logických bodech obrazovky; na displeji s měřítkem se kreslí víc pixelů. */
	static final int VELIKOST_DLAZDICE = 256;


	/**
	 * Načtené (rozparsované) téma. Načtení velkého tématu trvá sekundy, proto se sdílí mezi renderery: každý drží jeden odkaz a při zavření ho vrátí.
	 */
	static final class NacteneTema {
		final RenderThemeFuture future;
		final DisplayModel displayModel;
		final GraphicFactory grafika;
		/** Měřítko displeje, pro které je téma načtené (velikost dlaždice, symboly a písmo). */
		final double meritko;
		/** Násobek velikosti písma a značek. */
		final double pismo;
		/** Otisk tématu, ze kterého je načteno (soubor a jeho velikost a čas). */
		final String otiskPozadovaneho;
		/** Otisk skutečně použitého tématu a jeho vrstev pro klíč cache. */
		final String otiskPouziteho;
		/** Proč se nepoužilo zvolené téma, null když se použilo. */
		final String chyba;
		/** Nabídka vrstev použitého tématu, null když ji nemá. */
		final XmlRenderThemeStyleMenu menu;
		final long nacitaniMs;

		private NacteneTema(final RenderThemeFuture future, final DisplayModel displayModel, final GraphicFactory grafika, final double meritko, final double pismo, final String otiskPozadovaneho, final String otiskPouziteho, final String chyba,
				final XmlRenderThemeStyleMenu menu, final long start) {
			this.future = future;
			this.displayModel = displayModel;
			this.grafika = grafika;
			this.meritko = meritko;
			this.pismo = pismo;
			this.otiskPozadovaneho = otiskPozadovaneho;
			this.otiskPouziteho = otiskPouziteho;
			this.chyba = chyba;
			this.menu = menu;
			nacitaniMs = (System.nanoTime() - start) / 1_000_000;
		}

		/** Vrátí odkaz; s posledním odkazem se téma uvolní. Symboly vykreslené za běhu se uloží. */
		void uvolni() {
			if (grafika instanceof GrafikaOfflineMapy) {
				((GrafikaOfflineMapy) grafika).uloz();
			}
			future.decrementRefCount();
		}
	}

	/**
	 * Verze knihovny mapsforge a vykreslování GeoKuku v klíči cache: jiná verze kreslí jinak, dlaždice se pak vykreslí znovu. Při změně mapsforge v pom.xml
	 * změnit i tady (hlídá test), při změně vykreslování v GeoKuku zvýšit číslo za lomítkem.
	 */
	static final String VERZE_VYKRESLOVANI = "mapsforge 0.25.0/1";

	static final char PREDPONA_KLICE = 'o';
	static final char PREDPONA_KLICE_PREKRYVU = 't';

	/** Zoom, od kterého je mapa podrobná; menší maximum má jen přehledová mapa. */
	static final int PODROBNY_ZOOM = 14;

	private final MultiMapDataStore data;
	/** Rozsah a nejvyšší zoom dat každé otevřené mapy. */
	private final List<MapFileInfo> pokryti;
	private final NacteneTema tema;
	private final DatabaseRenderer renderer;
	private final String klic;
	/** Překryv přes jiný podklad: dlaždice s průhledným pozadím. */
	private final boolean prekryv;
	private String preskoceno;
	/** Stínování terénu z DEM dlaždic (HGT), null bez nich. */
	private final HillsRenderConfig kopce;
	/** Základní mapy (ne vrstvy): jen ony určují, kam se dlaždice kreslí. */
	private final List<MapFile> zakladni;
	private final int pocetVrstev;

	private int rozdelanych;
	private boolean zavrit;
	private boolean zavreny;

	private OfflineRenderer(final MultiMapDataStore data, final List<MapFileInfo> pokryti, final NacteneTema tema, final String klic, final boolean prekryv, final HillsRenderConfig kopce,
			final List<MapFile> zakladni, final int pocetVrstev) {
		this.data = data;
		this.pokryti = pokryti;
		this.tema = tema;
		this.klic = klic;
		this.prekryv = prekryv;
		this.kopce = kopce;
		this.zakladni = zakladni;
		this.pocetVrstev = pocetVrstev;
		tema.future.incrementRefCount();
		renderer = new DatabaseRenderer(data, tema.grafika, null, new PopiskyOfflineMapy(data, tema.future, tema.displayModel, tema.grafika), true, false, kopce);
	}

	/** Otevře mapy a načte téma; když téma načíst nejde, použije výchozí. Volající drží jeden odkaz na výsledek. */
	static OfflineRenderer otevri(final List<File> mapy, final TemaOfflineMapy tema) throws IOException {
		final NacteneTema nactene = nactiTema(tema);
		try {
			return otevri(mapy, nactene);
		} finally {
			nactene.uvolni();
		}
	}

	/**
	 * Otevře mapy s už načteným tématem.
	 *
	 * @throws IOException
	 *             když nejde otevřít žádný soubor mapy
	 */
	static OfflineRenderer otevri(final List<File> mapy, final NacteneTema tema) throws IOException {
		return otevri(mapy, tema, false);
	}

	/**
	 * @param prekryv
	 *            průhledné dlaždice pro překryv přes jiný podklad; mají v cache vlastní klíče
	 */
	static OfflineRenderer otevri(final List<File> mapy, final NacteneTema tema, final boolean prekryv) throws IOException {
		return otevri(mapy, Collections.emptyList(), null, tema, prekryv);
	}

	/**
	 * @param vrstvy
	 *            soubory .map s vrstvami (trasy, vrstevnice), které se kreslí jedním průchodem spolu se základními mapami a téma je kreslí podle svých pravidel;
	 *            nepočítají se do pokrytí podrobnou mapou
	 * @param slozkaDem
	 *            složka s DEM dlaždicemi (HGT, případně zazipované) pro stínování terénu, null = bez stínování
	 */
	static OfflineRenderer otevri(final List<File> mapy, final List<File> vrstvy, final File slozkaDem, final NacteneTema tema, final boolean prekryv) throws IOException {
		if (mapy.isEmpty()) {
			throw new IOException("Žádný soubor mapy.");
		}
		final MultiMapDataStore data = new MultiMapDataStore(MultiMapDataStore.DataPolicy.DEDUPLICATE);
		final StringBuilder otisk = new StringBuilder();
		final List<MapFileInfo> pokryti = new ArrayList<>();
		final List<MapFile> zakladni = new ArrayList<>();
		int vrstev = 0;
		OfflineMapaChyba prvniChyba = null;
		try {
			for (final File f : mapy) {
				final MapFile mapFile;
				try {
					mapFile = new MapFile(f);
				} catch (final RuntimeException e) {
					// Mapa, která se třeba právě kopíruje, nezastaví kreslení ostatních.
					log.debug("Soubor mapy {} nejde otevřít, kreslí se bez něj: {}", f, e.getMessage());
					if (prvniChyba == null) {
						prvniChyba = new OfflineMapaChyba("Soubor mapy " + f + " nejde otevřít: " + e.getMessage(), f.getName() + " nejde otevřít", e);
					}
					continue;
				}
				data.addMapDataStore(mapFile, false, false);
				zakladni.add(mapFile);
				pokryti.add(mapFile.getMapFileInfo());
				otisk.append(f.getAbsolutePath()).append(':').append(f.length()).append(':').append(f.lastModified()).append(':').append(mapFile.getMapFileInfo().mapDate).append('\n');
			}
			if (otisk.length() == 0) {
				throw prvniChyba;
			}
			for (final File f : vrstvy) {
				try {
					data.addMapDataStore(new MapFile(f), false, false);
					vrstev++;
					otisk.append("vrstva:").append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n');
				} catch (final RuntimeException e) {
					log.debug("Vrstvu {} nejde otevřít, kreslí se bez ní: {}", f, e.getMessage());
				}
			}
		} catch (final IOException | RuntimeException e) {
			data.close();
			throw e;
		}
		otisk.append(tema.otiskPouziteho).append('\n').append(tema.displayModel.getTileSize()).append('\n').append(VERZE_VYKRESLOVANI);
		if (tema.pismo != 1) {
			otisk.append('\n').append(tema.pismo);
		}
		final HillsRenderConfig kopce = prekryv ? null : kopce(slozkaDem, tema.grafika, otisk);
		final OfflineRenderer r = new OfflineRenderer(data, Collections.unmodifiableList(pokryti), tema, prekryv ? klicPrekryvu(otisk.toString()) : klic(otisk.toString()), prekryv, kopce, zakladni, vrstev);
		r.preskoceno = prvniChyba == null ? null : prvniChyba.getMessage();
		return r;
	}

	/** DEM dlaždice ve složce: soubory .hgt nebo .zip. */
	static List<File> demDlazdice(final File slozka) {
		final File[] soubory = slozka == null ? null : slozka.listFiles(f -> f.isFile() && (f.getName().toLowerCase(Locale.ROOT).endsWith(".hgt") || f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")));
		if (soubory == null) {
			return Collections.emptyList();
		}
		final List<File> dlazdice = new ArrayList<>(Arrays.asList(soubory));
		dlazdice.sort(Comparator.comparing(File::getName));
		return dlazdice;
	}

	/** Stínování terénu z DEM dlaždic ve složce; bez nich nebo při chybě null (mapa se kreslí bez stínování). Přidá do otisku, ze kterých souborů stínuje. */
	private static HillsRenderConfig kopce(final File slozkaDem, final GraphicFactory grafika, final StringBuilder otisk) {
		final List<File> dlazdice = demDlazdice(slozkaDem);
		if (dlazdice.isEmpty()) {
			return null;
		}
		try {
			final HillsRenderConfig kopce = new HillsRenderConfig(new MemoryCachingHgtReaderTileSource(new DemFolderFS(slozkaDem), new DiffuseLightShadingAlgorithm(), grafika)).indexOnThread();
			for (final File f : dlazdice) {
				otisk.append("dem:").append(f.getName()).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n');
			}
			return kopce;
		} catch (final RuntimeException e) {
			log.warn("Stínování terénu z {} nejde připravit, kreslí se bez něj: {}", slozkaDem, e.toString());
			return null;
		}
	}

	/** Proč se některá mapa ze složky neotevřela (první z nich), null když se otevřely všechny. */
	String getPreskoceno() {
		return preskoceno;
	}

	static NacteneTema nactiTema(final TemaOfflineMapy tema) throws IOException {
		return nactiTema(tema, null, 1);
	}

	/**
	 * Načte téma; když ho načíst nejde, načte výchozí. Volající drží jeden odkaz, viz {@link NacteneTema#uvolni()}.
	 *
	 * @param slozkaSymbolu
	 *            kam se ukládají vykreslené symboly tématu, null = nikam
	 * @param meritko
	 *            měřítko displeje: dlaždice má 256 × měřítko pixelů a symboly i písmo jsou úměrně větší
	 */
	static NacteneTema nactiTema(final TemaOfflineMapy tema, final File slozkaSymbolu, final double meritko) throws IOException {
		return nactiTema(tema, slozkaSymbolu, meritko, 1);
	}

	/**
	 * @param pismo
	 *            násobek velikosti písma a značek vůči měřítku displeje
	 */
	static NacteneTema nactiTema(final TemaOfflineMapy tema, final File slozkaSymbolu, final double meritko, final double pismo) throws IOException {
		final DisplayModel displayModel = new DisplayModel();
		displayModel.setUserScaleFactor((float) (meritko * pismo));
		displayModel.setFixedTileSize(velikostDlazdice(meritko));
		final long start = System.nanoTime();
		try {
			final GrafikaOfflineMapy grafika = new GrafikaOfflineMapy(souborSymbolu(slozkaSymbolu, tema));
			final TemaOfflineMapy.VyberVrstev vrstvy = new TemaOfflineMapy.VyberVrstev(tema.getVrstvy());
			final RenderThemeFuture future = nactiTema(tema, displayModel, grafika, vrstvy);
			final NacteneTema nactene = new NacteneTema(future, displayModel, grafika, meritko, pismo, tema.otisk(), otiskSVrstvami(tema, vrstvy), null, vrstvy.getMenu(), start);
			grafika.uloz();
			log.info("Téma offline mapy {} načteno za {} ms", tema, nactene.nacitaniMs);
			return nactene;
		} catch (final IOException e) {
			log.warn("Téma offline mapy {} nejde použít, kreslí se výchozím: {}", tema, e.getMessage());
			final GrafikaOfflineMapy grafika = new GrafikaOfflineMapy(souborSymbolu(slozkaSymbolu, TemaOfflineMapy.VYCHOZI));
			final TemaOfflineMapy.VyberVrstev vrstvy = new TemaOfflineMapy.VyberVrstev(VolbaVrstev.AUTOR);
			final RenderThemeFuture future = nactiTema(TemaOfflineMapy.VYCHOZI, displayModel, grafika, vrstvy);
			return new NacteneTema(future, displayModel, grafika, meritko, pismo, tema.otisk(), otiskSVrstvami(TemaOfflineMapy.VYCHOZI, vrstvy), e.getMessage(), null, start);
		}
	}

	private static String otiskSVrstvami(final TemaOfflineMapy tema, final TemaOfflineMapy.VyberVrstev vrstvy) {
		return vrstvy.getPopis() == null ? tema.otiskSouboru() : tema.otiskSouboru() + "\nvrstvy:" + vrstvy.getPopis();
	}

	/** Soubor se symboly tématu; jiný soubor tématu (i jiná verze téhož) má jiný soubor symbolů. */
	static File souborSymbolu(final File slozka, final TemaOfflineMapy tema) {
		return slozka == null ? null : new File(slozka, "symboly-" + klic(tema.otiskSouboru()).substring(1) + ".bin");
	}

	private static RenderThemeFuture nactiTema(final TemaOfflineMapy tema, final DisplayModel displayModel, final GraphicFactory grafika, final TemaOfflineMapy.VyberVrstev vrstvy) throws IOException {
		final RenderThemeFuture rtf = new RenderThemeFuture(grafika, tema.vytvor(vrstvy), displayModel);
		rtf.run();
		try {
			rtf.get();
		} catch (final ExecutionException e) {
			final Throwable pricina = e.getCause() != null && e.getCause().getCause() != null ? e.getCause().getCause() : e.getCause();
			throw new IOException("Téma " + tema + " nejde načíst: " + pricina, pricina);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Načítání tématu přerušeno");
		}
		return rtf;
	}

	/** Klíč dlaždic v cache: písmeno o a 8 šestnáctkových číslic, vejde se do sloupce typu. */
	static String klic(final String otisk) {
		return klic(PREDPONA_KLICE, otisk);
	}

	/** Klíč dlaždic překryvu: jiné písmeno, úklid cache podkladu ho nemaže. */
	static String klicPrekryvu(final String otisk) {
		return klic(PREDPONA_KLICE_PREKRYVU, otisk);
	}

	private static String klic(final char predpona, final String otisk) {
		final CRC32 crc = new CRC32();
		crc.update(otisk.getBytes(StandardCharsets.UTF_8));
		return String.format("%c%08x", predpona, crc.getValue());
	}

	/** Počet map, které se podařilo otevřít. */
	int getPocetMap() {
		return pokryti.size();
	}

	String getKlic() {
		return klic;
	}

	NacteneTema getTema() {
		return tema;
	}

	/** Soubor se symboly použitého tématu, null = symboly se neukládají. */
	File getSouborSymbolu() {
		return tema.grafika instanceof GrafikaOfflineMapy ? ((GrafikaOfflineMapy) tema.grafika).getSoubor() : null;
	}

	String getChybaTematu() {
		return tema.chyba;
	}

	static int velikostDlazdice(final double meritko) {
		return (int) Math.round(VELIKOST_DLAZDICE * meritko);
	}

	static Tile dlazdice(final KaLoc loc) {
		return dlazdice(loc, VELIKOST_DLAZDICE);
	}

	/** Dlaždice mapsforge; stejné x, y, z pokrývají stejné území při každé velikosti v pixelech. */
	static Tile dlazdice(final KaLoc loc, final int velikost) {
		return new Tile(loc.getFromSzUnsignedX(), loc.getFromSzUnsignedY(), (byte) loc.getMoumer(), velikost);
	}

	/** Bod pokrývá mapa s daty pro tento zoom; mapsforge nad svým maximem jen zvětšuje, proto stačí maximum od podrobného zoomu. */
	boolean jePodrobnaMapa(final double lat, final double lon, final int zoom) {
		for (final MapFileInfo info : pokryti) {
			if (info.zoomLevelMax >= Math.min(zoom, PODROBNY_ZOOM) && info.boundingBox.contains(lat, lon)) {
				return true;
			}
		}
		return false;
	}

	/** Dlaždice zasahuje do některé z map. */
	boolean pokryva(final KaLoc loc) {
		final Tile t = dlazdice(loc);
		for (final MapFile m : zakladni) {
			if (m.supportsTile(t)) {
				return true;
			}
		}
		return false;
	}

	/** Kolik vrstev (trasy, vrstevnice) se kreslí spolu se základními mapami. */
	int getPocetVrstev() {
		return pocetVrstev;
	}

	/** Začátek vykreslování; false, když už je renderer zavřený. */
	synchronized boolean zacni() {
		if (zavrit) {
			return false;
		}
		rozdelanych++;
		return true;
	}

	synchronized void skonci() {
		rozdelanych--;
		if (zavrit && rozdelanych == 0) {
			zavriTed();
		}
	}

	/** Zavře mapy, jakmile doběhnou rozdělaná vykreslení. */
	synchronized void zavri() {
		zavrit = true;
		if (rozdelanych == 0) {
			zavriTed();
		}
	}

	synchronized boolean isZavreny() {
		return zavreny;
	}

	private void zavriTed() {
		if (zavreny) {
			return;
		}
		zavreny = true;
		tema.uvolni();
		data.close();
		if (kopce != null) {
			kopce.interruptAndDestroy();
		}
	}

	/** Vykreslí dlaždici; volat mezi {@link #zacni()} a {@link #skonci()}. Do PNG pro cache se převádí až při ukládání. */
	BufferedImage vyrendruj(final KaLoc loc) throws IOException {
		final RendererJob job = new RendererJob(dlazdice(loc, tema.displayModel.getTileSize()), data, tema.future, tema.displayModel, 1f, prekryv, false);
		final TileBitmap bitmapa = renderer.executeJob(job);
		if (bitmapa == null) {
			throw new IOException("Dlaždici " + loc + " offline mapy nejde vykreslit.");
		}
		return AwtGraphicFactory.getBitmap(bitmapa);
	}
}
