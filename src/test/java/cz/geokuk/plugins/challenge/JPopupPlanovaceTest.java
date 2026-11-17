package cz.geokuk.plugins.challenge;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.framework.RozmisteniPrekryvu;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Popup plánovače: poloha pod odznakem uvnitř mapy, myš jen v jeho obdélníku, zavírání Esc a kliknutím mimo, obsah podle výsledku. */
public class JPopupPlanovaceTest {

	private ChallengeModel model;
	private JPopupPlanovace popup;
	private JPanel mapa;

	private void priprav() throws Exception {
		model = new ChallengeModel();
		popup = new JPopupPlanovace();
		model.inject(new EventFirer() {
			@Override
			public void fire(final Event0<?> event) {
				popup.onEvent((ChallengeZmenenyEvent) event);
			}
		});
		final MapoveUloziste u = new MapoveUloziste();
		model.setUloziste(() -> u);
		model.initAfterInject();
		popup.inject(model);
		mapa = new JPanel(null);
		mapa.setSize(1280, 700);
		mapa.add(popup);
		popup.setBounds(0, 0, 1280, 700);
		model.aktivuj(Sablony.cesko360());
		model.setZapnuto(true);
		SwingUtilities.invokeAndWait(() -> {});
	}

	private Challenge aktivni() {
		return model.aktivni().get(0);
	}

	private void vysledek(final VysledekSektoru v) throws Exception {
		model.nastavVysledek(aktivni360(), v);
		SwingUtilities.invokeAndWait(() -> {});
	}

	private static MouseEvent stisk(final Component c, final int x, final int y) {
		return new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
	}

	@Test
	public void polohaPodOdznakemUvnitrMapy() {
		final Dimension pozadovana = new Dimension(420, 330);
		final Rectangle odznak = JPruhPlanovace.oblastOdznaku(1920);
		final Rectangle r = JPopupPlanovace.poloha(new Dimension(1920, 1000), pozadovana, java.util.Collections.<Rectangle> emptyList());
		Assert.assertEquals("pod pruhem", RozmisteniPrekryvu.OKRAJ + RozmisteniPrekryvu.VYSKA_PRUHU + RozmisteniPrekryvu.POD_PRUHEM, r.y);
		Assert.assertEquals("kousek před odznakem", odznak.x - RozmisteniPrekryvu.PRED_ODZNAKEM, r.x);
		Assert.assertEquals(pozadovana, r.getSize());
		Assert.assertTrue("odznak zůstane vidět nad popupem", odznak.y + odznak.height <= r.y);
		for (final Dimension m : new Dimension[] { new Dimension(1920, 1000), new Dimension(1280, 700), new Dimension(800, 500), new Dimension(450, 400), new Dimension(400, 300), new Dimension(300, 200) }) {
			final Rectangle p = JPopupPlanovace.poloha(m, pozadovana, java.util.Collections.<Rectangle> emptyList());
			final Rectangle uvnitr = new Rectangle(RozmisteniPrekryvu.OKRAJ, 0, m.width - 2 * RozmisteniPrekryvu.OKRAJ, m.height - RozmisteniPrekryvu.OKRAJ);
			Assert.assertTrue(m + ": " + p, uvnitr.contains(p));
			Assert.assertFalse(m + ": popup nepřekrývá pruh " + p, p.intersects(RozmisteniPrekryvu.pruhPlanovace(RozmisteniPrekryvu.volnaSirkaPruhu(m.width))));
		}
		final Rectangle stredni = JPopupPlanovace.poloha(new Dimension(450, 400), pozadovana, java.util.Collections.<Rectangle> emptyList());
		Assert.assertEquals("když se vedle odznaku nevejde, posune se doleva", 450 - RozmisteniPrekryvu.OKRAJ, stredni.x + stredni.width);
		Assert.assertEquals(pozadovana.width, stredni.width);
		Assert.assertEquals("v úzké mapě se zúží", 400 - 2 * RozmisteniPrekryvu.OKRAJ, JPopupPlanovace.poloha(new Dimension(400, 300), pozadovana, java.util.Collections.<Rectangle> emptyList()).width);
		Assert.assertEquals("v nízké mapě se zkrátí jen oknem mapy", 300 - RozmisteniPrekryvu.OKRAJ - r.y,
				JPopupPlanovace.poloha(new Dimension(800, 300), pozadovana, java.util.Collections.<Rectangle> emptyList()).height);
		Assert.assertEquals("měřítko dole popup nezkrátí", pozadovana.height, JPopupPlanovace.poloha(new Dimension(800, 500), pozadovana, java.util.Collections.<Rectangle> emptyList()).height);
	}

	@Test
	public void mysZachytavaJenOtevrenyAJenVeSvemObdelniku() throws Exception {
		priprav();
		Assert.assertFalse(popup.contains(100, 100));
		popup.prepni();
		popup.doLayout();
		final Rectangle o = popup.obsah.getBounds();
		Assert.assertTrue(popup.jeOtevreno());
		Assert.assertTrue(popup.contains(o.x + 1, o.y + 1));
		Assert.assertTrue(popup.contains(o.x + o.width - 1, o.y + o.height - 1));
		Assert.assertFalse(popup.contains(o.x + o.width, o.y + 5));
		Assert.assertFalse(popup.contains(o.x + 5, o.y + o.height));
		Assert.assertFalse("střed mapy", popup.contains(900, 500));
		Assert.assertFalse("pruh nad popupem", popup.contains(10, 10));
	}

	@Test
	public void escZavre() throws Exception {
		priprav();
		final javax.swing.Action esc = popup.getActionMap().get(popup.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(javax.swing.KeyStroke.getKeyStroke("ESCAPE")));
		Assert.assertFalse("zavřený popup Esc neobsazuje", esc.isEnabled());
		popup.prepni();
		Assert.assertTrue(esc.isEnabled());
		esc.actionPerformed(new ActionEvent(popup, ActionEvent.ACTION_PERFORMED, "esc"));
		Assert.assertFalse(popup.jeOtevreno());
		Assert.assertFalse(popup.isVisible());
	}

	@Test
	public void klikMimoZavreKlikDovnitrANaOdznakNe() throws Exception {
		priprav();
		popup.prepni();
		popup.doLayout();
		final Rectangle o = popup.obsah.getBounds();
		popup.mysKdekoli(stisk(popup.obsah.souhrn, 2, 2));
		Assert.assertTrue("klik do popupu", popup.jeOtevreno());
		final Rectangle odznak = JPruhPlanovace.oblastOdznaku(1280);
		popup.mysKdekoli(stisk(mapa, odznak.x + 2, odznak.y + 2));
		Assert.assertTrue("stisk na odznaku nechá přepnutí na odznaku", popup.jeOtevreno());
		popup.mysKdekoli(new MouseEvent(mapa, MouseEvent.MOUSE_RELEASED, 0, 0, 900, 500, 1, false, MouseEvent.BUTTON1));
		Assert.assertTrue("puštění tlačítka nic nemění", popup.jeOtevreno());
		final MouseEvent mimo = stisk(mapa, o.x + o.width + 50, 500);
		popup.mysKdekoli(mimo);
		Assert.assertFalse("klik do mapy mimo popup", popup.jeOtevreno());
		Assert.assertFalse("klik mimo se neztratí", mimo.isConsumed());
	}

