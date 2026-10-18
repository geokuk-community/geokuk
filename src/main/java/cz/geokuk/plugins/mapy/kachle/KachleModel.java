/**
 *
 */
package cz.geokuk.plugins.mapy.kachle;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.core.program.FConst;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.framework.Model0;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.kachle.podklady.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 *
 */
@Slf4j
public class KachleModel extends Model0 {

	private static final String OFFLINE_MAPY_DIR = "offlineMapyDir";

	private static final String OFFLINE_MAPA_TEMA = "offlineMapaTema";
	/** Výběr vrstev každého tématu zvlášť; klíč je otisk jména tématu, jméno samo může být delší, než dovolí klíč nastavení. */
	private static final String OFFLINE_MAPA_VRSTVY_node = "offlineMapaVrstvy";

	private static final String OFFLINE_OSTRA = "offlineMapaOstra";
	private static final String OSM = "© přispěvatelé OpenStreetMap";
	private static final String NATURAL_EARTH = "Made with Natural Earth";

	private static final String OFFLINE_PISMO = "offlineMapaPismo";

	private static final String PREKRYV_TRAS = "prekryvTras";
	private static final String RELIEF_CUZK = "reliefCuzk";
	private static final String OBRYS_POKRYTI = "obrysPokrytiOfflineMap";
	private static final String STINOVANI_TERENU = "stinovaniTerenu";

