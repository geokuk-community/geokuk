package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.*;

import cz.geokuk.core.onoffline.OnofflineModel;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.data.DiagnosticsData;
import cz.geokuk.plugins.mapy.kachle.data.Ka;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;
import cz.geokuk.plugins.mapy.kachle.podklady.KachloDownloader.EPraznyObrazek;
import cz.geokuk.util.exception.*;
import cz.geokuk.util.pocitadla.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * V novém pojetí zodpovídá za získávání kachlí z disku, paměti i downloadování.
 *
 * @author Martin Veverka
 *
 */
@Slf4j
public class KachleZiskavac {

	/**
	 * Objekt zodpovědný za držení jedné kachle a její získání. Je zároveň v mapě jako keš.
	 *
	 * @author Martin
	 *
	 */

	class Kachlice {
		private final Ka ka;
		private final DvojiceExekucnichSluzeb des;
		private final DiagnosticsData diagnosticsData;

		// Dohromady stav kachlice.
		//    ! image & ! futura & irs = 0 ........ počáteční stav po vytvoření, stav po zkanclování dotažení
		//    ! image & futura   & irc > 0 ........ probíhá získávání
		//    image   & ! futura & irc = 0 ........ mám obrázek
		// jiné stavy nejsou možné nebo jinak:
		//   image / futura    ano     ne
		//              ano     -      irc=0
		//               ne    irc>0   irc=0
		// čili, když je něco v sezanmu pro výslede, je futura, kterou se získává a není image
		private Image image;
		private Future<?> futura;
		/** Stahování už posílá požadavek na server: při zrušení doběhne, aby se tatáž dlaždice nestahovala dvakrát. */
		private volatile boolean stahujeSe;
		private final List<ImageReceiver> irs = new ArrayList<ImageReceiver>();

		public Kachlice(final Ka ka, final DvojiceExekucnichSluzeb des, final DiagnosticsData diagnosticsData) {
			super();
			this.ka = ka;
			this.des = des;
			this.diagnosticsData = diagnosticsData;
			pocitKachlice.inc();
			//System.out.println("Vytvořena kachlice pro: " + ka);
		}

		synchronized void ziskej(final ImageReceiver ir) {
			if (image != null) {
				// Když už ho máme, tak nás nic nezajímá, ani ho nemusíme zaregistrovávat do seznamu
				ir.send(new KachloStav(image));
				return;
			} else {
				irs.add(ir);
				if (futura == null) { // nic se nezískává, začíná nutnost získat tu potvoru
					pocitDiskLoadSubmit.inc();
					final ListenableFuture<Image> future = des.disk.submit(this::nactiZDisku);
					futura = future;

					Futures.addCallback(future, new FutureCallback<Image>() {

						@Override
						public void onSuccess(final Image img) { // čtení u disku
							if (img != null) {
								pocitDiskLoadZasah.inc();
								onImageLoaded(img);
							} else {
								pocitDiskLoadMinuti.inc();
								pocitDownloadWebSubmit.inc();
								submitDownload();
							}

						}

						@Override
						public void onFailure(final Throwable t) { // čtení z disku
							if (!(t instanceof CancellationException)) { // když nebylo kanclováno, při kanclování se nespoští download
								pocitDiskLoadError.inc();
								submitDownload();
							}
						}

						private void submitDownload() {
							synchronized (Kachlice.this) {
								if (futura != future) {
									return; // čtení z disku bylo mezitím zrušeno
								}
								futura = null;
								if (irs.isEmpty()) {
									return;
								}
								spustDownload();
							}
						}

						private void spustDownload() {
							if (ka.getType().isOffline()) {
								spustRender();
							} else if (onofflineModel.isOnlineMode()) {
								final AtomicReference<Future<?>> moje = new AtomicReference<>();
								final ListenableFuture<ImageWithData> future = submitDownloadx(ka, des, diagnosticsData, Kachlice.this, moje);
								moje.set(future);
								futura = future;
								Futures.addCallback(future, new FutureCallback<ImageWithData>() {

									@Override
									public void onSuccess(final ImageWithData imageWithData) { // čtení z webu
										pocitDownloadWebOk.inc();
										onImageLoaded(imageWithData.getImg());
									}

									@Override
									public void onFailure(final Throwable t) { // čtení z webu
										if (t instanceof CancellationException) {
											return; // zrušilo se, protože dlaždici už nikdo nechce; příjemci nového požadavku čekají na jiné získání
										}
										pocitDownloadWebError.inc();
										onImageFailure(t);
									}
								}, MoreExecutors.directExecutor());
							} else { // v offline režimu nesmíme downloadovat a musíme poslat prázdný obrázek
								onImageLoaded(downloader.fejkovyObrazek(EPraznyObrazek.OFFLINE));
							}
						}

						private void spustRender() {
							final AtomicReference<Future<?>> moje = new AtomicReference<>();
							final ListenableFuture<ImageWithData> future = submitRender(ka, des, diagnosticsData, Kachlice.this, moje);
							moje.set(future);
							futura = future;
							Futures.addCallback(future, new FutureCallback<ImageWithData>() {

								@Override
								public void onSuccess(final ImageWithData imageWithData) {
									onImageLoaded(imageWithData.getImg());
								}

								@Override
								public void onFailure(final Throwable t) {
									if (t instanceof CancellationException) {
										return;
									}
									onImageFailure(t);
								}
							}, MoreExecutors.directExecutor());
						}

					}, MoreExecutors.directExecutor());
				}
			}
		}

