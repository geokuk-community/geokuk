/**
 *
 */
package cz.geokuk.plugins.cesty;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingWorker;

import cz.geokuk.core.coord.*;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Mouable;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;
import cz.geokuk.plugins.cesty.routovani.TrasyUseku;
import cz.geokuk.plugins.kesoid.*;
import cz.geokuk.plugins.kesoid.mvc.KeskyNactenyEvent;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboruChangedEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.lang.FUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 *
 */
@Slf4j
public class CestyModel extends Model0 {

	private class InvalidacniPesek {
		private Set<Wpt> puvodnici = new HashSet<>();

		public InvalidacniPesek(final Iterable<Wpt> wpts) {
			FUtil.addAll(puvodnici, wpts);
		}

		void invaliduj() {
			assert puvodnici != null : "Pokus o duplicitní použití invalidačního peška";
			for (final Wpt wpt : doc.getWpts()) {
				final boolean removedNow = puvodnici.remove(wpt);
				if (!removedNow) { // to znamená, že v čase vytváření peška tam nebyl
					wpt.invalidate();
				}
			}
			// Vše, co tam zbyle dnes nemáme a tedy invalidujeme
			for (final Wpt wpt : puvodnici) {
				wpt.invalidate();
			}
			// znemožníme invalidačního peška
			puvodnici = null;

		}

	}

	private class Worker extends SwingWorker<Void, Void> {

		private final List<Kesoid> kese;

