package cz.geokuk.plugins.mapy.sprava;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.OfflineMapaChangedEvent;
import cz.geokuk.plugins.mapy.sprava.StavMapy.Faze;
import lombok.extern.slf4j.Slf4j;

/**
 * Správa offline map z katalogu: stav map, kontrola na serveru a stažení. Na serveru se nic neděje samo, jen na žádost uživatele, a stahuje se vždy jen jedna
 * mapa. Síť i disk běží na vlastním vlákně, události {@link SpravaMapEvent} chodí na EDT.
 */
@Slf4j
public class SpravaMapModel extends Model0 {

	/** Volné místo, které stahovač nechává na disku navíc; popup podle něj hlídá, jestli se mapa vejde. */
	public static final long REZERVA_MISTA = StahovacMapy.REZERVA_MISTA;

	private static final long INTERVAL_PRUBEHU_NS = TimeUnit.MILLISECONDS.toNanos(250);

	private final KatalogMap katalog;
	private final ExecutorService vlakno = vlakno("Stahování offline mapy");
	/** Kontroly a stav z disku, aby nečekaly na dlouhé stahování. */
	private final ExecutorService kontrola = vlakno("Kontrola offline map");
	private final Map<String, StavMapy> stavy = new ConcurrentHashMap<>();
	private final AtomicReference<KatalogovaMapa> bezici = new AtomicReference<>();
	private final AtomicBoolean zruseno = new AtomicBoolean();
	private final StahovacMapy stahovac;
	private Supplier<File> slozka = () -> null;
	private Consumer<SpravaMapEvent> odpalovac = e -> SwingUtilities.invokeLater(() -> fire(e));
	private KachleModel kachleModel;
	private volatile long posledniPrubeh;

	private static ExecutorService vlakno(final String jmeno) {
		return Executors.newSingleThreadExecutor(r -> {
			final Thread t = new Thread(r, jmeno);
			t.setDaemon(true);
			return t;
		});
	}

	public SpravaMapModel() {
		this(KatalogMap.vestaveny());
	}

	SpravaMapModel(final KatalogMap katalog) {
		this.katalog = katalog;
		stahovac = new StahovacMapy("GeoKuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")", this::uvolniOfflineMapy);
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
		slozka = () -> kachleModel.getUmisteniSouboru() == null ? null : kachleModel.getUmisteniSouboru().getOfflineMapyDir().getEffectiveFile();
	}

	/** Pro testy: složka a odpalování událostí bez Swingu. */
	void nastavProTest(final Supplier<File> slozka, final Consumer<SpravaMapEvent> odpalovac) {
		this.slozka = slozka;
		this.odpalovac = odpalovac;
	}

	StahovacMapy getStahovac() {
		return stahovac;
	}

	@Override
	protected void initAndFire() {
		obnovStavy();
	}

	public void onEvent(final OfflineMapaChangedEvent event) {
		obnovStavy();
	}

	public void onEvent(final KachleUmisteniSouboruChangedEvent event) {
		obnovStavy();
	}

	public KatalogMap getKatalog() {
		return katalog;
	}

	/** Poslední známý stav mapy, bez sítě a bez disku. */
	public StavMapy getStav(final KatalogovaMapa mapa) {
		return stavy.computeIfAbsent(mapa.getId(), id -> StavMapy.novy(mapa));
	}

	public Optional<StavMapy> getBeziciStahovani() {
		final KatalogovaMapa m = bezici.get();
		return m == null ? Optional.empty() : Optional.of(getStav(m));
	}

	/** Přečte stav všech katalogových map z disku (bez sítě); změny přijdou událostmi. */
	public void obnovStavy() {
		kontrola.execute(() -> {
			final File s = slozka.get();
			if (s == null) {
				return;
			}
			for (final KatalogovaMapa m : katalog.getMapy()) {
				final StavMapy predtim = getStav(m);
				if (predtim.isBezi()) {
					continue;
				}
				final StavMapy ted = StahovacMapy.mistniStav(predtim, s);
				if (!ted.toString().equals(predtim.toString())) {
					zmena(ted);
				} else {
					stavy.put(m.getId(), ted);
				}
			}
		});
	}

	/** Stáhne mapu do složky offline map. False, když se už nějaká mapa stahuje. */
	public boolean stahni(final KatalogovaMapa mapa) {
		if (!bezici.compareAndSet(null, mapa)) {
			return false;
		}
		zruseno.set(false);
		vlakno.execute(() -> {
			try {
				final File s = slozka.get();
				if (s == null) {
					zmena(getStav(mapa).sFazi(Faze.CHYBA, "Složka offline map není nastavená."));
					return;
				}
				zmena(stahovac.stahni(getStav(mapa), s, zruseno::get, this::prubeh));
			} catch (final RuntimeException e) {
				log.warn("Stahování mapy {} selhalo", mapa, e);
				zmena(getStav(mapa).sFazi(Faze.CHYBA, "Stahování selhalo, podrobnosti jsou v logu."));
			} finally {
				bezici.set(null);
			}
		});
		return true;
	}

	/** Zruší běžící stahování; rozpracovaný soubor zůstane pro pokračování. */
	public void zrusStahovani() {
		if (bezici.get() != null) {
			zruseno.set(true);
		}
	}

	/** Zahodí rozpracované stahování mapy (ne to, které právě běží). */
	public void zahodRozpracovane(final KatalogovaMapa mapa) {
		kontrola.execute(() -> {
			final File s = slozka.get();
			if (s != null && bezici.get() != mapa) {
				StahovacMapy.zahod(s, mapa);
				zmena(StahovacMapy.mistniStav(getStav(mapa).sPrubehem(0, -1, false).sFazi(Faze.NIC, null), s));
			}
		});
	}

	/** Zeptá se serveru na jednu mapu (podmíněný HEAD). Jen na výslovnou žádost uživatele. */
	public void zkontroluj(final KatalogovaMapa mapa) {
		if (getStav(mapa).isBezi()) {
			return;
		}
		kontrola.execute(() -> {
			final File s = slozka.get();
			if (s == null || getStav(mapa).isBezi() || bezici.get() == mapa) {
				return;
			}
			zmena(stahovac.zkontroluj(getStav(mapa), s, this::prubeh));
		});
	}

	private void prubeh(final StavMapy stav) {
		final StavMapy predtim = stavy.put(stav.getMapa().getId(), stav);
		final long ted = System.nanoTime();
		if (predtim == null || predtim.getFaze() != stav.getFaze() || ted - posledniPrubeh >= INTERVAL_PRUBEHU_NS) {
			posledniPrubeh = ted;
			odpalovac.accept(new SpravaMapEvent(stav));
		}
	}

	private void zmena(final StavMapy stav) {
		stavy.put(stav.getMapa().getId(), stav);
		odpalovac.accept(new SpravaMapEvent(stav));
	}

	/** Zavře otevřené offline mapy, aby šlo starou mapu nahradit (Windows otevřený soubor nepřepíše); otevřou se znovu při dalším kreslení. */
	private void uvolniOfflineMapy() {
		final KachleModel k = kachleModel;
		if (k != null && k.getZiskavac() != null) {
			k.getZiskavac().getOfflineMapy().zavri();
			k.getZiskavac().getOfflineTrasy().zavri();
		}
	}

	/** Pro testy: počká, až vlákno dokončí zadanou práci. */
	void pockej() throws InterruptedException {
		for (final ExecutorService e : new ExecutorService[] { kontrola, vlakno }) {
			final java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
			e.execute(l::countDown);
			if (!l.await(30, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Vlákno správy map nedoběhlo");
			}
		}
	}
}
