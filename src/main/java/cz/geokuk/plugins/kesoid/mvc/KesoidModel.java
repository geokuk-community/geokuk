package cz.geokuk.plugins.kesoid.mvc;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import com.google.common.collect.Collections2;

import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.*;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.filtr.FilterDefinitionChangedEvent;
import cz.geokuk.plugins.kesoid.genetika.QualAlelaNames;
import cz.geokuk.plugins.kesoid.importek.MultiNacitac;
import cz.geokuk.plugins.kesoid.importek.MultiNacitacLoaderManager;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mapicon.*;
import cz.geokuk.plugins.vylety.EVylet;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.file.Filex;
import cz.geokuk.util.file.KeFile;
import lombok.Getter;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 *
 */
@Slf4j
public class KesoidModel extends Model0 {

	// FIXME tady nemohou být takovéto konstant, mohou se změnit
	private static final QualAlelaNames VYCHOZI_NECHTENE_ALELY = new QualAlelaNames("fnd:vztah", "dsbl:stav", "arch:stav");

	// Datamodelu
	private KesoidFilterModel filter;
	private QualAlelaNames jmenaAlelNaToolbaru;
	private QualAlelaNames jmenaNefenotypovanychAlel;
	private String jmenoSady = "neznama-sada";
	private IkonBag ikonBag;
	private KesBag vsechny;
	private GccomNick gccomNick;
	private ASada jmenoAktualniSadyIkon;
	private KesoidUmisteniSouboru umisteniSouboru;
	/** Mění se z EDT i z vlákna načítání: jen celou novou kopií v upravBlokovaneZdroje. */
	private volatile Set<File> blokovaneZdroje = Collections.emptySet();
	/** Složky, které se při posledním prohledání nepodařilo přečíst. */
	private volatile Set<File> nedostupnePriNacitani = Collections.emptySet();
	private GsakParametryNacitani gsakParametryNacitani;

	// injektovanci
	private final MultiNacitacLoaderManager multiNacitacLoaderManager = new MultiNacitacLoaderManager(this);
	private final IkonNacitacManager ikonNacitacLoaderManager = new IkonNacitacManager(this);
	private KesFilteringSwingWorker filteringSwingWorker;
	private ProgressModel progressModel;
	private Boolean onoff;
	private EZobrazeniKesi zobrazeniKesi;

	@Getter
	private KesoidPluginManager kesopidPluginManager;

	public void filtrujDleAlely(final String alelaName, final boolean zobrazit) {
		final Set<String> jmena = new HashSet<>(filter.getJmenaNechtenychAlel().getQualNames());
		boolean zmena;
		if (zobrazit) {
			zmena = jmena.remove(alelaName);
		} else {
			zmena = jmena.add(alelaName);
		}
		if (!zmena) {
			return; // není změna
		}
		setJmenaNechtenychAlel(new QualAlelaNames(jmena));
	}

	public FilterDefinition getDefinition() {
		return filter.getFilterDefinition().copy();
	}

	public KesoidFilterModel getFilter() {
		return filter;
	}

	/** Všechny načtené kešoidy, nebo null, dokud se nenačetly. */
	public KesBag getVsechnyKesoidy() {
		return vsechny;
	}

	public void prenactiKese() {
		startKesLoading();
	}

	public GccomNick getGccomNick() {
		return gccomNick;
	}

	public ASada getJmenoAktualniSadyIkon() {
		return jmenoAktualniSadyIkon;
	}

	public ProgressModel getProgressModel() {
		return progressModel;
	}

	/**
	 * @return the umisteniSouboru
	 */
	public KesoidUmisteniSouboru getUmisteniSouboru() {
		return umisteniSouboru;
	}

	public GsakParametryNacitani getGsakParametryNacitani() {
		return gsakParametryNacitani;
	}

	public void inject(final KesoidFilterModel filter) {
		this.filter = filter;
	}

	public void inject(final ProgressModel progressModel) {
		this.progressModel = progressModel;
	}

