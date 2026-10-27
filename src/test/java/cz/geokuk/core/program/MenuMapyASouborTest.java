package cz.geokuk.core.program;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.mapy.PodkladAction;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;

/** Pořadí položek v menu Mapy a Soubor a mnemoniky přesunutých položek. */
public class MenuMapyASouborTest {

	private static class SestavMenu extends Menu {
		SestavMenu(final Akce akce) {
			super(null, null);
			inject(akce);
		}

		JMenu mapy() {
			sestavMenuMapy();
			return menu;
		}

		JMenu soubor() {
			sestavMenuSoubor();
			return menu;
		}
	}

	/** Texty položek menu v pořadí; oddělovač je „|“. */
	private static List<String> texty(final JMenu menu) {
		final List<String> v = new ArrayList<>();
		for (final Component c : menu.getMenuComponents()) {
			v.add(c instanceof JPopupMenu.Separator ? "|" : ((JMenuItem) c).getText());
		}
		return v;
	}

	@Test
	public void menuMapyMaPrekryvyPodNadpisemASpravceOfflineMap() {
		final List<String> t = texty(new SestavMenu(new Akce()).mapy());
		Assert.assertEquals(java.util.Arrays.asList("Přes mapu", "Turistické a cyklotrasy", "ČÚZK – Stínovaný reliéf", "|", "Správce offline map…", "Téma offline mapy", "|", "Přibliž mapu",
				"Oddal mapu", "Na mapu Česka", "Zobrazit všechny keše", "|", "Online", "Ukládat mapy", "Postahovat dlaždice..."), t);
	}

	@Test
	public void nadpisPresMapuJeNeaktivni() {
		final JMenuItem nadpis = (JMenuItem) new SestavMenu(new Akce()).mapy().getMenuComponent(0);
		Assert.assertEquals("Přes mapu", nadpis.getText());
		Assert.assertFalse(nadpis.isEnabled());
	}

	@Test
	public void menuSouborMaPruvodceAOptimalizaciCache() {
		final List<String> t = texty(new SestavMenu(new Akce()).soubor());
		Assert.assertTrue(t.toString(), t.contains("Průvodce nastavením map…"));
		Assert.assertTrue(t.toString(), t.contains("Optimalizace cache dlaždic…"));
		Assert.assertFalse(t.toString(), t.contains("Optimalizace databáze…"));
		Assert.assertFalse(texty(new SestavMenu(new Akce()).mapy()).contains("Průvodce nastavením map…"));
		Assert.assertFalse(texty(new SestavMenu(new Akce()).mapy()).contains("Optimalizace cache dlaždic…"));
	}

	private static int pocetSMnemonikem(final JMenu menu, final int mnemonik) {
		int n = 0;
		for (final Component c : menu.getMenuComponents()) {
			if (c instanceof JMenuItem && ((JMenuItem) c).getMnemonic() == mnemonik) {
				n++;
			}
		}
		return n;
	}

	/** Mnemonik je písmeno z textu položky a v menu ho nemá žádná jiná položka (podklady Mapy používají Z, T, L, M, G, H, O, F, J). */
	@Test
	public void mnemonikyPresunutychAPrejmenovanychPolozekNekoliduji() {
		final JMenu mapy = new SestavMenu(new Akce()).mapy();
		Assert.assertEquals(1, pocetSMnemonikem(mapy, java.awt.event.KeyEvent.VK_S));
		final JMenuItem spravce = (JMenuItem) mapy.getMenuComponent(4);
		Assert.assertEquals("Správce offline map…", spravce.getText());
		Assert.assertEquals(java.awt.event.KeyEvent.VK_S, spravce.getMnemonic());
		for (final int zabrano : new int[] { 'Z', 'T', 'L', 'M', 'G', 'H', 'O', 'F', 'J', 'U' }) {
			Assert.assertNotEquals("písmeno " + (char) zabrano + " už mají jiné položky menu Mapy", zabrano, spravce.getMnemonic());
		}
		final JMenu soubor = new SestavMenu(new Akce()).soubor();
		Assert.assertEquals(1, pocetSMnemonikem(soubor, java.awt.event.KeyEvent.VK_C));
		Assert.assertEquals(1, pocetSMnemonikem(soubor, java.awt.event.KeyEvent.VK_P));
	}

	/** S naplněnými rastrovými podklady je před blokem Přes mapu oddělovač a mnemonik Správce offline map je v menu jediný. */
	@Test
	public void sPodkladyJeOddelovacPredPresMapuAMnemonikSJedinecny() {
		final Akce akce = new Akce();
		for (final EKaType ka : EKaType.values()) {
			akce.mapoakce.add(new PodkladAction(ka));
		}
		final JMenu mapy = new SestavMenu(akce).mapy();
		final List<String> t = texty(mapy);
		final int nadpis = t.indexOf("Přes mapu");
		Assert.assertTrue(t.toString(), nadpis > 1);
		Assert.assertEquals(t.toString(), "|", t.get(nadpis - 1));
		Assert.assertEquals(1, pocetSMnemonikem(mapy, java.awt.event.KeyEvent.VK_S));
	}
}