	@Test
	public void odznakPrepina() throws Exception {
		priprav();
		popup.prepni();
		Assert.assertTrue(popup.jeOtevreno());
		popup.prepni();
		Assert.assertFalse(popup.jeOtevreno());
	}

	@Test
	public void bezPruhuSeNeotevreAVypnutiZavre() throws Exception {
		priprav();
		model.setZapnuto(false);
		popup.prepni();
		Assert.assertFalse("bez pruhu není co ukázat", popup.jeOtevreno());
		model.setZapnuto(true);
		popup.prepni();
		Assert.assertTrue(popup.jeOtevreno());
		model.setZapnuto(false);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse("vypnutím plánovače se popup zavře", popup.jeOtevreno());
	}

	@Test
	public void kruzniceMaVysvetleniJenSKandidaty() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertFalse("bez výsledku není co vysvětlovat", o.kruznice.isVisible());
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[] { 213 }, new double[][] { { 213, 2800, 3000 }, { 47, 6200 } }));
		Assert.assertTrue(o.kruznice.isVisible());
		Assert.assertTrue(o.kruznice.getText(), o.kruznice.getText().contains("Kružnice 6,2 km na mapě: uvnitř leží nejbližší kandidáti všech sektorů v seznamu."));
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[] { 213 }, new double[][] {}));
		Assert.assertFalse("bez kandidátů kružnice nic neukazuje", o.kruznice.isVisible());
	}

	@Test
	public void obsahPodleVysledku() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertTrue(o.souhrn.getText(), o.souhrn.getText().startsWith("Počítá se"));
		Assert.assertFalse("bez výsledku není kam jít", o.dalsi.isEnabled());
		Assert.assertEquals(0, o.radky.size());
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[] { 213 }, new double[][] { { 213, 2800, 3000 }, { 47, 6200 } }));
		Assert.assertEquals("Splněno 357 z 360 (99 %), chybí 3", o.souhrn.getText());
		Assert.assertEquals(357, o.postup.getValue());
		Assert.assertEquals("99 %", o.postup.getString());
		final List<String> radky = new ArrayList<>();
		for (int i = 0; i < o.radky.size(); i++) {
			radky.add(o.radky.get(i));
		}
		Assert.assertEquals("jen sektory s kandidáty, po kružnici", java.util.Arrays.asList("47°  1 kandidát  nejbližší 6,2 km od středu", "213°  2 kandidáti  nejbližší 2,8 km od středu"), radky);
		Assert.assertEquals("+ 1 sektor bez kandidátů", o.bezKandidatu.getText());
		Assert.assertTrue(o.nejiste.getText(), o.nejiste.getText().contains("U 1 sektoru leží nalezená keš do 0,05° od hranice"));
		Assert.assertTrue(o.nejiste.isVisible());
		Assert.assertTrue(o.dalsi.isEnabled());
		Assert.assertEquals("▶ Další sektor", o.dalsi.getText());
		Assert.assertTrue(o.napoveda.getText(), o.napoveda.getText().contains(TextyPopupuPlanovace.napovedaSeznamu(Challenge.Typ.SEKTORY_360)));
		Assert.assertFalse("nadpis je jen název challenge", o.nadpis.getText().startsWith("Challenge "));
		Assert.assertEquals("Typ: 360°    Pravidla: " + TextyPopupuPlanovace.pravidla(aktivni().zdroj()), o.typ.getText());
		Assert.assertTrue(o.typ.getText(), o.typ.getText().endsWith("Pravidla: Project-GC") || o.typ.getText().endsWith("Pravidla: Stator"));
		Assert.assertFalse("zkratka PGC se nepíše", o.typ.getText().contains("PGC"));
	}

	@Test
	public void popupPiseOStropuStitkuNaMapeJenKdyzSeUplatni() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertEquals(" ", o.omezeniStitku.getText());
		model.nahlasStitkyNaMape(200, 1234);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Štítky na mapě: 200 nejbližších z 1\u00a0234", o.omezeniStitku.getText());
		model.nahlasStitkyNaMape(150, 150);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(" ", o.omezeniStitku.getText());
	}

	@Test
	public void popupPiseODosahuKandidatuJenKdyzHoVyrezPresahuje() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertFalse(o.dosahKandidatu.isVisible());
		model.nahlasDosahPresazeny(3200);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Kandidáti spočítáni do 3,2 km od středu mapy – posunutím mapy se dopočítají další", o.dosahKandidatu.getText());
		Assert.assertTrue(o.dosahKandidatu.isVisible());
		model.nahlasDosahPresazeny(Double.NaN);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse(o.dosahKandidatu.isVisible());
	}

	private static final int[] SIPKY = { java.awt.event.KeyEvent.VK_LEFT, java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.VK_UP, java.awt.event.KeyEvent.VK_DOWN };

	private static java.awt.event.KeyEvent klavesa(final Component zdroj, final int kod, final int modifikatory) {
		return new java.awt.event.KeyEvent(zdroj, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifikatory, kod, java.awt.event.KeyEvent.CHAR_UNDEFINED);
	}

	@Test
	public void seznamPousteSipkySShiftemACtrlemKZkratkamPlanovace() throws Exception {
		priprav();
		popup.prepni();
		final JList<String> seznam = popup.obsah.seznam;
		final int shift = java.awt.event.InputEvent.SHIFT_DOWN_MASK;
		for (final int kod : SIPKY) {
			Assert.assertFalse("Shift+šipka " + kod + " seznam nebere", SwingUtilities.processKeyBindings(klavesa(seznam, kod, shift)));
			Assert.assertFalse("Ctrl+Shift+šipka " + kod + " seznam nebere", SwingUtilities.processKeyBindings(klavesa(seznam, kod, shift | java.awt.event.InputEvent.CTRL_DOWN_MASK)));
		}
		Assert.assertTrue("obyčejné šipky seznam dál bere", SwingUtilities.processKeyBindings(klavesa(seznam, java.awt.event.KeyEvent.VK_DOWN, 0)));
		Assert.assertTrue("textové pole dál bere Shift+šipky pro výběr textu", SwingUtilities.processKeyBindings(klavesa(new JTextField("abc"), java.awt.event.KeyEvent.VK_RIGHT, shift)));
	}

	/** Zkratka v menu se spustí i s fokusem v seznamu popupu. */
	@Test
	public void zkratkaPlanovaceFungujeSFokusemVSeznamu() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		priprav();
		popup.prepni();
		final JList<String> seznam = popup.obsah.seznam;
		final int[] spusteno = new int[1];
		final JFrame okno = new JFrame();
		try {
			final AbstractAction akce = new AbstractAction("Další") {
				private static final long serialVersionUID = 1L;

				@Override
				public void actionPerformed(final ActionEvent e) {
					spusteno[0]++;
				}
			};
			akce.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
			final JMenuBar lista = new JMenuBar();
			final JMenu menu = new JMenu("Plánovač");
			menu.add(new JMenuItem(akce));
			lista.add(menu);
			okno.setJMenuBar(lista);
			okno.getContentPane().add(new JScrollPane(seznam));
			okno.pack();
			okno.setVisible(true);
			final java.awt.event.KeyEvent e = klavesa(seznam, java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
			Assert.assertTrue("zkratka byla zpracována", SwingUtilities.processKeyBindings(e));
			Assert.assertEquals(1, spusteno[0]);
		} finally {
			okno.dispose();
		}
	}

	@Test
	public void jenKandidatiPrepinaFiltrModelu() throws Exception {
		priprav();
		popup.prepni();
		popup.obsah.kandidati.doClick();
		Assert.assertEquals(aktivni(), model.jenKandidatiPro());
		popup.obsah.kandidati.doClick();
		Assert.assertNull(model.jenKandidatiPro());
		model.setJenKandidatiPro(aktivni());
		SwingUtilities.invokeAndWait(() -> {});
		popup.obnov();
		Assert.assertTrue("stav přepínače podle modelu (třeba z pruhu)", popup.obsah.kandidati.isSelected());
	}

	@Test
	public void nastaveniBezNapojeniNedostupneSNapojenimZavreAZavola() throws Exception {
		priprav();
		popup.initAfterInject();
		Assert.assertFalse(popup.obsah.nastaveni.isEnabled());
		final int[] volano = { 0 };
		popup.setNaNastaveni(() -> volano[0]++);
		popup.prepni();
		popup.obsah.nastaveni.doClick();
		Assert.assertEquals(1, volano[0]);
		Assert.assertFalse(popup.jeOtevreno());
	}

	private static void rozloz(final Container c) {
		for (int i = 0; i < 3; i++) {
			layoutRekurzivne(c);
		}
	}

	private static void layoutRekurzivne(final Container c) {
		c.doLayout();
		for (final Component k : c.getComponents()) {
			if (k instanceof Container) {
				layoutRekurzivne((Container) k);
			}
		}
	}

	/** Překážka (Detail keše, náhled) popup nezkrátí: má plnou velikost a překážku překryje, matice a nadpis jsou celé. */
	@Test
	public void maticeAHlavickaMajiPlnouVelikostiIPriPrekazceDole() throws Exception {
		priprav();
		final Challenge c = aktivujPolicka(Challenge.Typ.DT_MATICE);
		popup.prepni();
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Arrays.asList("D1/T1", "D3/T3"),
				java.util.Collections.singletonList(new KandidatPolicka("GC5", 50, 14, "D3/T3", "D3/T3", null)), 50));
		SwingUtilities.invokeAndWait(() -> {});
		final JPopupPlanovace.Obsah o = popup.obsah;
		final Dimension pref = o.getPreferredSize();
		final int y = RozmisteniPrekryvu.OKRAJ + RozmisteniPrekryvu.VYSKA_PRUHU + RozmisteniPrekryvu.POD_PRUHEM;
		final Rectangle detail = new Rectangle(0, y + pref.height - 80, 1280, 200);
		popup.setPrekazky(() -> java.util.Collections.singletonList(detail));
		popup.doLayout();
		rozloz(o);
		Assert.assertEquals("popup má plnou výšku", pref.height, o.getHeight());
		Assert.assertEquals("matice má plnou výšku", o.matice.getPreferredSize().height, o.matice.getHeight());
		Assert.assertEquals("matice má plnou šířku", o.matice.getPreferredSize().width, o.matice.getWidth());
		Assert.assertTrue("nadpis není zkrácený: " + o.nadpis.getWidth(), o.nadpis.getWidth() >= o.nadpis.getPreferredSize().width);
	}

	/** Když je okno mapy samo nižší než popup, obsah se posouvá a matice a nadpis zůstanou celé. */
	@Test
	public void vNizkemOknePopupPosouvaAMaticeZustaneCela() throws Exception {
		priprav();
		final Challenge c = aktivujPolicka(Challenge.Typ.DT_MATICE);
		popup.prepni();
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Arrays.asList("D1/T1", "D3/T3"),
				java.util.Collections.singletonList(new KandidatPolicka("GC5", 50, 14, "D3/T3", "D3/T3", null)), 50));
		SwingUtilities.invokeAndWait(() -> {});
		final JPopupPlanovace.Obsah o = popup.obsah;
		popup.setBounds(0, 0, 1280, 330);
		popup.doLayout();
		rozloz(o);
		Assert.assertTrue("popup je kratší než obsah", o.getHeight() < o.getPreferredSize().height);
		Assert.assertEquals("matice má plnou výšku", o.matice.getPreferredSize().height, o.matice.getHeight());
		Assert.assertEquals("matice má plnou šířku", o.matice.getPreferredSize().width, o.matice.getWidth());
		Assert.assertTrue("nadpis není zkrácený: " + o.nadpis.getWidth(), o.nadpis.getWidth() >= o.nadpis.getPreferredSize().width);
		final javax.swing.JScrollBar svisly = o.posuvnikObsahu.getVerticalScrollBar();
		Assert.assertTrue("svislý posuvník je vidět", svisly.isVisible());
		Assert.assertEquals("na začátku je nahoře", 0, o.posuvnikObsahu.getViewport().getViewPosition().y);
		svisly.setValue(svisly.getMaximum());
		rozloz(o);
		Assert.assertTrue("posunutím se obsah posune", o.posuvnikObsahu.getViewport().getViewPosition().y > 0);
		Assert.assertEquals("a dole je vidět konec obsahu", o.posuvnikObsahu.getViewport().getView().getHeight(), o.posuvnikObsahu.getViewport().getViewPosition().y + o.posuvnikObsahu.getViewport().getHeight());
	}

	/** Popup v rozvržení srohama leží nad neprůhledným panelem v rohu: klik v překryvu zasáhne popup, ne panel. */
	@Test
	public void popupLeziNadPanelemVRohu() throws Exception {
		priprav();
		popup.prepni();
		final JPanel srohama = new JPanel(new cz.geokuk.util.gui.CornerLayoutManager());
		final JPanel detail = new JPanel();
		detail.setOpaque(true);
		detail.setPreferredSize(new Dimension(500, 400));
		final JPanel podklad = new JPanel();
		srohama.add(detail, cz.geokuk.util.gui.ERoh.JZ);
		srohama.add(podklad);
		srohama.add(popup, cz.geokuk.util.gui.ERoh.NAD, 0);
		srohama.setSize(1050, 450);
		popup.setBounds(0, 0, 0, 0);
		rozloz(srohama);
		Assert.assertEquals("vrstva NAD pokrývá celý podklad", podklad.getBounds(), popup.getBounds());
		Assert.assertEquals(new Rectangle(0, 0, 1050, 450), popup.getBounds());
		final java.util.List<Rectangle> prekazky = RozmisteniPrekryvu.prekazky(srohama, podklad, false, popup);
		Assert.assertEquals("popup není překážka sám sobě", java.util.Collections.singletonList(detail.getBounds()), prekazky);
		Assert.assertEquals("bez výjimky by byl překážkou", 2, RozmisteniPrekryvu.prekazky(srohama, podklad, false).size());
		popup.setPrekazky(() -> java.util.Collections.singletonList(detail.getBounds()));
		rozloz(srohama);
		final Rectangle o = popup.obsah.getBounds();
		final Rectangle d = detail.getBounds();
		Assert.assertTrue("popup se s Detailem překrývá: " + o + " " + d, o.intersects(d));
		final Rectangle spolecne = o.intersection(d);
		final Component cil = srohama.findComponentAt(spolecne.x + spolecne.width / 2, spolecne.y + spolecne.height / 2);
		Assert.assertNotNull(cil);
		Assert.assertTrue("v překryvu je popup, ne Detail: " + cil, javax.swing.SwingUtilities.isDescendingFrom(cil, popup));
		Assert.assertEquals("Detail mimo popup zůstane klikatelný", detail, srohama.findComponentAt(d.x + 2, d.y + d.height - 2));
	}

	@Test
	public void nenastavenyDomovVyzveKeZmeneDomova() {
		final String text = TextyPopupuPlanovace.chyba("Domovské souřadnice nejsou nastavené.", 300);
		Assert.assertTrue(text, text.contains("Klikněte na mapu na místo domova a zvolte Soubor > Jsem doma…"));
	}

	@Test
	public void chybaVPredpoctuSePropisePlanovaciMistoPocitaSe() throws Exception {
		priprav();
		popup.prepni();
		final String duvod = "Keš GC4PCQB (střed challenge) není v načtených datech.";
		model.nastavStavPredpoctu(aktivni(), new StavPredpoctu(StavPredpoctu.Faze.CHYBA, 0, 0, duvod));
		SwingUtilities.invokeAndWait(() -> {});
		final String text = popup.obsah.souhrn.getText();
		Assert.assertTrue(text, text.contains(duvod));
		Assert.assertFalse(text, text.contains("Počítá se"));
		Assert.assertFalse("bez výsledku se ukáže jen chyba", popup.obsah.matice.isVisible());
		Assert.assertTrue(text, text.contains("vytvořte kopii se středem Domov či Souřadnice"));
		Assert.assertFalse("bez výsledku se ukáže jen chyba", popup.obsah.postup.isVisible());
		Assert.assertFalse(popup.obsah.nadpisSeznamu.isVisible());
		Assert.assertFalse(popup.obsah.posuvnik.isVisible());
		model.nastavStavPredpoctu(aktivni(), new StavPredpoctu(StavPredpoctu.Faze.HOTOVO, 1, 1, null));
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213 }, new int[0], new double[][] { { 213, 2800 }, { 47, 6200 } }));
		Assert.assertTrue("po chybě se postup vrátí", popup.obsah.postup.isVisible());
		Assert.assertTrue("po chybě se vrátí nadpis seznamu", popup.obsah.nadpisSeznamu.isVisible());
		Assert.assertTrue("po chybě se vrátí seznam", popup.obsah.posuvnik.isVisible());
		Assert.assertTrue(popup.obsah.souhrn.getText(), popup.obsah.souhrn.getText().startsWith("Splněno"));
	}

	@Test
	public void splnenaChallengeNemaKamDal() throws Exception {
		priprav();
		popup.prepni();
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[0], new int[0], new double[0][]));
		Assert.assertEquals("Splněno 360 z 360 (100 %), chybí 0", popup.obsah.souhrn.getText());
		Assert.assertEquals(0, popup.obsah.radky.size());
		Assert.assertFalse(popup.obsah.dalsi.isEnabled());
		Assert.assertEquals("100 %", popup.obsah.postup.getString());
	}

	private static int posluchacuMysi() {
		return Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK).length;
	}

	/** Otevřený popup má jediný globální posluchač myši a po zavření (i Esc, kliknutím mimo, vypnutím, odebráním z okna) žádný. */
	@Test
	public void posluchacMysiJenPoDobuOtevreni() throws Exception {
		priprav();
		final int pred = posluchacuMysi();
		for (int i = 0; i < 3; i++) {
			popup.prepni();
			Assert.assertEquals("otevřený", pred + 1, posluchacuMysi());
			popup.prepni();
			Assert.assertEquals("zavřený odznakem", pred, posluchacuMysi());
		}
		popup.prepni();
		popup.doLayout();
		popup.mysKdekoli(stisk(mapa, 1200, 600));
		Assert.assertEquals("zavřený kliknutím mimo", pred, posluchacuMysi());
		popup.prepni();
		model.setZapnuto(false);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("zavřený vypnutím plánovače", pred, posluchacuMysi());
		model.setZapnuto(true);
		popup.prepni();
		popup.removeNotify();
		Assert.assertFalse(popup.jeOtevreno());
		Assert.assertEquals("zavřený odebráním z okna", pred, posluchacuMysi());
	}

	private static List<String> radky(final JPopupPlanovace.Obsah o) {
		final List<String> r = new ArrayList<>();
		for (int i = 0; i < o.radky.size(); i++) {
			r.add(o.radky.get(i));
		}
		return r;
	}

	/** Popup ukazuje aktivní challenge; po zapnutí jiné ukáže tu. */
	@Test
	public void ukazujeAktivniChallenge() throws Exception {
		priprav();
		model.aktivuj(Sablony.praha360());
		SwingUtilities.invokeAndWait(() -> {});
		popup.prepni();
		Assert.assertEquals(TextyPopupuPlanovace.nadpis(Sablony.praha360()), popup.obsah.nadpis.getText());
		model.aktivuj(Sablony.cesko360());
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("po zapnutí jiné", TextyPopupuPlanovace.nadpis(Sablony.cesko360()), popup.obsah.nadpis.getText());
	}

	private Challenge aktivujPolicka(final Challenge.Typ typ) throws Exception {
		final Challenge c = model.ulozVlastni(new Challenge("Test " + typ, typ, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
		model.aktivuj(c);
		model.setZobrazena(c);
		SwingUtilities.invokeAndWait(() -> {});
		return c;
	}

	@Test
	public void dtMaticeUkazujeChybejiciPolicka() throws Exception {
		priprav();
		final Challenge c = aktivujPolicka(Challenge.Typ.DT_MATICE);
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertEquals("Typ: D/T", o.typ.getText());
		Assert.assertTrue("bez výsledku průběh", o.souhrn.getText().startsWith("Počítá se"));
		Assert.assertFalse("bez výsledku matice není (ukázala by vše splněné)", o.matice.isVisible());
		Assert.assertFalse(o.dalsi.isEnabled());
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Arrays.asList("D1/T1", "D5/T5"), java.util.Collections.emptyList()));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Splněno 79 z 81 (98 %), chybí 2", o.souhrn.getText());
		Assert.assertEquals(81, o.postup.getMaximum());
		Assert.assertEquals(79, o.postup.getValue());
		Assert.assertEquals("98 %", o.postup.getString());
		Assert.assertTrue("matice místo seznamu", o.matice.isVisible());
		Assert.assertTrue(o.legenda.isVisible());
		Assert.assertEquals(TextyPopupuPlanovace.vybranePolicko(null, 0, false, false), o.nadpisSeznamu.getText());
		Assert.assertEquals(JMaticeDT.Stav.BEZ_KANDIDATU, o.matice.stav("D5/T5"));
		Assert.assertEquals(JMaticeDT.Stav.SPLNENO, o.matice.stav("D2/T2"));
		Assert.assertFalse("střed je jen u 360°", o.stred.isVisible());
		Assert.assertFalse("prázdné upozornění nezabírá místo", o.nejiste.isVisible());
		Assert.assertFalse(o.omezeniStitku.isVisible());
		Assert.assertEquals("▶ Další políčko", o.dalsi.getText());
		Assert.assertFalse("bez kandidátů není kam jít", o.dalsi.isEnabled());
		Assert.assertFalse(o.vsechnaPolicka.isVisible());
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Arrays.asList("D1/T1", "D5/T5"), java.util.Collections.singletonList(new KandidatPolicka("GC5", 50, 14, "D5/T5", "D5/T5", null)), 50));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue(o.dalsi.isEnabled());
		Assert.assertEquals("1", o.matice.pocet("D5/T5"));
		o.dalsi.doClick();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("▶ vybere políčko s kandidáty", "D5/T5", model.vybranePolicko(c));
		Assert.assertEquals("D5/T5", o.matice.vybrane());
		Assert.assertNull("▶ filtr nezapíná", model.jenKandidatiPro());
		Assert.assertEquals("Vybrané políčko: D5/T5 – 1 kandidát, na mapě všechny keše (F4)", o.nadpisSeznamu.getText());
		Assert.assertTrue(o.vsechnaPolicka.isVisible());
		o.vsechnaPolicka.doClick();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull("✕ Zrušit výběr políčka zruší výběr", model.vybranePolicko(c));
		Assert.assertNull(o.matice.vybrane());
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Collections.emptyList(), java.util.Collections.emptyList()));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Splněno 81 z 81 (100 %), chybí 0", o.souhrn.getText());
		Assert.assertFalse("splněná matice nemá kam dál", o.dalsi.isEnabled());
	}

	/** D/T s cílem 3: D1/T1 má 1 nález a 5 kandidátů, D2/T1 je splněné (4 nálezy), ostatní bez nálezů a bez kandidátů. */
	static VysledekPolicek nasobny() {
		final int[] nalezu = new int[81];
		nalezu[VypocetMatice.indexStitku("D1/T1")] = 1;
		nalezu[VypocetMatice.indexStitku("D2/T1")] = 4;
		final java.util.List<String> chybi = new java.util.ArrayList<>();
		for (int i = 0; i < 81; i++) {
			if (nalezu[i] < 3) {
				chybi.add(VypocetMatice.stitek(i / 9, i % 9));
			}
		}
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			k.add(new KandidatPolicka("GC" + i, 50, 14, "D1/T1", "D1/T1", null));
		}
		return new VysledekPolicek(81, chybi, k, 50, 3, nalezu);
	}

	/** S násobností počítá souhrn a ukazatel postupu nálezy; řádek nad maticí i nálezy vybraného políčka. */
	@Test
	public void nasobnostVPopupuPocitaNalezy() throws Exception {
		priprav();
		final Challenge c = model.ulozVlastni(new Challenge("DT3", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC).sOpakovanim(3));
		model.aktivuj(c);
		model.setZobrazena(c);
		SwingUtilities.invokeAndWait(() -> {});
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		model.nastavVysledekPolicek(c, nasobny());
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Splněno 4 z 243 nálezů (2 %), chybí 239 nálezů v 80 políčkách", o.souhrn.getText());
		Assert.assertEquals(243, o.postup.getMaximum());
		Assert.assertEquals(4, o.postup.getValue());
		Assert.assertEquals(TextyPopupuPlanovace.legendaMatice(3), o.legenda.getText());
		Assert.assertEquals("1/3", o.matice.nalezyZCile("D1/T1"));
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko("D1/T1"));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("Vybrané políčko: D1/T1 – 1 z 3 nálezů, 5 kandidátů", o.nadpisSeznamu.getText());
	}

	/** Výběr políčka u 360° nic nezmění: políčka má jen matice D/T. */
	@Test
	public void vyberPolickaU360NicNedela() throws Exception {
		priprav();
		popup.prepni();
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47 }, new int[0], new double[][] { { 47, 6200 } }));
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko("D1/T1"));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull(model.vybranePolicko(aktivni360()));
		Assert.assertNull("filtr se nezapne", model.jenKandidatiPro());
	}

	/** Klik na políčko matice ho vybere a zapne „jen kandidáti“; F4 pak filtr vypne a výběr nechá; druhý klik výběr zruší. */
	@Test
	public void klikNaPolickoMaticeVyberePolickoAZapneFiltr() throws Exception {
		priprav();
		final Challenge c = aktivujPolicka(Challenge.Typ.DT_MATICE);
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		for (int i = 0; i < 50; i++) {
			k.add(new KandidatPolicka("GC" + i, 50, 14, "D3/T4", "D3/T4", null));
		}
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Arrays.asList("D3/T4"), k, 50));
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko("D3/T4"));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("D3/T4", model.vybranePolicko(c));
		Assert.assertEquals("klik zapne jen kandidáty", c, model.jenKandidatiPro());
		Assert.assertEquals("plné políčko", "Vybrané políčko: D3/T4 – 50 nejbližších ke středu mapy", o.nadpisSeznamu.getText());
		Assert.assertTrue(o.kandidati.isSelected());
		SwingUtilities.invokeAndWait(() -> model.setJenKandidatiPro(null));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("F4 výběr políčka nechá", "D3/T4", model.vybranePolicko(c));
		Assert.assertEquals("Vybrané políčko: D3/T4 – 50 nejbližších ke středu mapy, na mapě všechny keše (F4)", o.nadpisSeznamu.getText());
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko(null));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull(model.vybranePolicko(c));
		Assert.assertEquals(TextyPopupuPlanovace.vybranePolicko(null, 0, false, false), o.nadpisSeznamu.getText());
	}

	/** Vybraný sektor bez kandidáta ve výřezu má v popupu dovětek „mimo výřez“, vybrané políčko D/T „mimo výřez – Ctrl+Shift+šipka ukáže nejbližšího kandidáta“. */
	@Test
	public void vyberMimoVyrezMaDovetekVPopupu() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213 }, new int[0], new double[][] { { 213, 2800 }, { 47, 6200 } }));
		model.setVybranySektor(aktivni(), 213);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse(radky(o).toString(), radky(o).toString().contains(TextyPopupuPlanovace.MIMO_VYREZ_SEKTORU));
		model.nahlasVyberVeVyrezu(1);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse("kandidát je ve výřezu", radky(o).toString().contains(TextyPopupuPlanovace.MIMO_VYREZ_SEKTORU));
		model.nahlasVyberVeVyrezu(0);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(java.util.Arrays.asList("47°  1 kandidát  nejbližší 6,2 km od středu", "213°  1 kandidát  nejbližší 2,8 km od středu  mimo výřez"), radky(o));
		final Challenge dt = aktivujPolicka(Challenge.Typ.DT_MATICE);
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		k.add(new KandidatPolicka("GC1", 50, 14, "D3/T4", "D3/T4", null));
		model.nastavVysledekPolicek(dt, new VysledekPolicek(81, java.util.Arrays.asList("D3/T4"), k, 50));
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko("D3/T4"));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("nad maticí", o.mimoVyrez.isVisible());
		Assert.assertTrue("bez středu mapy obecný text: " + o.mimoVyrez.getText(), o.mimoVyrez.getText().contains(TextyPopupuPlanovace.MIMO_VYREZ));
		popup.setStredMapy(() -> new cz.geokuk.core.coordinates.Wgs(50, 14.1));
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko(null));
		SwingUtilities.invokeAndWait(() -> popup.vyberPolicko("D3/T4"));
		SwingUtilities.invokeAndWait(() -> {});
		final String vzdalenost = StitkyPruhu.vzdalenost(Geodezie.vzdalenostKoule(50, 14.1, 50, 14));
		Assert.assertTrue(o.mimoVyrez.getText(), o.mimoVyrez.getText().contains("mimo výřez – nejbližší kandidát je " + vzdalenost + " od středu mapy, Ctrl+Shift+← na něj skočí"));
		final cz.geokuk.core.coordinates.Wgs[] stred = { new cz.geokuk.core.coordinates.Wgs(50, 14.1) };
		popup.setStredMapy(() -> stred[0]);
		stred[0] = new cz.geokuk.core.coordinates.Wgs(50.1, 14);
		popup.onEvent(new cz.geokuk.core.coord.VyrezChangedEvent(null));
		final String posunuta = StitkyPruhu.vzdalenost(Geodezie.vzdalenostKoule(50.1, 14, 50, 14));
		Assert.assertTrue("po posunu mapy nová vzdálenost a šipka: " + o.mimoVyrez.getText(), o.mimoVyrez.getText().contains("je " + posunuta + " od středu mapy, Ctrl+Shift+↓ na něj skočí"));
		model.nahlasVyberVeVyrezu(3);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertFalse(o.mimoVyrez.isVisible());
	}

	/** Popup s nápovědou není širší než bez ní (nezávisle na písmu platformy), jen o malou rezervu. */
	private static void assertNapovedaNerozsiruje(final String co, final JPopupPlanovace.Obsah o) {
		final int s = o.getPreferredSize().width;
		o.napoveda.setVisible(false);
		o.invalidate();
		final int bez = o.getPreferredSize().width;
		o.napoveda.setVisible(true);
		o.invalidate();
		Assert.assertTrue(co + ": popup " + s + " px, bez nápovědy " + bez + " px", s <= bez + 30);
		Assert.assertTrue(co + ": nápověda " + o.napoveda.getPreferredSize() + " je užší než popup " + s, o.napoveda.getPreferredSize().width <= s);
	}

	/** Nápověda je zalomená na pevnou šířku a popup nerozšiřuje; D/T má pod maticí stejná tlačítka jako 360°. */
	@Test
	public void napovedaJeZalomenaAPopupNerozsirujeADtMaStejnaTlacitka() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[] { 213 }, new double[][] { { 213, 2800, 3000 }, { 47, 6200 } }));
		assertNapovedaNerozsiruje("360°", o);
		Assert.assertTrue(o.dalsi.isVisible() && o.kandidati.isVisible());
		final Challenge dt = aktivujPolicka(Challenge.Typ.DT_MATICE);
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		k.add(new KandidatPolicka("GC1", 50, 14, "D3/T4", "D3/T4", null));
		model.nastavVysledekPolicek(dt, new VysledekPolicek(81, java.util.Arrays.asList("D3/T4"), k, 50), 50.0, 14.0);
		SwingUtilities.invokeAndWait(() -> {});
		assertNapovedaNerozsiruje("D/T", o);
		Assert.assertTrue("D/T má Další políčko i Jen kandidáti", o.dalsi.isVisible() && o.kandidati.isVisible());
		Assert.assertTrue(o.napoveda.getText(), o.napoveda.getText().contains("F4"));
	}

	@Test
	public void maticeZvyraznujePolickoVybraneKese() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		final Challenge dt = aktivujPolicka(Challenge.Typ.DT_MATICE);
		final java.util.List<KandidatPolicka> k = new java.util.ArrayList<>();
		k.add(new KandidatPolicka("GC1", 50, 14, "D3/T4", "D3/T4", null));
		k.add(new KandidatPolicka("GC2", 50, 14, "D5/T5", "D5/T5", null));
		model.nastavVysledekPolicek(dt, new VysledekPolicek(81, java.util.Arrays.asList("D3/T4", "D5/T5"), k, 50), 50.0, 14.0);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull("bez výběru nic", o.matice.vybrane());
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(new VybranaKes("GC9", 50.0, 14.0, 4, 6)));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("políčko vybrané keše je zvýrazněné", "D3/T4", o.matice.vybrane());
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(new VybranaKes("GC8", 50.0, 14.0, 8, 8)));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("zvýraznění se přesune", "D5/T5", o.matice.vybrane());
		SwingUtilities.invokeAndWait(() -> model.setVybranaKes(null));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertNull("po kliku mimo zvýraznění zmizí", o.matice.vybrane());
		Assert.assertEquals("ale ◀ ▶ navážou na poslední", "D5/T5", model.vychoziPolickoPosunu(dt));
	}

	@Test
	public void kalendarUkazujeChybejiciDnyAPo360SeVratiSektory() throws Exception {
		priprav();
		model.setKalendarVUi(true);
		final Challenge c = aktivujPolicka(Challenge.Typ.KALENDAR);
		model.nastavVysledekPolicek(c, new VysledekPolicek(366, java.util.Arrays.asList("14. 3.", "29. 2."), java.util.Collections.emptyList()));
		SwingUtilities.invokeAndWait(() -> {});
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertEquals("Typ: Kalendář", o.typ.getText());
		Assert.assertEquals("Chybějící dny:", o.nadpisSeznamu.getText());
		Assert.assertEquals(366, o.postup.getMaximum());
		Assert.assertEquals(java.util.Arrays.asList("14. 3.", "29. 2."), radky(o));
		model.aktivuj(Sablony.cesko360());
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47 }, new int[0], new double[][] { { 47, 6200 } }));
		Assert.assertEquals("Typ: 360°    Pravidla: " + TextyPopupuPlanovace.pravidla(aktivni360().zdroj()), o.typ.getText());
		Assert.assertEquals(360, o.postup.getMaximum());
		Assert.assertTrue(o.stred.isVisible());
		Assert.assertEquals(TextyPopupuPlanovace.nadpisSeznamu(Challenge.Typ.SEKTORY_360), o.nadpisSeznamu.getText());
		Assert.assertEquals(java.util.Collections.singletonList("47°  1 kandidát  nejbližší 6,2 km od středu"), radky(o));
	}

	private Challenge aktivni360() {
		for (final Challenge c : model.aktivni()) {
			if (c.typ() == Challenge.Typ.SEKTORY_360) {
				return c;
			}
		}
		throw new AssertionError("není aktivní 360°");
	}

	/** Nastavení… zavře popup a spustí akci dialogu plánovače z menu (bez displeje a továrny akce skončí výjimkou, ta ukáže, kudy volání šlo). */
	@Test
	public void nastaveniSpustiAkciDialogu() throws Exception {
		priprav();
		final cz.geokuk.core.program.Akce akce = new cz.geokuk.core.program.Akce();
		popup.inject(akce);
		Assert.assertTrue(popup.obsah.nastaveni.isEnabled());
		popup.prepni();
		try {
			popup.obsah.nastaveni.doClick();
			Assert.fail("akce dialogu se měla spustit");
		} catch (final RuntimeException e) {
			// Bez displeje selže už vytvoření dialogu (zásobník vede přes akci), s displejem akce ohlásí chybějící továrnu (zpráva nese třídu akce).
			boolean akceDialogu = e.getMessage() != null && e.getMessage().contains(ChallengeNastaveniAction.class.getName());
			for (final StackTraceElement r : e.getStackTrace()) {
				akceDialogu |= r.getClassName().equals(ChallengeNastaveniAction.class.getName());
			}
			Assert.assertTrue("volání prošlo akcí dialogu: " + e, akceDialogu);
		}
		Assert.assertFalse("popup se před dialogem zavře", popup.jeOtevreno());
	}

	@Test
	public void dlouhyNazevChallengeNerozsiriPopupAVeZkracenemTvaruMaTooltip() {
		final JPopupPlanovace.Obsah o = new JPopupPlanovace.Obsah();
		final String text = TextyPopupuPlanovace.nadpis(Sablony.cesko360());
		o.nadpis.setPlnyText(text);
		Assert.assertEquals("getText je plný", text, o.nadpis.getText());
		Assert.assertTrue(o.nadpis.getPreferredSize().width <= JPopupPlanovace.NEJVETSI_SIRKA_NADPISU);
		o.nadpis.setSize(o.nadpis.getPreferredSize());
		Assert.assertTrue("zkrácený text: " + text.length(), o.nadpis.getToolTipText(null) != null || o.nadpis.getPreferredSize().width >= o.nadpis.getFontMetrics(o.nadpis.getFont()).stringWidth(text));
		o.nadpis.setSize(150, 20);
		Assert.assertEquals(text, o.nadpis.getToolTipText(null));
	}

	/** Klik na řádek s kandidáty vybere sektor (bez výběru keše), druhý klik výběr zruší; řádek bez kandidátů nic nedělá. ▶ jde po kružnici. */
	@Test
	public void klikNaRadekVyberSektorATlacitkoDalsiPoKruznici() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[0], new double[][] { { 213, 2800, 3000 }, { 47, 6200 } }));
		final Challenge c = aktivni360();
		Assert.assertEquals(-1, o.seznam.getSelectedIndex());
		SwingUtilities.invokeAndWait(() -> popup.klikNaRadek(o.polozky.get(1)));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(213, model.vybranySektor(c));
		Assert.assertEquals(1, o.seznam.getSelectedIndex());
		Assert.assertNull("keš se nevybírá", model.vybranyKandidat(c));
		SwingUtilities.invokeAndWait(() -> popup.klikNaRadek(o.polozky.get(1)));
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("druhý klik výběr zruší", PosunVyberu.ZADNY_SEKTOR, model.vybranySektor(c));
		Assert.assertEquals(-1, o.seznam.getSelectedIndex());
		o.dalsi.doClick();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("▶ od začátku kružnice", 47, model.vybranySektor(c));
		o.dalsi.doClick();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals(213, model.vybranySektor(c));
		o.dalsi.doClick();
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertEquals("za posledním s kandidáty zase první", 47, model.vybranySektor(c));
		Assert.assertEquals(0, o.seznam.getSelectedIndex());
	}

	/** Výběr v seznamu se nemění klávesnicí ani přímým výběrem, jen podle modelu. */
	@Test
	public void vyberVSeznamuJenPodleModelu() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213 }, new int[0], new double[][] { { 213, 2800 }, { 47, 6200 } }));
		o.seznam.setSelectedIndex(1);
		Assert.assertEquals(-1, o.seznam.getSelectedIndex());
		Assert.assertEquals(PosunVyberu.ZADNY_SEKTOR, model.vybranySektor(aktivni360()));
	}

	/** Domov s pozicí na mapě: akce „Jsem doma“ z menu, domov v nastavení bez preferencí. */
	private static final class Doma {
		final cz.geokuk.core.coordinates.Wgs[] hc = { cz.geokuk.core.coord.VyrezModel.DEFAULTNI_DOMACI_SOURADNICE };
		final cz.geokuk.plugins.refbody.RefbodyModel refbody = new cz.geokuk.plugins.refbody.RefbodyModel() {
			@Override
			public cz.geokuk.core.coordinates.Wgs getHc() {
				return hc[0];
			}

			@Override
			public void setHc(final cz.geokuk.core.coordinates.Wgs novy) {
				hc[0] = novy;
			}
		};
		final cz.geokuk.plugins.refbody.TadyJsemDomaAction akce = new cz.geokuk.plugins.refbody.TadyJsemDomaAction();
		final cz.geokuk.core.coord.PoziceModel pozice = new cz.geokuk.core.coord.PoziceModel();
		java.util.function.Consumer<cz.geokuk.core.coord.PoziceChangedEvent> poslech = e -> {};

		Doma() {
			akce.inject(refbody);
			pozice.inject(new EventFirer() {
				@Override
				public void fire(final Event0<?> event) {
					if (event instanceof cz.geokuk.core.coord.PoziceChangedEvent) {
						akce.onEvent((cz.geokuk.core.coord.PoziceChangedEvent) event);
						poslech.accept((cz.geokuk.core.coord.PoziceChangedEvent) event);
					}
				}
			});
			pozice.clearPozice();
		}
	}

	private void aktivujDomov() throws Exception {
		model.deaktivuj(aktivni());
		model.aktivuj(Sablony.od360Domova());
		SwingUtilities.invokeAndWait(() -> {});
	}

	/** U středu „domov“ jsou souřadnice domova; bez nastaveného domova to řádek řekne. */
	@Test
	public void stredDomovUkazeSouradnice() throws Exception {
		priprav();
		final Doma d = new Doma();
		popup.inject(d.refbody);
		aktivujDomov();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertEquals("Střed: domov není nastavený", o.stred.getText());
		Assert.assertFalse("odkaz Změnit domov… v popupu není", maTlacitko(o, "Změnit domov…"));
		Assert.assertTrue("Nastavení plánovače… v popupu zůstává", maTlacitko(o, "Nastavení plánovače…"));
		d.hc[0] = new cz.geokuk.core.coordinates.Wgs(50.0542, 14.39);
		popup.onEvent(new cz.geokuk.plugins.refbody.DomaciSouradniceSeZmenilyEvent(d.hc[0]));
		Assert.assertEquals("Střed: domov " + d.hc[0], o.stred.getText());
		Assert.assertTrue(o.stred.getText(), o.stred.getText().startsWith("Střed: domov N50°"));
	}

	private static boolean maTlacitko(final java.awt.Container c, final String text) {
		for (final java.awt.Component k : c.getComponents()) {
			if (k instanceof javax.swing.AbstractButton && text.equals(((javax.swing.AbstractButton) k).getText()) || k instanceof java.awt.Container && maTlacitko((java.awt.Container) k, text)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void textStreduDomova() {
		Assert.assertEquals("domov není nastavený", TextyPopupuPlanovace.stredDomov(null));
		Assert.assertEquals("domov není nastavený", TextyPopupuPlanovace.stredDomov(cz.geokuk.core.coord.VyrezModel.DEFAULTNI_DOMACI_SOURADNICE));
		final cz.geokuk.core.coordinates.Wgs w = new cz.geokuk.core.coordinates.Wgs(50.0542, 14.39);
		Assert.assertEquals("domov " + w, TextyPopupuPlanovace.stredDomov(w));
	}

	/** Výchozí výška popupu u 360° je aspoň taková jako u D/T, aby se popup při přepnutí typu zbytečně nezkracoval. */
	@Test
	public void vyskaPopupu360NeniMensiNezDT() throws Exception {
		priprav();
		popup.prepni();
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[] { 213 }, new double[][] { { 213, 2800, 3000 }, { 47, 6200 } }));
		final int vyska360 = popup.obsah.getPreferredSize().height;
		final Challenge c = aktivujPolicka(Challenge.Typ.DT_MATICE);
		model.nastavVysledekPolicek(c, new VysledekPolicek(81, java.util.Arrays.asList("D1/T1", "D3/T3"),
				java.util.Collections.singletonList(new KandidatPolicka("GC5", 50, 14, "D3/T3", "D3/T3", null)), 50));
		SwingUtilities.invokeAndWait(() -> {});
		final int vyskaDT = popup.obsah.getPreferredSize().height;
		Assert.assertTrue("360° " + vyska360 + " px, D/T " + vyskaDT + " px", vyska360 >= vyskaDT);
	}

	/** Klik na ◀ ▶, Jen kandidáti, Sektory a Štítky popup nezavře; klik na výběr challenge a do mapy ano. */
	@Test
	public void klikNaOvladaniListyPopupNezavre() throws Exception {
		priprav();
		final java.util.List<Rectangle> ovladani = JPruhPlanovace.oblastiOvladani(1280, java.util.Collections.<Rectangle> emptyList());
		Assert.assertEquals("◀ ▶, Jen kandidáti, Sektory, Štítky", 5, ovladani.size());
		for (final Rectangle r : ovladani) {
			popup.prepni();
			popup.doLayout();
			Assert.assertTrue(popup.jeOtevreno());
			final MouseEvent klik = stisk(mapa, r.x + r.width / 2, r.y + r.height / 2);
			popup.mysKdekoli(klik);
			Assert.assertTrue("klik na ovládací prvek lišty " + r, popup.jeOtevreno());
			Assert.assertFalse("klik se neztratí", klik.isConsumed());
			popup.zavri();
		}
		popup.prepni();
		final Rectangle vyber = JPruhPlanovace.rozloz(cz.geokuk.framework.RozmisteniPrekryvu.sirkaPruhu(cz.geokuk.framework.RozmisteniPrekryvu.volnaSirkaPruhu(1280)))[JPruhPlanovace.VYBER];
		popup.mysKdekoli(stisk(mapa, vyber.x + 2, vyber.y + 2));
		Assert.assertFalse("klik na výběr challenge popup zavře", popup.jeOtevreno());
	}

	/** Posun ◀ ▶ (Shift+←/→) a Jen kandidáti (F4) popup nezavírají. */
	@Test
	public void posunAFiltrKlavesamiPopupNezavrou() throws Exception {
		priprav();
		popup.prepni();
		ChallengeAkce.posun(model, true);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("posun ◀ ▶", popup.jeOtevreno());
		model.setKresliSektory(false);
		model.setKresliStitky(false);
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("přepínače Sektory a Štítky", popup.jeOtevreno());
		model.setJenKandidatiPro(aktivni());
		SwingUtilities.invokeAndWait(() -> {});
		Assert.assertTrue("Jen kandidáti (F4)", popup.jeOtevreno());
	}
}
