package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.io.File;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.data.KaLoc;

import lombok.extern.slf4j.Slf4j;

/**
 * Cache dlaždic ve více souborech: online a offline dlaždice se zapisují každé do svého souboru aktuální generace, čte se z něj, z předchozí generace a ze starého
 * společného {@code tiles.sqlite}, do kterého se už nezapisuje (jen ho zmenšuje Optimalizace). Generace rotují při prvním použití složky cache, viz {@link RotaceGeneraci}.
 * Offline dlaždice se poznají podle tvaru typu, viz {@link UklidOfflineCache#jeOffline}.
 */
@Slf4j
final class KachleGeneraceManager implements KachleManager {

	static final String SOUBOR_ONLINE = RotaceGeneraci.jmeno(false, 1);
	static final String SOUBOR_OFFLINE = RotaceGeneraci.jmeno(true, 1);

	/** Správci souborů jedné složky cache pro jednu roli: aktuální a předchozí generace. */
	private static final class Role {
		final KachleDBManager aktualni;
		/** Předchozí generace, null u první. */
		final KachleDBManager predchozi;

		Role(final KachleDBManager aktualni, final KachleDBManager predchozi) {
			this.aktualni = aktualni;
			this.predchozi = predchozi;
		}
	}

	/** Správci souborů jedné složky cache po rotaci při prvním použití. */
	private static final class Sestava {
		final File slozka;
		final Role online;
		final Role offline;

		Sestava(final File slozka, final Role online, final Role offline) {
			this.slozka = slozka;
			this.online = online;
			this.offline = offline;
		}
	}

	private final KachleCacheFolderHolder holder;
	private final long limitOnline;
	private final long limitOffline;

	/** Starý společný soubor, jen ke čtení. */
	private final KachleDBManager puvodni;

	private volatile Sestava sestava;

	private static final long PRODLEVA_POVYSENI_MS = 2000;

	private static final int POVYSOVANI_DAVKA = 200;

	/** Online dlaždice nalezené v starší generaci, čekající na zápis do aktuální. */
	private final Queue<Povyseni> povysovane = new java.util.concurrent.ConcurrentLinkedQueue<>();

	private final java.util.concurrent.atomic.AtomicBoolean zapisNaplanovan = new java.util.concurrent.atomic.AtomicBoolean();

	private final ScheduledThreadPoolExecutor povysovac = vytvorPovysovac();

	private static ScheduledThreadPoolExecutor vytvorPovysovac() {
		final ScheduledThreadPoolExecutor e = new ScheduledThreadPoolExecutor(1, r -> {
			final Thread t = new Thread(r, "Povýšení dlaždic v cache");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			return t;
		});
		e.setRemoveOnCancelPolicy(true);
		return e;
	}

	KachleGeneraceManager(final KachleCacheFolderHolder holder) {
		this(holder, RotaceGeneraci.LIMIT_ONLINE, RotaceGeneraci.LIMIT_OFFLINE);
	}

	KachleGeneraceManager(final KachleCacheFolderHolder holder, final long limitOnline, final long limitOffline) {
		this.holder = holder;
		this.limitOnline = limitOnline;
		this.limitOffline = limitOffline;
		puvodni = new KachleDBManager(holder);
	}

