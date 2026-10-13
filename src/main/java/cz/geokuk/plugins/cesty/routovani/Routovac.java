package cz.geokuk.plugins.cesty.routovani;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.util.MercatorProjection;

import cz.geokuk.core.coordinates.Wgs;
import lombok.extern.slf4j.Slf4j;

/**
 * Hledá pěší trasu po cestách z offline map (.map). Graf staví z obdélníku kolem startu a cíle; když v něm trasa není, obdélník zvětší. Start a cíl se přichytí
 * na nejbližší cestu a úsek k ní je součástí trasy.
 */
@Slf4j
public class Routovac implements AutoCloseable {

	/** Okraj obdélníku jako podíl delší strany obdélníku start–cíl. */
	private static final double OKRAJ = 0.3;
	/** Nejmenší okraj ve stupních. */
	private static final double MIN_OKRAJ = 0.005;
	private static final int POKUSU = 3;
	/** Dál od sebe než tolik metrů se po cestách nehledá. */
	static final double MAX_VZDALENOST = 30_000;
	/** O kolik metrů dál než nejbližší síť cest smí být síť, na kterou se bod přichytí. */
	static final double PRICHYCENI_NAVIC = 50;
	/** O kolik metrů dál než nejbližší cesta vůbec smí být síť, na kterou se bod přichytí; delší úsek trasy rovně už není „po cestě“. */
	static final double PRICHYCENI_STROP = 200;
	/** Totéž jako podíl vzdušné vzdálenosti startu a cíle, platí větší z obou. */
	static final double PRICHYCENI_STROP_PODIL = 0.25;
	/** Kolik bodů cest drží cache dlaždic jedné mapy. */
	private static final long MAX_BODU_CACHE = 3_000_000;

	private final Supplier<List<File>> zdrojMap;

	private String otiskMap;
	private List<MapaCest> mapy = Collections.emptyList();

	/** Hledání drží zámek routovače i sekundy; zadání práce na pozadí z EDT na něj nesmí čekat. */
	private final Object zamekVlakna = new Object();
	private ExecutorService vlakno;

	/**
	 * @param zdrojMap
	 *            soubory .map, ze kterých se hledá; volá se při každém hledání, změna seznamu nebo souborů mapy znovu otevře
	 */
	public Routovac(final Supplier<List<File>> zdrojMap) {
		this.zdrojMap = zdrojMap;
	}

	/** Routovač nad všemi soubory .map ve složce. */
	public static Routovac zeSlozky(final Supplier<File> slozka) {
		return new Routovac(() -> mapyVeSlozce(slozka.get()));
	}