	public void inject(final KesoidPluginManager kesopidPluginManager) {
		this.kesopidPluginManager = kesopidPluginManager;
	}

	/**
	 * Databáze GSAKu z aktuálního prohledání. Při „Načítat až po vybrání“ se databáze, kterou GeoKuk při minulém prohledání neviděl, nenačte, dokud ji uživatel nevybere.
	 */
	public void zaradGsakDatabaze(final Set<File> databaze) {
		zaradGsakDatabaze(databaze, Collections.emptySet());
	}

	/** Známé databáze v nedostupných složkách zůstanou známé. */
	public synchronized void zaradGsakDatabaze(final Set<File> databaze, final Set<File> nedostupne) {
		final MyPreferences pref = currPrefe().node(FPref.KESOID_node);
		final Collection<File> zname = pref.getFileCollection(FPref.ZNAME_GSAK_DATABAZE_value, null);
		if (zname != null && !getGsakParametryNacitani().isNacistVsechnyDatabaze()) {
			final Set<File> nove = new HashSet<>(databaze);
			nove.removeAll(zname);
			upravBlokovaneZdroje(b -> b.addAll(nove));
		}
		final Set<File> noveZname = new HashSet<>(databaze);
		if (zname != null) {
			for (final File f : zname) {
				if (jePod(f, nedostupne)) {
					noveZname.add(f);
				}
			}
		}
		if (zname == null || !noveZname.equals(new HashSet<>(zname))) {
			pref.putFileCollection(FPref.ZNAME_GSAK_DATABAZE_value, noveZname);
		}
	}

	public void setNedostupnePriNacitani(final Set<File> nedostupne) {
		nedostupnePriNacitani = nedostupne;
	}

	private static boolean jePod(final File f, final Collection<File> slozky) {
		final Path cesta = f.toPath();
		return slozky.stream().anyMatch(s -> cesta.startsWith(s.toPath()));
	}

	/** Databáze, do které jiný program právě zapisuje; načte se, až zápis skončí. */
	public boolean jeZamcena(final KeFile jmenoZdroje) {
		return multiNacitacLoaderManager.jeZamcena(jmenoZdroje.getFile());
	}

	public boolean maSeNacist(final KeFile jmenoZdroje) {
		return !blokovaneZdroje.contains(jmenoZdroje.getFile());
	}

	public void onEvent(final IkonyNactenyEvent event) {
		jmenoSady = event.getBag().getSada().getName();
		if (!jmenoSady.equals(jmenoAktualniSadyIkon.name())) {
			// Vybraná sada chybí a načetla se náhradní, ať se příště nezkouší znovu.
			jmenoAktualniSadyIkon = Atom.valueOf(ASada.class, jmenoSady);
			currPrefe().node(FPref.JMENO_VYBRANE_SADY_IKON_node).putAtom(FPref.JMENO_VYBRANE_SADY_IKON_value, jmenoAktualniSadyIkon);
			fire(new JmenoAktualniSadyIkonChangeEvent(jmenoAktualniSadyIkon));
		}
		setJmenaNefenotypovanychAlel(currPrefe().node(FPref.MAPICON_FENOTYP_node).getQualAlelaNames(jmenoSady, QualAlelaNames.EMPTY));
	}

	public void onEvent(final KeskyNactenyEvent aEvent) {
		vycistiBlokovaneZdroje(aEvent.getVsechny().getInformaceOZdrojich().getJmenaZdroju());
		startIkonLoad(false);
	}

	public void otevriListingVGeogetu(final Kesoid kes) {
		if (kes == null) {
			return;
		}
		// Tisková URL existuje jen u starých odkazů s guid, jinak stačí běžná URL listingu.
		URL url = kes.getUrlPrint();
		if (url == null) {
			url = kes.getUrlShow();
		}
		if (url == null) {
			return;
		}
		final Clipboard scl = getSystemClipboard();
		final StringSelection ss = new StringSelection(url.toExternalForm());
		try {
			scl.setContents(ss, null);
		} catch (final IllegalStateException e2) {
			FExceptionDumper.dump(e2, EExceptionSeverity.WORKARROUND, "Kopírování do schránky");
		}
	}

