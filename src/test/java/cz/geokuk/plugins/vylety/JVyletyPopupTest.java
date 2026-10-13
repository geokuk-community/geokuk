package cz.geokuk.plugins.vylety;

import java.awt.Component;
import java.awt.Container;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import javax.swing.*;

import org.junit.*;

/** Popup výletů: řádky podle seznamu výletů, kontrola jména a zašednutí během přepínání. */
public class JVyletyPopupTest {

	private final List<VyletInfo> vylety = new ArrayList<>(Arrays.asList(new VyletInfo(VyletyUlozisko.VYCHOZI_ID, "Výchozí", false, 12, 3), new VyletInfo("v2", "Šumava 2026", true, 48, 0),
			new VyletInfo("v3", "Praha – multiny", false, 7, 2)));
	private final List<String> volani = new ArrayList<>();
	private CompletableFuture<Void> aktivace;
	private boolean prepina;
	private JVyletyPopup popup;

	private final VyletModel model = new VyletModel() {
		@Override
		public List<VyletInfo> getVylety() {
			return vylety;
		}

		@Override
		public String validujJmeno(final String jmeno, final String id) {
			if (jmeno.trim().isEmpty()) {
				return "Jméno výletu nesmí být prázdné.";
			}
			for (final VyletInfo v : vylety) {
				if (v.getJmeno().equalsIgnoreCase(jmeno.trim()) && !v.getId().equals(id)) {
					return "Výlet s tímto jménem už je.";
				}
			}
			return null;
		}

		@Override
		public CompletableFuture<Void> aktivovat(final String id) {
			volani.add("aktivovat " + id);
			prepina = true;
			return aktivace;
		}

		@Override
		public CompletableFuture<String> novy(final String jmeno, final boolean aktivovat) {
			volani.add("novy " + jmeno + " " + aktivovat);
			return new CompletableFuture<>();
		}

		@Override
		public CompletableFuture<Void> prejmenovat(final String id, final String jmeno) {
			volani.add("prejmenovat " + id + " " + jmeno);
			return new CompletableFuture<>();
		}

		@Override
		public boolean isPrepina() {
			return prepina;
		}
	};

	@Before
	public void setUp() {
		aktivace = new CompletableFuture<>();
		popup = new JVyletyPopup();
		popup.setVyletModel(model);
		popup.obnov();
	}

