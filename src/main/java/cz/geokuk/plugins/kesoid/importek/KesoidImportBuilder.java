package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.framework.Progressor;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.GpxToWptContext;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.index2d.Indexator;
import cz.geokuk.util.procak.ProcakDispatcher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KesoidImportBuilder implements IImportBuilder, GpxToWptContext {



	private static final String PREFIX_BEZEJMENNYCH_WAYPOINTU = "Geokuk";
	static final String GEOCACHE = "Geocache";
	static final String GEOCACHE_FOUND = "Geocache Found";


	private final Genom genom;
	private KesBag kesBag;

	private int citacBezejmennychWaypintu;
	private InformaceOZdroji infoOCurrentnimZdroji;
	private final InformaceOZdrojich.Builder informaceOZdrojichBuilder = InformaceOZdrojich.builder();

	private final GccomNick gccomNick;
	private final ProgressModel progressModel;

	private final KesoidPluginManager kesoidPluginManager;

	private ProcakDispatcher<GpxWpt> gpxWptDispatcher;

	/** Waypointy v pořadí, v jakém půjdou do bagu, po úsecích: zdroj, který se četl, převzatý zdroj s hotovým indexem, waypointy vystavené až v {@link #done()}. */
	private final List<Usek> useky = new ArrayList<>();
	private Usek usek;
	/** Indexy zdrojů z dokončeného bagu, u kterých úsek obsahuje právě waypointy zdroje. */
	private final Map<File, Indexator<Wpt>> indexyZdroju = new HashMap<>();

	private static final class Usek {
		final File zdroj;
		final List<Wpt> wpty = new ArrayList<>();
		final Indexator<Wpt> hotovyIndex;

		Usek(final File zdroj, final Indexator<Wpt> hotovyIndex) {
			this.zdroj = zdroj;
			this.hotovyIndex = hotovyIndex;
		}
	}

	/** Jen jména, celé waypointy by při načítání zdvojnásobily potřebnou paměť. */
	private final Set<String> jmenaWaypointu = new HashSet<>(1023);
	private Predicate<KeFile> sledovaneZdroje = zdroj -> false;
	/** Waypointy sledovaných zdrojů podle souboru, aby šly příště převzít, když zdroj nepůjde přečíst. */
	private final Map<File, List<Wpt>> wptyPodleZdroje = new HashMap<>();
	private Wpt posledniVytvoreny;
	private InformaceOZdroji zdrojPoslednihoVytvoreneho;
	/** Vytvořené waypointy, které se zatím nevystavily (výjimečně, nebo procák waypoint zahodil). */
	private final Map<Wpt, InformaceOZdroji> nevystavene = new IdentityHashMap<>();
	private final Map<File, KliceZdroje.Sberac> kliceZdroju = new HashMap<>();
	private volatile KliceZdroje.Sberac sberacKlicu = new KliceZdroje.Sberac();

	public KesoidImportBuilder(final Genom genom, final GccomNick gccomNick, final ProgressModel progressModel, final KesoidPluginManager kesoidPluginManager) {
		this.genom = genom;
		this.gccomNick = gccomNick;
		this.progressModel = progressModel;
		this.kesoidPluginManager = kesoidPluginManager;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.plugins.kesoid.importek.IImportBuilder#addGpxWpt(cz.geokuk.plugins.kesoid.importek.GpxWpt)
	 */
	@Override
	public void addGpxWpt(final GpxWpt gpxwpt) {
		sberacKlicu.obsah(gpxwpt);
		if (gpxwpt.wgs == null || gpxwpt.wgs.lat < -85 || gpxwpt.wgs.lat > 85) {
			log.debug("Souradnice jsou mimo povoleny rozsah: {} - {}", gpxwpt.wgs, gpxwpt);
			return;
		}

		// vygenerovat jméno, pokud ho ještě nemáme
		final boolean generovane = gpxwpt.name == null;
		if (gpxwpt.name == null) {
			citacBezejmennychWaypintu++;
			gpxwpt.name = PREFIX_BEZEJMENNYCH_WAYPOINTU + citacBezejmennychWaypintu;
		}

		final boolean novy = jmenaWaypointu.add(gpxwpt.name);
		if (generovane) {
			sberacKlicu.bezejmenny();
		}
		sberacKlicu.pridej(KliceZdroje.klicJmena(gpxwpt.name));

		gpxwpt.iInformaceOZdroji = infoOCurrentnimZdroji; // aby si pamatoval, ze kterého je zdroje
		// a teď výpočty počtů
		infoOCurrentnimZdroji.pocetWaypointuCelkem++; // tak samozřejmě, že celkem je tam
		if (novy) {
			infoOCurrentnimZdroji.pocetWaypointuBranych++; // tak samozřejmě, že těch braných je také tam
			gpxWptDispatcher.dispatch(gpxwpt);
		}
	}

	@Override
	public void addTrackWpt(final GpxWpt wpt) {}

	@Override
	public void begTrack() {}

	@Override
	public void begTrackSegment() {}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.plugins.kesoid.importek.IImportBuilder#init()
	 */
	@Override
	public void init() {
		gpxWptDispatcher = kesoidPluginManager.createGpxWptProcakDispatcher(this,
				(gpxwpt, kepodr) -> {
					final Wpt wpt = new Wpt();
					wpt.setKepodr(kepodr);
					wpt.setWgs(gpxwpt.wgs);
					wpt.setElevation(urciElevation(gpxwpt));
					wpt.setName(gpxwpt.name);
					wpt.setNazev(vytvorNazev(gpxwpt));
					if (posledniVytvoreny != null) {
						nevystavene.put(posledniVytvoreny, zdrojPoslednihoVytvoreneho);
					}
					posledniVytvoreny = wpt;
					zdrojPoslednihoVytvoreneho = gpxwpt.iInformaceOZdroji;
					return wpt;
				});
	}
	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.plugins.kesoid.importek.IImportBuilder#done(cz.geokuk.plugins.kesoid.mapicon.Genom)
	 */
	@Override
	public void done() {
		zacniUsek(null, null);
		gpxWptDispatcher.done();
		final InformaceOZdrojich informaceOZdrojich = informaceOZdrojichBuilder.done();
//		Progressor progressor = progressModel.start(delkaTasku, "Vytvářím waypointy");

		// přesypeme do seznamu

		//////////////////////////////////////
		int pocet = 0;
		for (final Usek u : useky) {
			pocet += u.wpty.size();
		}
		log.debug("Indexuji waypointy: " + pocet);

		kesBag = new KesBag(genom, pocet);
		final Progressor progressor = progressModel.start(pocet, "Indexování");
		int citac = 0;
		try {
			for (final Usek u : useky) {
				kesBag.zacniUsek(u.hotovyIndex);
				for (final Wpt wpt : u.wpty) {
					kesBag.add(wpt);
					if (++citac % 1000 == 0) {
						progressor.setProgress(citac);
					}
				}
			}
			kesBag.setInformaceOZdrojich(informaceOZdrojich);
			kesBag.done();
		} finally {
			progressor.finish();
		}
		zapamatujIndexyZdroju();
		log.debug("Konec zpracování: " + pocet);
	}

	/** Index úseku se dá příště převzít se zdrojem, když úsek je jediný úsek zdroje a obsahuje přesně jeho waypointy se souřadnicemi ve stejném pořadí. */
	private void zapamatujIndexyZdroju() {
		final Map<File, Integer> pocetUseku = new HashMap<>();
		for (final Usek u : useky) {
			if (u.zdroj != null && !u.wpty.isEmpty()) {
				pocetUseku.merge(u.zdroj, 1, Integer::sum);
			}
		}
		for (int i = 0; i < useky.size(); i++) {
			final Usek u = useky.get(i);
			final Indexator<Wpt> index = kesBag.getIndexUseku(i);
			if (u.zdroj == null || index == null || pocetUseku.getOrDefault(u.zdroj, 0) != 1) {
				continue;
			}
			final List<Wpt> zdroje = wptyPodleZdroje.get(u.zdroj);
			if (zdroje != null && stejneSeSouradnicemi(u.wpty, zdroje)) {
				indexyZdroju.put(u.zdroj, index);
			}
		}
	}

	private static boolean stejneSeSouradnicemi(final List<Wpt> a, final List<Wpt> b) {
		final Iterator<Wpt> ia = a.iterator();
		final Iterator<Wpt> ib = b.iterator();
		while (true) {
			final Wpt wa = dalsiSeSouradnicemi(ia);
			final Wpt wb = dalsiSeSouradnicemi(ib);
			if (wa != wb) {
				return false;
			}
			if (wa == null) {
				return true;
			}
		}
	}

	private static Wpt dalsiSeSouradnicemi(final Iterator<Wpt> it) {
		while (it.hasNext()) {
			final Wpt w = it.next();
			if (!w.hasEmptyCoords()) {
				return w;
			}
		}
		return null;
	}

	/** Indexy zdrojů z posledního {@link #done()}, které jde příště převzít se zdrojem. */
	Map<File, Indexator<Wpt>> getIndexyZdroju() {
		return indexyZdroju;
	}

	private void zacniUsek(final File zdroj, final Indexator<Wpt> hotovyIndex) {
		usek = new Usek(zdroj, hotovyIndex);
		useky.add(usek);
	}

	private void pridej(final Wpt wpt) {
		if (usek == null) {
			zacniUsek(null, null);
		}
		usek.wpty.add(wpt);
	}

	@Override
	public void endTrack() {}

	@Override
	public void endTrackSegment() {}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.plugins.kesoid.importek.IImportBuilder#getKesBag()
	 */
	public KesBag getKesBag() {
		return kesBag;
	}



	public void setSledovaneZdroje(final Predicate<KeFile> sledovaneZdroje) {
		this.sledovaneZdroje = sledovaneZdroje;
	}

	@Override
	public void vazba(final GpxWpt gpxwpt, final String klic) {
		final InformaceOZdroji zdroj = gpxwpt.iInformaceOZdroji;
		if (zdroj != null) {
			kliceZdroju.computeIfAbsent(zdroj.jmenoZdroje.getFile(), f -> new KliceZdroje.Sberac()).pridej(KliceZdroje.klic(klic));
		}
	}

	/** Klíče vazeb všech waypointů přečtených z každého zdroje (i těch zahozených jako duplicity) a otisk jeho obsahu. */
	Map<File, KliceZdroje> getKliceZdroju() {
		final Map<File, KliceZdroje> vysledek = new HashMap<>();
		for (final Map.Entry<File, KliceZdroje.Sberac> e : kliceZdroju.entrySet()) {
			vysledek.put(e.getKey(), e.getValue().hotovo());
		}
		return vysledek;
	}

	public Map<File, List<Wpt>> getWptyPodleZdroje() {
		return wptyPodleZdroje;
	}

	public boolean maWaypointyZe(final File zdroj) {
		return wptyPodleZdroje.containsKey(zdroj);
	}

	private void zaznamenejZdroj(final InformaceOZdroji zdroj, final Wpt wpt) {
		if (zdroj != null && sledovaneZdroje.test(zdroj.jmenoZdroje)) {
			wptyPodleZdroje.computeIfAbsent(zdroj.jmenoZdroje.getFile(), f -> new ArrayList<>()).add(wpt);
		}
	}

	/**
	 * Převezme kešoidy zdroje z minulého načtení (hlavní waypoint z toho zdroje) i s jejich waypointy. Kešoid, jehož hlavní waypoint už je načtený z jiného zdroje, se přeskočí.
	 */
	public void prevezmi(final KeFile zdroj, final List<Wpt> stareWpty, final InformaceOZdroji stareInformace) {
		prevezmi(zdroj, stareWpty);
		if (stareInformace != null) {
			infoOCurrentnimZdroji.pocetWaypointuCelkem = stareInformace.pocetWaypointuCelkem;
			infoOCurrentnimZdroji.pocetWaypointuBranych = stareInformace.pocetWaypointuBranych;
		}
	}

	/**
	 * Převezme waypointy zdroje ze skupiny minulého načtení, se kterou se nic jiného nepřekrývá: kešoidy už jsou spárované, neposílají se znovu procákům. Waypoint bez
	 * souřadnic v minulém bagu nebyl a z kruhu kešoidu už je vyřazený, podruhé se přidat nesmí.
	 */
	void prevezmiZeSkupiny(final KeFile zdroj, final List<Wpt> stareWpty, final int celkem, final int brano, final Indexator<Wpt> index) {
		setCurrentlyLoading(zdroj, true);
		zacniUsek(zdroj.getFile(), index);
		for (final Wpt wpt : stareWpty) {
			if (!wpt.hasEmptyCoords()) {
				pridej(wpt);
			}
		}
		wptyPodleZdroje.put(zdroj.getFile(), stareWpty);
		infoOCurrentnimZdroji.pocetWaypointuCelkem = celkem;
		infoOCurrentnimZdroji.pocetWaypointuBranych = brano;
	}

	private void prevezmi(final KeFile zdroj, final List<Wpt> stareWpty) {
		setCurrentlyLoading(zdroj, true);
		for (final Wpt hlavni : stareWpty) {
			if (!hlavni.isMainWpt() || jmenaWaypointu.contains(hlavni.getName())) {
				continue;
			}
			for (final Wpt wpt : hlavni.getKesoid().getWpts()) {
				if (jmenaWaypointu.add(wpt.getName())) {
					pridej(wpt);
					zaznamenejZdroj(infoOCurrentnimZdroji, wpt);
				}
			}
		}
	}

	/** Počty waypointů právě načítaného zdroje: celkem a braných. */
	public synchronized int[] getPoctyCurrent() {
		return new int[] { infoOCurrentnimZdroji.pocetWaypointuCelkem, infoOCurrentnimZdroji.pocetWaypointuBranych };
	}

	public synchronized void setCurrentlyLoading(final KeFile aJmenoZdroje, final boolean nacteno) {
		if (usek == null || usek.hotovyIndex != null || !aJmenoZdroje.getFile().equals(usek.zdroj)) {
			zacniUsek(aJmenoZdroje.getFile(), null);
		}
		infoOCurrentnimZdroji = informaceOZdrojichBuilder.add(aJmenoZdroje, nacteno);
		sberacKlicu = kliceZdroju.computeIfAbsent(aJmenoZdroje.getFile(), f -> new KliceZdroje.Sberac());
	}

	@Override
	public void setTrackName(final String aTrackName) {}

	protected EKesStatus urciStatus(final boolean archived, final boolean availaible) {
		if (archived) {
			return EKesStatus.ARCHIVED;
		} else if (!availaible) {
			return EKesStatus.DISABLED;
		} else {
			return EKesStatus.ACTIVE;
		}
	}



	@Override
	public Set<Alela> definujUzivatslskeAlely(final GpxWpt gpxwpt) {
		final Set<Alela> alely = new HashSet<>();

		for (final Map.Entry<String, String> entry : gpxwpt.gpxg.userTags.entrySet()) {
			final String alelaName = entry.getValue();
			final String genName = entry.getKey();
			final Alela alela = genom.gen(genName).alela(alelaName);
			if (alela == null) {
				continue;
			}
			alely.add(alela);
			genom.UNIVERZALNI_DRUH.addGen(alela.getGen());
		}

		return alely.isEmpty() ? Collections.emptySet() : alely;
	}





	private int urciElevation(final GpxWpt gpxwpt) {
		if (gpxwpt.ele != 0) {
			return (int) gpxwpt.ele;
		} else {
			if (gpxwpt.gpxg != null) {
				return gpxwpt.gpxg.elevation;
			} else {
				return 0;
			}
		}
	}


	private String vytvorNazev(final GpxWpt gpxwpt) {
		String s;
		if (gpxwpt.desc == null) {
			if (gpxwpt.cmt == null) {
				s = "?";
			} else {
				s = gpxwpt.cmt;
			}
		} else {
			if (gpxwpt.cmt == null) {
				s = gpxwpt.desc;
			} else {
				if (gpxwpt.cmt.toLowerCase().contains(gpxwpt.desc.toLowerCase())) {
					s = gpxwpt.desc;
				} else {
					s = gpxwpt.desc + ", " + gpxwpt.cmt;
				}
			}
		}
		return s;
	}



	@Override
	public Genom getGenom() {
		return genom;
	}

	@Override
	public GccomNick getGccomNick() {
		return gccomNick;
	}

	/** Procák waypoint obvykle hned po vytvoření vystaví; vytvořený a zahozený do bagu nepatří, a tak ani do waypointů zdroje. */
	@Override
	public void expose(final Wpt wpt) {
		pridej(wpt);
		final InformaceOZdroji zdroj;
		if (wpt == posledniVytvoreny) {
			zdroj = zdrojPoslednihoVytvoreneho;
			posledniVytvoreny = null;
		} else {
			zdroj = nevystavene.remove(wpt);
		}
		zaznamenejZdroj(zdroj, wpt);
	}



}