	/** Otevírá offline mapu a načítá téma předem, mimo EDT. */
	private final ExecutorService priprava = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "Příprava offline mapy");
		t.setDaemon(true);
		return t;
	});

	/** Složka offline map se hlídá i bez kreslení, aby se dokopírovaná mapa ukázala bez pohybu mapou. */
	private final ScheduledExecutorService hlidaniSlozky = Executors.newSingleThreadScheduledExecutor(r -> {
		final Thread t = new Thread(r, "Hlídání složky offline map");
		t.setDaemon(true);
		return t;
	});

	private volatile boolean offlinePodklad;

	private final KachleCacheFolderHolder kachleCacheFolderHolder = new KachleCacheFolderHolder();

	private Object umisteniSouboru;

	private KachleZiskavac ziskavac;

	public final KachleManager kachleManager;

	public KachleModel() {
		kachleManager = KachleManagerFactory.getInstance(getKachleCacheFolderHolder());
	}

	/**
	 * @return the kachleCacheFolderHolder
	 */
	public KachleCacheFolderHolder getKachleCacheFolderHolder() {
		return kachleCacheFolderHolder;
	}

	public KachleZiskavac getZiskavac() {
		return ziskavac;
	}

	public void inject(final KachleZiskavac kachleZiskavac) {
		ziskavac = kachleZiskavac;
		ziskavac.setKachleManager(kachleManager);
	}

	/**
	 * @return the ukladatMapyNaDisk
	 */
	public boolean isUkladatMapyNaDisk() {
		// return Settings.vseobecne.ukladatMapyNaDisk.isSelected();
		final boolean b = currPrefe().getBoolean("ukladatMapyNaDisk", true);
		return b;
	}

	public void onEvent(final ZmenaMapNastalaEvent event) {
		offlinePodklad = event.getKatype() != null && event.getKatype().isOffline();
		nastavTrasyPodkladu();
		predpripravOfflineMapu();
		fajrujPrekryvTras();
	}

	private void predpripravOfflineMapu() {
		if (offlinePodklad && ziskavac != null) {
			priprava.execute(ziskavac.getOfflineMapy()::predpriprav);
		}
		if (isPrekryvTrasViditelny() && ziskavac != null) {
			priprava.execute(ziskavac.getOfflineTrasy()::predpriprav);
		}
	}

	/** Uživatel zapnul překryv Turistické a cyklotrasy. */
	public boolean isPrekryvTras() {
		return currPrefe().getBoolean(PREKRYV_TRAS, false);
	}

	public void setPrekryvTras(final boolean zapnuto) {
		if (zapnuto == isPrekryvTras()) {
			return;
		}
		currPrefe().putBoolean(PREKRYV_TRAS, zapnuto);
		predpripravOfflineMapu();
		fajrujPrekryvTras();
	}

	/** Podklad je offline mapa s tématem, které turistické a cyklotrasy kreslí samo. */
	public boolean isPrekryvTrasNadbytecny() {
		return offlinePodklad && getTemaOfflineMapy().isPaws();
	}

	/** Překryv tras se kreslí: je zapnutý a nad podkladem není nadbytečný. */
	public boolean isPrekryvTrasViditelny() {
		return isPrekryvTras() && !isPrekryvTrasNadbytecny();
	}

	/** Uživatel zapnul překryv Stínovaný reliéf ČÚZK. */
	public boolean isReliefCuzk() {
		return currPrefe().getBoolean(RELIEF_CUZK, false);
	}

	public void setReliefCuzk(final boolean zapnuto) {
		if (zapnuto == isReliefCuzk()) {
			return;
		}
		currPrefe().putBoolean(RELIEF_CUZK, zapnuto);
		fire(new ReliefCuzkEvent(zapnuto));
	}

	/** Stínování terénu z DEM dlaždic ve složce vrstev je zapnuté (výchozí); bez DEM dlaždic nemá žádný účinek. */
	public boolean isStinovaniTerenu() {
		return currPrefe().getBoolean(STINOVANI_TERENU, true);
	}

	public void setStinovaniTerenu(final boolean zapnuto) {
		if (zapnuto == isStinovaniTerenu()) {
			return;
		}
		currPrefe().putBoolean(STINOVANI_TERENU, zapnuto);
		nastavOfflineMapy(getUmisteniSouboru());
		fire(new OfflineMapaChangedEvent());
	}

	/** V mapě se kreslí obrys území, které pokrývají offline mapy ve složce. */
	public boolean isObrysPokryti() {
		return currPrefe().getBoolean(OBRYS_POKRYTI, false);
	}

	public void setObrysPokryti(final boolean zapnuto) {
		if (zapnuto == isObrysPokryti()) {
			return;
		}
		currPrefe().putBoolean(OBRYS_POKRYTI, zapnuto);
		fire(new ObrysPokrytiEvent(zapnuto));
	}

	/** Obrys mapy, nad jejímž řádkem v popupu je myš; neukládá se. */
	public void setZvyrazneniMapy(final String klic) {
		fire(new ZvyrazneniMapyEvent(klic));
	}

	private void fajrujPrekryvTras() {
		fire(new PrekryvTrasEvent(isPrekryvTras(), isPrekryvTrasNadbytecny()));
	}

	public void onEvent(final OnofflineModelChangeEvent eve) {
		if (eve.isOnlineMOde()) {
			ziskavac.clearMemoryCache();
		}
	}

	/**
	 * @param ukladatMapyNaDisk
	 *            the ukladatMapyNaDisk to set
	 */
	public void setUkladatMapyNaDisk(final boolean ukladatMapyNaDisk) {
		if (ukladatMapyNaDisk == isUkladatMapyNaDisk()) {
			return;
		}
		currPrefe().putBoolean("ukladatMapyNaDisk", ukladatMapyNaDisk);
		fire(new KachleModelChangeEvent());
	}

	/**
	 * @param umisteniSouboru
	 *            the umisteniSouboru to set
	 */
	public void setUmisteniSouboru(final KachleUmisteniSouboru umisteniSouboru) {
		if (umisteniSouboru.equals(this.umisteniSouboru)) {
			return;
		}
		this.umisteniSouboru = umisteniSouboru;
		kachleCacheFolderHolder.setKachleCacheDir(umisteniSouboru.getKachleCacheDir());
		currPrefe().putFilex(OFFLINE_MAPY_DIR, umisteniSouboru.getOfflineMapyDir());
		nastavOfflineMapy(umisteniSouboru);
		fire(new KachleUmisteniSouboruChangedEvent(umisteniSouboru));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.Model0#initAndFire()
	 */
	@Override
	protected void initAndFire() {
		// Mapy zkopírované nebo vyměněné za běhu se projeví hned.
		ziskavac.setPriZmeneOfflineMapy(() -> SwingUtilities.invokeLater(() -> fire(new OfflineMapaChangedEvent())));
		nastavVzhledOfflineMapy();
		setUmisteniSouboru(loadUmisteniSouboru());
		fire(new KachleModelChangeEvent());
		fajrujPrekryvTras();
		fire(new ReliefCuzkEvent(isReliefCuzk()));
		fire(new ObrysPokrytiEvent(isObrysPokryti()));
		hlidaniSlozky.scheduleWithFixedDelay(this::zkontrolujSlozkuOfflineMap, 2, 2, TimeUnit.SECONDS);
		ziskavac.spustPredvykresleniPrehledu();
	}

	void zkontrolujSlozkuOfflineMap() {
		try {
			if (offlinePodklad) {
				ziskavac.getOfflineMapy().zkontrolujSlozku();
			}
			if (isPrekryvTrasViditelny()) {
				ziskavac.getOfflineTrasy().zkontrolujSlozku();
			}
		} catch (final RuntimeException e) {
			log.warn("Složku offline map nejde zkontrolovat: {}", e.toString());
		}
	}

	private KachleUmisteniSouboru loadUmisteniSouboru() {
		final KachleUmisteniSouboru u = new KachleUmisteniSouboru();
		u.setKachleCacheDir(KachleUmisteniSouboru.KACHLE_CACHE_DIR);
		u.setOfflineMapyDir(offlineMapyDir(currPrefe()));
		return u;
	}

	/** Složka offline map podle nastavení; čte se i dřív, než se model inicializuje. */
	public static cz.geokuk.util.file.Filex offlineMapyDir(final MyPreferences pref) {
		return pref.getFilex(OFFLINE_MAPY_DIR, KachleUmisteniSouboru.OFFLINE_MAPY_DIR);
	}

	/** Měřítko displeje, na kterém je mapa; ostrá offline mapa podle něj kreslí dlaždice s víc pixely, rychlejší vždy po 256 px. */
	public void setMeritkoDispleje(final double meritko) {
		ziskavac.getOfflineMapy().nastavMeritko(isOfflineMapaOstra() ? meritko : 1);
		ziskavac.getOfflineTrasy().nastavMeritko(isOfflineMapaOstra() ? meritko : 1);
	}

	/** Ostrost: dlaždice offline mapy podle zvětšení systému, nebo bez něj (rychlejší, mapu zvětší systém). */
	public boolean isOfflineMapaOstra() {
		return currPrefe().getBoolean(OFFLINE_OSTRA, true);
	}

	/** Velikost písma a značek offline mapy v procentech, 80–200. */
	public int getOfflineMapaPismoProcent() {
		return (int) Math.round(OfflineMapy.zaokrouhliPismo(currPrefe().getInt(OFFLINE_PISMO, 100) / 100.0) * 100);
	}

	public void setVzhledOfflineMapy(final boolean ostra, final int pismoProcent) {
		if (ostra == isOfflineMapaOstra() && pismoProcent == getOfflineMapaPismoProcent()) {
			return;
		}
		currPrefe().putBoolean(OFFLINE_OSTRA, ostra);
		currPrefe().putInt(OFFLINE_PISMO, (int) Math.round(OfflineMapy.zaokrouhliPismo(pismoProcent / 100.0) * 100));
		nastavVzhledOfflineMapy();
		ziskavac.clearMemoryCache();
		predpripravOfflineMapu();
		fire(new OfflineMapaChangedEvent());
	}

	private void nastavVzhledOfflineMapy() {
		setMeritkoDispleje(meritkoVychoziObrazovky());
		ziskavac.getOfflineMapy().nastavPismo(getOfflineMapaPismoProcent() / 100.0);
		ziskavac.getOfflineTrasy().nastavPismo(getOfflineMapaPismoProcent() / 100.0);
	}

	private static double meritkoVychoziObrazovky() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			return 1;
		}
		return java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getDefaultTransform().getScaleX();
	}

	public KachleUmisteniSouboru getUmisteniSouboru() {
		return (KachleUmisteniSouboru) umisteniSouboru;
	}

	/** Zvolené téma s výběrem vrstev, který si pro něj uživatel uložil. */
	public TemaOfflineMapy getTemaOfflineMapy() {
		final TemaOfflineMapy tema = temaZNastaveni(currPrefe().get(OFFLINE_MAPA_TEMA, ""), offlineMapyDir(currPrefe()).getEffectiveFile());
		final MyPreferences vrstvy = currPrefe().node(OFFLINE_MAPA_VRSTVY_node);
		final String volba = vrstvy.get(klicVrstev(tema), vrstvy.get(tema.klicNastaveni(), null));
		return tema.sVrstvami(VolbaVrstev.zTextu(volba));
	}

	/** Klíč výběru vrstev z textu tématu v nastavení, takže výběr přežije přesun přenosného GeoKuku. */
	static String klicVrstev(final TemaOfflineMapy tema) {
		return TemaOfflineMapy.klicNastaveni(temaDoNastaveni(tema));
	}

	/** Text tématu do nastavení; soubor ve složce programu se uloží relativně, aby téma přežilo přesun přenosného GeoKuku. */
	static String temaDoNastaveni(final TemaOfflineMapy tema) {
		final String text = tema.naText();
		if (tema.isVestavene()) {
			return text;
		}
		return MyPreferences.cestaDoNastaveni(tema.getSoubor(), FConst.KOREN) + text.substring(tema.getSoubor().getPath().length());
	}

	/**
	 * Téma z nastavení. Soubor, který na uložené cestě není (starší nastavení s absolutní cestou po přesunu GeoKuku), se hledá pod stejným jménem ve složce
	 * offline map.
	 */
	static TemaOfflineMapy temaZNastaveni(final String text, final File slozkaOfflineMap) {
		String t = text;
		if (t != null && t.startsWith(MyPreferences.ZNACKA_KORENE)) {
			final int i = t.indexOf('|');
			final String cesta = i < 0 ? t : t.substring(0, i);
			t = MyPreferences.cestaZNastaveni(cesta, FConst.KOREN).getPath() + (i < 0 ? "" : t.substring(i));
		}
		final TemaOfflineMapy tema = TemaOfflineMapy.zTextu(t);
		if (tema.isVestavene() || tema.getSoubor().exists() || slozkaOfflineMap == null) {
			return tema;
		}
		final File vSlozce = new File(slozkaOfflineMap, tema.getSoubor().getName());
		return vSlozce.isFile() ? tema.sSouborem(vSlozce) : tema;
	}

	/** Zvolí téma; výběr vrstev se vezme ten, který má téma uložený. */
	public void setTemaOfflineMapy(final TemaOfflineMapy tema) {
		if (tema.stejnyZdroj(getTemaOfflineMapy())) {
			return;
		}
		currPrefe().put(OFFLINE_MAPA_TEMA, temaDoNastaveni(tema));
		nastavOfflineMapy(getUmisteniSouboru());
		fire(new OfflineMapaChangedEvent());
		fajrujPrekryvTras();
	}

	/** Výběr vrstev zvoleného tématu; uloží se k tématu. */
	public void setVrstvyTematu(final VolbaVrstev volba) {
		final TemaOfflineMapy tema = getTemaOfflineMapy();
		if (volba.equals(tema.getVrstvy())) {
			return;
		}
		final MyPreferences pref = currPrefe().node(OFFLINE_MAPA_VRSTVY_node);
		pref.remove(tema.klicNastaveni());
		if (volba.isAutor()) {
			pref.remove(klicVrstev(tema));
		} else {
			pref.put(klicVrstev(tema), volba.naText());
		}
		nastavOfflineMapy(getUmisteniSouboru());
		fire(new OfflineMapaChangedEvent());
	}

	/** Vrstvy zvoleného tématu, null když je téma nemá nebo ještě není načtené (načte se s první dlaždicí offline mapy). */
	public VrstvyTematu getVrstvyTematu() {
		final VrstvyTematu v = ziskavac.getOfflineMapy().getVrstvyTematu();
		return v != null && v.getTema().stejnyZdroj(getTemaOfflineMapy()) ? v : null;
	}

	private void nastavOfflineMapy(final KachleUmisteniSouboru u) {
		ziskavac.getOfflineMapy().setStinovani(isStinovaniTerenu());
		ziskavac.getOfflineMapy().setSlozkaSymbolu(new java.io.File(u.getKachleCacheDir().getEffectiveFile(), "offline-temata"));
		ziskavac.getOfflineMapy().nastav(u.getOfflineMapyDir().getEffectiveFile(), getTemaOfflineMapy());
		ziskavac.getOfflineTrasy().setSlozkaSymbolu(new java.io.File(u.getKachleCacheDir().getEffectiveFile(), "offline-trasy"));
		ziskavac.getOfflineTrasy().nastav(u.getOfflineMapyDir().getEffectiveFile(), TemaOfflineMapy.TRASY);
		nastavTrasyPodkladu();
		predpripravOfflineMapu();
	}

	/** Offline podklad s tématem, které kreslí vrstvy tras, je kreslí sám; překryv by je nakreslil podruhé přes popisky. */
	private void nastavTrasyPodkladu() {
		if (ziskavac != null) {
			ziskavac.getOfflineTrasy().setTrasyKresliPodklad(offlinePodklad && getTemaOfflineMapy().isKresliVrstvyTras());
		}
	}

	/** Střed výřezu pokrývá podrobná offline mapa (nebo mapy ještě nejsou otevřené). */
	public boolean jePodrobnaOfflineMapa(final Wgs bod, final int zoom) {
		return ziskavac == null || ziskavac.getOfflineMapy().jePodrobnaMapa(bod, zoom);
	}

	/** Atribuce offline mapy: data podle otevřených map (OSM, přehledová mapa z Natural Earth) a téma ze souboru uživatele. */
	public String getAtribuceOfflineMapy() {
		final String data = atribuceDat(ziskavac.getOfflineMapy().getNazvyMap());
		final TemaOfflineMapy tema = getTemaOfflineMapy();
		if (tema.isVestavene()) {
			return data;
		}
		final String jmeno = tema.getSoubor().getName();
		final int tecka = jmeno.lastIndexOf('.');
		return data + ", téma " + (tecka > 0 ? jmeno.substring(0, tecka) : jmeno);
	}

	static String atribuceDat(final List<String> mapy) {
		final boolean prehled = mapy.contains(PrehledovaMapa.SOUBOR);
		if (prehled && mapy.size() == 1) {
			return NATURAL_EARTH;
		}
		return prehled ? OSM + ", " + NATURAL_EARTH : OSM;
	}
}
