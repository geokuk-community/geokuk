package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Dialog průvodce: kroky, výchozí volby, tlačítka na konci; s displejem, model je zkoušený bez něj. */
public class JPruvodceMapTest {

	private static final class Kontext implements PruvodceKontext {
		long volne = 84_000_000_000L;
		final List<PruvodceVolba> prepnuti = new ArrayList<>();
		final List<String> stazene = new ArrayList<>();
		boolean zruseno;
		boolean mapaStazena;

		@Override
		public KatalogMap getKatalog() {
			return KatalogMap.vestaveny();
		}

		@Override
		public List<MatriceMap.Radek> matice() {
			return MatriceMap.sestav(KatalogMap.vestaveny(), m -> StavMapyProGui.stav(m, mapaStazena ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null),
					VrstvyProGui.katalogCesko(), StavVrstvy::novy);
		}

		@Override
		public long volneMisto() {
			return volne;
		}

		@Override
		public boolean stahni(final Polozka polozka) {
			stazene.add(polozka.getId());
			return true;
		}

		@Override
		public void zrusStahovani() {
			zruseno = true;
		}

		@Override
		public void prepni(final PruvodceVolba volba) {
			prepnuti.add(volba);
		}
	}

	@After
	public void uklid() {
		SpousteniPruvodce.zrus();
	}