	static List<File> mapyVeSlozce(final File slozka) {
		final File[] soubory = slozka == null ? null : slozka.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".map"));
		if (soubory == null) {
			return Collections.emptyList();
		}
		final List<File> vysledek = new ArrayList<>(Arrays.asList(soubory));
		vysledek.sort(Comparator.comparing(File::getName));
		return vysledek;
	}

	/**
	 * Najde trasu na pozadí v jednom vlákně routovače. Zrušení budoucnosti s přerušením ukončí hledání se stavem {@link Trasa.Stav#ZRUSENO}.
	 */
	public Future<Trasa> najdiNaPozadi(final Wgs start, final Wgs cil) {
		synchronized (zamekVlakna) {
			if (vlakno == null) {
				vlakno = Executors.newSingleThreadExecutor(r -> {
					final Thread t = new Thread(r, "Routování");
					t.setDaemon(true);
					t.setPriority(Thread.NORM_PRIORITY - 1);
					return t;
				});
			}
			return vlakno.submit(() -> najdi(start, cil, () -> Thread.currentThread().isInterrupted()));
		}
	}

	/**
	 * Najde trasu. Nevolat z EDT, čte z disku a může trvat i sekundy.
	 *
	 * @param zruseno
	 *            průběžně se ptá, jestli hledání nezrušit
	 */
	public synchronized Trasa najdi(final Wgs start, final Wgs cil, final BooleanSupplier zruseno) {
		final long t0 = System.nanoTime();
		try {
			final Trasa t = hledej(start, cil, zruseno);
			log.debug("Trasa {} → {}: {} za {} ms", start, cil, t, (System.nanoTime() - t0) / 1_000_000);
			return t;
		} catch (final CancellationException e) {
			return Trasa.bez(Trasa.Stav.ZRUSENO);
		}
	}

	private Trasa hledej(final Wgs start, final Wgs cil, final BooleanSupplier zruseno) {
		zkontroluj(zruseno);
		final List<MapaCest> m = aktualniMapy();
		if (!pokryva(m, start) || !pokryva(m, cil)) {
			return Trasa.bez(Trasa.Stav.BEZ_MAPY);
		}
		if (start.vzdalenost(cil) > MAX_VZDALENOST) {
			return Trasa.bez(Trasa.Stav.PRILIS_DALEKO);
		}
		if (start.equals(cil)) {
			return Trasa.nalezena(Arrays.asList(start, cil));
		}
		double okraj = Math.max(OKRAJ * Math.max(Math.abs(start.lat - cil.lat), Math.abs(start.lon - cil.lon)), MIN_OKRAJ);
		for (int pokus = 0; pokus < POKUSU; pokus++, okraj *= 2) {
			final BoundingBox bb = new BoundingBox(Math.max(-85, Math.min(start.lat, cil.lat) - okraj), Math.max(-180, Math.min(start.lon, cil.lon) - okraj),
					Math.min(85, Math.max(start.lat, cil.lat) + okraj), Math.min(180, Math.max(start.lon, cil.lon) + okraj));
			final GrafCest graf = postavGraf(m, bb, zruseno);
			final List<Wgs> body = hledejVGrafu(graf, start, cil, zruseno);
			if (body != null) {
				return Trasa.nalezena(body);
			}
		}
		return Trasa.bez(Trasa.Stav.NENALEZENO);
	}

	private static boolean pokryva(final List<MapaCest> mapy, final Wgs w) {
		for (final MapaCest mc : mapy) {
			if (mc.pokryva(w.lat, w.lon)) {
				return true;
			}
		}
		return false;
	}

	static GrafCest postavGraf(final List<MapaCest> mapy, final BoundingBox bb, final BooleanSupplier zruseno) {
		final GrafCest graf = new GrafCest(mikro(bb.minLatitude), mikro(bb.minLongitude), mikro(bb.maxLatitude), mikro(bb.maxLongitude));
		for (final MapaCest mc : mapy) {
			if (!mc.prekryva(bb)) {
				continue;
			}
			final byte z = mc.getZoom();
			final int x1 = MercatorProjection.longitudeToTileX(bb.minLongitude, z);
			final int x2 = MercatorProjection.longitudeToTileX(bb.maxLongitude, z);
			final int y1 = MercatorProjection.latitudeToTileY(bb.maxLatitude, z);
			final int y2 = MercatorProjection.latitudeToTileY(bb.minLatitude, z);
			for (int x = x1; x <= x2; x++) {
				for (int y = y1; y <= y2; y++) {
					zkontroluj(zruseno);
					final MapaCest.Dlazdice d = mc.dlazdice(x, y);
					for (int i = 0; i < d.cary.length; i++) {
						graf.pridejCaru(d.cary[i], d.koeficienty[i]);
					}
				}
			}
		}
		graf.dokonci();
		return graf;
	}

	/** Trasa v grafu, nebo null, když start a cíl nejsou u téže souvislé sítě cest. */
	private static List<Wgs> hledejVGrafu(final GrafCest graf, final Wgs start, final Wgs cil, final BooleanSupplier zruseno) {
		final GrafCest.Prichyceni[] ps = graf.nejblizsiVKomponentach(mikro(start.lat), mikro(start.lon));
		final GrafCest.Prichyceni[] pc = graf.nejblizsiVKomponentach(mikro(cil.lat), mikro(cil.lon));
		final double vzdusne = start.vzdalenost(cil);
		final double mezStart = mezPrichyceni(graf, ps, vzdusne);
		final double mezCil = mezPrichyceni(graf, pc, vzdusne);
		int nejlepsi = -1;
		for (int k = 0; k < ps.length; k++) {
			if (ps[k] != null && pc[k] != null && ps[k].vzdalenost <= mezStart && pc[k].vzdalenost <= mezCil
					&& (nejlepsi < 0 || ps[k].vzdalenost + pc[k].vzdalenost < ps[nejlepsi].vzdalenost + pc[nejlepsi].vzdalenost)) {
				nejlepsi = k;
			}
		}
		if (nejlepsi < 0) {
			return null;
		}
		final int[] cesta = graf.najdiCestu(ps[nejlepsi], pc[nejlepsi], zruseno);
		if (cesta == null) {
			return null;
		}
		final List<Wgs> body = new ArrayList<>(cesta.length / 2 + 2);
		body.add(start);
		for (int i = 0; i < cesta.length; i += 2) {
			final Wgs w = new Wgs(cesta[i] / 1e6, cesta[i + 1] / 1e6);
			if (!w.equals(body.get(body.size() - 1))) {
				body.add(w);
			}
		}
		if (!cil.equals(body.get(body.size() - 1))) {
			body.add(cil);
		}
		return body;
	}

	/**
	 * Jak daleko smí být síť, na kterou se bod přichytí. Nejvýš kousek za nejbližší sítí, která vede ven z obdélníku; jinak by se bod u konce cesty uříznuté
	 * okrajem obdélníku přichytil rovnou čarou na jinou síť. Uzavřené sítě (ostrůvky) nikam nevedou, proto se podle nich mez neurčuje, ale bod na ostrůvku se
	 * nepřichytí na síť, která je mnohem dál.
	 */
	private static double mezPrichyceni(final GrafCest graf, final GrafCest.Prichyceni[] p, final double vzdusne) {
		double otevrena = Double.POSITIVE_INFINITY;
		double kterakoli = Double.POSITIVE_INFINITY;
		for (int k = 0; k < p.length; k++) {
			if (p[k] != null) {
				kterakoli = Math.min(kterakoli, p[k].vzdalenost);
				if (graf.isOtevrena(k)) {
					otevrena = Math.min(otevrena, p[k].vzdalenost);
				}
			}
		}
		return Math.min(otevrena + PRICHYCENI_NAVIC, kterakoli + Math.max(PRICHYCENI_STROP, vzdusne * PRICHYCENI_STROP_PODIL));
	}

	private static int mikro(final double stupne) {
		return (int) Math.round(stupne * 1e6);
	}

	private static void zkontroluj(final BooleanSupplier zruseno) {
		if (zruseno.getAsBoolean()) {
			throw new CancellationException();
		}
	}

	private List<MapaCest> aktualniMapy() {
		final List<File> soubory = zdrojMap.get();
		final StringBuilder sb = new StringBuilder();
		for (final File f : soubory) {
			sb.append(f.getAbsolutePath()).append(':').append(f.length()).append(':').append(f.lastModified()).append('\n');
		}
		final String otisk = sb.toString();
		if (!otisk.equals(otiskMap)) {
			zavriMapy();
			otiskMap = otisk;
			final List<MapaCest> nove = new ArrayList<>();
			for (final File f : soubory) {
				try {
					final MapaCest mc = MapaCest.otevri(f, MAX_BODU_CACHE);
					if (mc != null) {
						nove.add(mc);
					}
				} catch (final RuntimeException e) {
					// Rozbitá nebo právě kopírovaná mapa nezastaví hledání v ostatních.
					log.warn("Offline mapu {} nejde otevřít pro hledání cest: {}", f, e.toString());
				}
			}
			mapy = nove;
		}
		return mapy;
	}

	private void zavriMapy() {
		for (final MapaCest mc : mapy) {
			mc.close();
		}
		mapy = Collections.emptyList();
	}

	@Override
	public void close() {
		final ExecutorService v;
		synchronized (zamekVlakna) {
			v = vlakno;
			vlakno = null;
		}
		if (v != null) {
			v.shutdownNow();
		}
		synchronized (this) {
			zavriMapy();
			otiskMap = null;
		}
	}
}
