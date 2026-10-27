package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.*;
import java.util.List;

import javax.swing.UIManager;

import com.google.common.collect.LinkedListMultimap;

import cz.geokuk.core.coord.Coord;
import cz.geokuk.core.coord.JSingleSlide0;
import cz.geokuk.core.coord.PrvniNacitaniMapy;
import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.framework.AfterEventReceiverRegistrationInit;
import cz.geokuk.plugins.mapy.ZmenaMapNastalaEvent;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.OfflineMapaChangedEvent;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.podklady.DiagnostikaDlazdic;
import cz.geokuk.plugins.mapy.kachle.podklady.KaOneReq;
import cz.geokuk.plugins.mapy.kachle.podklady.Priority;
import cz.geokuk.util.pocitadla.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class JKachlovnik extends JSingleSlide0 implements AfterEventReceiverRegistrationInit {

	private static final long serialVersionUID = -6300199882447791157L;

	private static Pocitadlo pocitZustalychKachli = new PocitadloRoste("Počet znovu použitých kachlí",
			"Počet kachlí jKachle, které jako kompoenty zůstaly a jen se změnila její lokace, protože po reinicializaci "
					+ "byly na svém místě (obvykle posun) a nebylo je tudíž nutné znovu vytvářet");

	private static Pocitadlo pocitReinicializaceKachlovniku = new PocitadloRoste("Kolikrát bylo nuceno reinicializovat " + "celý kachlovník",
			"Říká, kolikrát byla zavolána metoda init pro reinicializaci celého kachlovníku v důsledku posunu, změnu " + "velikosti, zůůmování atd.");

	private static Pocitadlo pocitVynuceneNepouzitiExistujiciKachle = new PocitadloRoste("Vynucené použití neexistující " + "kachle",
			"Kolikrát do JKachlovnik.ini() přišlo z venku, že JKachle komponenty nesmím použít");

	private static final Pocitadlo pocitKachliVKachlovniku2 = new PocitadloMalo("#kachlí v kachlovníku", "");

	private EKaType katype = null;

	private KachleModel kachleModel;

	// TODO musí to být private a musí být na tru nastaveno jen při rendrování
	protected boolean vykreslovatokamzite;

	/** Jen pro ladící účely */
	public final String nazevKachlovniku;

	private final Priority priorita;

	/** Po výpadku sítě se chybné dlaždice zkoušejí znovu, i když uživatel s mapou nehne. */
	private final javax.swing.Timer opakovaniChybnych = new javax.swing.Timer((int) JKachle.PRVNI_POKUS_PO_CHYBE_MS, e -> zkusZnovuChybne());

	private PredvykresleniOkoli predvykresleni;

	private transient LogoNacitani logoNacitani;

	// je to jen kvuli garbage collectoru, aby nezrusil, NERUSIT PROMENNU i kdyz zdanlive je to na nic
	public JKachlovnik(final String nazevKachlovniku, final Priority priority) {
		this.nazevKachlovniku = nazevKachlovniku;
		priorita = priority;
		setLayout(null);
		setPreferredSize(new Dimension(800, 600));
		setBackground(Color.GREEN);
		setOpaque(false);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.framework.AfterEventReceiverRegistrationInit#initAfterEventReceiverRegistration()
	 */
	@Override
	public void initAfterEventReceiverRegistration() {
		registerEvents();
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	public void onEvent(final OnofflineModelChangeEvent event) {
		if (event.isOnlineMOde()) {
			// Při zapnutí online módu e musí překlreslit. To se stane i když není online mód právě zapínán, ale to nevadí.
			init(false);
		}
	}

	public void onEvent(final OfflineMapaChangedEvent event) {
		if (katype != null && katype.isOffline()) {
			init(false);
		}
	}

	public void onEvent(final ZmenaMapNastalaEvent event) {
		setKachloType(event.getKatype());
	}

	@Override
	public void onVyrezChanged() {
		init(true);
	}

	/**
	 * @param katype
	 *            the kachloTypes to set
	 */
	public void setKachloType(final EKaType katype) {
		if (this.katype == katype) {
			return;
		}
		this.katype = katype;
		init(false);
	}

	/** Jestli se má tam, kde dlaždice ještě nedorazily, kreslit slabé logo místo prázdna. */
	protected boolean kreslitLogoNacitani() {
		return false;
	}

	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		if (kreslitLogoNacitani()) {
			kresliLogoNacitani((Graphics2D) g);
		}
	}

	private void kresliLogoNacitani(final Graphics2D g) {
		final Path2D.Float cekajici = new Path2D.Float(Path2D.WIND_NON_ZERO);
		int dlazdic = 0;
		int hotovych = 0;
		boolean chyba = false;
		for (final Component c : getComponents()) {
			if (c instanceof JKachle) {
				final JKachle dlazdice = (JKachle) c;
				dlazdic++;
				if (dlazdice.maHotovyObrazek()) {
					hotovych++;
				}
				if (dlazdice.cekaNaObrazek()) {
					cekajici.append(c.getBounds(), false);
				} else if (dlazdice.jeChybna()) {
					chyba = true;
				}
			}
		}
		// Logo patří jen k prvnímu načítání mapy; po úplném načtení výřezu nebo první chybě už se neukáže.
		if (dlazdic > 0 && (chyba || cekajici.getCurrentPoint() == null)) {
			PrvniNacitaniMapy.ukonci();
		} else if (PrvniNacitaniMapy.jeDostNactenych(hotovych, dlazdic)) {
			PrvniNacitaniMapy.zobrazVrstvy();
		}
		if (cekajici.getCurrentPoint() == null) {
			return;
		}
		final Area oblast = new Area(cekajici);
		final Color pozadiPanelu = UIManager.getColor("Panel.background");
		final boolean tmave = pozadiPanelu != null && pozadiPanelu.getRed() + pozadiPanelu.getGreen() + pozadiPanelu.getBlue() < 3 * 128;
		final Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setColor(tmave ? pozadiPanelu : Color.WHITE);
			g2.fill(oblast);
		} finally {
			g2.dispose();
		}
		if (!PrvniNacitaniMapy.probiha()) {
			return;
		}
		if (logoNacitani == null) {
			logoNacitani = new LogoNacitani();
			PrvniNacitaniMapy.prekresliPoUkonceni(this);
		}
		logoNacitani.kresli(g, getWidth(), getHeight(), oblast, tmave);
	}

	protected KachleModel getKachleModel() {
		return kachleModel;
	}

	protected EKaType getKachloType() {
		return katype;
	}

	protected JKachle createJKachle(final Ka ka) {
		return new JKachle(this, ka);
	}

	protected void init(final boolean smimZnovuPouzitKachle) {
		if (!DiagnostikaDlazdic.zapnuto()) {
			inicializuj(smimZnovuPouzitKachle);
			return;
		}
		final long zacatek = System.nanoTime();
		try {
			inicializuj(smimZnovuPouzitKachle);
		} finally {
			DiagnostikaDlazdic.initKachlovniku(nazevKachlovniku, System.nanoTime() - zacatek, getComponentCount());
		}
	}

	private void inicializuj(final boolean smimZnovuPouzitKachle) {
		zrusPredvykresleni();
		if (!isSoordInitialized()) {
			return;
		}
		if (getWidth() == 0 || getHeight() == 0) {
			return; // nemá smysl rendrovat prázdný kachlovník
		}
		final Coord soord = getSoord();
		assert kachleModel != null;
		if (katype == null) {
			return;
		}
		pocitReinicializaceKachlovniku.inc();
		if (!smimZnovuPouzitKachle) {
			pocitVynuceneNepouzitiExistujiciKachle.inc();
		}
		;

		// Musí to být multimapa, protože při malých měřítcích je jedna kachle vícekrát zorbazena,
		// takže pro jeden klíč máme hodně hodnot
		final LinkedListMultimap<KaLoc, JKachle> mapaKachli = LinkedListMultimap.create();
		Arrays.stream(getComponents()).map(jka -> (JKachle) jka).forEach(jka -> {
			mapaKachli.put(jka.getKaLoc(), jka);
		});
		;
		log.trace("Mapa {} kachlí {}", getComponents().length, mapaKachli);
		final Kaputer kaputer = new Kaputer(soord);
		final List<JKachle> nove = new ArrayList<>();
		if (log.isTraceEnabled()) {
			log.trace("Vykreslovani kachli od {} pro {} -- {}", kaputer.getKachlePoint(0, 0), kaputer.getKachleMou(0, 0), kaputer);
		}
		for (int yi = 0; yi < kaputer.getPocetKachliY(); yi++) {
			log.trace(" .... řádek {} {}", yi, soord);
			for (int xi = 0; xi < kaputer.getPocetKachliX(); xi++) {
				final KaLoc kaloc = kaputer.getKaloc(xi, yi);
				final List<JKachle> seznamStejnychKachli = mapaKachli.get(kaloc);
				final boolean kachleSePouzije = smimZnovuPouzitKachle && seznamStejnychKachli.size() > 0;
				final JKachle jkachle;
				if (!kachleSePouzije) {
					log.trace("............... Vytváření JKachle" + kaloc);
					jkachle = createJKachle(new Ka(kaloc, katype));
					add(jkachle); // a přidat jako komponentu
					nove.add(jkachle);

				} else { // použije se původní kachle
					jkachle = seznamStejnychKachli.remove(0); // jednu z nich vezmeme, je jedno kterou, všechny mají stejný obsah
					pocitZustalychKachli.inc();
					jkachle.zkusZnovuPoChybe(kachleModel, priorita);
				}
				// napozicujeme každou kachli do správné podoby
				final Point p = kaputer.getKachlePoint(xi, yi);
				log.trace("....... vykresulji kachli {} na {}", kaloc, p);
				// System.out.println("KACHLE xx: " + new Point(x,y) + " ********* " + getSize());
				jkachle.setLocation(p.x, p.y);
			}
		}
		seradOdStredu(nove, getWidth(), getHeight());
		final Mou stred = soord.getMoustred();
		final DiagnostikaDlazdic.Pohled pohled = priorita == Priority.KACHLE
				? DiagnostikaDlazdic.novyPohled(nazevKachlovniku, soord.getMoumer(), stred.xx, stred.yy, soord.getMouSize().dxx, soord.getMouSize().dyy, nove.size())
				: null;
		for (final JKachle jkachle : nove) {
			jkachle.nastavPohled(pohled);
			jkachle.ziskejObsah(kachleModel, priorita);
		}
		// System.out.println("mame komponent: " + super.getComponentCount());
		// System.out.println("Nahrazenych kachli: " + kachles.size());

		// Kachle, které zbyly v mapě jsou komponenty, jenž nebyly recyklovány, musí být odstraněny
		mapaKachli.values().forEach(jka -> {
			jka.uzTeNepotrebuju();
			remove(jka);
		});
		pocitKachliVKachlovniku2.set(getComponentCount());
		log.trace("Počet komponent (nejspíš kachlí) v kachlovníku: {}", getComponentCount());
	}

	/** Seřadí dlaždice podle vzdálenosti od středu okna, ty uprostřed jsou vidět nejdřív a mají se získat první. */
	static void seradOdStredu(final List<? extends Component> kachle, final int sirka, final int vyska) {
		kachle.sort(Comparator.comparingLong(k -> {
			final long dx = 2L * k.getX() + k.getWidth() - sirka;
			final long dy = 2L * k.getY() + k.getHeight() - vyska;
			return dx * dx + dy * dy;
		}));
	}

	void kachleZpracovana(final JKachle jKachle) {
		if (jKachle.jeChybna()) {
			EventQueue.invokeLater(opakovaniChybnych::start);
		}
		if (katype != null && katype.isOffline() && priorita == Priority.KACHLE && PredvykresleniOkoli.jeVhodne(Runtime.getRuntime().availableProcessors())) {
			EventQueue.invokeLater(this::moznaPredvykreslit);
		}
	}

	private void zrusPredvykresleni() {
		if (predvykresleni != null) {
			predvykresleni.zrus();
		}
	}

	/** Když jsou všechny viditelné dlaždice hotové, vykreslí se na pozadí prstenec dlaždic kolem výřezu. */
	private void moznaPredvykreslit() {
		if (katype == null || !katype.isOffline() || kachleModel == null || !isSoordInitialized() || getSoord() == null) {
			return;
		}
		final Component[] kachle = getComponents();
		if (kachle.length == 0) {
			return;
		}
		for (final Component c : kachle) {
			if (!((JKachle) c).jeTamUzCelyObrazek()) {
				return;
			}
		}
		if (predvykresleni == null) {
			final KachleModel km = kachleModel;
			predvykresleni = new PredvykresleniOkoli((ka, prijemce) -> km.getZiskavac().ziskejObsah(new KaOneReq(ka, prijemce, priorita), DiagnosticsData.create(null, nazevKachlovniku, null)));
		}
		if (predvykresleni.jeSpusteno()) {
			return;
		}
		predvykresleni.spust(PredvykresleniOkoli.okoli(new Kaputer(getSoord()), katype, getWidth(), getHeight()));
	}

	private void zkusZnovuChybne() {
		boolean nejakaChybna = false;
		for (final Component c : getComponents()) {
			final JKachle jkachle = (JKachle) c;
			jkachle.zkusZnovuPoChybe(kachleModel, priorita);
			nejakaChybna |= jkachle.jeChybna();
		}
		if (!nejakaChybna) {
			opakovaniChybnych.stop();
		}
	}

	private void registerEvents() {
	}

}