	private static List<AbstractButton> tlacitka(final Container c) {
		final List<AbstractButton> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (k instanceof AbstractButton) {
				v.add((AbstractButton) k);
			}
			if (k instanceof Container) {
				v.addAll(tlacitka((Container) k));
			}
		}
		return v;
	}

	private static AbstractButton tlacitko(final Container c, final String text) {
		return tlacitka(c).stream().filter(b -> b.getText() != null && b.getText().contains(text)).findFirst().orElseThrow(() -> new AssertionError("chybí tlačítko " + text));
	}

	private static List<JLabel> popisky(final Container c) {
		final List<JLabel> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (k instanceof JLabel) {
				v.add((JLabel) k);
			}
			if (k instanceof Container) {
				v.addAll(popisky((Container) k));
			}
		}
		return v;
	}

	private static void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	@Test
	public void projdeKrokyAPrepneBezStahovani() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final Kontext k = new Kontext();
			k.mapaStazena = true;
			final JPruvodceMap d = new JPruvodceMap(null, k);
			Assert.assertTrue("Česko je předvybrané a jde dál", tlacitko(d.getContentPane(), JPruvodceMap.DAL).isEnabled());
			tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			Assert.assertTrue("Turistická offline je předvybraná", ((JToggleButton) tlacitka(d.getContentPane()).stream().filter(b -> b instanceof JToggleButton && b.getText().contains("Turistická")).findFirst().get()).isSelected());
			Assert.assertTrue("ČÚZK topo je u Česka", tlacitka(d.getContentPane()).stream().anyMatch(b -> b instanceof JToggleButton && b.getText().contains("ČÚZK topo")));
			Assert.assertFalse("ZBGIS Slovenska u Česka není", tlacitka(d.getContentPane()).stream().anyMatch(b -> b instanceof JToggleButton && b.getText().contains("ZBGIS")));
			tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			final JCheckBox trasy = (JCheckBox) tlacitka(d.getContentPane()).stream().filter(b -> b instanceof JCheckBox && b.getText().startsWith("Turistická vrstva")).findFirst().get();
			Assert.assertTrue("trasy jsou předvolené i u offline Turistické", trasy.isSelected());
			Assert.assertTrue(trasy.isEnabled());
			final JCheckBox vrstevnice = (JCheckBox) tlacitka(d.getContentPane()).stream().filter(b -> b instanceof JCheckBox && b.getText().startsWith("Vrstevnice")).findFirst().get();
			Assert.assertFalse(vrstevnice.isSelected());
			Assert.assertTrue(vrstevnice.isEnabled());
			vrstevnice.doClick();
			tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			Assert.assertTrue("shrnutí uvádí položky", popisky(d.getContentPane()).stream().anyMatch(l -> l.getText().contains("Česko – vrstevnice")));
			tlacitko(d.getContentPane(), JPruvodceMap.JEN_PREPNOUT).doClick();
			Assert.assertEquals(1, k.prepnuti.size());
			Assert.assertEquals(PodkladPruvodce.TURISTICKA_OFFLINE, k.prepnuti.get(0).getPodklad());
			Assert.assertTrue(k.prepnuti.get(0).getVrstvy().contains(Cast.TRASY) && k.prepnuti.get(0).getVrstvy().contains(Cast.VRSTEVNICE));
			Assert.assertTrue("nic se nestahuje", k.stazene.isEmpty());
			Assert.assertFalse(d.isDisplayable());
		});
	}

	@Test
	public void stahnoutAPrepnoutSpustiPrvniPolozku() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final Kontext k = new Kontext();
			final JPruvodceMap d = new JPruvodceMap(null, k);
			for (int i = 0; i < 3; i++) {
				tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			}
			tlacitko(d.getContentPane(), JPruvodceMap.STAHNOUT_A_PREPNOUT).doClick();
			Assert.assertEquals("mapa a vedle ní trasy", java.util.Arrays.asList("europe/czech-republic", "tourist/europe/czech-republic"), k.stazene);
			Assert.assertTrue("na stažení mapy se čeká", k.prepnuti.isEmpty());
			Assert.assertTrue(SpousteniPruvodce.bezi());
		});
	}

	@Test
	public void nedostatekMistaVypneStahovani() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final Kontext k = new Kontext();
			k.volne = 100_000_000L;
			final JPruvodceMap d = new JPruvodceMap(null, k);
			for (int i = 0; i < 3; i++) {
				tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			}
			Assert.assertFalse(tlacitko(d.getContentPane(), JPruvodceMap.STAHNOUT_A_PREPNOUT).isEnabled());
			Assert.assertFalse("bez stažené mapy by přepnutí nechalo mapu prázdnou", tlacitko(d.getContentPane(), JPruvodceMap.JEN_PREPNOUT).isEnabled());
			Assert.assertTrue(popisky(d.getContentPane()).stream().anyMatch(l -> l.getText().contains("není dost místa")));
		});
	}

	@Test
	public void onlinePodkladNenabidneVrstevniceAStinovani() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final Kontext k = new Kontext();
			final JPruvodceMap d = new JPruvodceMap(null, k);
			tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			tlacitko(d.getContentPane(), "OpenStreetMap").doClick();
			tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			final JCheckBox vrstevnice = (JCheckBox) tlacitka(d.getContentPane()).stream().filter(b -> b instanceof JCheckBox && b.getText().startsWith("Vrstevnice")).findFirst().get();
			Assert.assertFalse("vrstevnice jen s offline mapou", vrstevnice.isEnabled());
			Assert.assertTrue(popisky(d.getContentPane()).stream().anyMatch(l -> l.getText().contains("Jen s offline mapou.")));
			tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			Assert.assertTrue("přepnutí na OSM s trasami", popisky(d.getContentPane()).stream().anyMatch(l -> l.getText().contains("OpenStreetMap") && l.getText().contains("turistická vrstva")));
		});
	}

	@Test
	public void escZavreDialogAEnterJdeDal() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final JPruvodceMap d = new JPruvodceMap(null, new Kontext());
			Assert.assertSame("Enter v prvním kroku je Dál", tlacitko(d.getContentPane(), JPruvodceMap.DAL), d.getRootPane().getDefaultButton());
			final java.awt.event.ActionListener esc = d.getRootPane().getActionForKeyStroke(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0));
			Assert.assertNotNull(esc);
			esc.actionPerformed(null);
			Assert.assertFalse("Esc dialog zavřel", d.isDisplayable());
		});
	}

	/** Offline Turistická bez stažené mapy by po „jen přepnout“ zůstala prázdná, proto je to tlačítko vypnuté. */
	@Test
	public void jenPrepnoutNaOfflineBezMapyJeVypnute() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final JPruvodceMap d = new JPruvodceMap(null, new Kontext());
			for (int i = 0; i < 3; i++) {
				tlacitko(d.getContentPane(), JPruvodceMap.DAL).doClick();
			}
			Assert.assertFalse(tlacitko(d.getContentPane(), JPruvodceMap.JEN_PREPNOUT).isEnabled());
		});
	}

	/** Výběr zemí přežije přepnutí „Zobrazit všechny země“ oběma směry. */
	@Test
	public void vyberPrezijePrepnutiVsechnyZeme() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final JPruvodceMap d = new JPruvodceMap(null, new Kontext());
			// Jednozemní režim s „Jinou zemí“ nechává po přepnutí na víc zemí zastaralý combo box, který výběr dřív vynuloval.
			tlacitko(d.getContentPane(), "Jiná země").doClick();
			tlacitko(d.getContentPane(), JPruvodceMap.VICE_ZEMI).doClick();
			tlacitko(d.getContentPane(), "Česko").doClick();
			tlacitko(d.getContentPane(), "Slovensko").doClick();
			tlacitko(d.getContentPane(), "Bavorsko").doClick();
			final String[] zeme = { "Česko", "Slovensko", "Bavorsko" };
			for (int kolo = 0; kolo < 2; kolo++) {
				tlacitko(d.getContentPane(), JPruvodceMap.VSECHNY_ZEME).doClick();
				for (final String z : zeme) {
					Assert.assertTrue(z + " zůstalo vybrané po přepnutí " + kolo, tlacitko(d.getContentPane(), z).isSelected());
				}
			}
			d.dispose();
		});
	}

	/** Přepnutí „Zobrazit všechny země“ při jedné zvolené „Jiné zemi“ výběr nezmění. */
	@Test
	public void jinaZemePrezijePrepnutiVsechnyZeme() throws Exception {
		Assume.assumeFalse(GraphicsEnvironment.isHeadless());
		naEdt(() -> {
			final JPruvodceMap d = new JPruvodceMap(null, new Kontext());
			tlacitko(d.getContentPane(), "Jiná země").doClick();
			tlacitko(d.getContentPane(), JPruvodceMap.VSECHNY_ZEME).doClick();
			tlacitko(d.getContentPane(), JPruvodceMap.VSECHNY_ZEME).doClick();
			Assert.assertTrue("Dál zůstává povolené", tlacitko(d.getContentPane(), JPruvodceMap.DAL).isEnabled());
			d.dispose();
		});
	}
}
