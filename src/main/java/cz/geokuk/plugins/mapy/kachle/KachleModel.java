/**
 *
 */
package cz.geokuk.plugins.mapy.kachle;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.kachle.podklady.*;

/**
 * @author Martin Veverka
 *
 */
public class KachleModel extends Model0 {

	private static final String OFFLINE_MAPY_DIR = "offlineMapyDir";

	private static final String OFFLINE_MAPA_TEMA = "offlineMapaTema";

	private static final String OFFLINE_OSTRA = "offlineMapaOstra";
	private static final String OSM = "© přispěvatelé OpenStreetMap";
	private static final String NATURAL_EARTH = "Made with Natural Earth";

	private static final String OFFLINE_PISMO = "offlineMapaPismo";

	/** Otevírá offline mapu a načítá téma předem, mimo EDT. */
	private final ExecutorService priprava = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "Příprava offline mapy");
		t.setDaemon(true);
		return t;
	});

	private volatile boolean offlinePodklad;

	private final KachleCacheFolderHolder kachleCacheFolderHolder = new KachleCacheFolderHolder();

	public KachloDownloader kachloDownloader;

	private Object umisteniSouboru;

	private KachleZiskavac ziskavac;

	public final KachleManager kachleManager;

	/**
	 * @param bb
	 */
	public KachleModel() {
		kachleManager = KachleManagerFactory.getInstance(getKachleCacheFolderHolder());
		assert kachleCacheFolderHolder != null;
	}

	/**
	 * @return the kachleCacheFolderHolder
	 */
	public KachleCacheFolderHolder getKachleCacheFolderHolder() {
		assert kachleCacheFolderHolder != null;
		return kachleCacheFolderHolder;
	}

	public KachleZiskavac getZiskavac() {
		return ziskavac;
	}

	public void inject(final KachleZiskavac kachleZiskavac) {
		ziskavac = kachleZiskavac;
		ziskavac.setKachleManager(kachleManager);
	}

	public void inject(final KachloDownloader kachloDownloader) {
		this.kachloDownloader = kachloDownloader;
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
		predpripravOfflineMapu();
	}

	private void predpripravOfflineMapu() {
		if (offlinePodklad && ziskavac != null) {
			priprava.execute(ziskavac.getOfflineMapy()::predpriprav);
		}
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
	}

	private KachleUmisteniSouboru loadUmisteniSouboru() {
		final KachleUmisteniSouboru u = new KachleUmisteniSouboru();
		u.setKachleCacheDir(KachleUmisteniSouboru.KACHLE_CACHE_DIR);
		u.setOfflineMapyDir(currPrefe().getFilex(OFFLINE_MAPY_DIR, KachleUmisteniSouboru.OFFLINE_MAPY_DIR));
		return u;
	}

	/** Měřítko displeje, na kterém je mapa; offline mapa podle něj kreslí dlaždice s víc pixely. */
	public void setMeritkoDispleje(final double meritko) {
		ziskavac.getOfflineMapy().nastavMeritko(meritko);
	}

	/** Ostrost: dlaždice offline mapy podle zvětšení systému, nebo bez něj (rychlejší, mapu zvětší systém). */
	public boolean isOfflineMapaOstra() {
		return currPrefe().getBoolean(OFFLINE_OSTRA, true);
	}

	/** Velikost písma a značek offline mapy v procentech, 80–150. */
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
		setMeritkoDispleje(isOfflineMapaOstra() ? meritkoVychoziObrazovky() : 1);
		ziskavac.getOfflineMapy().nastavPismo(getOfflineMapaPismoProcent() / 100.0);
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

	public TemaOfflineMapy getTemaOfflineMapy() {
		return TemaOfflineMapy.zTextu(currPrefe().get(OFFLINE_MAPA_TEMA, ""));
	}

	public void setTemaOfflineMapy(final TemaOfflineMapy tema) {
		if (tema.equals(getTemaOfflineMapy())) {
			return;
		}
		currPrefe().put(OFFLINE_MAPA_TEMA, tema.naText());
		nastavOfflineMapy(getUmisteniSouboru());
		fire(new OfflineMapaChangedEvent());
	}

	private void nastavOfflineMapy(final KachleUmisteniSouboru u) {
		ziskavac.getOfflineMapy().setSlozkaSymbolu(new java.io.File(u.getKachleCacheDir().getEffectiveFile(), "offline-temata"));
		ziskavac.getOfflineMapy().nastav(u.getOfflineMapyDir().getEffectiveFile(), getTemaOfflineMapy());
		predpripravOfflineMapu();
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
