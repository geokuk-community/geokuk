package cz.geokuk.plugins.challenge;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assume;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.EventFirer;
import cz.geokuk.plugins.challenge.NastaveniChallengeTest.MapoveUloziste;

/** Dialog plánovače challenge: seznam, kopie šablony, úprava vlastní definice, zapnutí; potřebuje displej. */
public class JChallengeDialogTest {

	private ChallengeModel model;
	private JChallengeDialog dialog;

	@Before
	public void setUp() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			model = new ChallengeModel();
			model.inject(new EventFirer() {
				@Override
				public void fire(final Event0<?> event) {
					if (dialog != null) {
						dialog.onEvent((ChallengeZmenenyEvent) event);
					}
				}
			});
			final MapoveUloziste u = new MapoveUloziste();
			model.setUloziste(() -> u);
			model.initAfterInject();
			dialog = new JChallengeDialog();
			dialog.inject(model);
		});
	}

	@After
	public void tearDown() throws Exception {
		if (dialog != null) {
			SwingUtilities.invokeAndWait(() -> dialog.dispose());
		}
	}

	private static <T extends Component> List<T> najdi(final Container c, final Class<T> typ, final List<T> vysledek) {
		for (final Component k : c.getComponents()) {
			if (typ.isInstance(k)) {
				vysledek.add(typ.cast(k));
			}
			if (k instanceof Container) {
				najdi((Container) k, typ, vysledek);
			}
		}
		return vysledek;
	}

	/** Viditelné tlačítko s textem; „Vytvořit kopii…“ je u seznamu i v radě u šablony. */
	private AbstractButton tlacitko(final String text) {
		AbstractButton skryte = null;
		for (final AbstractButton b : najdi(dialog.getContentPane(), AbstractButton.class, new ArrayList<AbstractButton>())) {
			if (text.equals(b.getText())) {
				if (viditelne(b)) {
					return b;
				}
				if (skryte == null) {
					skryte = b;
				}
			}
		}
		if (skryte != null) {
			return skryte;
		}
		throw new AssertionError("tlačítko " + text);
	}

	private JTextField pole(final int index) {
		final List<JTextField> pole = new ArrayList<>();
		for (final JTextField t : najdi(dialog.getContentPane(), JTextField.class, new ArrayList<JTextField>())) {
			if (!(t.getParent() instanceof JSpinner.DefaultEditor)) {
				pole.add(t);
			}
		}
		return pole.get(index);
	}

	private String chyba() {
		for (final JLabel l : najdi(dialog.getContentPane(), JLabel.class, new ArrayList<JLabel>())) {
			if (l.getForeground().getRed() == 180 && l.getForeground().getGreen() == 30) {
				return l.getText().trim();
			}
		}
		throw new AssertionError("štítek s chybou");
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Challenge vybrana(final JChallengeDialog d) {
		return ((JList<Challenge>) najdi(d.getContentPane(), JList.class, new ArrayList<JList>()).get(0)).getSelectedValue();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private JList<Challenge> seznam() {
		return najdi(dialog.getContentPane(), JList.class, new ArrayList<JList>()).get(0);
	}

	private void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private void mys(final int radek, final int x, final int id) {
		final JList<Challenge> l = seznam();
		l.setSize(300, 400);
		final java.awt.Rectangle r = l.getCellBounds(radek, radek);
		l.dispatchEvent(new java.awt.event.MouseEvent(l, id, System.currentTimeMillis(), 0, x, r.y + r.height / 2, 1, false, java.awt.event.MouseEvent.BUTTON1));
	}

	private void klik(final int radek, final int x) {
		mys(radek, x, java.awt.event.MouseEvent.MOUSE_PRESSED);
		mys(radek, x, java.awt.event.MouseEvent.MOUSE_RELEASED);
		mys(radek, x, java.awt.event.MouseEvent.MOUSE_CLICKED);
	}

	@Test
	public void klikNaZnackuZapneAVypneChallengeABezVyberuRadku() throws Exception {
		naEdt(() -> {
			final Challenge b = dialog.polozky().get(1);
			Assert.assertEquals(0, seznam().getSelectedIndex());
			Assert.assertFalse(model.jeAktivni(b));
			klik(1, 4);
			Assert.assertTrue("klik na ○ zapne", model.jeAktivni(b));
			Assert.assertEquals("řádek se nevybral", 0, seznam().getSelectedIndex());
			klik(1, 4);
			Assert.assertFalse("klik na ● vypne", model.jeAktivni(b));
			Assert.assertEquals(0, seznam().getSelectedIndex());
		});
	}

	@Test
	public void klikNaZnackuPrepneAktivniChallengeJakoRadio() throws Exception {
		naEdt(() -> {
			final Challenge a = dialog.polozky().get(0);
			final Challenge b = dialog.polozky().get(1);
			klik(0, 4);
			Assert.assertTrue(model.jeAktivni(a));
			Assert.assertTrue("zaškrtávátko vybraného řádku to sleduje", zaskrtavatko().isSelected());
			klik(1, 4);
			Assert.assertTrue(model.jeAktivni(b));
			Assert.assertFalse("dosud aktivní se vypne", model.jeAktivni(a));
			Assert.assertFalse(zaskrtavatko().isSelected());
		});
	}

	@Test
	public void klikNaTextRadekJenVybereAAktivaceSeNemeni() throws Exception {
		naEdt(() -> {
			final Challenge b = dialog.polozky().get(1);
			klik(1, 120);
			Assert.assertEquals("řádek se vybral", 1, seznam().getSelectedIndex());
			Assert.assertFalse("aktivace se nezměnila", model.jeAktivni(b));
		});
	}

	private javax.swing.JCheckBox zaskrtavatko() {
		return najdi(dialog.getContentPane(), javax.swing.JCheckBox.class, new ArrayList<javax.swing.JCheckBox>()).stream().filter(c -> "Aktivní challenge".equals(c.getText())).findFirst().get();
	}

	@Test
	public void seznamMaSablonyAPrvniJeVybranaAJenKProhlizeni() throws Exception {
		naEdt(() -> {
			Assert.assertEquals(4, dialog.polozky().size());
			Assert.assertEquals(0, seznam().getSelectedIndex());
			Assert.assertTrue("šablona se kopíruje", tlacitko("Vytvořit kopii…").isEnabled());
			Assert.assertFalse("šablonu nejde uložit", tlacitko("Uložit změny").isEnabled());
			Assert.assertFalse("šablonu nejde smazat", tlacitko("Smazat").isEnabled());
			Assert.assertFalse("název šablony nejde měnit", pole(0).isEnabled());
		});
	}

	/** U šablony je nad formulářem rada; kopii dělá tlačítko pod seznamem; u vlastní definice rada není. */
	@Test
	public void radaUSablonyVytvoriKopii() throws Exception {
		naEdt(() -> {
			Assert.assertTrue("rada u šablony", viditelne(dialog.radaSablony));
			final JLabel text = najdi(dialog.radaSablony, JLabel.class, new ArrayList<JLabel>()).get(0);
			Assert.assertEquals("Definici šablony nelze měnit, pro úpravu vytvořte kopii.", text.getText());
			Assert.assertTrue("rada nemá vlastní tlačítko, kopie je tlačítko pod seznamem", najdi(dialog.radaSablony, AbstractButton.class, new ArrayList<AbstractButton>()).isEmpty());
			tlacitko("Vytvořit kopii…").doClick();
			Assert.assertEquals(1, model.vlastni().size());
			Assert.assertEquals("360° od domova (vlastní)", seznam().getSelectedValue().nazev());
			Assert.assertFalse("u vlastní definice rada není", dialog.radaSablony.isVisible());
			Assert.assertTrue(tlacitko("Uložit změny").isEnabled());
		});
	}

	@Test
	public void kopieSablonyJeVybranaAJdeUpravit() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			Assert.assertEquals(5, dialog.polozky().size());
			final Challenge vybrana = seznam().getSelectedValue();
			Assert.assertEquals("360° od domova (vlastní)", vybrana.nazev());
			Assert.assertTrue(tlacitko("Uložit změny").isEnabled());
			Assert.assertTrue(tlacitko("Smazat").isEnabled());
			Assert.assertTrue(tlacitko("Vytvořit kopii…").isEnabled());
			Assert.assertEquals(1, model.vlastni().size());
		});
	}

	@Test
	public void ulozeniZmeniNazevASouradniceVlastniDefinice() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			tlacitko("Souřadnice").doClick();
			pole(0).setText("Moje 360");
			pole(1).setText("N50°06.000 E14°30.000");
			tlacitko("Uložit změny").doClick();
			Assert.assertEquals(chyba(), " ".trim(), chyba());
			Assert.assertEquals(1, model.vlastni().size());
			final Challenge c = model.vlastni().get(0);
			Assert.assertEquals("Moje 360", c.nazev());
			Assert.assertEquals(Challenge.DruhStredu.SOURADNICE, c.stred().druh());
			Assert.assertEquals(50.1, c.stred().lat(), 1e-9);
			Assert.assertEquals(14.5, c.stred().lon(), 1e-9);
			Assert.assertEquals("po uložení je vybrána přejmenovaná", "Moje 360", seznam().getSelectedValue().nazev());
			Assert.assertEquals(5, dialog.polozky().size());
		});
	}

	@Test
	public void chybnyVstupSeHlasiAModelSeNezmeni() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			final Challenge pred = model.vlastni().get(0);
			tlacitko("Souřadnice").doClick();
			pole(1).setText("N95°00.000 E14°00.000");
			tlacitko("Uložit změny").doClick();
			Assert.assertTrue(chyba(), chyba().contains("Souřadnice neznám"));
			pole(1).setText("nesmysl");
			tlacitko("Uložit změny").doClick();
			Assert.assertTrue(chyba(), chyba().contains("Souřadnice neznám. Zadejte např. N50°03.253 E14°23.399."));
			Assert.assertEquals(pred, model.vlastni().get(0));
			pole(0).setText("");
			tlacitko("Domov").doClick();
			tlacitko("Uložit změny").doClick();
			Assert.assertTrue(chyba(), chyba().contains("název"));
			Assert.assertEquals(pred, model.vlastni().get(0));
		});
	}

	@Test
	public void zapnutiSeProjeviVModelu() throws Exception {
		naEdt(() -> {
			final AbstractButton zapnuto = tlacitko("Aktivní challenge");
			Assert.assertFalse(model.jeAktivni(seznam().getSelectedValue()));
			zapnuto.doClick();
			Assert.assertTrue(model.jeAktivni(Sablony.od360Domova()));
			zapnuto.doClick();
			Assert.assertFalse(model.jeAktivni(Sablony.od360Domova()));
		});
	}

	@Test
	public void zmenaVModeluSePromitneDoSeznamu() throws Exception {
		naEdt(() -> {
			model.ulozVlastni(new Challenge("Zvenku", Challenge.Typ.SEKTORY_360, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.STATOR));
			Assert.assertEquals(5, dialog.polozky().size());
			Assert.assertEquals("Zvenku", dialog.polozky().get(4).nazev());
		});
	}

	@Test
	public void ulozeniNadSablonouNicNezmeniAniNehlasi() throws Exception {
		naEdt(() -> {
			final AbstractButton uloz = tlacitko("Uložit změny");
			Assert.assertFalse(uloz.isEnabled());
			for (final java.awt.event.ActionListener l : uloz.getActionListeners()) {
				l.actionPerformed(new java.awt.event.ActionEvent(uloz, 0, ""));
			}
			Assert.assertTrue("šablona se nepřepíše ani nezkopíruje", model.vlastni().isEmpty());
			Assert.assertEquals("bez chybové hlášky", "", chyba());
			Assert.assertEquals(4, dialog.polozky().size());
		});
	}

	@Test
	public void dialogMaViditelneTlacitkoZavritPodObsahem() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			dialog.setSize(760, 420);
			dialog.validate();
			final AbstractButton zavrit = tlacitko("Zavřít");
			Assert.assertTrue(zavrit.isShowing() || zavrit.isVisible());
			Assert.assertTrue("tlačítko má velikost", zavrit.getWidth() > 0 && zavrit.getHeight() > 0);
			final java.awt.Point p = javax.swing.SwingUtilities.convertPoint(zavrit, 0, 0, dialog.getContentPane());
			final java.awt.Point uloz = javax.swing.SwingUtilities.convertPoint(tlacitko("Uložit změny"), 0, 0, dialog.getContentPane());
			Assert.assertTrue("Zavřít je pod formulářem: " + p + " × " + uloz, p.y > uloz.y);
			Assert.assertTrue("a uvnitř dialogu", p.y + zavrit.getHeight() <= dialog.getContentPane().getHeight());
			zavrit.doClick();
			Assert.assertFalse("zavře dialog", dialog.isDisplayable());
		});
	}

	/** Je komponenta viditelná, když by byl dialog zobrazen: viditelná je sama i všichni její rodiče. */
	private static boolean viditelne(final Component c) {
		for (Component k = c; k != null; k = k.getParent()) {
			if (!k.isVisible() && !(k instanceof java.awt.Window)) {
				return false;
			}
		}
		return true;
	}

	private Challenge dt(final String nazev) {
		return new Challenge(nazev, Challenge.Typ.DT_MATICE, Challenge.Stred.souradnice(50, 14), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC);
	}

	@Test
	public void dtJdeVDialoguUpravitZapnoutASmazat() throws Exception {
		naEdt(() -> {
			model.ulozVlastni(dt("Moje DT"));
			model.ulozVlastni(new Challenge("Kal", Challenge.Typ.KALENDAR, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
			Assert.assertEquals("šablony, D/T a kalendář (nedostupná)", 6, dialog.polozky().size());
			seznam().setSelectedValue(dialog.polozky().get(4), true);
			Assert.assertEquals("Moje DT", seznam().getSelectedValue().nazev());
			Assert.assertTrue("D/T jde uložit", tlacitko("Uložit změny").isEnabled());
			Assert.assertFalse("pole 360° jsou u D/T skrytá", viditelne(tlacitko("Domov")));
			Assert.assertFalse(viditelne(tlacitko("Souřadnice")));
			Assert.assertFalse(viditelne(tlacitko("Keš (kód)")));
			Assert.assertTrue("název je vidět", viditelne(pole(0)));
			pole(0).setText("Přejmenované DT");
			tlacitko("Uložit změny").doClick();
			Assert.assertEquals(chyba(), "", chyba());
			final Challenge ulozena = model.vlastni().get(0);
			Assert.assertEquals("Přejmenované DT", ulozena.nazev());
			Assert.assertEquals("typ zůstal", Challenge.Typ.DT_MATICE, ulozena.typ());
			Assert.assertEquals("střed zůstal", Challenge.Stred.souradnice(50, 14), ulozena.stred());
			final AbstractButton zapnuto = tlacitko("Aktivní challenge");
			zapnuto.doClick();
			Assert.assertTrue(model.jeAktivni(ulozena));
			tlacitko("Vytvořit kopii…");
			seznam().setSelectedIndex(0);
			Assert.assertTrue("u šablony 360° jsou pole vidět", viditelne(tlacitko("Domov")));
			Assert.assertTrue(viditelne(tlacitko("Souřadnice")));
		});
	}

	private String textPolozky(final Challenge c) {
		final javax.swing.ListCellRenderer<? super Challenge> r = seznam().getCellRenderer();
		return PolozkaChallengeRenderer.plnyText((JLabel) r.getListCellRendererComponent(seznam(), c, 0, false, false));
	}

	@Test
	public void ulozenyKalendarJdeVDialoguJenVypnoutASmazat() throws Exception {
		naEdt(() -> {
			final Challenge kal = model.ulozVlastni(new Challenge("Kal", Challenge.Typ.KALENDAR, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
			model.aktivuj(kal);
			Assert.assertTrue(dialog.polozky().contains(kal));
			Assert.assertTrue(textPolozky(kal), textPolozky(kal).contains("(nedostupná)"));
			seznam().setSelectedValue(kal, true);
			Assert.assertFalse("úpravy ne", tlacitko("Uložit změny").isEnabled());
			Assert.assertFalse(pole(0).isEnabled());
			Assert.assertTrue("smazat ano", tlacitko("Smazat").isEnabled());
			final AbstractButton zapnuto = tlacitko("Aktivní challenge");
			Assert.assertTrue("vypnout ano", zapnuto.isEnabled());
			Assert.assertTrue(zapnuto.isSelected());
			zapnuto.doClick();
			Assert.assertFalse(model.jeAktivni(kal));
			Assert.assertFalse("zapnout znovu už ne", zapnuto.isEnabled());
			Assert.assertTrue("data zůstala", model.vlastni().contains(kal));
		});
	}

	@Test
	public void kopieSablonyMaticeDtJeDtAJdeUpravit() throws Exception {
		naEdt(() -> {
			Challenge sablona = null;
			for (final Challenge c : dialog.polozky()) {
				if (c.typ() == Challenge.Typ.DT_MATICE) {
					sablona = c;
				}
			}
			Assert.assertNotNull("šablona D/T je v seznamu", sablona);
			seznam().setSelectedValue(sablona, true);
			Assert.assertFalse("šablonu nejde upravit", tlacitko("Uložit změny").isEnabled());
			tlacitko("Vytvořit kopii…").doClick();
			final Challenge kopie = seznam().getSelectedValue();
			Assert.assertEquals(Challenge.Typ.DT_MATICE, kopie.typ());
			Assert.assertEquals("Matice D/T (vlastní)", kopie.nazev());
			Assert.assertTrue(tlacitko("Uložit změny").isEnabled());
			tlacitko("Aktivní challenge").doClick();
			Assert.assertTrue(model.jeAktivni(kopie));
		});
	}

	@Test
	public void kolikratSplnitJenUDtAPlatiHnedUSablonyIVlastniBezUlozit() throws Exception {
		naEdt(() -> {
			Challenge sablona = null;
			for (final Challenge c : dialog.polozky()) {
				if (c.typ() == Challenge.Typ.DT_MATICE) {
					sablona = c;
				}
			}
			Assert.assertNotNull("šablona D/T je v seznamu", sablona);
			seznam().setSelectedValue(Sablony.od360Domova(), true);
			Assert.assertFalse("u 360° číselník není", dialog.opakovani.isVisible());
			seznam().setSelectedValue(sablona, true);
			Assert.assertTrue(dialog.opakovani.isVisible());
			Assert.assertTrue("číselník je v prvním řádku vedle Aktivní challenge", dialog.opakovani.getParent() == najdi(dialog.getContentPane(), javax.swing.JCheckBox.class, new ArrayList<javax.swing.JCheckBox>()).get(0).getParent());
			Assert.assertTrue("násobnost šablony je nastavení uživatele", dialog.opakovani.isEnabled());
			Assert.assertEquals("výchozí hodnota šablony v tooltipu", "Šablona počítá 1×", dialog.opakovani.getToolTipText());
			Assert.assertFalse("u šablony Uložit změny není vidět", tlacitko("Uložit změny").isVisible());
			dialog.opakovani.setValue(2);
			dialog.uloziCekajiciNasobnost();
			Assert.assertEquals("změna šablony platí hned", 2, seznam().getSelectedValue().opakovani());
			Assert.assertEquals("šablona se nepřejmenuje ani nestane vlastní", sablona.nazev(), seznam().getSelectedValue().nazev());
			Assert.assertEquals(2, dialog.opakovani.getValue());
			dialog.opakovani.setValue(1);
			dialog.uloziCekajiciNasobnost();
			Assert.assertEquals(1, seznam().getSelectedValue().opakovani());
			Assert.assertFalse("ostatní pole šablony zůstávají jen ke čtení", pole(0).isEnabled());
			tlacitko("Vytvořit kopii…").doClick();
			final Challenge kopie = seznam().getSelectedValue();
			Assert.assertTrue(dialog.opakovani.isEnabled());
			Assert.assertNull("u vlastní challenge bez tooltipu", dialog.opakovani.getToolTipText());
			Assert.assertTrue("u vlastní je Uložit změny", tlacitko("Uložit změny").isVisible());
			pole(0).setText("Upravený název");
			dialog.opakovani.setValue(3);
			dialog.uloziCekajiciNasobnost();
			Assert.assertEquals("násobnost vlastní platí bez Uložit", 3, model.vlastni().get(0).opakovani());
			Assert.assertEquals("rozpracovaný název zůstal", "Upravený název", pole(0).getText());
			tlacitko("Uložit změny").doClick();
			Assert.assertEquals("Uložit násobnost nevrátí", 3, model.vlastni().get(0).opakovani());
			Assert.assertEquals(3, seznam().getSelectedValue().opakovani());
			Assert.assertEquals(3, dialog.opakovani.getValue());
			Assert.assertNotNull(kopie);
		});
	}

	private int opakovaniVModelu(final String nazev) {
		for (final Challenge c : model.vsechny()) {
			if (c.nazev().equals(nazev)) {
				return c.opakovani();
			}
		}
		throw new AssertionError("není " + nazev);
	}

	/** Násobnost čekající na odklad se při přepnutí řádku uloží do původní challenge, ne do té, na kterou se přepnulo. */
	@Test
	public void kolikratSplnitPriPrepnutiRadkuPatriPuvodniChallenge() throws Exception {
		naEdt(() -> {
			final Challenge a = model.ulozVlastni(new Challenge("DT A", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
			final Challenge b = model.ulozVlastni(new Challenge("DT B", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
			seznam().setSelectedValue(a, true);
			dialog.opakovani.setValue(5);
			seznam().setSelectedValue(b, true);
			Assert.assertEquals("původní challenge má novou násobnost", 5, opakovaniVModelu("DT A"));
			Assert.assertEquals("druhá se nezměnila", 1, opakovaniVModelu("DT B"));
			Assert.assertEquals("číselník ukazuje hodnotu nově vybrané", 1, dialog.opakovani.getValue());
		});
	}

	/** Změna násobnosti a hned Uložit změny: násobnost se uloží. */
	@Test
	public void kolikratSplnitHnedPoZmeneUlozitZmenyUlozi() throws Exception {
		naEdt(() -> {
			final Challenge a = model.ulozVlastni(new Challenge("DT A", Challenge.Typ.DT_MATICE, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.PGC));
			seznam().setSelectedValue(a, true);
			dialog.opakovani.setValue(6);
			tlacitko("Uložit změny").doClick();
			Assert.assertEquals("násobnost je uložená", 6, opakovaniVModelu("DT A"));
			Assert.assertEquals(6, dialog.opakovani.getValue());
		});
	}

	/** Násobnost se po změně číselníku uloží sama po krátkém odkladu (časovač). */
	@Test
	public void kolikratSplnitSeUlozySamoPoOdkladu() throws Exception {
		naEdt(() -> {
			Challenge sablona = null;
			for (final Challenge c : dialog.polozky()) {
				if (c.typ() == Challenge.Typ.DT_MATICE) {
					sablona = c;
				}
			}
			seznam().setSelectedValue(sablona, true);
			dialog.opakovani.setValue(4);
			Assert.assertEquals("hned po změně se ještě neuložilo", 1, opakovaniVModelu(sablona.nazev()));
		});
		final long konec = System.currentTimeMillis() + 5000;
		while (System.currentTimeMillis() < konec) {
			final int[] hodnota = new int[1];
			SwingUtilities.invokeAndWait(() -> hodnota[0] = seznam().getSelectedValue().opakovani());
			if (hodnota[0] == 4) {
				return;
			}
			Thread.sleep(50);
		}
		Assert.fail("násobnost se po odkladu neuložila");
	}

	/** U 360° se „Kolikrát splnit“ neuloží, ani když by číselník měl jinou hodnotu (je skrytý). */
	@Test
	public void kolikratSplnitSeU360Neuklada() throws Exception {
		naEdt(() -> {
			final Challenge c = model.ulozVlastni(new Challenge("Moje 360", Challenge.Typ.SEKTORY_360, Challenge.Stred.domov(), Challenge.OblastDef.zadna(), Challenge.ZdrojPravidel.STATOR));
			seznam().setSelectedValue(c, true);
			dialog.opakovani.setValue(3);
			tlacitko("Uložit změny").doClick();
			Assert.assertTrue("bez chyby: " + chyba(), chyba().trim().isEmpty());
			Assert.assertEquals(1, seznam().getSelectedValue().opakovani());
		});
	}

	@Test
	public void poOtevreniJeVybranaChallengeZobrazenaVPruhu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final List<Challenge> vsechny = model.vsechny();
			final Challenge druha = vsechny.get(1);
			model.aktivuj(vsechny.get(0));
			model.aktivuj(druha);
			model.setZobrazena(druha);
			final JChallengeDialog novy = new JChallengeDialog();
			try {
				novy.inject(model);
				Assert.assertEquals(druha.nazev(), vybrana(novy).nazev());
			} finally {
				novy.dispose();
			}
		});
	}

	/** Pole souřadnic se nesmí oříznout ani při výchozí velikosti dialogu a písmu 150 %. */
	@Test
	public void poleSouradnicSeVejdeCeleVeVychoziVelikostiA150Procent() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			tlacitko("Souřadnice").doClick();
			final JTextField p = pole(1);
			for (final double meritko : new double[] { 1.0, 1.5 }) {
				zvetsiPismo(dialog.getContentPane(), meritko);
				p.setText(FormularDefinice.UKAZKA_SOURADNIC);
				dialog.pack();
				dialog.validate();
				final int potreba = p.getFontMetrics(p.getFont()).stringWidth(FormularDefinice.UKAZKA_SOURADNIC) + p.getInsets().left + p.getInsets().right;
				Assert.assertTrue(meritko + ": šířka pole " + p.getWidth() + " < " + potreba, p.getWidth() >= potreba);
				Assert.assertTrue(meritko + ": pole je celé v dialogu", SwingUtilities.convertRectangle(p.getParent(), p.getBounds(), dialog.getContentPane()).getMaxX() <= dialog.getContentPane().getWidth());
			}
		});
	}

	private static void zvetsiPismo(final Component c, final double meritko) {
		if (c.getFont() != null) {
			c.setFont(c.getFont().deriveFont((float) (12 * meritko)));
		}
		if (c instanceof Container) {
			for (final Component k : ((Container) c).getComponents()) {
				zvetsiPismo(k, meritko);
			}
		}
	}

	@Test
	public void kopieVlastniChallengeJeVybranaNeaktivniAJdeUpravit() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			final Challenge vlastni = seznam().getSelectedValue();
			Assert.assertTrue("kopii jde vytvořit i z vlastní", tlacitko("Vytvořit kopii…").isEnabled());
			tlacitko("Vytvořit kopii…").doClick();
			final Challenge kopie = seznam().getSelectedValue();
			Assert.assertEquals(vlastni.nazev() + " (kopie)", kopie.nazev());
			Assert.assertEquals(vlastni.typ(), kopie.typ());
			Assert.assertFalse(model.jeAktivni(kopie));
			Assert.assertTrue(tlacitko("Uložit změny").isEnabled());
			Assert.assertEquals(2, model.vlastni().size());
		});
	}

	/** Seznam nemá vodorovný posuvník a přípona „(šablona)“ je u každé šablony vidět. */
	@Test
	public void seznamBezPosuvnikuSPriponouSablony() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			dialog.pack();
			dialog.setSize(dialog.getWidth(), dialog.getHeight());
			dialog.validate();
			final javax.swing.JScrollPane sp = najdi(dialog.getContentPane(), javax.swing.JScrollPane.class, new ArrayList<javax.swing.JScrollPane>()).get(0);
			final JList<?> seznam = (JList<?>) sp.getViewport().getView();
			Assert.assertFalse("vodorovný posuvník", sp.getHorizontalScrollBar().isVisible());
			final int sirka = sp.getViewport().getWidth();
			for (int i = 0; i < seznam.getModel().getSize(); i++) {
				final JLabel l = (JLabel) vykresli(seznam, i);
				l.setSize(sirka, l.getPreferredSize().height);
				Assert.assertEquals("položka " + i, 1, l.getPreferredSize().width);
				Assert.assertTrue(PolozkaChallengeRenderer.plnyText(l), PolozkaChallengeRenderer.plnyText(l).contains("(šablona)"));
			}
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static Component vykresli(final JList<?> seznam, final int i) {
		return ((javax.swing.ListCellRenderer) seznam.getCellRenderer()).getListCellRendererComponent(seznam, seznam.getModel().getElementAt(i), i, false, false);
	}

	/** Volba pravidel jmenuje služby i tvar Země a tooltip vysvětluje rozdíl. */
	@Test
	public void volbaPravidelMaNazvySluzebAVysvetleni() throws Exception {
		naEdt(() -> {
			javax.swing.JComponent pravidla = null;
			for (final Component k : vsechnyKomponenty(dialog.getContentPane(), new ArrayList<Component>())) {
				if (k instanceof javax.swing.JComboBox && ((javax.swing.JComboBox<?>) k).getItemCount() == 2 && "Project-GC".equals(((javax.swing.JComboBox<?>) k).getItemAt(0))) {
					pravidla = (javax.swing.JComponent) k;
				}
			}
			Assert.assertNotNull("volba pravidel s položkami Koule a Elipsoid", pravidla);
			Assert.assertEquals("Stator", ((javax.swing.JComboBox<?>) pravidla).getItemAt(1));
			Assert.assertTrue(pravidla.getToolTipText(), pravidla.getToolTipText().contains("Stator: přesnější výpočet, včetně Lab keší, bez Locationless.") && !pravidla.getToolTipText().contains("elipsoid"));
		});
	}

	/** Seznam je tak široký, že se v něm vejde název každé šablony i s příponami. */
	@Test
	public void seznamUnesePlnyNazevSablonySPriponami() throws Exception {
		naEdt(() -> {
			final JList<Challenge> s = seznam();
			final java.awt.FontMetrics fm = s.getFontMetrics(s.getFont());
			final int sirka = ((javax.swing.JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, s)).getPreferredSize().width;
			Assert.assertEquals("šířka seznamu odpovídá písmu", JChallengeDialog.sirkaSeznamu(fm), sirka);
			for (final Challenge c : Sablony.vse()) {
				final int potreba = fm.stringWidth(PolozkaChallengeRenderer.ZAPNUTA + "  " + c.nazev() + "  (šablona)");
				Assert.assertTrue(c.nazev() + ": " + potreba + " > " + sirka, potreba <= sirka - 40 || sirka == 700);
			}
		});
	}

	/** Formulář ukazuje typ vybrané challenge. */
	@Test
	public void formularUkazujeTyp() throws Exception {
		naEdt(() -> {
			Assert.assertEquals("360°", dialog.typ.getText());
		});
	}

	private static List<Component> vsechnyKomponenty(final Container c, final List<Component> vysledek) {
		for (final Component k : c.getComponents()) {
			vysledek.add(k);
			if (k instanceof Container) {
				vsechnyKomponenty((Container) k, vysledek);
			}
		}
		return vysledek;
	}

	private static void fokus(final JTextField pole) {
		// Správce fokusu zahazuje události pole, které fokus opravdu nemá; posluchači se volají přímo.
		final java.awt.event.FocusEvent e = new java.awt.event.FocusEvent(pole, java.awt.event.FocusEvent.FOCUS_GAINED);
		for (final java.awt.event.FocusListener l : pole.getFocusListeners()) {
			l.focusGained(e);
		}
	}

	/** Přechod fokusu do pole Souřadnice nebo Keš (kód) přepne příslušné radio; uložení pak použije vybranou volbu. */
	@Test
	public void fokusVPoliStreduPrepneRadio() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			tlacitko("Domov").doClick();
			Assert.assertTrue("výchozí volba je Domov", tlacitko("Domov").isSelected());
			Assert.assertTrue("pole jde použít i bez předchozí volby radia", pole(1).isEnabled());
			fokus(pole(1));
			Assert.assertTrue(tlacitko("Souřadnice").isSelected());
			Assert.assertFalse(tlacitko("Domov").isSelected());
			fokus(pole(2));
			Assert.assertTrue(tlacitko("Keš (kód)").isSelected());
			Assert.assertFalse(tlacitko("Souřadnice").isSelected());
			tlacitko("Domov").doClick();
			Assert.assertTrue("ruční volba radia funguje dál", tlacitko("Domov").isSelected());
			fokus(pole(1));
			pole(1).setText("N50°06.000 E14°30.000");
			tlacitko("Uložit změny").doClick();
			Assert.assertEquals(chyba(), 1, model.vlastni().size());
			Assert.assertEquals(Challenge.DruhStredu.SOURADNICE, model.vlastni().get(0).stred().druh());
		});
	}

	/** U šablony jde jen o čtení: fokus v poli nic nepřepne. */
	@Test
	public void fokusVPoliSablonyRadioNemeni() throws Exception {
		naEdt(() -> {
			final boolean domov = tlacitko("Domov").isSelected();
			Assert.assertFalse(pole(1).isEnabled());
			fokus(pole(1));
			Assert.assertEquals(domov, tlacitko("Domov").isSelected());
			Assert.assertFalse(tlacitko("Souřadnice").isSelected());
		});
	}

	/** Formát souřadnic je šedá nápověda přímo v poli a po zadání textu zmizí. */
	@Test
	public void napovedaSouradnicZmiziPoZadaniTextu() throws Exception {
		naEdt(() -> {
			tlacitko("Vytvořit kopii…").doClick();
			final cz.geokuk.util.gui.JPoleSNapovedou p = (cz.geokuk.util.gui.JPoleSNapovedou) pole(1);
			Assert.assertEquals(FormularDefinice.UKAZKA_SOURADNIC, p.getNapoveda());
			Assert.assertTrue(p.ukazujeNapovedu());
			p.setText("N50°06.000 E14°30.000");
			Assert.assertFalse(p.ukazujeNapovedu());
			p.setText("");
			Assert.assertTrue(p.ukazujeNapovedu());
		});
	}
}