	public void pridejDoSeznamuVGeogetu(final Kesoid kes) {
		if (kes == null) {
			return;
		}
		final URL url = kes.getUrlShow();
		if (url == null) {
			return;
		}
		final Clipboard scl = getSystemClipboard();
		final StringSelection ss = new StringSelection(url.toExternalForm());
		try {
			scl.setContents(ss, null);
		} catch (final IllegalStateException e2) {
			FExceptionDumper.dump(e2, EExceptionSeverity.WORKARROUND, "Kopírování do schránky");
		}
	}

	public void pridejKodKesoiduDoClipboardu(final Kesoid kes) {
		if (kes == null) {
			return;
		}
		final Clipboard scl = getSystemClipboard();
		final StringSelection ss = new StringSelection(kes.getIdentifier());
		try {
			scl.setContents(ss, null);
		} catch (final IllegalStateException e2) {
			FExceptionDumper.dump(e2, EExceptionSeverity.WORKARROUND, "Kopírování do schránky");
		}
	}

	/** Vrátí filtr i skryté typy keší na výchozí hodnoty. */
	public void nastavImplicitniFiltr() {
		setDefinition(new FilterDefinition());
		setJmenaNechtenychAlel(VYCHOZI_NECHTENE_ALELY);
	}

	public void setDefinition(final FilterDefinition filterDefinition) {
		if (filterDefinition.equals(filter.getFilterDefinition())) {
			return;
		}
		filter.setFilterDefinition(filterDefinition);
		currPrefe().putStructure(FPref.KESFILTER_structure_node, filterDefinition);
		currPrefe().node(FPref.KESFILTER_structure_node).putInt("prahVyletu", filterDefinition.getPrahVyletu().ordinal());
		fajruj();
	}

	public void setGccomNick(final GccomNick gccomNick) {
		if (gccomNick.equals(this.gccomNick)) {
			return;
		}
		this.gccomNick = gccomNick;
		currPrefe().node(FPref.NASTAVENI_node).put(FPref.GEOCACHING_COM_NICK_value, gccomNick.name);
		currPrefe().node(FPref.NASTAVENI_node).putInt(FPref.GEOCACHING_COM_NICK_ID_value, gccomNick.id);
		fire(new GccomNickChangedEvent(gccomNick));
		startKesLoading();
	}

	public void setIkonBag(final IkonBag ikonBag) {
		this.ikonBag = ikonBag;
		fire(new IkonyNactenyEvent(ikonBag, getJmenoAktualniSadyIkon()));
		startKesLoading();
	}

	public void setJmenaAlelNaToolbaru(final QualAlelaNames jmenaAlelNaToolbaru) {
		if (jmenaAlelNaToolbaru.equals(filter.getJmenaNechtenychAlel())) {
			return;
		}
		this.jmenaAlelNaToolbaru = jmenaAlelNaToolbaru;
		currPrefe().node(FPref.KESOID_FILTR_node).putQualAlelaNames(FPref.KESOID_FILTER_NATOOLBARU_value, jmenaAlelNaToolbaru);
		fajruj();
	}

	public void setJmenaNefenotypovanychAlel(final QualAlelaNames jmenaNefenotypovanychAlel) {
		if (jmenaNefenotypovanychAlel.equals(this.jmenaNefenotypovanychAlel)) {
			return;
		}
		this.jmenaNefenotypovanychAlel = jmenaNefenotypovanychAlel;
		currPrefe().node(FPref.MAPICON_FENOTYP_node).putQualAlelaNames(jmenoSady, jmenaNefenotypovanychAlel);
		fire(new FenotypPreferencesChangedEvent(jmenaNefenotypovanychAlel));
	}

