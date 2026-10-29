package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.util.*;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Cache dlaždic ve více souborech: online dlaždice zůstávají samostatně v původním {@code tiles.sqlite}, offline dlaždice se zapisují do vlastního souboru
 * {@code tiles-offline-N.sqlite} a čtou se z něj a potom z původního souboru (starší offline dlaždice). Offline dlaždice se poznají podle tvaru typu, viz
 * {@link UklidOfflineCache#jeOffline}.
 */
final class KachleGeneraceManager implements KachleManager {

	static final String SOUBOR_OFFLINE = "tiles-offline-1.sqlite";

	/** Původní společný soubor: online dlaždice a starší offline dlaždice. */
	private final KachleDBManager puvodni;
	private final KachleDBManager offline;

	KachleGeneraceManager(final KachleCacheFolderHolder holder) {
		puvodni = new KachleDBManager(holder);
		offline = new KachleDBManager(holder, SOUBOR_OFFLINE);
	}

	/** Původní soubor se kvůli offline dlaždicím nezakládá: čte se z něj, jen když už je. */
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
		if (!UklidOfflineCache.jeOffline(typ)) {
			return puvodni.exists(ki, typ);
		}
		return offline.exists(ki, typ) || jePuvodni() && puvodni.exists(ki, typ);
	}

	@Override
	public Image load(final Ka ki) {
		return load(ki, ki.typToString());
	}

	@Override
	public Image load(final Ka ki, final String typ) {
		if (!UklidOfflineCache.jeOffline(typ)) {
			return puvodni.load(ki, typ);
		}
		final Image obrazek = offline.load(ki, typ);
		return obrazek != null || !jePuvodni() ? obrazek : puvodni.load(ki, typ);
	}

	@Override
	public boolean save(final Collection<ItemToSave> itemsToSave) {
		final List<ItemToSave> proOffline = new ArrayList<>();
		final List<ItemToSave> proOnline = new ArrayList<>();
		for (final ItemToSave item : itemsToSave) {
			(UklidOfflineCache.jeOffline(item.typ) ? proOffline : proOnline).add(item);
		}
		boolean vse = true;
		if (!proOnline.isEmpty()) {
			vse = puvodni.save(proOnline);
		}
		if (!proOffline.isEmpty()) {
			vse &= offline.save(proOffline);
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
	static int soucet(final int a, final int b) {
		return a < 0 || b < 0 ? KachleDBManager.NEDOKONCENO : a + b;
	}

	@Override
	public void predotevri() {
		puvodni.predotevri();
		offline.predotevri();
	}

	@Override
	public OptimalizaceDatabaze optimalizace() {
		return puvodni.optimalizace();
	}
}
