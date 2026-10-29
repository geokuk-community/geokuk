package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.util.*;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Cache dlaždic ve více souborech: online a offline dlaždice se zapisují každé do svého souboru, čte se z něj a potom ze starého {@code tiles.sqlite}, do
 * kterého se už nezapisuje (jen ho zmenšuje Optimalizace). Offline dlaždice se poznají podle tvaru typu, viz {@link UklidOfflineCache#jeOffline}.
 */
final class KachleGeneraceManager implements KachleManager {

	static final String SOUBOR_ONLINE = "tiles-online-1.sqlite";
	static final String SOUBOR_OFFLINE = "tiles-offline-1.sqlite";

	/** Starý společný soubor, jen ke čtení. */
	private final KachleDBManager puvodni;
	private final KachleDBManager online;
	private final KachleDBManager offline;

	KachleGeneraceManager(final KachleCacheFolderHolder holder) {
		puvodni = new KachleDBManager(holder);
		online = new KachleDBManager(holder, SOUBOR_ONLINE);
		offline = new KachleDBManager(holder, SOUBOR_OFFLINE);
	}

	private KachleDBManager aktualni(final String typ) {
		return UklidOfflineCache.jeOffline(typ) ? offline : online;
	}

	/** Starý soubor se nezakládá: čte se z něj, jen když už je. */
	private boolean jePuvodni() {
		final File f = puvodni.souborCache();
		return f != null && f.isFile();
	}

	@Override
	public boolean exists(final Ka ki) {
		return exists(ki, ki.typToString());
	}

	@Override
	public boolean exists(final Ka ki, final String typ) {
		return aktualni(typ).exists(ki, typ) || jePuvodni() && puvodni.exists(ki, typ);
	}

	@Override
	public Image load(final Ka ki) {
		return load(ki, ki.typToString());
	}

	@Override
	public Image load(final Ka ki, final String typ) {
		final Image obrazek = aktualni(typ).load(ki, typ);
		if (obrazek != null || !jePuvodni()) {
			return obrazek;
		}
		return puvodni.load(ki, typ);
	}

	@Override
	public boolean save(final Collection<ItemToSave> itemsToSave) {
		final List<ItemToSave> pro = new ArrayList<>();
		final List<ItemToSave> proOnline = new ArrayList<>();
		for (final ItemToSave item : itemsToSave) {
			(UklidOfflineCache.jeOffline(item.typ) ? pro : proOnline).add(item);
		}
		boolean vse = true;
		if (!proOnline.isEmpty()) {
			vse = online.save(proOnline);
		}
		if (!pro.isEmpty()) {
			vse &= offline.save(pro);
		}
		return vse;
	}

	@Override
	public int smazOfflineKrome(final Set<String> ponechat) {
		return soucet(offline.smazOfflineKrome(ponechat), jePuvodni() ? puvodni.smazOfflineKrome(ponechat) : 0);
	}

	@Override
	public int smazOfflineNesedici(final String globalni, final Function<KaLoc, String> ocekavany) {
		return soucet(offline.smazOfflineNesedici(globalni, ocekavany), jePuvodni() ? puvodni.smazOfflineNesedici(globalni, ocekavany) : 0);
	}

	/** Součet počtů smazaných dlaždic; {@link KachleDBManager#NEDOKONCENO}, když se některý úklid nedokončil. */
	private static int soucet(final int a, final int b) {
		return a < 0 || b < 0 ? KachleDBManager.NEDOKONCENO : a + b;
	}

	@Override
	public void predotevri() {
		online.predotevri();
		offline.predotevri();
		if (jePuvodni()) {
			puvodni.predotevri();
		}
	}

	@Override
	public OptimalizaceDatabaze optimalizace() {
		return puvodni.optimalizace();
	}
}