		/** Dlaždice offline mapy se hledá v cache pod klíčem aktuálních map a tématu. */
		private Image nactiZDisku() throws IOException {
			if (!ka.getType().isOffline()) {
				return kachleManager.load(ka);
			}
			final OfflineRenderer renderer = offlineMapy.pouzij();
			try {
				if (!renderer.pokryva(ka.getLoc())) {
					return null; // dlaždice mimo mapy se jen vybarví a neukládá
				}
				return kachleManager.load(ka, renderer.getKlic());
			} finally {
				renderer.skonci();
			}
		}

		/** Stahování začne posílat požadavek na server, jen když je pořád aktuální a někdo dlaždici chce. */
		synchronized boolean zahajStahovani(final AtomicReference<Future<?>> moje) {
			if (futura == null || futura != moje.get() || irs.isEmpty()) {
				if (futura != null && futura == moje.get()) {
					futura = null;
				}
				return false;
			}
			stahujeSe = true;
			return true;
		}

		public synchronized void onImageLoaded(final Image image) {
			futura = null;
			stahujeSe = false;
			this.image = image;
			for (final ImageReceiver ir : odeberPrijemce()) {
				ir.send(new KachloStav(image));
			}
		}

		public synchronized void onImageFailure(final Throwable t) {
			futura = null;
			stahujeSe = false;
			for (final ImageReceiver ir : odeberPrijemce()) {
				ir.send(new KachloStav(t));
			}
		}

		/** Příjemce se během rozesílání může odhlásit (rendr), proto se rozesílá z kopie. */
		private List<ImageReceiver> odeberPrijemce() {
			final List<ImageReceiver> prijemci = new ArrayList<>(irs);
			irs.clear();
			return prijemci;
		}

		@Override
		protected void finalize() {
			if (image != null) {
				pocitMemZameteni.inc();
			}
			pocitKachlice.dec();
		}

		public synchronized void kancluj(final ImageReceiver imageReceiver, final DiagnosticsData diagnosticsData2) {
			pocitCancelCelkem.inc();
			if (image != null) {
				pocitCancelPozde.inc();
			}
			irs.remove(imageReceiver); // už nechce, tak mu nebudeme nic říkat
			if (irs.isEmpty() && futura != null && !stahujeSe) {
				// Bez přerušení vlákna: přerušení zavře kanál souboru diskové cache.
				futura.cancel(false);
				futura = null;
			}
		}
	}