	/** Správci souborů pro složku cache; při prvním použití složky se rotují generace, dřív než se cokoli otevře. */
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
			final RotaceGeneraci.Stav stav = slozka == null ? new RotaceGeneraci.Stav(1, 1) : RotaceGeneraci.rotuj(slozka, limitOnline, limitOffline);
			s = new Sestava(slozka, role(stav.online, false), role(stav.offline, true));
			sestava = s;
			return s;
		}
	}

	private Role role(final int generace, final boolean offline) {
		return new Role(new KachleDBManager(holder, RotaceGeneraci.jmeno(offline, generace)),
				generace > 1 ? new KachleDBManager(holder, RotaceGeneraci.jmeno(offline, generace - 1)) : null);
	}

	private Role role(final String typ) {
		final Sestava s = sestava();
		return UklidOfflineCache.jeOffline(typ) ? s.offline : s.online;
	}

	/** Soubor správce existuje; starší generace se nezakládají. */
	private static boolean jeSoubor(final KachleDBManager manager) {
		final File f = manager == null ? null : manager.souborCache();
		return f != null && f.isFile();
	}

	/** Starý soubor se nezakládá: čte se z něj, jen když už je. */
	private boolean jePuvodni() {
		return jeSoubor(puvodni);
	}

	/** Starší soubory, ve kterých se dlaždice hledá po aktuální generaci, od nejnovější. */
	private List<KachleDBManager> starsi(final Role role) {
		final List<KachleDBManager> v = new ArrayList<>(2);
		if (jeSoubor(role.predchozi)) {
			v.add(role.predchozi);
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
		final Role role = role(typ);
		if (role.aktualni.exists(ki, typ)) {
			return true;
		}
		for (final KachleDBManager m : starsi(role)) {
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
		final Role role = role(typ);
		final Image obrazek = role.aktualni.load(ki, typ);
		if (obrazek != null) {
			return obrazek;
		}
		final boolean offline = UklidOfflineCache.jeOffline(typ);
		for (final KachleDBManager m : starsi(role)) {
			final byte[] bajty = m.bajty(ki, typ);
			final Image zeStarsiho = m.dekoduj(ki, bajty);
			if (zeStarsiho != null) {
				if (!offline) {
					povysit(role.aktualni, new ItemToSave(ki, typ, bajty));
				}
				return zeStarsiho;
			}
		}
		return null;
	}

	/**
	 * Online dlaždice nalezená jen ve starém souboru se zapíše do nového, ať přežije jeho smazání; zapisuje se po dávkách na pozadí. Offline dlaždice se
	 * nepovyšují, vykreslí se znovu.
	 */
	private void povysit(final KachleDBManager kam, final ItemToSave item) {
		povysovane.add(new Povyseni(kam, item));
		if (povysovane.size() >= KachleDBManager.DAVKA_ZAPISU) {
			povysovac.execute(this::zapisPovysene);
		} else if (zapisNaplanovan.compareAndSet(false, true)) {
			povysovac.schedule(this::zapisPovysene, PRODLEVA_POVYSENI_MS, TimeUnit.MILLISECONDS);
		}
	}

	/** Dlaždice čekající na zápis do aktuální generace {@code kam}. */
	private static final class Povyseni {
		final KachleDBManager kam;
		final ItemToSave item;

		Povyseni(final KachleDBManager kam, final ItemToSave item) {
			this.kam = kam;
			this.item = item;
		}
	}

	private void zapisPovysene() {
		zapisNaplanovan.set(false);
		final Map<KachleDBManager, List<ItemToSave>> davky = new IdentityHashMap<>();
		int pocet = 0;
		for (Povyseni p = povysovane.poll(); p != null && pocet < POVYSOVANI_DAVKA; p = povysovane.poll()) {
			davky.computeIfAbsent(p.kam, k -> new ArrayList<>()).add(p.item);
			pocet++;
		}
		for (final Map.Entry<KachleDBManager, List<ItemToSave>> e : davky.entrySet()) {
			if (!e.getKey().save(e.getValue())) {
				log.debug("Povýšení {} dlaždic do aktuální cache se nepovedlo.", e.getValue().size());
			}
		}
	}

	@Override
	public boolean save(final Collection<ItemToSave> itemsToSave) {
		final Sestava sest = sestava();
		final List<ItemToSave> proOffline = new ArrayList<>();
		final List<ItemToSave> proOnline = new ArrayList<>();
		for (final ItemToSave item : itemsToSave) {
			(UklidOfflineCache.jeOffline(item.typ) ? proOffline : proOnline).add(item);
		}
		boolean vse = true;
		if (!proOnline.isEmpty()) {
			vse = sest.online.aktualni.save(proOnline);
		}
		if (!proOffline.isEmpty()) {
			vse &= sest.offline.aktualni.save(proOffline);
		}
		return vse;
	}

	@Override
	public int smazOfflineKrome(final Set<String> ponechat) {
		final Role role = sestava().offline;
		int soucet = role.aktualni.smazOfflineKrome(ponechat);
		for (final KachleDBManager m : starsi(role)) {
			soucet = soucet(soucet, m.smazOfflineKrome(ponechat));
		}
		return soucet;
	}

	@Override
	public int smazOfflineNesedici(final String globalni, final Function<KaLoc, String> ocekavany) {
		final Role role = sestava().offline;
		int soucet = role.aktualni.smazOfflineNesedici(globalni, ocekavany);
		for (final KachleDBManager m : starsi(role)) {
			soucet = soucet(soucet, m.smazOfflineNesedici(globalni, ocekavany));
		}
		return soucet;
	}

	/** Součet počtů smazaných dlaždic; {@link KachleDBManager#NEDOKONCENO}, když se některý úklid nedokončil. */
	private static int soucet(final int a, final int b) {
		return a < 0 || b < 0 ? KachleDBManager.NEDOKONCENO : a + b;
	}

	@Override
	public void predotevri() {
		final Sestava s = sestava();
		s.online.aktualni.predotevri();
		s.offline.aktualni.predotevri();
		for (final Role role : Arrays.asList(s.online, s.offline)) {
			for (final KachleDBManager m : starsi(role)) {
				m.predotevri();
			}
		}
	}

	@Override
	public OptimalizaceDatabaze optimalizace() {
		return puvodni.optimalizace();
	}
}
