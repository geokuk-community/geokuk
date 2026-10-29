package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.util.*;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

/**
 * Cache dlaždic ve více souborech: online dlaždice zůstávají samostatně v původním {@code tiles.sqlite}, offline dlaždice se zapisují do souboru aktuální
 * generace {@code tiles-offline-N.sqlite} a čtou se z něj, z předchozí generace a potom z původního souboru (starší offline dlaždice). Generace rotují při prvním
 * použití složky cache, viz {@link RotaceGeneraci}. Offline dlaždice se poznají podle tvaru typu, viz {@link UklidOfflineCache#jeOffline}.
 */
final class KachleGeneraceManager implements KachleManager {

	static final String SOUBOR_OFFLINE = RotaceGeneraci.jmeno(1);

	/** Správci souborů offline dlaždic jedné složky cache. */
	private static final class Sestava {
		final File slozka;
		final KachleDBManager aktualni;
		/** Předchozí generace, null u první. */
		final KachleDBManager predchozi;

		Sestava(final File slozka, final KachleDBManager aktualni, final KachleDBManager predchozi) {
			this.slozka = slozka;
			this.aktualni = aktualni;
			this.predchozi = predchozi;
		}
	}

	private final KachleCacheFolderHolder holder;
	private final long limitOffline;
	private final java.util.function.LongSupplier hodiny;

	/** Původní společný soubor: online dlaždice a starší offline dlaždice. */
	private final KachleDBManager puvodni;

	private volatile Sestava sestava;

	/** Maže staré generace na pozadí, ať první dlaždice nečeká na smazání velkého souboru. */
	private final java.util.concurrent.Executor mazaci;

	KachleGeneraceManager(final KachleCacheFolderHolder holder) {
		this(holder, RotaceGeneraci.LIMIT_OFFLINE, System::currentTimeMillis);
	}

	KachleGeneraceManager(final KachleCacheFolderHolder holder, final long limitOffline, final java.util.function.LongSupplier hodiny) {
		this(holder, limitOffline, hodiny, KachleGeneraceManager::naPozadi);
	}

	KachleGeneraceManager(final KachleCacheFolderHolder holder, final long limitOffline, final java.util.function.LongSupplier hodiny, final java.util.concurrent.Executor mazaci) {
		this.mazaci = mazaci;
		this.holder = holder;
		this.limitOffline = limitOffline;
		this.hodiny = hodiny;
		puvodni = new KachleDBManager(holder);
	}

	private static void naPozadi(final Runnable uloha) {
		final Thread t = new Thread(uloha, "Mazání starých generací cache");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	/** Správci offline souborů pro složku cache; při prvním použití složky se rotují generace, dřív než se cokoli otevře. */
	private Sestava sestava() {
		final cz.geokuk.util.file.Filex filex = holder.getKachleCacheFolder();
		final File slozka = filex == null ? null : filex.getEffectiveFile();
		Sestava s = sestava;
		if (s != null && Objects.equals(s.slozka, slozka)) {
			return s;
		}
		synchronized (this) {
			s = sestava;
			if (s != null && Objects.equals(s.slozka, slozka)) {
				return s;
			}
			final int generace = slozka == null ? 1 : RotaceGeneraci.rotuj(slozka, limitOffline, hodiny.getAsLong(), mazaci, f -> java.nio.file.Files.deleteIfExists(f.toPath()));
			s = new Sestava(slozka, new KachleDBManager(holder, RotaceGeneraci.jmeno(generace)),
					generace > 1 ? new KachleDBManager(holder, RotaceGeneraci.jmeno(generace - 1)) : null);
			sestava = s;
			return s;
		}
	}

	/** Soubor správce existuje; starší soubory se nezakládají. */
	private static boolean jeSoubor(final KachleDBManager manager) {
		final File f = manager == null ? null : manager.souborCache();
		return f != null && f.isFile();
	}

	/** Původní soubor se kvůli offline dlaždicím nezakládá: čte se z něj, jen když už je. */
	private boolean jePuvodni() {
		return jeSoubor(puvodni);
	}

	/** Starší soubory offline dlaždic, ve kterých se hledá po aktuální generaci, od nejnovější. */
	private List<KachleDBManager> starsi(final Sestava s) {
		final List<KachleDBManager> v = new ArrayList<>(2);
		if (jeSoubor(s.predchozi)) {
			v.add(s.predchozi);
		}
		if (jePuvodni()) {
			v.add(puvodni);
		}
		return v;
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
		final Sestava s = sestava();
		if (s.aktualni.exists(ki, typ)) {
			return true;
		}
		for (final KachleDBManager m : starsi(s)) {
			if (m.exists(ki, typ)) {
				return true;
			}
		}
		return false;
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
		final Sestava s = sestava();
		final Image obrazek = s.aktualni.load(ki, typ);
		if (obrazek != null) {
			return obrazek;
		}
		for (final KachleDBManager m : starsi(s)) {
			final Image starsiObrazek = m.load(ki, typ);
			if (starsiObrazek != null) {
				return starsiObrazek;
			}
		}
		return null;
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
			vse &= sestava().aktualni.save(proOffline);
		}
		return vse;
	}

	@Override
	public int smazOfflineKrome(final Set<String> ponechat) {
		final Sestava s = sestava();
		int soucet = s.aktualni.smazOfflineKrome(ponechat);
		for (final KachleDBManager m : starsi(s)) {
			soucet = soucet(soucet, m.smazOfflineKrome(ponechat));
		}
		return soucet;
	}

	@Override
	public int smazOfflineNesedici(final String globalni, final Function<KaLoc, String> ocekavany) {
		final Sestava s = sestava();
		int soucet = s.aktualni.smazOfflineNesedici(globalni, ocekavany);
		for (final KachleDBManager m : starsi(s)) {
			soucet = soucet(soucet, m.smazOfflineNesedici(globalni, ocekavany));
		}
		return soucet;
	}

	/** Součet počtů smazaných dlaždic; {@link KachleDBManager#NEDOKONCENO}, když se některý úklid nedokončil. */
	static int soucet(final int a, final int b) {
		return a < 0 || b < 0 ? KachleDBManager.NEDOKONCENO : a + b;
	}

	@Override
	public void predotevri() {
		puvodni.predotevri();
		final Sestava s = sestava();
		s.aktualni.predotevri();
		for (final KachleDBManager m : starsi(s)) {
			if (m != puvodni) {
				m.predotevri();
			}
		}
	}

	@Override
	public OptimalizaceDatabaze optimalizace() {
		return puvodni.optimalizace();
	}
}
