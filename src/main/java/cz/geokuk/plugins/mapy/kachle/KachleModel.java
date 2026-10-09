/**
 *
 */
package cz.geokuk.plugins.mapy.kachle;

import javax.swing.SwingUtilities;

import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.kachle.podklady.*;

/**
 * @author Martin Veverka
 *
 */
public class KachleModel extends Model0 {

	private static final String OFFLINE_MAPY_DIR = "offlineMapyDir";

	private static final String OFFLINE_MAPA_TEMA = "offlineMapaTema";

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
		setUmisteniSouboru(loadUmisteniSouboru());
		fire(new KachleModelChangeEvent());
	}

	private KachleUmisteniSouboru loadUmisteniSouboru() {
		final KachleUmisteniSouboru u = new KachleUmisteniSouboru();
		u.setKachleCacheDir(KachleUmisteniSouboru.KACHLE_CACHE_DIR);
		u.setOfflineMapyDir(currPrefe().getFilex(OFFLINE_MAPY_DIR, KachleUmisteniSouboru.OFFLINE_MAPY_DIR));
		return u;
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
		ziskavac.getOfflineMapy().nastav(u.getOfflineMapyDir().getEffectiveFile(), getTemaOfflineMapy());
	}

	/** Atribuce offline mapy: data OSM a téma ze souboru uživatele. */
	public String getAtribuceOfflineMapy() {
		final TemaOfflineMapy tema = getTemaOfflineMapy();
		if (tema.isVestavene()) {
			return "© přispěvatelé OpenStreetMap";
		}
		final String jmeno = tema.getSoubor().getName();
		final int tecka = jmeno.lastIndexOf('.');
		return "© přispěvatelé OpenStreetMap, téma " + (tecka > 0 ? jmeno.substring(0, tecka) : jmeno);
	}
}