		public Worker(final List<Kesoid> kese) {
			this.kese = kese;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see javax.swing.SwingWorker#doInBackground()
		 */
		@Override
		protected Void doInBackground() throws Exception {
			final Clipboard scl = getSystemClipboard();
			for (final Kesoid kes : kese) {
				if (isCancelled()) {
					break;
				}
				scl.setContents(new StringSelection(kes.getUrlShow().toExternalForm()), null);
				Thread.sleep(100);
			}
			return null;
		}

		@Override
		protected void done() {
			if (isCancelled()) {
				return;
			}
			try {
				get();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (final ExecutionException e) {
				FExceptionDumper.dump(e.getCause(), EExceptionSeverity.DISPLAY, "Kopírování odkazů do schránky");
			}
		}

	}


	private static final String MUJ_VYLET = "Můj výlet";

	public static final String VYLET_EXTENSION = "gpx";
	private static final String PANEL_CESTY_value = "panelCesty";
	private Doc doc = new Doc();

	private final Updator updator = new Updator();

	/** Aktivní cesta, vždy je nějaká aktivní, když ne platí bod číslo 1 */
	private Cesta curta;
	private CestyZperzistentnovac cestyZperzistentnovac;

	private Worker worker;

	private KesBag kesBag;
	private KesoidModel kesoidModel;
	private PoziceModel poziceModel;

	private Coord moord;

	private boolean probihaPridavani;

	private KachleModel kachleModel;
	/** Hledání tras úseků po cestě; založí se, až je potřeba. */
	private TrasyUseku trasyUseku;
	/** Úseky, které dostaly typ podle {@link #getTypNovychUseku()}, ne volbou uživatele; bez offline mapy z nich budou úseky rovně. */
	private final Set<Usek> automatickyPoCeste = Collections.newSetFromMap(new WeakHashMap<>());

	public void addToVylet(final Mouable mouable) {
		pridejBodNaMisto(mouable);
	}

	public void bezNaBod(final Bod bod) {
		poziceModel.setPozice(bod);
	}

	public void bezNaBodVpred() {
		final Bod bod = doc.findBod(poziceModel.getPoziceq().getPoziceMouable());
		if (bod != null) {
			if (!bod.isCil()) {
				poziceModel.setPozice(bod.getBvpred());
			}
		} else {
			bezNaZacatekCesty(curta);
		}
	}

	public void bezNaBodVzad() {
		final Bod bod = doc.findBod(poziceModel.getPoziceq().getPoziceMouable());
		if (bod != null) {
			if (!bod.isStart()) {
				poziceModel.setPozice(bod.getBvzad());
			}
		} else {
			bezNaKonecCesty(curta);
		}
	}

	public void bezNaKonecCesty(Cesta cesta) {
		if (cesta == null) {
			cesta = doc.findNejblizsiCesta(moord.getMoustred());
		}
		if (cesta == null) {
			return;
		}
		curta = cesta;
		poziceModel.setPozice(cesta.getCil());
	}

	public void bezNaZacatekCesty(Cesta cesta) {
		if (cesta == null) {
			cesta = doc.findNejblizsiCesta(moord.getMoustred());
		}
		if (cesta == null) {
			return;
		}
		curta = cesta;
		poziceModel.setPozice(cesta.getStart());
	}

	public File defaultAktualnihoVyletuFile() {
		final File defaultFile = new File(kesoidModel.getUmisteniSouboru().getCestyDir().getEffectiveFile(), "Můj výlet.gpx");
		return currPrefe().node(FPref.VYLET_node).getFile(FPref.AKTUALNI_SOUBOR_value, defaultFile);
	}

	public File defaultExportuDoGgt() {
		return new File(kesoidModel.getUmisteniSouboru().getGeogetDataDir().getEffectiveFile(), "lovim.ggt");
	}

	public void exportujDoGgt(final File file, final Doc doc2) throws IOException {
		cestyZperzistentnovac.zapisGgt(doc, file);
	}

	public Cesta getCurta() {
		return curta;
	}

	public Doc getDoc() {
		return doc;
	}

	public File getImplicitniVyletNovyFile() {
		final File dir = defaultAktualnihoVyletuFile().getParentFile();
		final File result = najdiNeexistujiciSoubor(new File(dir, MUJ_VYLET + "." + VYLET_EXTENSION));
		return result;
	}

	public File getImplicitniVyletSaveAsNovyFile() {
		final File file = doc.getFile();
		if (file == null) {
			return getImplicitniVyletNovyFile();
		} else {
			final File result = najdiNeexistujiciSoubor(file);
			return result;
		}
	}

	public File getImplicitniVyletSaveCopyNovyFile() {
		final File file = doc.getFile();
		if (file == null) {
			return getImplicitniVyletNovyFile();
		} else {
			final File result = najdiNeexistujiciSoubor(new File(file.getParentFile(), "Kopie " + file.getName()));
			return result;
		}
	}

	public int getPocetWaypointuVeVyletu() {
		return doc.getPocetWaypointu();
	}

	public void importuj(final List<File> files) {
		final CestyImportSwingWorker wrk = new CestyImportSwingWorker(cestyZperzistentnovac, kesBag, this, files);
		wrk.execute();
	}

	public void inject(final CestyZperzistentnovac cestyZperzistentnovac) {
		this.cestyZperzistentnovac = cestyZperzistentnovac;
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	public void inject(final PoziceModel poziceModel) {
		this.poziceModel = poziceModel;
	}

	public boolean isOnVylet(final Mouable mouable) {
		if (mouable == null) {
			return false;
		}
		if (mouable instanceof Wpt) {
			final Wpt wpt = (Wpt) mouable;
			return doc.hasWpt(wpt);
		}
		if (mouable instanceof Bod) {
			return true;
		}
		return false;
	}

	public void nasypVyletDoGeogetu() {
		if (worker != null) {
			worker.cancel(true);
		}
		final List<Kesoid> kesoidy = new ArrayList<>();
		for (final Wpt wpt : doc.getWpts()) {
			kesoidy.add(wpt.getKesoid());
		}
		worker = new Worker(kesoidy);
		worker.execute();
	}

	public void odeberBod(final Mouable mouable) {
		final Bod bod = doc.findBod(mouable);
		if (bod == null) {
			return;
		}
		removeBod(bod);
		fireCesta();
	}

	public void onEvent(final KeskyNactenyEvent aEvent) {
		kesBag = aEvent.getVsechny();
	}

	public void onEvent(final PoziceSeMaMenitEvent event) {
		for (final Bod bod : doc.getBody()) {
			if (event.mou.equals(bod.getMou())) {
				int priorita = 20;
				if (bod.isStart()) {
					priorita = 21;
				}
				if (bod.isCil()) {
					priorita = 22;
				}
				event.add(bod, priorita);
			}
		}
	}

	public void onEvent(final VyrezChangedEvent aEvent) {
		moord = aEvent.getMoord();
	}

	public void otevri(final File file) {
		final Doc doc = new Doc();
		doc.setFile(file);
		final CestyOtevriSwingWorker wrk = new CestyOtevriSwingWorker(cestyZperzistentnovac, kesBag, this, file);
		wrk.execute();
	}

	public void pospojujVzdusneUseky(final Cesta cesta) {
		updator.pospojujVzdusneUseky(cesta);
		fireCesta();
	}

	public void prepniVzdusnostUseku(final Usek usek, final boolean vzdusny) {
		if (usek.isVzdusny() == vzdusny) {
			return;
		}
		nastavTypUseku(usek, vzdusny ? TypUseku.VZDUSNY : getTypNovychUseku());
	}

	/** Typ úseku zvolený uživatelem. */
	public void nastavTypUseku(final Usek usek, final TypUseku typ) {
		automatickyPoCeste.remove(usek);
		if (usek.getTyp() == typ) {
			return;
		}
		updator.setTyp(usek, typ);
		fireCesta();
	}

	/** Typ nových úseků: po cestě, nebo rovně. */
	public TypUseku getTypNovychUseku() {
		final String typ = currPrefe().node(FPref.VYLET_node).get(FPref.TYP_NOVYCH_USEKU_value, TypUseku.PO_CESTE.name());
		return TypUseku.ROVNE.name().equals(typ) ? TypUseku.ROVNE : TypUseku.PO_CESTE;
	}

	public void setTypNovychUseku(final TypUseku typ) {
		if (typ == TypUseku.VZDUSNY) {
			throw new IllegalArgumentException("Nové úseky nemohou být vzdušné");
		}
		if (typ == getTypNovychUseku()) {
			return;
		}
		currPrefe().node(FPref.VYLET_node).put(FPref.TYP_NOVYCH_USEKU_value, typ.name());
		fireCesta();
	}

	/** Znovu hledá trasy úseků po cestě, které nebyly nalezeny nebo neměly offline mapu. */
	public void prepocitejUsekyPoCeste() {
		trasyUseku().prepocitej(doc);
	}

	/** Pro úsek se právě hledá trasa po cestách. */
	public boolean isHledaSeTrasa(final Usek usek) {
		return trasyUseku != null && trasyUseku.isPocita(usek);
	}

	private TrasyUseku trasyUseku() {
		if (trasyUseku == null) {
			trasyUseku = new TrasyUseku(this::slozkaOfflineMap, this::poVypoctuTrasy);
		}
		return trasyUseku;
	}

	/** Složka offline map; null, dokud mapy nemají načtené umístění souborů (při startu). */
	File slozkaOfflineMap() {
		final KachleUmisteniSouboru umisteni = kachleModel == null ? null : kachleModel.getUmisteniSouboru();
		return umisteni == null ? null : umisteni.getOfflineMapyDir().getEffectiveFile();
	}

	public void onEvent(final KachleUmisteniSouboruChangedEvent event) {
		dopocitejTrasy();
	}

	private void poVypoctuTrasy(final Usek usek) {
		if (usek.getStavTrasy() == Trasa.Stav.BEZ_MAPY && automatickyPoCeste.remove(usek)) {
			updator.setTyp(usek, TypUseku.ROVNE);
		}
		fireCesta();
	}

	/** Nově vzniklý úsek dostane typ nových úseků. */
	private void novyUsek(final Usek usek) {
		if (usek == null) {
			return;
		}
		final TypUseku typ = getTypNovychUseku();
		updator.setTyp(usek, typ);
		if (typ == TypUseku.PO_CESTE) {
			automatickyPoCeste.add(usek);
		}
	}

	public void presunBod(final Bod bb, Mouable mouable) {
		while (mouable instanceof Bod) {
			final Bod bod = (Bod) mouable;
			mouable = bod.getMouable();
		}
		invalidate(mouable);
		invalidate(bb);
		if (bb.isStartocil()) {
			updator.setMouable(bb.getCesta().getStart(), mouable);
			updator.setMouable(bb.getCesta().getCil(), mouable);
		} else {
			updator.setMouable(bb, mouable);
		}
		fireCesta();
	}

	public void presunoutVyhoziBod(final Bod bod) {
		final Cesta cesta = bod.getCesta();
		final Cesta novaCesta = updator.rozdelCestuVBode(bod);
		updator.pipojitCestuPred(cesta, novaCesta);
		fireCesta();
	}

	public Bod pridejBodNaKonec(final Mouable mouable) {
		if (curta == null) {
			final Cesta cesta = Cesta.create();
			updator.xadd(doc, cesta);
			curta = cesta;
		}
		final Bod cilPred = getCurta().getCil();
		final Bod bod = updator.pridejNaKonec(getCurta(), mouable);
		if (bod != cilPred) {
			novyUsek(bod.getUvzad());
		}
		fireCesta();
		return bod;
	}

	public void pridejBodNaMisto(final Mouable mouable) {
		if (curta == null) {
			Cesta cesta = doc.findNejblizsiCesta(mouable.getMou());
			if (cesta == null) {
				cesta = Cesta.create();
			}
			updator.xadd(doc, cesta);
			curta = cesta;
		}
		final Bod startPred = getCurta().getStart();
		final Bod cilPred = getCurta().getCil();
		final Bod bod = updator.pridejNaMisto(getCurta(), mouable);
		// Bod vložený doprostřed úseku převezme jeho typ; nový úsek je jen na začátku nebo na konci cesty.
		if (bod == getCurta().getCil() && bod != cilPred) {
			novyUsek(bod.getUvzad());
		} else if (bod == getCurta().getStart() && bod != startPred) {
			novyUsek(bod.getUvpred());
		}
		fireCesta();
	}

	public void pripojitCestuPred(final Cesta cesta1, final Cesta cesta2) {
		doc.kontrolaKonzistence();
		cesta1.kontrolaKonzistence();
		cesta2.kontrolaKonzistence();
		updator.pipojitCestuPred(cesta1, cesta2);
		curta = cesta1;
		curta.kontrolaKonzistence();
		doc.kontrolaKonzistence();
		fireCesta();
	}

	public void pripojitCestuZa(final Cesta cesta1, final Cesta cesta2) {
		doc.kontrolaKonzistence();
		cesta1.kontrolaKonzistence();
		cesta2.kontrolaKonzistence();
		updator.pipojitCestuZa(cesta1, cesta2);
		curta = cesta1;
		curta.kontrolaKonzistence();
		doc.kontrolaKonzistence();
		fireCesta();
	}

	public Usek removeBod(final Bod bb) {
		final Usek usek = updator.removeBod(bb);
		fireCesta();
		return usek;
	}

	public void removeCestu(final Cesta cesta) {
		for (final Bod bod : cesta.getBody()) {
			invalidate(bod);
		}
		updator.remove(cesta);
		fireCesta();
	}

	public void reverseCestu(final Cesta cesta) {
		updator.reverse(cesta);
		fireCesta();
	}

	public void rozdelCestuVBode(final Bod bod, final boolean aSmazOdriznutyKus) {
		invalidate(doc);
		final Cesta novaCesta = updator.rozdelCestuVBode(bod);
		if (aSmazOdriznutyKus) {
			final InvalidacniPesek invalidacniPesek = invalidacniPesek();
			updator.remove(novaCesta);
			invalidacniPesek.invaliduj();
		}
		fireCesta();
	}

	public void rozdelCestuVUseku(final Usek usek, final Mou mou, final boolean aSmazOdriznutyKus) {
		invalidate(doc);
		final Cesta novaCesta = updator.rozdelCestuVUseku(usek, mou);
		if (aSmazOdriznutyKus) {
			final InvalidacniPesek invalidacniPesek = invalidacniPesek();
			updator.remove(novaCesta);
			invalidacniPesek.invaliduj();
		}
		fireCesta();
	}

	public Bod rozdelUsekNaDvaNove(final Usek usek, final Mou mouNovy) {
		final Bod bod = updator.rozdelUsekNaDvaNove(usek, mouNovy);
		fireCesta();
		return bod;
	}

	public void setPridavaniBodu(final boolean probihaPridavani) {
		if (this.probihaPridavani == probihaPridavani) {
			return;
		}
		this.probihaPridavani = probihaPridavani;
		fire(new PridavaniBoduEvent(probihaPridavani));
	}

	public void smazatUsekAOtevritNeboRozdelitCestu(final Usek usek) {
		updator.smazatUsekAOtevritNeboRozdelitCestu(usek);
		fireCesta();

	}

	public void spojCestyVPrekryvnemBode(final Bod bod) {
		final Bod druhyBod = bod.getKoncovyBodDruheCestyVhodnyProSpojeni();
		if (druhyBod == null) {
			return;
		}
		if (bod.isCil()) {
			updator.pipojitCestuZa(bod.getCesta(), druhyBod.getCesta());
		} else if (bod.isStart()) {
			updator.pipojitCestuPred(bod.getCesta(), druhyBod.getCesta());
		}
		fireCesta();
	}

	public void uloz(final File file, final Doc doc, final boolean statSeImplicitniProDokument) throws IOException {
		new Ukladac().uloz(file, doc);
		if (statSeImplicitniProDokument) {
			doc.setFile(file);
			doc.resetChanged();
			setDefaultProAktualniVyletFile(file);
		}
		fireCesta();
	}

	public void uzavriCestu(final Cesta cesta) {
		if (cesta.isKruh()) {
			return;
		}
		final Bod cilPred = cesta.getCil();
		final Bod bod = updator.pridejNaKonec(cesta, cesta.getStart().getMouable());
		if (bod != cilPred) {
			novyUsek(bod.getUvzad());
		}
		fireCesta();
	}

	public void zavri() {
		invalidate(doc);
		doc = new Doc();
		currPrefe().node(FPref.VYLET_node).putBoolean(FPref.JE_OTEVRENY_VYLET_value, false);
		fireCesta();
	}

	public void znovuVsechnoPripni() {
		cestyZperzistentnovac.pripniNaWayponty(doc.getCesty(), kesBag);
		fireCesta();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.program.Model0#initAndFire()
	 */
	@Override
	protected void initAndFire() {
		if (otevreVyletPriStartu()) {
			otevri(currPrefe().node(FPref.VYLET_node).getFile(FPref.AKTUALNI_SOUBOR_value, null));
		}
		fireCesta();
		fire(new PridavaniBoduEvent(false)); // přidávání neprobíhá, tak aŤ se provede příslušný event
		fire(new PanelCestyEvent(isPanelCesty()));
	}

	/** Panel s údaji o vybrané cestě v rohu mapy. */
	public boolean isPanelCesty() {
		return currPrefe().node(FPref.VYLET_node).getBoolean(PANEL_CESTY_value, true);
	}

	public void setPanelCesty(final boolean zobrazit) {
		if (zobrazit == isPanelCesty()) {
			return;
		}
		currPrefe().node(FPref.VYLET_node).putBoolean(PANEL_CESTY_value, zobrazit);
		fire(new PanelCestyEvent(zobrazit));
	}

	/** Při startu se otevře výlet, který byl otevřený při minulém ukončení. */
	public boolean otevreVyletPriStartu() {
		final boolean mameOtevritVylet = currPrefe().node(FPref.VYLET_node).getBoolean(FPref.JE_OTEVRENY_VYLET_value, false);
		final File file = currPrefe().node(FPref.VYLET_node).getFile(FPref.AKTUALNI_SOUBOR_value, null);
		return mameOtevritVylet && file != null && file.canRead();
	}

	void prevezmiImportovaneCesty(final List<Cesta> cesty) {
		for (final Cesta cesta : cesty) {
			doc.xadd(cesta);
		}
		invalidate(doc);
		fireCesta();
	}

	void prevezmiNoveOtevrenyDokument(final Doc doc) {
		if (doc.getFile() != null) {
			setDefaultProAktualniVyletFile(doc.getFile());
		}
		doc.resetChanged();
		invalidate(doc);
		invalidate(this.doc);
		this.doc = doc;
		fireCesta();
	}

	void setCurta(final Cesta curta) {
		if (this.curta == curta) {
			return;
		}
		this.curta = curta;
		fireCesta();
	}

	private void cleanCurta() {
		if (curta == null) {
			return;
		}
		for (final Cesta cesta : doc.getCesty()) {
			if (cesta == curta) {
				return;
			}
		}
		curta = null;
	}

	private void fireCesta() {
		cleanCurta();
		poziceModel.refreshPozice();
		if (curta != null) {
			curta.kontrolaKonzistence();
		}
		fire(new CestyChangedEvent(doc, curta));
		dopocitejTrasy();
	}

	private void dopocitejTrasy() {
		if (slozkaOfflineMap() == null) {
			return; // bez mapových podkladů (testy) se trasy nehledají
		}
		if (trasyUseku == null) {
			boolean potreba = false;
			for (final Usek usek : doc.getUseky()) {
				potreba |= usek.isPoCeste() && usek.getStavTrasy() == null;
			}
			if (!potreba) {
				return;
			}
		}
		trasyUseku().dopocitej(doc);
	}

	private InvalidacniPesek invalidacniPesek() {
		final InvalidacniPesek pesek = new InvalidacniPesek(doc.getWpts());
		return pesek;
	}

	private void invalidate(final Doc doc) {
		if (doc == null) {
			return;
		}
		for (final Bod bod : doc.getBody()) {
			invalidate(bod);
		}
	}

	private void invalidate(final Mouable mouable) {
		if (mouable instanceof Wpt) {
			final Wpt wpt = (Wpt) mouable;
			wpt.invalidate();
		} else if (mouable instanceof Bod) {
			final Bod bod = (Bod) mouable;
			invalidate(bod.getMouable());
		}
	}

	private File najdiNeexistujiciSoubor(final File aFile) {
		final File dir = aFile.getParentFile();
		final String pureName = aFile.getName();
		final Pattern pat = Pattern.compile("^(.*?)(?: *\\(\\d+\\))?(\\.[^.]+)?$");
		final Matcher mat = pat.matcher(pureName);
		log.debug(pureName + ": " + mat.matches() + " -- " + pat);
		log.debug(mat.group(1) + " * " + mat.group(2));
		// String baseName = poz < 0 ? pureName : pureName.substring(0, poz);
		// String extension = poz < 0 ? "" : pureName.substring(poz);
		final String baseName = mat.group(1);
		final String extension = mat.group(2);
		for (int i = 0;; i++) {
			final File file = new File(dir, baseName + (i == 0 ? "" : " (" + i + ")") + extension);
			if (!file.exists()) {
				return file;
			}
		}

	}

	private void setDefaultProAktualniVyletFile(final File file) {
		currPrefe().node(FPref.VYLET_node).putFile(FPref.AKTUALNI_SOUBOR_value, file);
		currPrefe().node(FPref.VYLET_node).putBoolean(FPref.JE_OTEVRENY_VYLET_value, true);
	}
}
