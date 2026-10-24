package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.KatalogVrstev;
import cz.geokuk.plugins.mapy.sprava.KatalogovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva;
import cz.geokuk.plugins.mapy.sprava.NabidkaRadku;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Regiony stažených zemí na skutečném katalogu map: pořadí nadpisů, hledání a rada v mapě. */
public class JMapyPopupRegionyTest {

	private static final KatalogMap KATALOG = KatalogMap.vestaveny();

	/** Země s regiony: celek, nestažený region, jiný region a bod ve vlastním regionu. */
	private static final String[][] ZEME = { { "europe/germany", "Hamburk", "Bavorsko" }, { "europe/spain", "Baleáry", "Madrid" }, { "europe/france", "Alsasko", "Bretaň" },
			{ "europe/italy", "Jih", "Střední Itálie" }, { "europe/netherlands", "Drenthe", "Utrecht" }, { "europe/great-britain", "Wales", "Skotsko" } };

	private static List<RadekMapy> radky(final Predicate<KatalogovaMapa> mame, final KatalogVrstev vrstvy, final Predicate<KatalogovaVrstva> vrstva) {
		return RadkyMap.sestav(KATALOG, m -> StavMapyProGui.stav(m, mame.test(m) ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, vrstvy, v -> VrstvyProGui.stav(v, vrstva.test(v) ? StavVrstvy.Mistni.MAME : StavVrstvy.Mistni.NEMAME, StavMapy.Faze.NIC, 0, null));
	}

	private static JMapyPopup popup(final List<RadekMapy> radky, final boolean slozkaOtevrena) {
		final JMapyPopup p = new JMapyPopup();
		p.nactiStavSlozky = () -> Optional.of(slozkaOtevrena);
		p.obnov(radky);
		return p;
	}

	/** Texty viditelných štítků v pořadí, v jakém jsou v popupu. */
	private static List<String> viditelne(final Container c, final boolean predek, final List<String> v) {
		for (final Component k : c.getComponents()) {
			final boolean ano = predek && k.isVisible();
			if (k instanceof JLabel && ano && ((JLabel) k).getText() != null) {
				v.add(((JLabel) k).getText());
			}
			if (k instanceof Container) {
				viditelne((Container) k, ano, v);
			}
		}
		return v;
	}

	private static List<String> viditelne(final JMapyPopup p) {
		return viditelne(p, true, new ArrayList<>());
	}

	private static List<Component> radekRegionu(final JMapyPopup p, final String nazev) {
		final JLabel l = vsechnyStitky(p, new ArrayList<>()).stream().filter(k -> nazev.equals(k.getText()) && jeVidet(k)).findFirst().get();
		final Container tabulka = l.getParent();
		final java.awt.GridBagLayout rozlozeni = (java.awt.GridBagLayout) tabulka.getLayout();
		final int y = rozlozeni.getConstraints(l).gridy;
		final List<Component> v = new ArrayList<>();
		for (final Component k : tabulka.getComponents()) {
			if (rozlozeni.getConstraints(k).gridy == y) {
				pridej(v, k);
			}
		}
		return v;
	}

	private static void pridej(final List<Component> v, final Component k) {
		v.add(k);
		if (k instanceof Container) {
			for (final Component d : ((Container) k).getComponents()) {
				pridej(v, d);
			}
		}
	}

	private static List<JLabel> vsechnyStitky(final Container c, final List<JLabel> v) {
		for (final Component k : c.getComponents()) {
			if (k instanceof JLabel) {
				v.add((JLabel) k);
			}
			if (k instanceof Container) {
				vsechnyStitky((Container) k, v);
			}
		}
		return v;
	}

	private static boolean jeVidet(final Component k) {
		for (Component c = k; c != null; c = c.getParent()) {
			if (!c.isVisible()) {
				return false;
			}
		}
		return true;
	}

	private static long kolikrat(final List<String> texty, final String text) {
		return texty.stream().filter(text::equals).count();
	}

	private static Predicate<KatalogovaMapa> celekStazeny(final String id) {
		return m -> m.getId().equals(id);
	}

	/** Region stažené země se při hledání ukáže u své země, ne pod nadpisem Mimo Evropu. */
	@Test
	public void regionStazeneZemeNeniPodNadpisemMimoEvropu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (final String[] z : ZEME) {
				final JMapyPopup p = popup(radky(celekStazeny(z[0]), KatalogVrstev.vestaveny(), v -> false), true);
				p.getHledat().setText(z[1]);
				final List<String> texty = viditelne(p);
				Assert.assertEquals(z[1] + " jednou", 1, kolikrat(texty, z[1]));
				Assert.assertFalse(z[0] + " " + texty, texty.contains(JMapyPopup.MIMO_EVROPU));
			}
		});
	}

	/** Bez hledání je Mimo Evropu až za evropskými zeměmi. */
	@Test
	public void nadpisMimoEvropuJeZaEvropskymiZememi() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = popup(radky(celekStazeny("europe/germany"), KatalogVrstev.vestaveny(), v -> false), true);
			final List<String> texty = viditelne(p);
			Assert.assertTrue(texty.toString(), texty.indexOf(JMapyPopup.MIMO_EVROPU) > texty.indexOf("Rakousko"));
			Assert.assertTrue(texty.toString(), texty.indexOf(JMapyPopup.MIMO_EVROPU) > texty.indexOf("Španělsko"));
		});
	}

	/** Celek s vrstvami u regionů: region ve složce se hledáním najde i ve sbalené zemi a v sbaleném oddílu. */
	@Test
	public void hledaniNajdeRegionVeSlozceSCelkemAVrstvami() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			for (final boolean otevrena : new boolean[] { true, false }) {
				final JMapyPopup p = popup(radky(celekStazeny("europe/germany"), VrstvyProGui.katalogNemeckoPoRegionech(), v -> true), otevrena);
				Assert.assertEquals("bez hledání jsou regiony sbalené", 0, kolikrat(viditelne(p), "Bavorsko"));
				p.getHledat().setText("bavorsko");
				final List<String> texty = viditelne(p);
				Assert.assertEquals(otevrena + " " + texty, 1, kolikrat(texty, "Bavorsko"));
				Assert.assertEquals("ostatní regiony se neukážou", 0, kolikrat(texty, "Sasko"));
				Assert.assertEquals(1, kolikrat(texty, "Německo"));
				Assert.assertFalse(texty.contains(JMapyPopup.MIMO_EVROPU));
				p.getHledat().setText("");
				Assert.assertEquals("po smazání hledání zpět", 0, kolikrat(viditelne(p), "Bavorsko"));
			}
		});
	}

	/** Region stažené celé země je při hledání ve složce vypnutý s vysvětlením, nebo bez tlačítek s poznámkou „v mapě celé země“. */
	@Test
	public void regionStazeneZemeJeProHledaniNeaktivni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = popup(radky(celekStazeny("europe/germany"), VrstvyProGui.katalogNemeckoPoRegionech(), v -> v.getRegion().equals("europe/germany/bayern")), true);
			p.getHledat().setText("bavorsko");
			final List<JButton> tlacitka = new ArrayList<>();
			for (final Component k : radekRegionu(p, "Bavorsko")) {
				if (k instanceof JButton && ((JButton) k).getText().startsWith("Stáhnout")) {
					tlacitka.add((JButton) k);
				}
			}
			Assert.assertFalse(tlacitka.isEmpty());
			for (final JButton b : tlacitka) {
				Assert.assertFalse(b.isEnabled());
				Assert.assertEquals(NabidkaRadku.V_CELE_ZEME_NEJDE, b.getToolTipText());
			}
			p.getHledat().setText("hamburk");
			final List<Component> hamburk = radekRegionu(p, "Hamburk");
			Assert.assertTrue(hamburk.stream().anyMatch(k -> k instanceof JLabel && "✔ v mapě celé země".equals(((JLabel) k).getText())));
			Assert.assertTrue(hamburk.stream().noneMatch(k -> k instanceof JButton && ((JButton) k).getText().startsWith("Stáhnout")));
		});
	}

	private static Set<String> vsechnyRegiony(final String celekId) {
		final Set<String> v = new HashSet<>();
		KATALOG.regiony(KATALOG.podleId(celekId).get()).forEach(r -> v.add(r.getId()));
		return v;
	}

	private static Function<KatalogovaMapa, StavMapy> stavy(final Set<String> mame) {
		return m -> StavMapyProGui.stav(m, mame.contains(m.getId()) ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null);
	}

	/** Při všech stažených regionech země rada v mapě nenabídne celek (dvojí stažení). */
	@Test
	public void radaNenabidneCelekPriStazenychRegionech() {
		final Wgs[] body = { new Wgs(48.54, 9.04), new Wgs(40.42, -3.70), new Wgs(48.85, 2.35), new Wgs(41.9, 12.5), new Wgs(52.37, 4.9), new Wgs(51.5, -0.12) };
		for (int i = 0; i < body.length; i++) {
			final String celekId = ZEME[i][0];
			final Wgs b = body[i];
			final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, stavy(vsechnyRegiony(celekId)), b, null, new RadaOfflineMapy.Vyrez(b.lat - 0.4, b.lon - 0.6, b.lat + 0.4, b.lon + 0.6));
			Assert.assertNull(celekId, r.mapa);
			Assert.assertNull(celekId, r.odkaz);
			Assert.assertTrue(celekId, r.sousedni.stream().noneMatch(s -> s.mapa.getId().equals(celekId)));
		}
	}

	/** Německo stažené celé nebo po regionech: v Bádensku-Württembersku rada nabídne jen sousední Švýcarsko a Rakousko či Alsasko, ne Německo. */
	@Test
	public void radaVBadensku() {
		final Wgs konstanz = new Wgs(47.66, 9.18);
		final RadaOfflineMapy.Vyrez vyrez = new RadaOfflineMapy.Vyrez(47.26, 8.58, 48.06, 9.78);
		final Set<String> regiony = vsechnyRegiony("europe/germany");
		final Set<String> celek = new HashSet<>(Collections.singleton("europe/germany"));
		for (final Set<String> mame : java.util.Arrays.asList(regiony, celek)) {
			final RadaOfflineMapy r = RadaOfflineMapy.pro(KATALOG, stavy(mame), konstanz, null, vyrez);
			Assert.assertNull(r.mapa);
			final List<String> sousedni = new ArrayList<>();
			r.sousedni.forEach(s -> sousedni.add(s.mapa.getId()));
			Assert.assertEquals(sousedni.toString(), "europe/switzerland", sousedni.get(0));
			Assert.assertFalse(sousedni.toString(), sousedni.contains("europe/germany"));
		}
	}
}