	public void setJmenaNechtenychAlel(final QualAlelaNames jmenaNechtenychAlel) {
		if (jmenaNechtenychAlel.equals(filter.getJmenaNechtenychAlel())) {
			return;
		}
		filter.setJmenaNechtenychAlel(jmenaNechtenychAlel);
		currPrefe().node(FPref.KESOID_FILTR_node).putQualAlelaNames(FPref.KESOID_FILTER_ALELY_value, jmenaNechtenychAlel);
		fajruj();
	}

	public void setJmenoAktualniSadyIkon(final ASada jmenoAktualniSadyIkon) {
		if (jmenoAktualniSadyIkon.equals(jmenaAlelNaToolbaru)) {
			return;
		}
		this.jmenoAktualniSadyIkon = jmenoAktualniSadyIkon;
		currPrefe().node(FPref.JMENO_VYBRANE_SADY_IKON_node).putAtom(FPref.JMENO_VYBRANE_SADY_IKON_value, jmenoAktualniSadyIkon);
		fire(new JmenoAktualniSadyIkonChangeEvent(jmenoAktualniSadyIkon));
		startIkonLoad(true);
	}

	public void setNacitatSoubor(final KeFile jmenoZdroje, final boolean nacitat) {
		// TODO : speed up
		final Collection<File> changedFiles = Collections2.transform(vsechny.getInformaceOZdrojich().getSubtree(jmenoZdroje), informaceOZdroji -> informaceOZdroji.jmenoZdroje.getFile());
		log.debug("Změna nastavení načítání ({}): {}", nacitat, changedFiles);
		if (upravBlokovaneZdroje(b -> nacitat ? b.removeAll(changedFiles) : b.addAll(changedFiles))) {
			startKesLoading();
		}
	}

	public void setOnoff(final boolean onoff) {
		if (this.onoff != null && this.onoff == onoff) {
			return;
		}
		this.onoff = onoff;
		currPrefe().node(FPref.KESOID_node).putBoolean(FPref.KESOID_VISIBLE_value, onoff);
		fire(new KesoidOnoffEvent(onoff));
	}

	public void setZobrazeniKesi(final EZobrazeniKesi zobrazeniKesi) {
		if (this.zobrazeniKesi == zobrazeniKesi) {
			return;
		}
		this.zobrazeniKesi = zobrazeniKesi;
		currPrefe().node(FPref.KESOID_node).putEnum(FPref.ZOBRAZENI_KESI_value, zobrazeniKesi);
		fire(new ZobrazeniKesiEvent(zobrazeniKesi));
	}

	public void setPrekrocenLimitWaypointuVeVyrezu(final boolean prekrocenLimit) {
		fire(new PrekrocenLimitWaypointuVeVyrezuEvent(prekrocenLimit));
	}

	public void setGsakParametryNacitani(final GsakParametryNacitani aGsakParametryNacitani) {
		gsakParametryNacitani = aGsakParametryNacitani;
		final MyPreferences pref = currPrefe().node(FPref.GSAK_node);
		pref.putStringSet(FPref.GSAK_CAS_NALEZU_value, aGsakParametryNacitani.getCasNalezu());
		pref.putStringSet(FPref.GSAK_CAS_NENALEZU_value, aGsakParametryNacitani.getCasNenalezu());
		pref.putBoolean(FPref.GSAK_NACITAT_VSECHNO, aGsakParametryNacitani.isNacistVsechnyDatabaze());
		fire(new GsakParametryNacitaniChangedEvent(gsakParametryNacitani));
	}