	/**
	 * Řízení ukládání achlí na disk po čancích
	 *
	 * @author Martin Veverka
	 *
	 */
	class KachleUkladac {
		private static final int CAS_PO_KTEREM_SE_ZAHAJI_UKLADANI_NA_DISK = 5;
		private final BlockingQueue<Ukladanec> queue = Queues.newLinkedBlockingQueue();

		ScheduledExecutorService planovacUkladani = Executors.newSingleThreadScheduledExecutor();

		private ScheduledFuture<?> future;

		/**
		 * Zaplánuje uložení kachlice.
		 *
		 * @param ukladanec
		 */
		synchronized void zaplanujUlozeni(final Ukladanec ukladanec) {
			if (future != null) {
				future.cancel(false); // pokud je něco naplánováno, zabiju to, tím odložím ukládání, false aby to nezabylo, když ukládání začalo
			}
			queue.add(ukladanec);
			if (queue.size() >= MAXIMALNI_POCET_UKLADANYCH_KACHLI_V_JEDNOM_CHUNKU) { // jakmile je toho dost, ukládá se hned a není nutno plánovat
				submitChunks();
			} else { // plánuji zahájit ukládání pro případ, že nová kachle nepřijde.
				future = planovacUkladani.schedule(() -> {
					try {
						submitChunks();
					} catch (final RuntimeException e) {
						log.error("Naplánované ukládání dlaždic na disk selhalo", e);
					}
				}, CAS_PO_KTEREM_SE_ZAHAJI_UKLADANI_NA_DISK, TimeUnit.SECONDS);
			}
		}

		private void submitDiskSave(final Collection<Ukladanec> ukladanci) {
			if (!kachleModel.isUkladatMapyNaDisk()) {
				return; // zakázáno ukládat
			}
			log.debug("Submitujeme ukladani kachle na disk #{}", ukladanci.size());
			execDiskWrite.execute(() -> {
				try {
					log.info("Ukladani kachle na disk #{}:", ukladanci.size());
					final List<ItemToSave> list = ukladanci.stream().map(ukladanec -> new ItemToSave(ukladanec.getKa(), ukladanec.getTyp(), ukladanec.getRawData())).collect(Collectors.toList());
					if (kachleManager.save(list)) {
						pocitZapsanoChunkuNaDisk.inc();
						pocitZapsanoNaDisk.add(list.size());
					}
				} catch (final RuntimeException e) {
					log.error("Ukládání dlaždic na disk selhalo", e);
				}
			});
		}

		synchronized private void submitChunks() {
			for (;;) {
				final ArrayList<Ukladanec> list = new ArrayList<>(MAXIMALNI_POCET_UKLADANYCH_KACHLI_V_JEDNOM_CHUNKU);
				queue.drainTo(list, MAXIMALNI_POCET_UKLADANYCH_KACHLI_V_JEDNOM_CHUNKU);
				if (list.size() == 0) {
					return;
				}
				submitDiskSave(list);
			}
		}
	}

	@Data
	private static class DvojiceExekucnichSluzeb {
		private final Priority priority;
		private final ListeningExecutorService disk;
		private final ListeningExecutorService web;
		/** Vykreslování offline mapy. */
		private final ListeningExecutorService render;
		private final org.slf4j.Logger log;

		private BlockingQueue<Runnable> diskQueue;
		private BlockingQueue<Runnable> webQueue;
		private BlockingQueue<Runnable> renderQueue;

		public DvojiceExekucnichSluzeb(final Priority priority, final ExecutorService disk, final ExecutorService web, final ExecutorService render, final Logger log) {
			this.priority = priority;
			this.disk = MoreExecutors.listeningDecorator(disk);
			this.web = MoreExecutors.listeningDecorator(web);
			this.render = MoreExecutors.listeningDecorator(render);
			this.log = log;

			diskQueue = ((ThreadPoolExecutor) disk).getQueue();
			webQueue = ((ThreadPoolExecutor) web).getQueue();
			renderQueue = ((ThreadPoolExecutor) render).getQueue();
		}

