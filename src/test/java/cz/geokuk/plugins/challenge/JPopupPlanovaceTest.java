package cz.geokuk.plugins.challenge;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.Assert;
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
		model.nastavVysledek(aktivni(), v);
		SwingUtilities.invokeAndWait(() -> {});
	}

	private static MouseEvent stisk(final Component c, final int x, final int y) {
		return new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
	}

	@Test
	public void polohaPodOdznakemUvnitrMapy() {
		final Dimension pozadovana = new Dimension(420, 330);
		final Rectangle odznak = JPopupPlanovace.oblastOdznaku(1920);
		final Rectangle r = JPopupPlanovace.poloha(new Dimension(1920, 1000), pozadovana);
		Assert.assertEquals("pod pruhem", RozmisteniPrekryvu.OKRAJ + RozmisteniPrekryvu.VYSKA_PRUHU + JPopupPlanovace.POD_PRUHEM, r.y);
		Assert.assertEquals("kousek před odznakem", odznak.x - JPopupPlanovace.PRED_ODZNAKEM, r.x);
		Assert.assertEquals(pozadovana, r.getSize());
		Assert.assertTrue("odznak zůstane vidět nad popupem", odznak.y + odznak.height <= r.y);
		for (final Dimension m : new Dimension[] { new Dimension(1920, 1000), new Dimension(1280, 700), new Dimension(800, 500), new Dimension(450, 400), new Dimension(400, 300), new Dimension(300, 200) }) {
			final Rectangle p = JPopupPlanovace.poloha(m, pozadovana);
			final Rectangle uvnitr = new Rectangle(RozmisteniPrekryvu.OKRAJ, 0, m.width - 2 * RozmisteniPrekryvu.OKRAJ, m.height - RozmisteniPrekryvu.OKRAJ);
			Assert.assertTrue(m + ": " + p, uvnitr.contains(p));
			Assert.assertFalse(m + ": popup nepřekrývá pruh " + p, p.intersects(RozmisteniPrekryvu.pruhPlanovace(RozmisteniPrekryvu.volnaSirkaPruhu(m.width))));
		}
		final Rectangle stredni = JPopupPlanovace.poloha(new Dimension(450, 400), pozadovana);
		Assert.assertEquals("když se vedle odznaku nevejde, posune se doleva", 450 - RozmisteniPrekryvu.OKRAJ, stredni.x + stredni.width);
		Assert.assertEquals(pozadovana.width, stredni.width);
		Assert.assertEquals("v úzké mapě se zúží", 400 - 2 * RozmisteniPrekryvu.OKRAJ, JPopupPlanovace.poloha(new Dimension(400, 300), pozadovana).width);
		Assert.assertEquals("v nízké mapě se zkrátí", 300 - r.y - RozmisteniPrekryvu.OKRAJ, JPopupPlanovace.poloha(new Dimension(800, 300), pozadovana).height);
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
		final Rectangle odznak = JPopupPlanovace.oblastOdznaku(1280);
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
	public void obsahPodleVysledku() throws Exception {
		priprav();
		popup.prepni();
		final JPopupPlanovace.Obsah o = popup.obsah;
		Assert.assertTrue(o.souhrn.getText(), o.souhrn.getText().startsWith("Počítá se"));
		Assert.assertFalse("bez výsledku není kam jít", o.dalsi.isEnabled());
		Assert.assertEquals(0, o.radky.size());
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[] { 47, 213, 305 }, new int[] { 213 }, new double[][] { { 213, 2800, 3000 }, { 47, 6200 } }));
		Assert.assertEquals("Splněno 357 z 360, chybí 3", o.souhrn.getText());
		Assert.assertEquals(357, o.postup.getValue());
		Assert.assertEquals("99 %", o.postup.getString());
		final List<String> radky = new ArrayList<>();
		for (int i = 0; i < o.radky.size(); i++) {
			radky.add(o.radky.get(i));
		}
		Assert.assertEquals(java.util.Arrays.asList("213°  2 kandidáti  nejbližší 2,8 km", "47°  1 kandidát  nejbližší 6,2 km", "305°  0 kandidátů  –"), radky);
		Assert.assertEquals("1 sektor je na hraně (±0,05°), počítá se jako nejistý.", o.nejiste.getText());
		Assert.assertTrue(o.dalsi.isEnabled());
		Assert.assertTrue(o.nadpis.getText(), o.nadpis.getText().startsWith("Challenge "));
		Assert.assertEquals("Typ: 360°    Pravidla: " + TextyPopupuPlanovace.pravidla(aktivni().zdroj()), o.typ.getText());
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

	@Test
	public void splnenaChallengeNemaKamDal() throws Exception {
		priprav();
		popup.prepni();
		vysledek(TextyPopupuPlanovaceTest.vysledek(new int[0], new int[0], new double[0][]));
		Assert.assertEquals("Splněno 360 z 360, chybí 0", popup.obsah.souhrn.getText());
		Assert.assertEquals(0, popup.obsah.radky.size());
		Assert.assertFalse(popup.obsah.dalsi.isEnabled());
		Assert.assertEquals("100 %", popup.obsah.postup.getString());
	}
}