	/**
	 * @param aUmisteniSouboru
	 *            the umisteniSouboru to set
	 */
	public void setUmisteniSouboru(final KesoidUmisteniSouboru aUmisteniSouboru) {
		if (aUmisteniSouboru.equals(this.umisteniSouboru)) {
			return;
		}
		final boolean nacistIkony = !aUmisteniSouboru.equalsImageLocations(umisteniSouboru);
		final boolean nacistKese = !aUmisteniSouboru.equalsDataLocations(umisteniSouboru);
		umisteniSouboru = aUmisteniSouboru;

		final MyPreferences pref = currPrefe().node(FPref.UMISTENI_SOUBORU_node);
		pref.putFilex(FPref.KES_DIR_value, aUmisteniSouboru.getKesDir());
		pref.putFilex(FPref.GEOGET_DATA_DIR_value, aUmisteniSouboru.getGeogetDataDir());
		pref.putFilex(FPref.GSAK_DATA_DIR_value, aUmisteniSouboru.getGsakDataDir());
		pref.remove("vyjimkyDir"); // mazat ze starých verzí
		synchronized (this) {
			blokovaneZdroje = new HashSet<>(currPrefe().node(FPref.KESOID_node).getFileCollection(FPref.BLOKOVANE_ZDROJE_value, new HashSet<File>()));
		}
		fire(new KesoidUmisteniSouboruChangedEvent(aUmisteniSouboru));
		if (nacistIkony) {
			startIkonLoad(true);
		} else { // když se načítají ikony, tak se vždy potom čtou keše
			if (nacistKese) {
				startKesLoading();
			}
		}
	}

	public void setVsechnyKesoidy(final KesBag vsechnyKesoidy) {
		vsechny = vsechnyKesoidy;
		spustFiltrovani();
		fire(new KeskyNactenyEvent(vsechnyKesoidy));
	}

	public void spustFiltrovani() {
		if (vsechny == null) {
			return;
		}
		if (filteringSwingWorker != null) {
			filteringSwingWorker.cancel(true);
		}
		filteringSwingWorker = new KesFilteringSwingWorker(vsechny, filter, this, getProgressModel());
		filteringSwingWorker.execute();
	}