		// ExecutorService nedekorovanyServisProZjisteni
	}

	private static Pocitadlo pocitBrzdeniOfflineKdyzJeOnline = new PocitadloRoste("ka61 #počet brzdení dávkového downloadu",
			"Počítá, kolikrát se o 100 ms zbrzdilo dávkové dotahování, protože běžel online.");

	private static final int MAXIMALNI_POCET_UKLADANYCH_KACHLI_V_JEDNOM_CHUNKU = 300;

	private static final int NTHREADS_WEB_CORE_ONLINE = 5;

	private static final int NTHREADS_WEB_CORE_BATCH = 5;

	private static final int NTHREADS_WEB_MAXIMUM_BACTH = 10;

	private static final int WEB_QUEUE_SIZE = 100;

	private static final int BATCH_DISK_QUEUE_SIZE = 100;

	/** Vykreslování škáluje na víc vláken špatně a jedno jádro má zůstat pro okno. */
	static final int NTHREADS_RENDER = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1));

	/** Čtení z cache je hlavně dekódování obrázků, víc vláken pomůže na víc jádrech. */
	static final int NTHREADS_DISK = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));

	private final Pocitadlo pocitSubmitJednaDlazdice = new PocitadloRoste("ka01 Počet požadavků na jednu dlaždici",
			"Kolikrát byl nakonec zadán požadavek na získání jedné dlaždice z jedné vrstvy. Tedy pokud zobrazujeme mapu s turistickými trasami a cyklotrasami, je dlaždice na podklad, na turistickou i na cyklo počítána zvlášť.");

	private final Pocitadlo pocitMemZasah = new PocitadloRoste("ka11 MEM cache #zásahů",
			"Kolikrát se podařilo hledanou dlaždici zasáhnout v paměti. Číslo stále roste a mělo by být ve srovnání s ostatními zásahy co největší.");

	private final Pocitadlo pocitMemMinuti = new PocitadloRoste("ka12 MEM cache #minutí",
			"Kolikrát se nepodařilo hledanou dlaždici v paměťové keši nalézt a co se dělo dál není tímto atributem určeno..");

	private final Pocitadlo pocitMemZameteni = new PocitadloRoste("ka13 MEM cache #zametených", "Počet obrázků, které garbage collector zametrl pryč a my díky tomu odstranili referenci z keše.");

	private final PocitadloRoste pocitDiskLoadSubmit = new PocitadloRoste("ka21 DISK cache #požadovaných",
			"Kolikrát bylo požadováno číst dlaždici z disku. Obsahuje všechny zásahy, minutí, chyby a také skutečně zknclované čtení");

	private final PocitadloRoste pocitDiskLoadZasah = new PocitadloRoste("ka22 DISK cache #zásahů",
			"Kolikrát se podařilo hledanou dlaždici zasáhnout na disku, tedy naloadovat. Číslo stále roste a mělo by být ve srovnání s ostatními zásahy co největší.");

	private final PocitadloRoste pocitDiskLoadMinuti = new PocitadloRoste("ka23 DISK cache #minutí",
			"Kolikrát se nepodařilo hledanou dlaždici v paměťové keši minout, co se dělo dál není tímto atributem určeno..");

	private final PocitadloRoste pocitDiskLoadError = new PocitadloRoste("ka24 DISK cache #chyb čtení", "Kolikrát selhalo čtení dlaždich z disku.");

	private final PocitadloRoste pocitDownloadWebSubmit = new PocitadloRoste("ka31 WEB #požadovaných",
			"Kolikrát bylo požadováno číst dlaždici z webu. Vždy poté, co se nenašly na disku.disku. Obsahuje všechny úspěšně i neúspěšně načtené a také zkanclované");

	private final PocitadloRoste pocitDownloadWebOk = new PocitadloRoste("ka32 WEB #načtených", "Kolikrát se muselo hledanou kachli stáhnout z webu a to úspěšně.");

	private final PocitadloRoste pocitDownloadWebError = new PocitadloRoste("ka33 WEB #chyb", "Kolikrát downloadování dlaždice zahlásilo chybu.");

	private final PocitadloRoste pocitRenderOk = new PocitadloRoste("ka36 RENDER #vykreslených", "Kolik dlaždic offline mapy se vykreslilo.");

	private final PocitadloRoste pocitRenderError = new PocitadloRoste("ka37 RENDER #chyb", "Kolikrát vykreslení dlaždice offline mapy selhalo.");

	private final PocitadloRoste pocitZapsanoChunkuNaDisk = new PocitadloRoste("ka41 disk write #bloků",
			"V kolika diskovžch operacích byl prováděn zápis na disk. Z důvodu optimalizace se zápisy na disk združují do větších bloků.");

	private final PocitadloRoste pocitZapsanoNaDisk = new PocitadloRoste("ka42 disk write #dlaždic", "Kolik dlaždic bylo úspěšně zapsáno na disk asynchronním zapisovačem.");

	private final PocitadloRoste pocitCancelCelkem = new PocitadloRoste("ka51 #cancel celkem", "Celkový počet vydaných poždavaků na kanclování");

	private final PocitadloRoste pocitCancelPozde = new PocitadloRoste("ka55 #cance pozdní, když už je kachle získána", "Počet tancelů, které přišly pozdě, kdy už jsou data načtena.");

	private final Pocitadlo pocitVelikostPametoveKese = new PocitadloMalo("Počet dlaždic v memcache: ",
			"Počet závisí na velikosti pro Javu dostupné paměti (-Xmx) a měl by se ustálit na určité hodnotě, občas možná snížit, nikdy však nemůže být menší než počet dlaždic na mapě.");
	private final PocitadloNula pocitVelikostDiskFrontyOnline = new PocitadloNula("ka1 velikost fronty čtení z disku (online)", "Kolik je ve frontě požadavků na čtení dlaždic z webu.");

	private final PocitadloNula pocitVelikostDiskFrontyBatch = new PocitadloNula("ka2 velikost fronty čtení z disku (batch)", "Kolik je ve frontě požadavků na čtení dlaždic z webu.");

	private final PocitadloNula pocitVelikostWebFrontyOnline = new PocitadloNula("ka3 veliksot fronty čtení z webu (online)", "Kolik je ve frontě požadavků na čtení dlaždic z webu.");

	private final PocitadloNula pocitVelikostWebFrontyBatch = new PocitadloNula("ka3 veliksot fronty čtení z webu (batch)", "Kolik je ve frontě požadavků na čtení dlaždic z webu.");
	private final PocitadloNula pocitVelikostZapisoveFronty = new PocitadloNula("ka3 veliksot fronty zápisu na disk", "Kolik bloků dlaždic je ještě ve frontě a čeká na zápis na disk.");

	private final PocitadloMalo pocitKachlice = new PocitadloMalo("#kachlic",
			"Počet tzv. kachlic, což je objekt určený k řízení získávání kachle z disku, z webu a pak ukládání. Kachlice také reprezentují MEM cache");

	private final EnumMap<Priority, DvojiceExekucnichSluzeb> exekucniSluzby = new EnumMap<>(Priority.class);

	private final ExecutorService execDiskWrite = Executors.newFixedThreadPool(1);

	private final ScheduledExecutorService tikacVelikostiFront = Executors.newSingleThreadScheduledExecutor();
	private final KachloDownloader downloader = new KachloDownloader();

	/** Dlaždic v paměti je nejvýš pár obrazovek, dál se berou z disku. */
	private static final int MAX_KACHLI_V_PAMETI = 300;

	private final Cache<Ka, Kachlice> kachlmap = CacheBuilder.newBuilder().maximumSize(MAX_KACHLI_V_PAMETI).softValues().build();

	private KachleManager kachleManager;

	final KachleUkladac ukladac = new KachleUkladac();

	/** Bez sítě selže každá dlaždice, hlásí se proto souhrnně podle druhu chyby. */
	private final Map<String, OpakovaneChyby> chybyStahovani = new ConcurrentHashMap<>();
	/** Dlaždice mimo území podkladu (server vrátil 404); kreslí se prázdné a znovu se nestahují. */
	private final Cache<String, Boolean> mimoUzemi = CacheBuilder.newBuilder().maximumSize(10_000).build();
	static final Image PRAZDNA_MIMO_UZEMI = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
	/** Dlaždice, které se nedávno nepodařilo stáhnout; jinak by se při každém překreslení stahovaly znovu. */
	private final Cache<String, Boolean> nedavnoNestazene = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.SECONDS).maximumSize(10_000).build();

	private OnofflineModel onofflineModel;

	private final OfflineMapy offlineMapy = new OfflineMapy(this::clearMemoryCache);

	private KachleModel kachleModel;

	public KachleZiskavac() {

		final DvojiceExekucnichSluzeb dvojiceOnline = new DvojiceExekucnichSluzeb(
				// Fronta je pro oonline přístup neomezená, protože nemůžeme nijak blokovat rsponsivnost UI */
				Priority.KACHLE, Executors.newFixedThreadPool(NTHREADS_DISK), Executors.newFixedThreadPool(NTHREADS_WEB_CORE_ONLINE), Executors.newFixedThreadPool(NTHREADS_RENDER),
				LoggerFactory.getLogger(KachleZiskavac.class.getSimpleName() + "_online"));

		final DvojiceExekucnichSluzeb dvojiceBatch = new DvojiceExekucnichSluzeb(Priority.STAHOVANI,
				// Pro background stahování. Jen jedno vlákno pro disk, krátká fronta a po přetížení nechat na volajícím */
				new ThreadPoolExecutor(1, 1, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(BATCH_DISK_QUEUE_SIZE), new ThreadPoolExecutor.CallerRunsPolicy()),
				// Pokud bude fornta na web přetížená, vykonává stahování vlákno pro dotahování z disku, čímž se to zpomalí
				new ThreadPoolExecutor(NTHREADS_WEB_CORE_BATCH, NTHREADS_WEB_MAXIMUM_BACTH, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(WEB_QUEUE_SIZE), new ThreadPoolExecutor.CallerRunsPolicy()),
				// Export vykresluje jedním vláknem, obrazovka má přednost.
				Executors.newFixedThreadPool(1),
				LoggerFactory.getLogger(KachleZiskavac.class.getSimpleName() + "_batch"));

		exekucniSluzby.put(Priority.KACHLE, dvojiceOnline);
		exekucniSluzby.put(Priority.STAHOVANI, dvojiceBatch);

		tikacVelikostiFront.scheduleAtFixedRate(() -> {
			try {
				pocitVelikostDiskFrontyOnline.set(dvojiceOnline.diskQueue.size());
				pocitVelikostDiskFrontyBatch.set(dvojiceBatch.diskQueue.size());
				pocitVelikostWebFrontyOnline.set(dvojiceOnline.webQueue.size());
				pocitVelikostWebFrontyBatch.set(dvojiceBatch.webQueue.size());
				pocitVelikostZapisoveFronty.set(((ThreadPoolExecutor) execDiskWrite).getQueue().size());
				pocitVelikostPametoveKese.set(kachlmap.asMap().size());
			} catch (final Exception e) {
				log.error("Chyba při tikání a zjišťování velikosti fornt: {}", e);
			}

		}, 1l, 1l, TimeUnit.SECONDS);
		// Konec programu volá System.exit, dlaždice čekající na zápis by se jinak ztratily.
		Runtime.getRuntime().addShutdownHook(new Thread(this::dopisNaDisk, "Dopsání dlaždic na disk"));
	}

	private void dopisNaDisk() {
		ukladac.submitChunks();
		execDiskWrite.shutdown();
		try {
			execDiskWrite.awaitTermination(5, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** Smazat se musí třeba při zapnutí online módi */
	public void clearMemoryCache() {
		log.debug("Cache cleared");
		kachlmap.asMap().clear();
	}

	public OfflineMapy getOfflineMapy() {
		return offlineMapy;
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void inject(final OnofflineModel onofflineModel) {
		this.onofflineModel = onofflineModel;
	}

	public void setKachleManager(final KachleManager kachleManager) {
		this.kachleManager = kachleManager;
	}

	/**
	 * Toto je hlavní metoda celého procesu.
	 *
	 *
	 * Získá obsah, získává ho postupně a plní ho do ImageReceveru v rekvesti
	 *
	 * @param req
	 */
	public Kanceler ziskejObsah(final KaOneReq req, final DiagnosticsData diagnosticsDatax) {
		final DiagnosticsData diagnosticsData = diagnosticsDatax.with("priorita", req.getPriorita());

		final DvojiceExekucnichSluzeb dvojiceExekucnichSluzeb = exekucniSluzby.get(req.getPriorita());
		final Logger log = dvojiceExekucnichSluzeb.log;
		log.debug("ziskejObsah: {}", diagnosticsData);
		final Kanceler kanceler = ziskejObsah(dvojiceExekucnichSluzeb, req, diagnosticsData);
		return kanceler;

	}

	private ListenableFuture<ImageWithData> submitDownloadx(final Ka ka, final DvojiceExekucnichSluzeb dvojiceExekucnichSluzeb, final DiagnosticsData diagnosticsData, final Kachlice kachlice,
			final AtomicReference<Future<?>> moje) {
		final Logger log = dvojiceExekucnichSluzeb.log;
		final URL url = ka.getUrl();
		log.debug("SUBMITTING WEB DOWNLOAD  \"{}\" | {}", url, diagnosticsData);
		final ListenableFuture<ImageWithData> future = dvojiceExekucnichSluzeb.web.submit(() -> {

			// Pokud stahujeme backgroundově a jsou nějaké požadavny na online kachle, tak ty online mají absolutní přednost,
			// backgroundy brzíme.
			while (dvojiceExekucnichSluzeb.priority == Priority.STAHOVANI && exekucniSluzby.get(Priority.KACHLE).webQueue.size() > 0) {
				pocitBrzdeniOfflineKdyzJeOnline.inc();
				Thread.sleep(100);
			}
			final String klic = url.toExternalForm();
			if (mimoUzemi.getIfPresent(klic) != null) {
				return new ImageWithData(PRAZDNA_MIMO_UZEMI, new byte[0]);
			}
			if (nedavnoNestazene.getIfPresent(klic) != null) {
				throw new IOException("Dlaždici se nedávno nepodařilo stáhnout: " + url);
			}
			if (!kachlice.zahajStahovani(moje)) {
				throw new CancellationException("Dlaždici už nikdo nechce: " + url);
			}
			diagnosticsData.send("Web download - start");
			log.debug("DOWNLOAD START: \"{}\" | {}", url, diagnosticsData);
			ImageWithData imageWithData;
			try {
				imageWithData = downloader.downloadImage(url, ka.getType().getHlavicky());
				// Ukládá se hned, i když byl požadavek mezitím zrušen: stažená data by se jinak zahodila a stahovala znovu.
				ukladac.zaplanujUlozeni(new Ukladanec(ka, imageWithData.getData(), kachlice));
				log.debug("DOWNLOAD END  : \"{}\" | {}", url, diagnosticsData);
				diagnosticsData.send("Web download - end success");
				return imageWithData;
			} catch (final KachloDownloader.ChybaServeru e) {
				if (!ka.getType().jeMimoUzemi(e.getKod(), ka.getLoc())) {
					throw zaznamenejChybu(e, url, klic, diagnosticsData, log);
				}
				mimoUzemi.put(klic, Boolean.TRUE);
				diagnosticsData.send("Web download - mimo území podkladu");
				return new ImageWithData(PRAZDNA_MIMO_UZEMI, new byte[0]);
			} catch (final Exception e) {
				throw zaznamenejChybu(e, url, klic, diagnosticsData, log);
			}
		});
		return future;
	}

	private ListenableFuture<ImageWithData> submitRender(final Ka ka, final DvojiceExekucnichSluzeb dvojiceExekucnichSluzeb, final DiagnosticsData diagnosticsData, final Kachlice kachlice,
			final AtomicReference<Future<?>> moje) {
		return dvojiceExekucnichSluzeb.render.submit(() -> {
			while (dvojiceExekucnichSluzeb.priority == Priority.STAHOVANI && exekucniSluzby.get(Priority.KACHLE).renderQueue.size() > 0) {
				pocitBrzdeniOfflineKdyzJeOnline.inc();
				Thread.sleep(100);
			}
			if (!kachlice.zahajStahovani(moje)) {
				throw new CancellationException("Dlaždici už nikdo nechce: " + ka);
			}
			try {
				final OfflineRenderer renderer = offlineMapy.pouzij();
				try {
					final ImageWithData imageWithData = renderer.vyrendruj(ka.getLoc());
					pocitRenderOk.inc();
					if (renderer.pokryva(ka.getLoc())) {
						ukladac.zaplanujUlozeni(new Ukladanec(ka, renderer.getKlic(), imageWithData.getData(), kachlice));
					}
					return imageWithData;
				} finally {
					renderer.skonci();
				}
			} catch (final Exception e) {
				pocitRenderError.inc();
				chybyStahovani.computeIfAbsent("offline " + e.getClass().getName(), k -> new OpakovaneChyby("Chyba při vykreslení dlaždice offline mapy (" + e.getClass().getName() + ")")).ohlas(e);
				diagnosticsData.send(e.toString());
				throw e;
			}
		});
	}

	private Exception zaznamenejChybu(final Exception e, final URL url, final String klic, final DiagnosticsData diagnosticsData, final Logger log) {
		log.debug("DOWNLOAD ERROR  : \"{}\" | {}", url, e);
		if (!(e instanceof InterruptedException || e instanceof InterruptedIOException || Thread.currentThread().isInterrupted())) {
			nedavnoNestazene.put(klic, Boolean.TRUE);
		}
		chybyStahovani.computeIfAbsent(e.getClass().getName(), k -> new OpakovaneChyby("Chyba při stahování dlaždice (" + k + ")")).ohlas(e);
		diagnosticsData.send(e.toString());
		return e;
	}

	/**
	 * Zadá požadavekl na získání jedné kachle.
	 *
	 * @param kaoneReq
	 * @param imageReceiver
	 * @param aDiagnosticsData
	 * @return
	 */
	private Kanceler ziskejObsah(final DvojiceExekucnichSluzeb dvojiceExekucnichSluzeb, final KaOneReq kaoneReq, final DiagnosticsData aDiagnosticsData) {
		final ImageReceiver imageReceiver = kaoneReq.getImageReceiver();
		pocitSubmitJednaDlazdice.inc();
		final Ka kaone = kaoneReq.getKa();
		final DiagnosticsData diagnosticsData = aDiagnosticsData.with("kaOne", kaoneReq);

		final Kachlice kachlice = kachlmap.asMap().computeIfAbsent(kaone, k -> new Kachlice(k, dvojiceExekucnichSluzeb, diagnosticsData));
		(kachlice.image == null ? pocitMemMinuti : pocitMemZasah).inc();
		kachlice.ziskej(imageReceiver);
		return () -> {
			kachlice.kancluj(imageReceiver, diagnosticsData);
		};
	}
}
