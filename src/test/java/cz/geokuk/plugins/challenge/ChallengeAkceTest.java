package cz.geokuk.plugins.challenge;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Akce menu Kešoidy > Challenge: stav z modelu, zkratka F4 pro jen kandidáty. */
public class ChallengeAkceTest {

	private ChallengeModel model;
	private final ChallengeZapnoutAction zapnout = new ChallengeZapnoutAction();
	private final ChallengeDalsiAction dalsi = new ChallengeDalsiAction();
	private final ChallengePredchoziAction predchozi = new ChallengePredchoziAction();
	private final ChallengeJenKandidatiAction kandidati = new ChallengeJenKandidatiAction();

	@Before
	public void setUp() {
		model = new ChallengeModel();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				final ChallengeZmenenyEvent e = (ChallengeZmenenyEvent) event;
				zapnout.onEvent(e);
				dalsi.onEvent(e);
				predchozi.onEvent(e);
				kandidati.onEvent(e);
			}
		});
		final MapoveUloziste u = new MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		zapnout.inject(model);
		dalsi.inject(model);
		predchozi.inject(model);
		kandidati.inject(model);
	}

	/** Plánovač zapisuje vybraného kandidáta ve vlákně Swingu; zápis z testu musí doběhnout, než začne další test. */
	@After
	public void pockejNaEdt() throws Exception {
		SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void f4PrepinaJenKandidatyDalsiAPredchoziMajiShiftSipky() {
		Assert.assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), kandidati.getValue(Action.ACCELERATOR_KEY));
		Assert.assertEquals("▶ je Shift+→", KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, java.awt.event.InputEvent.SHIFT_DOWN_MASK), dalsi.getValue(Action.ACCELERATOR_KEY));
		Assert.assertEquals("◀ je Shift+←", KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, java.awt.event.InputEvent.SHIFT_DOWN_MASK), predchozi.getValue(Action.ACCELERATOR_KEY));
		Assert.assertNull("lišta jen úchytem a menu", zapnout.getValue(Action.ACCELERATOR_KEY));
	}

	@Test
	public void zapnoutPrepinaModelAStavSeVraciZModelu() {
		Assert.assertFalse(zapnout.isSelected());
		zapnout.onSlectedChange(true);
		Assert.assertTrue(model.jeZapnuto());
		Assert.assertTrue(zapnout.isSelected());
		model.setZapnuto(false);
		Assert.assertFalse(zapnout.isSelected());
	}

	@Test
	public void dalsiAPredchoziAFiltrJsouDostupneJenSViditelnymPruhem() {
		Assert.assertFalse(dalsi.isEnabled());
		Assert.assertFalse(predchozi.isEnabled());
		Assert.assertFalse(kandidati.isEnabled());
		model.setZapnuto(true);
		Assert.assertFalse("zapnuto, ale bez aktivní challenge", dalsi.isEnabled());
		model.aktivuj(Sablony.cesko360());
		Assert.assertTrue(dalsi.isEnabled());
		Assert.assertTrue(predchozi.isEnabled());
		Assert.assertTrue(kandidati.isEnabled());
		model.setZapnuto(false);
		Assert.assertFalse(dalsi.isEnabled());
		Assert.assertFalse(kandidati.isEnabled());
	}

	@Test
	public void jenKandidatiZapneFiltrPrvniAktivniChallengeAVypneHo() {
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		Assert.assertEquals(Sablony.cesko360(), model.zobrazena());
		kandidati.onSlectedChange(true);
		Assert.assertEquals(Sablony.cesko360(), model.jenKandidatiPro());
		Assert.assertTrue(kandidati.isSelected());
		kandidati.onSlectedChange(false);
		Assert.assertNull(model.jenKandidatiPro());
		Assert.assertFalse(kandidati.isSelected());
	}

	@Test
	public void f4UkazeZpravuSeStavem() {
		final java.util.List<String> zpravy = new java.util.ArrayList<>();
		kandidati.inject(new cz.geokuk.plugins.kesoid.ToastModel() {
			@Override
			public void ukaz(final String text) {
				zpravy.add(text);
			}
		});
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		kandidati.onSlectedChange(true);
		kandidati.onSlectedChange(false);
		Assert.assertEquals(java.util.Arrays.asList("Plánovač: jen kandidáti – Zapnuto", "Plánovač: jen kandidáti – Vypnuto"), zpravy);
	}

	@Test
	public void ctrlShiftSipkySkaceNaKandidataAHlasiKdyzZadnyNeni() {
		final java.util.List<String> zpravy = new java.util.ArrayList<>();
		final cz.geokuk.plugins.kesoid.ToastModel toast = new cz.geokuk.plugins.kesoid.ToastModel() {
			@Override
			public void ukaz(final String text) {
				zpravy.add(text);
			}
		};
		final int[] klavesy = { KeyEvent.VK_UP, KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT };
		for (final SmerSkoku smer : SmerSkoku.values()) {
			final ChallengeSkokAction skok = new ChallengeSkokAction(smer);
			Assert.assertEquals(KeyStroke.getKeyStroke(klavesy[smer.ordinal()], java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK), skok.getValue(Action.ACCELERATOR_KEY));
			Assert.assertFalse("bez lišty nejde", skok.isEnabled());
			skok.inject(model);
			skok.inject(toast);
			skok.setPlanovac(() -> null);
			skok.actionPerformed(null);
		}
		Assert.assertEquals(java.util.Arrays.asList("Plánovač – žádný kandidát na sever", "Plánovač – žádný kandidát na východ", "Plánovač – žádný kandidát na jih",
				"Plánovač – žádný kandidát na západ"), zpravy);
	}

	@Test
	public void jenKandidatiBezAktivniChallengeNicNezapne() {
		kandidati.onSlectedChange(true);
		Assert.assertNull(model.jenKandidatiPro());
		Assert.assertFalse(kandidati.isSelected());
	}

	@Test
	public void zobrazenaJeJedinaAktivni() {
		Assert.assertNull(model.zobrazena());
		model.aktivuj(Sablony.cesko360());
		Assert.assertEquals(Sablony.cesko360(), model.zobrazena());
		final Challenge druha = model.ulozVlastni(new Challenge("Druhá", Challenge.Typ.SEKTORY_360, Challenge.Stred.souradnice(50, 14), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(druha);
		Assert.assertEquals("zapnutí druhé vypnulo první", druha, model.zobrazena());
		model.deaktivuj(druha);
		Assert.assertNull(model.zobrazena());
	}

	private final java.util.List<VybranyKandidat> vystredeno = new java.util.ArrayList<>();

	private PlanovacChallenge planovac() {
		return new PlanovacChallenge(model, id -> java.util.Optional.empty(), () -> new cz.geokuk.core.coordinates.Wgs(50, 14), () -> java.nio.file.Paths.get("neexistuje"),
				() -> new cz.geokuk.core.coordinates.Wgs(50.0, 14.0), vystredeno::add);
	}

	private void priravVysledek() throws Exception {
		model.aktivuj(Sablony.cesko360());
		final java.util.List<VysledekSektoru.KandidatKes> kandidati = new java.util.ArrayList<>();
		kandidati.add(new VysledekSektoru.KandidatKes("GC1", 50.01, 14.0, 10));
		kandidati.add(new VysledekSektoru.KandidatKes("GC2", 50.20, 14.0, 20));
		model.nastavVysledek(Sablony.cesko360(), new VysledekSektoru(new java.util.BitSet(360), new java.util.BitSet(360), new VysledekSektoru.Kandidati[360], 0, kandidati));
		SwingUtilities.invokeAndWait(() -> {});
	}

	/** Chybí 10°, 20° a 30°; kandidáty mají 10° a 20°. */
	private void pripravSektory(final Challenge c) throws Exception {
		model.aktivuj(c);
		model.setZapnuto(true);
		model.nastavVysledek(c, TextyPopupuPlanovaceTest.vysledek(new int[] { 10, 20, 30 }, new int[0], new double[][] { { 10, 1000 }, { 20, 2000 } }));
		SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void posunJdePoSektorechSKandidatyBezVyberuKeseABezVystredeni() throws Exception {
		pripravSektory(Sablony.cesko360());
		Assert.assertEquals("10°", posun(true));
		Assert.assertEquals("20°", posun(true));
		Assert.assertEquals("30° nemá kandidáty, kružnice se uzavře", "10°", posun(true));
		Assert.assertEquals("20°", posun(false));
		Assert.assertEquals(20, model.vybranySektor(Sablony.cesko360()));
		Assert.assertNull("keš se nevybírá", model.vybranyKandidat(Sablony.cesko360()));
		Assert.assertTrue("mapa se nevystřeďuje", vystredeno.isEmpty());
	}

	/** Posun ve vlákně Swingu jako z tlačítka nebo menu. */
	private String posun(final boolean dopredu) throws Exception {
		final String[] r = new String[1];
		SwingUtilities.invokeAndWait(() -> r[0] = ChallengeAkce.posun(model, dopredu));
		return r[0];
	}

	/** Kandidáti v 10° (GC10, sever od středu 50/14) a 20° (GC20); oba sektory chybí a mají kandidáty. */
	private void pripravSektorySKesemi() throws Exception {
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		final java.util.BitSet obsazene = new java.util.BitSet(360);
		obsazene.set(0, 360);
		final VysledekSektoru.Kandidati[] k = new VysledekSektoru.Kandidati[360];
		for (final int s : new int[] { 10, 20 }) {
			obsazene.clear(s);
			k[s] = new VysledekSektoru.Kandidati(s);
			k[s].pridej("GC" + s, 1000 * s);
		}
		final java.util.List<VysledekSektoru.KandidatKes> kese = java.util.Arrays.asList(new VysledekSektoru.KandidatKes("GC10", 50.01, 14.0, 10),
				new VysledekSektoru.KandidatKes("GC20", 49.99, 14.0, 20));
		model.nastavVysledek(Sablony.cesko360(), new VysledekSektoru(obsazene, new java.util.BitSet(360), k, 358, kese));
		SwingUtilities.invokeAndWait(() -> {});
	}

	@Test
	public void poSkokuZrusiVyberSektoruIZvyrazneniNaMape() throws Exception {
		pripravSektorySKesemi();
		final PlanovacChallenge p = planovac();
		SwingUtilities.invokeAndWait(() -> ChallengeAkce.skoc(model, p, SmerSkoku.SEVER));
		SwingUtilities.invokeAndWait(() -> {});
		final Challenge c = Sablony.cesko360();
		Assert.assertEquals(10, model.vybranySektor(c));
		Assert.assertNotNull(model.vybranyKandidat(c));
		model.setVybranySektor(c, PosunVyberu.ZADNY_SEKTOR);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(-1, model.vybranySektor(c));
		Assert.assertNull("kandidát po skoku zvýraznění nedrží", model.vybranyKandidat(c));
		Assert.assertEquals(-1, model.vychoziSektorPosunu(c));
	}

	@Test
	public void skokVeSmeruVybereIJehoSektorAPosunKesNecha() throws Exception {
		pripravSektorySKesemi();
		final PlanovacChallenge p = planovac();
		final VybranyKandidat[] k = new VybranyKandidat[1];
		SwingUtilities.invokeAndWait(() -> k[0] = ChallengeAkce.skoc(model, p, SmerSkoku.SEVER));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("GC10", k[0].id());
		Assert.assertEquals("Ctrl+Shift+šipka vybere i sektor keše", 10, model.vybranySektor(Sablony.cesko360()));
		Assert.assertEquals("▶ jde z vybraného sektoru dál", "20°", posun(true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("◀ ▶ vybranou keš nechají", "GC10", model.vybranyKandidat(Sablony.cesko360()).id());
		Assert.assertEquals(20, model.vybranySektor(Sablony.cesko360()));
	}

	/** Kandidát v sektoru 0° (přímo na sever): Ctrl+Shift+šipka vybere i sektor 0. */
	@Test
	public void skokNaKandidataVSektoruNulaVybereSektorNula() throws Exception {
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		final java.util.BitSet obsazene = new java.util.BitSet(360);
		obsazene.set(1, 360);
		final VysledekSektoru.Kandidati[] k = new VysledekSektoru.Kandidati[360];
		k[0] = new VysledekSektoru.Kandidati(0);
		k[0].pridej("GC0", 1000);
		model.nastavVysledek(Sablony.cesko360(),
				new VysledekSektoru(obsazene, new java.util.BitSet(360), k, 359, java.util.Collections.singletonList(new VysledekSektoru.KandidatKes("GC0", 50.01, 14.0, 0))));
		SwingUtilities.invokeAndWait(() -> {});
		final PlanovacChallenge p = planovac();
		final VybranyKandidat[] v = new VybranyKandidat[1];
		SwingUtilities.invokeAndWait(() -> v[0] = ChallengeAkce.skoc(model, p, SmerSkoku.SEVER));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("GC0", v[0].id());
		Assert.assertEquals(0, model.vybranySektor(Sablony.cesko360()));
	}

	@Test
	public void nazevATooltipPosunuPodleTypu() {
		Assert.assertEquals("Další sektor", ChallengeAkce.nazevPosunu(Challenge.Typ.SEKTORY_360, true));
		Assert.assertEquals("Předchozí políčko", ChallengeAkce.nazevPosunu(Challenge.Typ.DT_MATICE, false));
		Assert.assertEquals("Další sektor s kandidáty po kružnici (Shift+→); Ctrl+Shift+šipka: nejbližší kandidát tím směrem", ChallengeAkce.tipPosunu(Challenge.Typ.SEKTORY_360, true));
		Assert.assertEquals("Předchozí políčko s kandidáty po řádcích matice (Shift+←); Ctrl+Shift+šipka: nejbližší kandidát tím směrem", ChallengeAkce.tipPosunu(Challenge.Typ.DT_MATICE, false));
		Assert.assertEquals("Další sektor", dalsi.getValue(Action.NAME));
		final Challenge dt = model.ulozVlastni(new Challenge("DT", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(dt);
		model.setZapnuto(true);
		Assert.assertEquals("Další políčko", dalsi.getValue(Action.NAME));
		Assert.assertEquals("Předchozí políčko", predchozi.getValue(Action.NAME));
		Assert.assertEquals(ChallengeAkce.tipPosunu(Challenge.Typ.DT_MATICE, true), dalsi.getValue(Action.SHORT_DESCRIPTION));
	}

	private Challenge pripravMatici(final java.util.List<KandidatPolicka> kandidati, final String... chybejici) throws Exception {
		final Challenge dt = model.ulozVlastni(new Challenge("DT", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(dt);
		model.setZapnuto(true);
		model.nastavVysledekPolicek(dt, new VysledekPolicek(81, java.util.Arrays.asList(chybejici), kandidati, 50));
		SwingUtilities.invokeAndWait(() -> {});
		return dt;
	}

	private static void kandidati(final java.util.List<KandidatPolicka> k, final String stitek, final int pocet, final double nejblizsiM) {
		for (int i = 0; i < pocet; i++) {
			k.add(new KandidatPolicka("GC" + stitek + i, 50, 14, stitek, stitek, null, nejblizsiM + i * 100));
		}
	}

	@Test
	public void tooltipSipekDTUkazeCilAPocet() throws Exception {
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		kandidati(k, "D3/T4", 12, 2100);
		pripravMatici(k, "D1/T1", "D3/T4");
		final String dopredu = ChallengeAkce.tipPosunu(model, Challenge.Typ.DT_MATICE, true);
		Assert.assertEquals("<html>Další políčko: D3/T4 · 12 kandidátů, nejbližší 2,1 km od středu mapy · mapa se neposune<br>Zkratka Shift+→ · Ctrl+Shift+šipka: nejbližší kandidát tím směrem</html>", dopredu);
		Assert.assertEquals("Předchozí políčko: D3/T4 · 12 kandidátů, nejbližší 2,1 km od středu mapy · mapa se neposune", ChallengeAkce.cilPosunu(model, false));
		Assert.assertEquals("tooltip výběr nemění", null, model.vybranePolicko(model.zobrazena()));
		Assert.assertTrue(ChallengeAkce.jeKamPosunout(model, true));
	}

	@Test
	public void tooltipSipekDTPlnePolickoAJedenKandidat() throws Exception {
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		kandidati(k, "D2/T2", 50, 300);
		kandidati(k, "D3/T4", 1, 40);
		pripravMatici(k, "D2/T2", "D3/T4");
		Assert.assertEquals("Další políčko: D2/T2 · 50 nejbližších ke středu mapy · mapa se neposune", ChallengeAkce.cilPosunu(model, true));
		model.setVybranePolicko(model.zobrazena(), "D2/T2");
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Další políčko: D3/T4 · 1 kandidát, nejbližší 40 m od středu mapy · mapa se neposune", ChallengeAkce.cilPosunu(model, true));
	}

	@Test
	public void tooltipSipekDTBezCileASBezVysledku() throws Exception {
		final Challenge dt = pripravMatici(new java.util.ArrayList<>(), "D1/T1");
		Assert.assertEquals("Žádné další políčko s kandidáty", ChallengeAkce.cilPosunu(model, true));
		Assert.assertEquals("Žádné předchozí políčko s kandidáty", ChallengeAkce.cilPosunu(model, false));
		Assert.assertFalse(ChallengeAkce.jeKamPosunout(model, true));
		Assert.assertFalse(ChallengeAkce.jeKamPosunout(model, false));
		model.aktivuj(Sablony.cesko360());
		model.setZobrazena(Sablony.cesko360());
		Assert.assertNull("výsledek ještě není", ChallengeAkce.cilPosunu(model, true));
		Assert.assertTrue("bez výsledku šipky nešednou", ChallengeAkce.jeKamPosunout(model, true));
		Assert.assertEquals(ChallengeAkce.tipPosunu(Challenge.Typ.SEKTORY_360, true), ChallengeAkce.tipPosunu(model, Challenge.Typ.SEKTORY_360, true));
		Assert.assertNotNull(dt);
	}

	/** Středem 50, 14; chybí sektory 40, 100, 150, 250, 300 a 330 (330 bez kandidátů), kandidáty mají všechny ostatní z nich. */
	private Challenge pripravSektorySeStredem() throws Exception {
		final Challenge c = Sablony.cesko360();
		model.aktivuj(c);
		model.setZapnuto(true);
		model.nastavVysledek(c, TextyPopupuPlanovaceTest.vysledek(new int[] { 40, 100, 150, 250, 300, 330 }, new int[0],
				new double[][] { { 40, 1000 }, { 100, 2000 }, { 150, 3000 }, { 250, 4000 }, { 300, 5000 } }), 50.0, 14.0);
		SwingUtilities.invokeAndWait(() -> {});
		return c;
	}

	private int sektorKese(final Challenge c, final VybranaKes kes) {
		return model.prehled().sektorBodu(kes.lat(), kes.lon());
	}

	@Test
	public void posunJdeOdVybranehoSektoruNahoruDoluAPresKruznici() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		model.setVybranySektor(c, 150);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(250, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(150, model.dalsiSektor(c, false));
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranySektor(c, 300);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("za posledním následuje první", 40, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("před prvním je poslední", 300, model.dalsiSektor(c, false));
	}

	@Test
	public void posunJdeOdSektoruVybraneKese() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		final VybranaKes kes = new VybranaKes("GC1", 50.0, 14.5, -1, -1);
		model.setVybranaKes(kes);
		final int s = sektorKese(c, kes);
		Assert.assertTrue("keš leží v některém sektoru: " + s, s >= 0);
		Assert.assertEquals("výchozí bod je sektor keše", s, model.vychoziSektorPosunu(c));
		final String dalsi = ChallengeAkce.cilPosunu(model, true);
		final int ocekavany = nejblizsiSektorSKandidaty(s, true);
		Assert.assertTrue(dalsi + " má začínat sektorem " + ocekavany, dalsi.startsWith("Další sektor: " + ocekavany + "° "));
		Assert.assertEquals("tooltip i skutečný posun jdou stejně", ocekavany, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranaKes(kes);
		Assert.assertEquals("vybraný sektor je novější než keš", ocekavany, model.vychoziSektorPosunu(c));
	}

	@Test
	public void posunNavazujeNaSektorZrusenekeseDokudHoUzivatelNezrusi() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		final VybranaKes kes = new VybranaKes("GC1", 50.0, 14.5, -1, -1);
		model.setVybranaKes(kes);
		final int s = sektorKese(c, kes);
		model.setVybranaKes(null);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("sektor zrušené keše zůstane výchozím bodem", s, model.vychoziSektorPosunu(c));
		Assert.assertTrue("na mapě se po kliku mimo nezvýrazňuje", VyberZvyrazneni.vyres(model.prehled(), model, 0, -1).jeNic());
		Assert.assertEquals(PosunVyberu.ZADNY_SEKTOR, model.zvyraznenySektor(c));
		final String dalsi = ChallengeAkce.cilPosunu(model, true);
		final int ocekavany = nejblizsiSektorSKandidaty(s, true);
		Assert.assertTrue(dalsi + " má začínat sektorem " + ocekavany, dalsi.startsWith("Další sektor: " + ocekavany + "° "));
		Assert.assertEquals(ocekavany, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranySektor(c, PosunVyberu.ZADNY_SEKTOR);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("po zrušení (Esc, „N° ✕“) se nezačíná od zapamatovaného sektoru", PosunVyberu.ZADNY_SEKTOR, model.vychoziSektorPosunu(c));
		Assert.assertTrue(VyberZvyrazneni.vyres(model.prehled(), model, 0, -1).jeNic());
		Assert.assertEquals("začíná se od nuly", 40, model.dalsiSektor(c, true));
	}

	@Test
	public void posunPolickaNavazujeNaPolickoZruseneKese() throws Exception {
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		kandidati(k, "D3/T4", 2, 2100);
		final Challenge dt = pripravMatici(k, "D3/T4");
		model.setVybranaKes(new VybranaKes("GC9", 50.0, 14.0, 4, 6));
		model.setVybranaKes(null);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull("na mapě se po kliku mimo nezvýrazňuje", model.zvyraznenePolicko(dt));
		Assert.assertEquals("ale je výchozím bodem posunu", "D3/T4", model.vychoziPolickoPosunu(dt));
		model.setVybranePolicko(dt, null);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull("po zrušení nic", model.vychoziPolickoPosunu(dt));
	}

	@Test
	public void posunPolickaJdeOdPolickaVybraneKeseAPoKlikuMimoOdPoslednihoPolicka() throws Exception {
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		kandidati(k, "D1/T1", 2, 1000);
		kandidati(k, "D3/T4", 2, 2000);
		kandidati(k, "D5/T5", 2, 3000);
		final Challenge dt = pripravMatici(k, "D1/T1", "D3/T4", "D5/T5");
		model.setVybranaKes(new VybranaKes("GC9", 50.0, 14.0, 4, 6));
		Assert.assertEquals("výchozí bod je políčko vybrané keše", "D3/T4", model.zvyraznenePolicko(dt));
		Assert.assertTrue(ChallengeAkce.cilPosunu(model, true).startsWith("Další políčko: D5/T5"));
		Assert.assertTrue(ChallengeAkce.cilPosunu(model, false).startsWith("Předchozí políčko: D1/T1"));
		model.setVybranaKes(null);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("po kliku mimo se nejdřív ukáže poslední políčko", ChallengeAkce.cilPosunu(model, true).startsWith("Znovu ukázat políčko: D3/T4"));
		Assert.assertEquals("D3/T4", model.dalsiPolicko(dt, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("D5/T5", model.dalsiPolicko(dt, true));
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranePolicko(dt, null);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("po zrušení se začíná od prvního", ChallengeAkce.cilPosunu(model, true).startsWith("Další políčko: D1/T1"));
	}

	@Test
	public void klikMimoZrusiZvyrazneniVybranehoPolickaAleZapamatujeHoProPosun() throws Exception {
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		kandidati(k, "D1/T1", 2, 1000);
		kandidati(k, "D3/T4", 2, 2000);
		kandidati(k, "D5/T5", 2, 3000);
		final Challenge dt = pripravMatici(k, "D1/T1", "D3/T4", "D5/T5");
		model.setVybranePolicko(dt, "D3/T4");
		SwingUtilities.invokeAndWait(() -> {});
		model.zrusZvyrazneniKlikemMimo();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull("zvýraznění pryč", model.zvyraznenePolicko(dt));
		Assert.assertNull("i vybrané políčko", model.vybranePolicko(dt));
		Assert.assertTrue(model.maZapamatovanyVyber(dt));
		Assert.assertTrue("první ▶ ukáže znovu D3/T4", ChallengeAkce.cilPosunu(model, true).startsWith("Znovu ukázat políčko: D3/T4"));
		Assert.assertTrue("první ◀ také", ChallengeAkce.cilPosunu(model, false).startsWith("Znovu ukázat políčko: D3/T4"));
		Assert.assertEquals("D3/T4", model.dalsiPolicko(dt, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("D3/T4", model.zvyraznenePolicko(dt));
		Assert.assertTrue("druhý ▶ jde dál", ChallengeAkce.cilPosunu(model, true).startsWith("Další políčko: D5/T5"));
		model.zrusZvyrazneniKlikemMimo();
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranePolicko(dt, null);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse("zrušení výběru políčka v popupu paměť maže", model.maZapamatovanyVyber(dt));
		Assert.assertTrue("▶ jde od prvního políčka", ChallengeAkce.cilPosunu(model, true).startsWith("Další políčko: D1/T1"));
	}

	@Test
	public void klikMimoZrusiZvyrazneniVybranehoSektoruAleZapamatujeHoProPosun() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		model.setVybranySektor(c, 150);
		SwingUtilities.invokeAndWait(() -> {});
		model.zrusZvyrazneniKlikemMimo();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(PosunVyberu.ZADNY_SEKTOR, model.zvyraznenySektor(c));
		Assert.assertEquals("výchozí bod zůstal", 150, model.vychoziSektorPosunu(c));
		Assert.assertTrue(ChallengeAkce.cilPosunu(model, true).startsWith("Znovu ukázat sektor: 150° "));
		Assert.assertEquals("první ▶ znovu zvýrazní poslední sektor", 150, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("a je zase vybraný", 150, model.zvyraznenySektor(c));
		Assert.assertTrue(ChallengeAkce.cilPosunu(model, true).startsWith("Další sektor: 250° "));
		Assert.assertEquals("druhý ▶ jde dál", 250, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		model.zrusZvyrazneniKlikemMimo();
		model.setVybranySektor(c, PosunVyberu.ZADNY_SEKTOR);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("zrušení výběru políčka v popupu paměť maže", PosunVyberu.ZADNY_SEKTOR, model.vychoziSektorPosunu(c));
		Assert.assertEquals(40, model.dalsiSektor(c, true));
	}

	@Test
	public void klikDoPrazdneMapyJakoZmenaPoziceZrusiZvyrazneniAleZapamatujeSektor() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		final SledovaniVybraneKese sledovani = new SledovaniVybraneKese();
		sledovani.inject(model);
		final cz.geokuk.core.coord.PoziceModel pozice = new cz.geokuk.core.coord.PoziceModel();
		pozice.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				if (event instanceof cz.geokuk.core.coord.PoziceChangedEvent) {
					sledovani.onEvent((cz.geokuk.core.coord.PoziceChangedEvent) event);
				}
			}
		});
		model.setVybranySektor(c, 150);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(150, model.zvyraznenySektor(c));
		pozice.setPozice(new cz.geokuk.core.coordinates.Wgs(50.2, 14.3));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("klik mimo zvýraznění zrušil", PosunVyberu.ZADNY_SEKTOR, model.zvyraznenySektor(c));
		Assert.assertEquals("a sektor se pamatuje", 150, model.vychoziSektorPosunu(c));
	}

	@Test
	public void zrusenyVyberKeseSeUPosunuZapominaJenZruseni() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		final VybranaKes kes = new VybranaKes("GC1", 50.0, 14.5, -1, -1);
		model.setVybranaKes(kes);
		final int s = sektorKese(c, kes);
		model.setVybranaKes(null);
		model.setVybranySektor(c, 250);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("výslovně vybraný sektor má přednost", 250, model.vychoziSektorPosunu(c));
		model.setVybranySektor(c, PosunVyberu.ZADNY_SEKTOR);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNotEquals(s, model.vychoziSektorPosunu(c));
		Assert.assertEquals(PosunVyberu.ZADNY_SEKTOR, model.vychoziSektorPosunu(c));
	}

	@Test
	public void posunNeZustaneZaseknutyKdyzJeCilJizVybranyAKesJeNovejsi() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		final VybranaKes kes = new VybranaKes("GC1", 50.0, 14.5, -1, -1);
		model.setVybranaKes(kes);
		final int s = sektorKese(c, kes);
		final int t = nejblizsiSektorSKandidaty(s, true);
		model.setVybranySektor(c, t);
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranaKes(null);
		model.setVybranaKes(kes);
		Assert.assertEquals("klik na keš přebil výběr sektoru", s, model.vychoziSektorPosunu(c));
		Assert.assertEquals(t, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("po posunu je zvýrazněn cíl, ne sektor keše", t, model.vychoziSektorPosunu(c));
		Assert.assertEquals("další posun jde dál", nejblizsiSektorSKandidaty(t, true), model.dalsiSektor(c, true));
	}

	@Test
	public void posunPolickaNeZustaneZaseknutyKdyzJeCilJizVybranyAKesJeNovejsi() throws Exception {
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		kandidati(k, "D3/T4", 2, 2100);
		final Challenge dt = pripravMatici(k, "D3/T4");
		model.setVybranePolicko(dt, "D3/T4");
		SwingUtilities.invokeAndWait(() -> {});
		final long pred = model.casVyberuKese();
		model.setVybranaKes(new VybranaKes("GC9", 50.0, 14.0, 6, 8));
		Assert.assertTrue(model.casVyberuKese() > pred);
		final java.util.List<ChallengeZmenenyEvent.Druh> udalosti = new java.util.ArrayList<>();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				udalosti.add(((ChallengeZmenenyEvent) event).druh());
			}
		});
		Assert.assertEquals("D3/T4", model.dalsiPolicko(dt, true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("výběr se ohlásí i při stejném políčku: " + udalosti, udalosti.contains(ChallengeZmenenyEvent.Druh.POLICKO));
	}

	@Test
	public void zruseniVyberuSektoruPoSkokuZrusiIZvyrazneni() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		model.nastavVybranyKandidat(c, new VybranyKandidat("GC2", 50.0, 14.0, 150, 3000));
		model.setVybranySektor(c, 150);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("zvýrazněn je sektor kandidáta", 150, model.vychoziSektorPosunu(c));
		model.setVybranySektor(c, PosunVyberu.ZADNY_SEKTOR);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(-1, model.vybranySektor(c));
		Assert.assertNull("vybraný kandidát držel zvýraznění", model.vybranyKandidat(c));
		Assert.assertEquals("na mapě nic nezůstane zvýrazněné", -1, model.vychoziSektorPosunu(c));
		Assert.assertTrue(VyberZvyrazneni.vyres(model.prehled(), model, 0, -1).jeNic());
	}

	@Test
	public void posunJdeOdSektoruVybranehoKandidata() throws Exception {
		final Challenge c = pripravSektorySeStredem();
		model.nastavVybranyKandidat(c, new VybranyKandidat("GC2", 50.0, 14.0, 150, 3000));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(250, model.dalsiSektor(c, true));
		SwingUtilities.invokeAndWait(() -> {});
		model.setVybranySektor(c, -1);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("po zrušení výběru není od čeho", -1, model.vychoziSektorPosunu(c));
		Assert.assertEquals("bez výběru ◀ začíná od posledního sektoru", 300, model.dalsiSektor(c, false));
	}

	/** Nejbližší sektor s kandidáty po kružnici za {@code od} (vpřed) nebo před ním (zpět). */
	private static int nejblizsiSektorSKandidaty(final int od, final boolean dopredu) {
		final int[] sektory = { 40, 100, 150, 250, 300 };
		for (int i = 1; i <= 360; i++) {
			final int s = Math.floorMod(od + (dopredu ? i : -i), 360);
			for (final int x : sektory) {
				if (x == s) {
					return s;
				}
			}
		}
		throw new AssertionError();
	}

	@Test
	public void tooltipSipek360UkazeSektorAKandidaty() throws Exception {
		pripravSektory(Sablony.cesko360());
		Assert.assertEquals("Další sektor: 10° · 1 kandidát, nejbližší 1,0 km od středu · mapa se neposune", ChallengeAkce.cilPosunu(model, true));
		Assert.assertEquals("Předchozí sektor: 20° · 1 kandidát, nejbližší 2,0 km od středu · mapa se neposune", ChallengeAkce.cilPosunu(model, false));
		Assert.assertEquals(-1, model.vybranySektor(Sablony.cesko360()));
	}

	@Test
	public void zkratkyShiftSipkaPosouvajiStejneJakoTlacitka() throws Exception {
		pripravSektory(Sablony.cesko360());
		dalsi.actionPerformed(new ActionEvent(this, 0, ""));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(10, model.vybranySektor(Sablony.cesko360()));
		dalsi.actionPerformed(new ActionEvent(this, 0, ""));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(20, model.vybranySektor(Sablony.cesko360()));
		predchozi.actionPerformed(new ActionEvent(this, 0, ""));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(10, model.vybranySektor(Sablony.cesko360()));
	}

	@Test
	public void shiftSipkaVyberePlanovacemKandidataVeSmeru() throws Exception {
		priravVysledek();
		model.setZapnuto(true);
		final PlanovacChallenge p = planovac();
		final java.util.List<String> zpravy = new java.util.ArrayList<>();
		final cz.geokuk.plugins.kesoid.ToastModel toast = new cz.geokuk.plugins.kesoid.ToastModel() {
			@Override
			public void ukaz(final String text) {
				zpravy.add(text);
			}
		};
		final ChallengeSkokAction sever = new ChallengeSkokAction(SmerSkoku.SEVER);
		final ChallengeSkokAction jih = new ChallengeSkokAction(SmerSkoku.JIH);
		for (final ChallengeSkokAction a : new ChallengeSkokAction[] { sever, jih }) {
			a.inject(model);
			a.inject(toast);
			a.setPlanovac(() -> p);
		}
		sever.actionPerformed(new ActionEvent(this, 0, ""));
		Assert.assertEquals("na severu je kandidát, mapa se na něj vystředí", 1, vystredeno.size());
		Assert.assertTrue("bez zprávy", zpravy.isEmpty());
		jih.actionPerformed(new ActionEvent(this, 0, ""));
		Assert.assertEquals("na jihu nikdo", java.util.Collections.singletonList("Plánovač – žádný kandidát na jih"), zpravy);
		Assert.assertEquals(1, vystredeno.size());
	}

	@Test
	public void akcePosunouVyberPriStisku() throws Exception {
		pripravSektory(Sablony.cesko360());
		SwingUtilities.invokeAndWait(() -> {
			dalsi.actionPerformed(new ActionEvent(this, 0, ""));
			dalsi.actionPerformed(new ActionEvent(this, 0, ""));
		});
		Assert.assertEquals(20, model.vybranySektor(Sablony.cesko360()));
		SwingUtilities.invokeAndWait(() -> predchozi.actionPerformed(new ActionEvent(this, 0, "")));
		Assert.assertEquals(10, model.vybranySektor(Sablony.cesko360()));
	}

	@Test
	public void bezChallengeBezPlanovaceNeboBezModeluNicNeudelaANespadne() throws Exception {
		Assert.assertNull("bez aktivní challenge", posun(true));
		Assert.assertNull("bez modelu", ChallengeAkce.posun(null, false));
		priravVysledek();
		Assert.assertNull("bez sektorů s kandidáty není kam", posun(true));
		dalsi.actionPerformed(new ActionEvent(this, 0, ""));
		predchozi.actionPerformed(new ActionEvent(this, 0, ""));
		Assert.assertTrue("nic se nevystředí", vystredeno.isEmpty());
	}

	@Test
	public void stitekJeCitelnyNaPozadiVsechTemat() {
		final Color[] pozadi = { new Color(204, 204, 204), new Color(238, 238, 238), new Color(102, 102, 102), Color.WHITE, Color.BLACK, new Color(40, 40, 40) };
		for (final Color p : pozadi) {
			Assert.assertTrue(p + ": kontrast " + StitekChallenge.kontrast(StitekChallenge.barva(p), p), StitekChallenge.kontrast(StitekChallenge.barva(p), p) >= 4.5);
		}
		Assert.assertEquals("kde stačí, zůstane oranžová", new Color(200, 100, 0), StitekChallenge.barva(Color.BLACK));
		Assert.assertEquals(new Color(200, 100, 0), StitekChallenge.barva(null));
	}

	@Test
	public void predchoziAkceJeDostupnaUVsechTypu() {
		final Challenge dt = model.ulozVlastni(new Challenge("DT", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(dt);
		model.setZapnuto(true);
		Assert.assertTrue(dalsi.isEnabled());
		Assert.assertTrue(predchozi.isEnabled());
		model.setZapnuto(false);
		Assert.assertFalse(predchozi.isEnabled());
	}

	@Test
	public void akceAFiltrPracujiSAktivniChallenge() throws Exception {
		model.aktivuj(Sablony.cesko360());
		model.aktivuj(Sablony.praha360());
		model.setZapnuto(true);
		Assert.assertEquals(Sablony.praha360(), model.zobrazena());
		kandidati.onSlectedChange(true);
		Assert.assertEquals("filtr patří aktivní challenge", Sablony.praha360(), model.jenKandidatiPro());
	}

	@Test
	public void dalsiJdeNaAktivniChallengeAFiltrSeZobrazujeProNi() throws Exception {
		model.aktivuj(Sablony.cesko360());
		model.aktivuj(Sablony.praha360());
		model.setZapnuto(true);
		model.nastavVysledek(Sablony.praha360(), TextyPopupuPlanovaceTest.vysledek(new int[] { 7 }, new int[0], new double[][] { { 7, 500 } }));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("další jde na aktivní challenge", "7°", posun(true));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse("filtr ještě není zapnutý", kandidati.isSelected());
		model.setJenKandidatiPro(Sablony.praha360());
		Assert.assertTrue("přepínač filtru ukazuje stav aktivní challenge", kandidati.isSelected());
		model.aktivuj(Sablony.cesko360());
		Assert.assertTrue("filtr se přenese na jinou zapnutou challenge", kandidati.isSelected());
	}

	private Challenge kalendar() {
		return model.ulozVlastni(new Challenge("Kal", Challenge.Typ.KALENDAR, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
	}

	@Test
	public void ulozenyAktivniKalendarSeNepromitaDoPruhuADtJdeZaNim() {
		final Challenge kal = kalendar();
		final Challenge dt = model.ulozVlastni(new Challenge("DT", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(kal);
		model.aktivuj(dt);
		model.setZapnuto(true);
		Assert.assertEquals("kalendář je první aktivní, ale pruh ukazuje D/T", dt, model.zobrazena());
		Assert.assertTrue(model.jePruhViditelny());
		Assert.assertEquals(Challenge.Typ.DT_MATICE, model.stavPruhu().typ());
		Assert.assertEquals("kalendář se nepočítá mezi aktivní v pruhu", 1, model.stavPruhu().pocetAktivnich());
		Assert.assertTrue(dalsi.isEnabled());
	}

	@Test
	public void jenKalendarAktivniPruhNeukazeAAkceJsouVypnute() {
		final Challenge kal = kalendar();
		model.aktivuj(kal);
		model.setZapnuto(true);
		Assert.assertNull(model.zobrazena());
		Assert.assertFalse(model.jePruhViditelny());
		Assert.assertFalse(model.stavPruhu().jeViditelny());
		Assert.assertFalse(dalsi.isEnabled());
		Assert.assertTrue("data se nemažou, kalendář zůstává aktivní", model.jeAktivni(kal));
		try {
			model.setZobrazena(kal);
			Assert.fail("kalendář se v pruhu zobrazit nedá");
		} catch (final ChallengeChyba e) {
			Assert.assertFalse(e.getMessage().isEmpty());
		}
	}

	@Test
	public void kalendarSeVUiNenabizi() {
		Assert.assertTrue(model.jeVUi(Sablony.cesko360()));
		final Challenge kal = kalendar();
		Assert.assertFalse(model.jeVUi(kal));
		model.setKalendarVUi(true);
		Assert.assertTrue("jen pro testy a budoucí vydání", model.jeVUi(kal));
	}
}