	private static List<Component> vse(final Container c) {
		final List<Component> vse = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			vse.add(k);
			if (k instanceof Container) {
				vse.addAll(vse((Container) k));
			}
		}
		return vse;
	}

	private List<JButton> tlacitka(final String text) {
		final List<JButton> t = new ArrayList<>();
		for (final Component k : vse(popup)) {
			if (k instanceof JButton && text.equals(((JButton) k).getText())) {
				t.add((JButton) k);
			}
		}
		return t;
	}

	private boolean maPopisek(final String text) {
		for (final Component k : vse(popup)) {
			if (k instanceof JLabel && text.equals(((JLabel) k).getText())) {
				return true;
			}
		}
		return false;
	}

	private JTextField pole() {
		for (final Component k : vse(popup)) {
			if (k instanceof JTextField) {
				return (JTextField) k;
			}
		}
		return null;
	}

	@Test
	public void radkyPodleSeznamu() {
		Assert.assertTrue(maPopisek("Šumava 2026"));
		Assert.assertTrue("aktivní výlet místo tlačítka", maPopisek("aktivní"));
		Assert.assertEquals("Aktivovat u neaktivních", 2, tlacitka("Aktivovat").size());
		final List<JButton> smazat = tlacitka("Smazat");
		Assert.assertEquals(3, smazat.size());
		Assert.assertFalse("Výchozí nejde smazat", smazat.get(0).isEnabled());
		Assert.assertEquals(JVyletyPopup.TEXT_VYCHOZI_NESMAZAT, smazat.get(0).getToolTipText());
		Assert.assertTrue(smazat.get(1).isEnabled());
		Assert.assertTrue("věta o souborech přímo v popupu", maPopisek(JVyletyPopup.TEXT_SOUBORY));
	}

	@Test
	public void novyVyletSKontrolouJmena() {
		tlacitka("Nový výlet…").get(0).doClick();
		final JTextField pole = pole();
		Assert.assertEquals("první volné jméno", "Výlet 4", pole.getText());
		final JButton zalozit = tlacitka("Založit").get(0);
		Assert.assertTrue(zalozit.isEnabled());
		pole.setText("šumava 2026");
		Assert.assertFalse(zalozit.isEnabled());
		Assert.assertTrue(maPopisek("Výlet s tímto jménem už je."));
		Assert.assertTrue("během psaní se popup nezavře", popup.isRozpracovany());
		pole.setText("Jeseníky");
		Assert.assertTrue(zalozit.isEnabled());
		zalozit.doClick();
		Assert.assertEquals(Collections.singletonList("novy Jeseníky true"), volani);
		Assert.assertFalse(popup.isRozpracovany());
	}

	@Test
	public void prejmenovaniZrusitVratiRadek() {
		tlacitka("Přejmenovat").get(2).doClick();
		Assert.assertEquals("Praha – multiny", pole().getText());
		Assert.assertTrue("vlastní jméno je v pořádku", tlacitka("Uložit").get(0).isEnabled());
		Assert.assertFalse("ostatní tlačítka během úpravy zašedlá", tlacitka("Aktivovat").get(0).isEnabled());
		tlacitka("Zrušit").get(0).doClick();
		Assert.assertNull(pole());
		Assert.assertTrue(volani.isEmpty());
	}

	@Test
	public void behemPrepinaniVseZasedle() {
		tlacitka("Aktivovat").get(1).doClick();
		Assert.assertEquals(Collections.singletonList("aktivovat v3"), volani);
		Assert.assertTrue(maPopisek("Přepínám…"));
		Assert.assertTrue(maPopisek(JVyletyPopup.TEXT_PREPINANI));
		for (final Component k : vse(popup)) {
			if (k instanceof JButton) {
				Assert.assertFalse(((JButton) k).getText(), k.isEnabled());
			}
		}
		prepina = false;
		aktivace.complete(null);
		Assert.assertFalse(maPopisek("Přepínám…"));
		Assert.assertTrue(maPopisek(JVyletyPopup.TEXT_SOUBORY));
		Assert.assertTrue(tlacitka("Nový výlet…").get(0).isEnabled());
	}

	/** Přepínání spuštěné odjinud (model ho hlásí) zašedne popup také. */
	@Test
	public void prepinaniPodleModelu() {
		prepina = true;
		popup.obnov();
		Assert.assertFalse(tlacitka("Nový výlet…").get(0).isEnabled());
		Assert.assertTrue(maPopisek(JVyletyPopup.TEXT_PREPINANI));
	}

	@Test
	public void textSmazani() {
		Assert.assertEquals("Smazat výlet „Praha – multiny“ (7 keší k lovení, 2 vynechaných)?", JVyletyPopup.textSmazani(vylety.get(2)));
		Assert.assertEquals("Smazat výlet „Šumava 2026“ (48 keší k lovení, 0 vynechaných)?\nAktivním výletem se stane Výchozí.", JVyletyPopup.textSmazani(vylety.get(1)));
	}

	@Test
	public void zkraceniJmenaVeStavovemRadku() {
		Assert.assertEquals("Šumava 2026", JPopisekVyletu.zkrat("Šumava 2026"));
		Assert.assertEquals("Velmi dlouhé jméno…", JPopisekVyletu.zkrat("Velmi dlouhé jméno výletu na Šumavu"));
		final JPopisekVyletu popisek = new JPopisekVyletu();
		popisek.inject(model);
		popisek.obnovJmeno();
		Assert.assertEquals("jméno vždy, i u jediného výletu", "Výlet: Šumava 2026", popisek.getText());
	}

	/** Akce, které výlet upravují, jsou během přepínání zašedlé; ostatní ne. */
	@Test
	public void akceBehemPrepinani() {
		final List<VyletAction0> upravuji = Arrays.asList(new VyletAnoAction(null), new VyletNeAction(null), new VyletNevimAction(null), new VyletSmazAnoAction(), new VyletSmazNeAction());
		final VyletZoomAction zoom = new VyletZoomAction();
		final List<VyletAction0> vse = new ArrayList<>(upravuji);
		vse.add(zoom);
		for (final VyletAction0 a : vse) {
			a.inject(model);
			a.setEnabled(true);
		}
		prepina = true;
		for (final VyletAction0 a : vse) {
			a.onEvent(new VyletyZmenenyEvent(model));
		}
		for (final VyletAction0 a : upravuji) {
			Assert.assertFalse(a.getClass().getSimpleName(), a.isEnabled());
		}
		Assert.assertTrue(zoom.isEnabled());
		prepina = false;
		for (final VyletAction0 a : upravuji) {
			a.onEvent(new VyletyZmenenyEvent(model));
			Assert.assertTrue("po přepnutí se vrátí: " + a.getClass().getSimpleName(), a.isEnabled());
		}
	}
}