	public void startIkonLoad(final boolean prenacti) {
		ikonNacitacLoaderManager.startLoad(prenacti);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.Model0#initAndFire()
	 */
	@Override
	protected void initAndFire() {
		final String gccomNickName = currPrefe().node(FPref.NASTAVENI_node).get(FPref.GEOCACHING_COM_NICK_value, "sem napište svůj nick na geocaching.com");
		final int gccomNickId = currPrefe().node(FPref.NASTAVENI_node).getInt(FPref.GEOCACHING_COM_NICK_ID_value, -1);

		setGccomNick(new GccomNick(gccomNickName, gccomNickId));
		final FilterDefinition filterDefinition = currPrefe().getStructure(FPref.KESFILTER_structure_node, new FilterDefinition());
		final int prahVyletuOrdinal = currPrefe().node(FPref.KESFILTER_structure_node).getInt("prahVyletu", filterDefinition.getPrahVyletu().ordinal());
		for (final EVylet vylet : EVylet.values()) {
			if (prahVyletuOrdinal == vylet.ordinal()) {
				filterDefinition.setPrahVyletu(vylet);
			}
		}
		filter.setFilterDefinition(filterDefinition);

		filter.setJmenaNechtenychAlel(currPrefe().node(FPref.KESOID_FILTR_node).getQualAlelaNames(FPref.KESOID_FILTER_ALELY_value, VYCHOZI_NECHTENE_ALELY));
		jmenaAlelNaToolbaru = currPrefe().node(FPref.KESOID_FILTR_node).getQualAlelaNames(FPref.KESOID_FILTER_NATOOLBARU_value, VYCHOZI_NECHTENE_ALELY);

		final ASada jmenoAktualniSadyIkon = currPrefe().node(FPref.JMENO_VYBRANE_SADY_IKON_node).getAtom(FPref.JMENO_VYBRANE_SADY_IKON_value, ASada.STANDARD, ASada.class);
		this.jmenoAktualniSadyIkon = jmenoAktualniSadyIkon;
		fire(new JmenoAktualniSadyIkonChangeEvent(jmenoAktualniSadyIkon));
		// fire(new GccomNickChangedEvent(gccomNick));
		// loadUmisteniSouboru();
		setGsakParametryNacitani(loadGsakParametryNacitani());
		setUmisteniSouboru(loadUmisteniSouboru());

		setOnoff(currPrefe().node(FPref.KESOID_node).getBoolean(FPref.KESOID_VISIBLE_value, true));
		setZobrazeniKesi(currPrefe().node(FPref.KESOID_node).getEnum(FPref.ZOBRAZENI_KESI_value, EZobrazeniKesi.AUTOMATICKY, EZobrazeniKesi.class));
		fajruj();
	}

	private void fajruj() {
		fire(new FilterDefinitionChangedEvent(filter.getFilterDefinition(), filter.getJmenaNechtenychAlel(), jmenaAlelNaToolbaru));
		spustFiltrovani();
	}

	private KesoidUmisteniSouboru loadUmisteniSouboru() {
		final KesoidUmisteniSouboru u = new KesoidUmisteniSouboru();
		final MyPreferences pref = currPrefe().node(FPref.UMISTENI_SOUBORU_node);
		u.setKesDir(pref.getFilex("kesDir", KesoidUmisteniSouboru.KES_DIR));
		u.setCestyDir(KesoidUmisteniSouboru.CESTY_DIR);
		u.setGeogetDataDir(pref.getFilex("geogetDataDir", KesoidUmisteniSouboru.GEOGET_DATA_DIR));
		u.setGsakDataDir(pref.getFilex("gsakDataDir", KesoidUmisteniSouboru.GSAK_DATA_DIR));
		u.setImage3rdPartyDir(KesoidUmisteniSouboru.IMAGE_3RDPARTY_DIR);
		u.setImageMyDir(KesoidUmisteniSouboru.IMAGE_MY_DIR);
		u.setAnoGgtFile(KesoidUmisteniSouboru.ANO_GGT);
		u.setNeGgtFile(KesoidUmisteniSouboru.NE_GGT);
		return u;
	}

	private GsakParametryNacitani loadGsakParametryNacitani() {
		final GsakParametryNacitani g = new GsakParametryNacitani();
		final MyPreferences pref = currPrefe().node(FPref.GSAK_node);
		g.setCasNalezu(pref.getStringList(FPref.GSAK_CAS_NALEZU_value, Arrays.asList("UserData")));
		g.setCasNenalezu(pref.getStringList(FPref.GSAK_CAS_NENALEZU_value, Arrays.asList("UserData")));
		g.setNacistVsechnyDatabaze(pref.getBoolean(FPref.GSAK_NACITAT_VSECHNO, true));
		return g;
	}

	private void startKesLoading() {
		if (ikonBag != null && gccomNick != null) {
			multiNacitacLoaderManager.startLoad(true, ikonBag.getGenom());
		}
	}

	/** Zapomene blokované zdroje, které už nejsou; zdroje v dočasně nedostupné složce (síť, USB) zůstanou blokované. */
	void vycistiBlokovaneZdroje(final Set<File> zdroje) {
		final List<Path> nedostupne = new ArrayList<>();
		final KesoidUmisteniSouboru u = getUmisteniSouboru();
		if (u != null) {
			for (final Filex f : Arrays.asList(u.getKesDir(), u.getGeogetDataDir(), u.getGsakDataDir())) {
				final File slozka = f == null ? null : f.getEffectiveFileIfActive();
				if (slozka != null && !MultiNacitac.jeCitelnaSlozka(slozka)) {
					nedostupne.add(slozka.toPath());
				}
			}
		}
		final Set<File> priNacitani = nedostupnePriNacitani;
		upravBlokovaneZdroje(b -> b.removeIf(f -> !zdroje.contains(f) && nedostupne.stream().noneMatch(f.toPath()::startsWith) && !jePod(f, priNacitani)));
	}

	/** Upraví kopii blokovaných zdrojů; když se změnila, uloží ji. */
	private synchronized boolean upravBlokovaneZdroje(final java.util.function.Predicate<Set<File>> uprava) {
		final Set<File> nove = new HashSet<>(blokovaneZdroje);
		if (!uprava.test(nove)) {
			return false;
		}
		blokovaneZdroje = nove;
		currPrefe().node(FPref.KESOID_node).putFileCollection(FPref.BLOKOVANE_ZDROJE_value, nove);
		return true;
	}

}
