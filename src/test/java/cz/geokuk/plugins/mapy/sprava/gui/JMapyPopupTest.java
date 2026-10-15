package cz.geokuk.plugins.mapy.sprava.gui;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

import javax.swing.*;

import cz.geokuk.plugins.mapy.sprava.SpravaMapModel;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.program.PrehledovaMapa;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.PolygonPokryti;

/** Popup Mapy: řádky podle stavu, akce, odkazy na weby a čtení složky offline map. */
public class JMapyPopupTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final long MB = 1_000_000L;

	private static RadekMapy.Builder katalog(final String id, final RadekMapy.Stav stav) {
		return RadekMapy.katalogova(id, id, stav).velikost(600 * MB).zdroj("download.mapsforge.org").licence("ODbL 1.0");
	}

	private static List<JButton> tlacitka(final Container c) {
		final List<JButton> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (k instanceof JButton) {
				v.add((JButton) k);
			}
			if (k instanceof Container) {
				v.addAll(tlacitka((Container) k));
			}
		}
		return v;
	}

	static List<JButton> tlacitkaV(final Container c) {
		return tlacitka(c);
	}

	private static JButton tlacitko(final Container c, final String text) {
		return tlacitka(c).stream().filter(b -> text.equals(b.getText())).findFirst().orElse(null);
	}

	private static final class Zaznam implements OvladaniMap {
		final List<String> volani = new ArrayList<>();

		@Override
		public void stahni(final String id) {
			volani.add("stahni " + id);
		}

		@Override
		public void stahniVrstvu(final String id) {
			volani.add("stahniVrstvu " + id);
		}

		@Override
		public void zrusStahovani() {
			volani.add("zrus");
		}

		@Override
		public void zkontroluj(final String id) {
			volani.add("zkontroluj " + id);
		}

		@Override
		public void zvyrazniMapu(final String klic) {
			volani.add("zvyrazni " + klic);
		}

		@Override
		public void otevriSlozku() {
			volani.add("slozka");
		}
	}

	private static List<RadekMapy> dalsiMapy() {
		final List<RadekMapy> r = new ArrayList<>();
		r.add(RadekMapy.katalogova("de", "Německo", RadekMapy.Stav.NEMAME).velikost(3000 * MB).build());
		for (final String n : new String[] { "Bavorsko", "Berlín", "Sasko", "Šlesvicko-Holštýnsko" }) {
			r.add(RadekMapy.katalogova("de/" + n, n, RadekMapy.Stav.NEMAME).skupina("Německo").velikost(100 * MB).build());
		}
		for (final String n : new String[] { "Skotsko", "Wales" }) {
			r.add(RadekMapy.katalogova("uk/" + n, n, RadekMapy.Stav.NEMAME).skupina("Spojené království").velikost(100 * MB).build());
		}
		for (final String n : new String[] { "Rakousko", "Slovensko", "Švýcarsko", "Polsko", "Česko" }) {
			r.add(RadekMapy.katalogova("x/" + n, n, RadekMapy.Stav.NEMAME).velikost(100 * MB).build());
		}
		return r;
	}

	private static long viditelnychLabelu(final JMapyPopup p, final String text) {
		return labels(p).stream().filter(l -> text.equals(l.getText()) && l.isVisible()).count();
	}

	/** Hledání ukáže jen shodné mapy a země; bez diakritiky a velikosti písmen; prázdné hledání ukáže vše. */
	@Test
	public void hledaniFiltrujeMapy() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			Assert.assertNotNull("od 8 map je hledání", java.util.Arrays.stream(p.getComponents()).filter(c -> c == p.getHledat()).findFirst().orElse(null));
			p.getHledat().setText("slesvi");
			Assert.assertEquals(1, viditelnychLabelu(p, "Šlesvicko-Holštýnsko"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Bavorsko"));
			Assert.assertEquals("země s nalezenou mapou zůstane", 1, viditelnychLabelu(p, "Německo"));
			p.getHledat().setText("NĚMECKO");
			Assert.assertEquals("hledá se i podle země", 1, viditelnychLabelu(p, "Bavorsko"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Rakousko"));
			p.getHledat().setText("rak");
			Assert.assertEquals(0, viditelnychLabelu(p, "Německo"));
			Assert.assertEquals(1, viditelnychLabelu(p, "Rakousko"));
			p.getHledat().setText("zzz");
			Assert.assertEquals(1, viditelnychLabelu(p, JMapyPopup.NIC_NENALEZENO));
			p.getHledat().setText("");
			Assert.assertEquals(0, viditelnychLabelu(p, JMapyPopup.NIC_NENALEZENO));
			Assert.assertEquals("bez hledání jsou regiony sbalené", 0, viditelnychLabelu(p, "Bavorsko"));
			Assert.assertEquals(1, viditelnychLabelu(p, "Německo"));
		});
	}

	private static List<RadekMapy> mapaSVrstvami(final cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni trasy, final cz.geokuk.plugins.mapy.sprava.StavMapy.Faze faze) {
		final cz.geokuk.plugins.mapy.sprava.KatalogMap katalog = cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny();
		final cz.geokuk.plugins.mapy.sprava.KatalogVrstev vrstvy = cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko();
		return RadkyMap.sestav(katalog,
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, m.getId().equals("europe/czech-republic") ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, vrstvy,
				v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, v.getId().startsWith("trasy") ? trasy : cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, faze, 0, null));
	}

	/** Vrstvy jsou jen u stažené mapy, každá pod svou mapou; ostatní mapy vrstvy nemají. */
	@Test
	public void vrstvyJsouPodStazenouMapou() {
		final List<RadekMapy> radky = mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC);
		final List<String> vrstvy = new ArrayList<>();
		String posledniMapa = null;
		for (final RadekMapy r : radky) {
			if (r.isVrstva()) {
				Assert.assertEquals("vrstva je hned za svou mapou", "europe/czech-republic", posledniMapa);
				Assert.assertEquals("europe/czech-republic", r.mapaId);
				vrstvy.add(r.nazev);
			} else {
				posledniMapa = r.id;
			}
		}
		Assert.assertEquals(Arrays.asList("Turistické trasy", "Vrstevnice", "Stínování terénu"), vrstvy);
		final RadekMapy trasy = radky.stream().filter(RadekMapy::isVrstva).findFirst().get();
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, trasy.stav);
		Assert.assertEquals(25_000_000L, trasy.velikost);
		Assert.assertEquals(RadekMapy.Stav.NEMAME, radky.stream().filter(r -> "Vrstevnice".equals(r.nazev)).findFirst().get().stav);
	}

	/** Vrstvy v popupu: tlačítko Stáhnout u vrstvy volá stahování vrstvy, ne mapy; hover na vrstvě ukazuje obrys její mapy. */
	@Test
	public void popupMaVrstvyAStahujeJe() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertTrue(labels(p).stream().anyMatch(l -> "Turistické trasy".equals(l.getText())));
			Assert.assertTrue(labels(p).stream().anyMatch(l -> "Stínování terénu".equals(l.getText())));
			final List<JButton> stahnout = tlacitkaSTextem(p, JMapyPopup.STAHNOUT);
			Assert.assertTrue(stahnout.size() >= 3);
			for (final JButton b : stahnout) {
				b.doClick();
			}
			Assert.assertTrue(z.volani.toString(), z.volani.contains("stahniVrstvu trasy/europe/czech-republic"));
			Assert.assertTrue(z.volani.toString(), z.volani.contains("stahniVrstvu dem/europe/czech-republic"));
			Assert.assertTrue("mapy ke stažení volají stahování mapy", z.volani.stream().anyMatch(v -> v.startsWith("stahni europe/")));
			final RadekMapy vrstva = mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC).stream().filter(RadekMapy::isVrstva).findFirst().get();
			Assert.assertEquals("europe/czech-republic", JMapyPopup.klicZvyrazneni(vrstva));
		});
	}

	/** Nová verze vrstvy se nabídne k aktualizaci, chyba má „Stáhnout znovu“; během stahování vrstvy nejde stáhnout nic jiného. */
	@Test
	public void stavyVrstev() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.JINA_VERZE, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertEquals(1, tlacitkaSTextem(p, JMapyPopup.AKTUALIZOVAT).size());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertFalse("během stahování vrstvy jde jen zrušit", tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.STAHNOUT).stream().noneMatch(JButton::isEnabled));
		});
	}

	/** Země s regiony jsou ve stromu sbalené; klik na zemi regiony rozbalí a sbalí; samostatné mapy jsou vidět vždy. */
	@Test
	public void stromZemiSbalenyARozbalovany() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			Assert.assertEquals(1, viditelnychLabelu(p, "Německo"));
			Assert.assertEquals("země bez vlastní mapy má hlavičku", 1, viditelnychLabelu(p, "Spojené království"));
			Assert.assertEquals(1, viditelnychLabelu(p, "Rakousko"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Bavorsko"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Wales"));
			p.prepniUzel("Německo");
			Assert.assertEquals(1, viditelnychLabelu(p, "Bavorsko"));
			Assert.assertEquals("jiná země zůstane sbalená", 0, viditelnychLabelu(p, "Wales"));
			p.prepniUzel("Spojené království");
			Assert.assertEquals(1, viditelnychLabelu(p, "Wales"));
			p.prepniUzel("Německo");
			Assert.assertEquals(0, viditelnychLabelu(p, "Bavorsko"));
			p.prepniUzel("Německo");
			p.obnov(dalsiMapy());
			Assert.assertEquals("rozbalení přežije přestavbu popupu", 1, viditelnychLabelu(p, "Bavorsko"));
		});
	}

	/** Hledání rozbalí země s nalezenou mapou, ostatní zmizí; po vymazání se vrátí ruční rozbalení. */
	@Test
	public void hledaniRozbaliZemeSNalezenouMapou() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			p.getHledat().setText("bav");
			Assert.assertEquals(1, viditelnychLabelu(p, "Bavorsko"));
			Assert.assertEquals("hlavička země zůstane", 1, viditelnychLabelu(p, "Německo"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Berlín"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Spojené království"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Rakousko"));
			p.getHledat().setText("");
			Assert.assertEquals(0, viditelnychLabelu(p, "Bavorsko"));
		});
	}

	/** Vestavěný katalog Evropy: země s regiony (Německo, Francie…) jsou sbalené, popup nemá stovky viditelných řádků. */
	@Test
	public void katalogEvropyJeVPopupuSbaleny() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(), cz.geokuk.plugins.mapy.sprava.StavMapy::novy, Collections.emptyList(), null));
			Assert.assertEquals(1, viditelnychLabelu(p, "Německo"));
			Assert.assertEquals(1, viditelnychLabelu(p, "Francie"));
			Assert.assertEquals(1, viditelnychLabelu(p, "Spojené království"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Bavorsko"));
			p.prepniUzel("Německo");
			Assert.assertEquals(1, viditelnychLabelu(p, "Bavorsko"));
			long viditelnychRadku = labels(p).stream().filter(l -> l.isVisible() && l.getText() != null && !l.getText().isEmpty()).count();
			Assert.assertTrue("rozbalené Německo + sbalené ostatní, ne všech 130 map: " + viditelnychRadku, viditelnychRadku < 250);
		});
	}

	/** Hledání popup nezavírá ani neotevírá znovu (měnila by se velikost); víc slov se hledá v libovolném pořadí. */
	@Test
	public void filtrovaniNemeniVelikostPopupu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final int[] zmen = { 0 };
			p.setPoZmeneObsahu(() -> zmen[0]++);
			p.obnov(dalsiMapy());
			zmen[0] = 0;
			final java.awt.Dimension pred = p.getPreferredSize();
			for (final String t : new String[] { "r", "ra", "rak", "zzz", "" }) {
				p.getHledat().setText(t);
			}
			Assert.assertEquals(0, zmen[0]);
			Assert.assertEquals("velikost popupu se při hledání nemění", pred, p.getPreferredSize());
			p.getHledat().setText("nemecko sasko");
			Assert.assertEquals(1, viditelnychLabelu(p, "Sasko"));
			Assert.assertEquals(0, viditelnychLabelu(p, "Bavorsko"));
			p.getHledat().setText("sasko nemecko");
			Assert.assertEquals(1, viditelnychLabelu(p, "Sasko"));
		});
	}

	/** Text hledání se maže až při skutečném zavření popupu, ne při skrytí kvůli změně velikosti nebo přestavbě. */
	@Test
	public void hledaniSeMazeJenPriZavreni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			p.getHledat().setText("rak");
			final java.awt.event.HierarchyEvent skryti = new java.awt.event.HierarchyEvent(p, java.awt.event.HierarchyEvent.HIERARCHY_CHANGED, p, null,
					java.awt.event.HierarchyEvent.SHOWING_CHANGED);
			for (final java.awt.event.HierarchyListener l : p.getHierarchyListeners()) {
				l.hierarchyChanged(skryti);
			}
			Assert.assertEquals("rak", p.getHledat().getText());
			p.obnov(dalsiMapy());
			Assert.assertEquals("rak", p.getHledat().getText());
			p.popupZavren();
			Assert.assertEquals("", p.getHledat().getText());
		});
	}

	/** Skutečně zobrazený popup: psaní do pole po znacích ho nezavře ani nezničí hledání, zavření ho vymaže, Esc zavře. */
	@Test(timeout = 30000)
	public void psaniDoPoleVeSkutecnemPopupu() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JPanel kotva = new JPanel();
		final JMapyPopup p = new JMapyPopup();
		final cz.geokuk.util.gui.PopupNadBlokem popup = new cz.geokuk.util.gui.PopupNadBlokem(kotva, () -> {});
		// stejné zapojení jako v JBlokMap
		popup.setDrzOtevreny(p::isHledaniAktivni);
		popup.setPoZavreni(p::popupZavren);
		p.setPoZmeneObsahu(() -> {
			if (popup.viditelny() == p) {
				popup.ukaz(p, kotva, true);
			}
		});
		try {
			SwingUtilities.invokeAndWait(() -> {
				okno.setSize(900, 700);
				okno.getContentPane().add(kotva, java.awt.BorderLayout.SOUTH);
				okno.setVisible(true);
				p.setOvladani(new Zaznam());
				p.obnov(dalsiMapy());
				popup.ukaz(p, kotva, false);
			});
			SwingUtilities.invokeAndWait(() -> Assert.assertTrue("popup je vidět", p.isShowing()));
			for (final char c : "rak".toCharArray()) {
				SwingUtilities.invokeAndWait(() -> {
					try {
						p.getHledat().getDocument().insertString(p.getHledat().getDocument().getLength(), String.valueOf(c), null);
					} catch (final javax.swing.text.BadLocationException e) {
						throw new IllegalStateException(e);
					}
				});
			}
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertTrue("popup po psaní zůstal otevřený", p.isShowing());
				Assert.assertEquals("rak", p.getHledat().getText());
				Assert.assertEquals(1, viditelnychLabelu(p, "Rakousko"));
				Assert.assertEquals(0, viditelnychLabelu(p, "Slovensko"));
				popup.zavri();
				Assert.assertEquals("", p.getHledat().getText());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				popup.zastav();
				okno.dispose();
			});
		}
	}

	private static void fokusDoPole(final JMapyPopup p) {
		for (final java.awt.event.FocusListener l : p.getHledat().getFocusListeners()) {
			l.focusGained(new java.awt.event.FocusEvent(p.getHledat(), java.awt.event.FocusEvent.FOCUS_GAINED));
		}
	}

	private static void klik(final java.awt.Component kde) {
		kde.dispatchEvent(new java.awt.event.MouseEvent(kde, java.awt.event.MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false));
	}

	/** Píše-li uživatel, po znovuzobrazení popupu (změna velikosti při průběhu stahování) se pole znovu žádá o fokus; klik mimo popup psaní ukončí. */
	@Test
	public void psaniPrezijeZnovuzobraveniAKlikMimoHoUkonci() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			Assert.assertFalse(p.isHledaniAktivni());
			p.poZnovuZobrazeni();
			Assert.assertEquals("nepíše se, fokus se nežádá", 0, p.pozadavkuNaFokus);
			fokusDoPole(p);
			Assert.assertTrue(p.isHledaniAktivni());
			p.poZnovuZobrazeni();
			Assert.assertEquals(1, p.pozadavkuNaFokus);
			p.zacniSledovatKlikyMimo();
			try {
				klik(labels(p).get(0));
				Assert.assertTrue("klik do popupu psaní neukončí", p.isHledaniAktivni());
				klik(new JPanel());
				Assert.assertFalse("klik mimo popup psaní ukončí, popup se pak zavře odjetím myši", p.isHledaniAktivni());
				p.poZnovuZobrazeni();
				Assert.assertEquals(1, p.pozadavkuNaFokus);
			} finally {
				p.prestanSledovatKlikyMimo();
			}
			fokusDoPole(p);
			p.popupZavren();
			Assert.assertFalse("zavřením popupu psaní končí", p.isHledaniAktivni());
		});
	}

	/** Těžký popup (nevejde se do okna) nepřijme fokus, proto v něm pole hledání není a písmena nejdou do hlavního okna. */
	@Test(timeout = 30000)
	public void vTezkemPopupuBezHledani() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JPanel kotva = new JPanel();
		final JMapyPopup p = new JMapyPopup();
		final cz.geokuk.util.gui.PopupNadBlokem popup = new cz.geokuk.util.gui.PopupNadBlokem(kotva, () -> {});
		try {
			SwingUtilities.invokeAndWait(() -> {
				okno.setSize(300, 250);
				okno.getContentPane().add(kotva, java.awt.BorderLayout.SOUTH);
				okno.setVisible(true);
				p.setOvladani(new Zaznam());
				p.obnov(dalsiMapy());
				popup.ukaz(p, kotva, false);
			});
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertTrue(p.isShowing());
				Assert.assertFalse("popup je vyšší než okno, takže těžký", SwingUtilities.getWindowAncestor(p) instanceof java.awt.Frame);
				Assert.assertFalse(p.getHledat().isVisible());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				popup.zastav();
				okno.dispose();
			});
		}
	}

	/** Hledání přežije přestavbu popupu (třeba při změně stavu mapy) a bez mála map se nenabízí. */
	@Test
	public void hledaniPrezijePrestavbuAMaloMapBezHledani() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			p.getHledat().setText("rak");
			final List<RadekMapy> zmenene = dalsiMapy();
			zmenene.set(4, RadekMapy.katalogova("x/Rakousko", "Rakousko", RadekMapy.Stav.CHYBA).chyba("Stažení se přerušilo").build());
			p.obnov(zmenene);
			Assert.assertEquals("rak", p.getHledat().getText());
			Assert.assertEquals(0, viditelnychLabelu(p, "Slovensko"));
			Assert.assertEquals(1, viditelnychLabelu(p, "Rakousko"));
			final JMapyPopup malo = new JMapyPopup();
			malo.setOvladani(new Zaznam());
			malo.obnov(dalsiMapy().subList(7, 10));
			Assert.assertNull(java.util.Arrays.stream(malo.getComponents()).filter(c -> c == malo.getHledat()).findFirst().orElse(null));
		});
	}

	/** Výsledek kontroly: vše aktuální, počet novějších, nebo že selhala; vždy s časem. */
	@Test
	public void vysledekKontroly() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Instant ted = Instant.now();
			final RadekMapy cz = katalog("cz", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build();
			Assert.assertNull("ještě se nekontrolovalo", p.vysledekKontroly(Collections.singletonList(cz)));
			final RadekMapy aktualni = katalog("cz", RadekMapy.Stav.AKTUALNI).zkontrolovano(ted).build();
			final RadekMapy novejsi = katalog("sk", RadekMapy.Stav.NOVEJSI).zkontrolovano(ted).build();
			final RadekMapy selhala = katalog("at", RadekMapy.Stav.AKTUALNI).zkontrolovano(ted).chyba("Server není dostupný").build();
			Assert.assertTrue(p.vysledekKontroly(Collections.singletonList(aktualni)).startsWith(JMapyPopup.VSE_AKTUALNI + " · zkontrolováno "));
			Assert.assertTrue(p.vysledekKontroly(Arrays.asList(aktualni, novejsi)).startsWith("Novější data: 1 mapa · zkontrolováno "));
			Assert.assertTrue(p.vysledekKontroly(Arrays.asList(aktualni, selhala)).startsWith(JMapyPopup.KONTROLA_SELHALA));
			p.setOvladani(new Zaznam());
			p.obnov(Arrays.asList(aktualni));
			Assert.assertTrue(labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith(JMapyPopup.VSE_AKTUALNI)));
		});
	}

	/** U stahované mapy je za procenty odhad času z rychlosti. */
	@Test
	public void stahovaniMaOdhadCasu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final long[] cas = { 0 };
			p.hodiny = () -> cas[0];
			p.setOvladani(new Zaznam());
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(10 * MB).build()));
			cas[0] = 10_000;
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(110 * MB).build()));
			Assert.assertTrue(labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().contains("%") && l.getText().contains("zbývá asi 50 s")));
		});
	}

	private static List<JButton> tlacitkaSTextem(final JMapyPopup p, final String text) {
		final List<JButton> v = new ArrayList<>();
		for (final JButton b : tlacitka(p)) {
			if (text.equals(b.getText())) {
				v.add(b);
			}
		}
		return v;
	}

	/** Mapa, která se na disk nevejde (s rezervou stahovače), se nedá stáhnout; tooltip říká totéž číslo, se kterým se porovnává. */
	@Test
	public void nedostatekMistaNaDisku() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final long rezerva = SpravaMapModel.REZERVA_MISTA;
			p.setVolneMisto(300 * MB);
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.NEMAME).build(), katalog("pl", RadekMapy.Stav.NEMAME).velikost(100 * MB).build()));
			final List<JButton> stahnout = tlacitkaSTextem(p, JMapyPopup.STAHNOUT);
			Assert.assertEquals(2, stahnout.size());
			Assert.assertFalse("600 MB na 300 MB nejde", stahnout.get(0).isEnabled());
			Assert.assertTrue(stahnout.get(0).getToolTipText(), stahnout.get(0).getToolTipText().contains("Na disku je volných jen 300 MB"));
			Assert.assertTrue("100 MB + rezerva se vejde", stahnout.get(1).isEnabled());
			// přesně na hranici: mapa + rezerva se vejde, o bajt míň ne
			p.setVolneMisto(600 * MB + rezerva);
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.STAHNOUT).stream().allMatch(JButton::isEnabled));
			p.setVolneMisto(600 * MB + rezerva - 1);
			Assert.assertFalse("o bajt méně už ne", tlacitkaSTextem(p, JMapyPopup.STAHNOUT).get(0).isEnabled());
			p.setVolneMisto(610 * MB);
			final JButton nejde = tlacitkaSTextem(p, JMapyPopup.STAHNOUT).get(0);
			Assert.assertFalse("610 MB na 600 MB mapu s rezervou nestačí", nejde.isEnabled());
			Assert.assertTrue("tooltip počítá i rezervu: " + nejde.getToolTipText(), nejde.getToolTipText().contains("volných jen 610 MB, mapa potřebuje asi 652 MB"));
			p.setVolneMisto(-1);
			Assert.assertTrue("neznámé volné místo nic nevypíná", tlacitkaSTextem(p, JMapyPopup.STAHNOUT).stream().allMatch(JButton::isEnabled));
		});
	}

	/** Chyba u rozpracované mapy počítá jen zbytek, ne celou mapu; aktualizace celou. */
	@Test
	public void rozpracovanaMapaPotrebujeJenZbytek() {
		final RadekMapy chyba = katalog("cz", RadekMapy.Stav.CHYBA).velikost(600 * MB).stazeno(500 * MB).build();
		Assert.assertEquals(100 * MB + SpravaMapModel.REZERVA_MISTA, JMapyPopup.potrebneMisto(chyba));
		final RadekMapy novejsi = katalog("sk", RadekMapy.Stav.NOVEJSI).velikost(300 * MB).build();
		Assert.assertEquals(300 * MB + SpravaMapModel.REZERVA_MISTA, JMapyPopup.potrebneMisto(novejsi));
		Assert.assertEquals(0, JMapyPopup.potrebneMisto(katalog("at", RadekMapy.Stav.AKTUALNI).build()));
	}

	/** Nula z getUsableSpace je chyba, ne plný disk. */
	@Test
	public void nulaJeNeznameMisto() {
		Assert.assertEquals(-1, JBlokMap.bezNuly(0));
		Assert.assertEquals(-1, JBlokMap.bezNuly(-5));
		Assert.assertEquals(1234, JBlokMap.bezNuly(1234));
	}

	/** Myš nad řádkem (kterýmkoli políčkem) zvýrazní obrys té mapy, odjetí ho zruší. */
	@Test
	public void najetiNaRadekZvyrazniObrys() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build(), RadekMapy.vlastni("paws.map").build()));
			final JLabel nazev = labels(p).stream().filter(l -> "cz".equals(l.getText())).findFirst().get();
			z.volani.clear();
			for (final java.awt.event.MouseListener l : nazev.getMouseListeners()) {
				l.mouseEntered(new java.awt.event.MouseEvent(nazev, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
				l.mouseExited(new java.awt.event.MouseEvent(nazev, java.awt.event.MouseEvent.MOUSE_EXITED, 0, 0, 1, 1, 0, false));
			}
			Assert.assertEquals(Arrays.asList("zvyrazni cz", "zvyrazni null"), z.volani);
			Assert.assertEquals("soubor:paws.map", JMapyPopup.klicZvyrazneni(RadekMapy.vlastni("paws.map").build()));
		});
	}

	/** Skrytí popupu během najetí na řádek obrys zruší; popup bez displeje není vidět, takže stačí událost o změně viditelnosti. */
	@Test
	public void skrytiPopupuZrusiZvyrazneni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(Collections.singletonList(katalog("cz", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build()));
			final JLabel nazev = labels(p).stream().filter(l -> "cz".equals(l.getText())).findFirst().get();
			for (final java.awt.event.MouseListener l : nazev.getMouseListeners()) {
				l.mouseEntered(new java.awt.event.MouseEvent(nazev, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
			}
			Assert.assertEquals(Collections.singletonList("zvyrazni cz"), z.volani);
			final java.awt.event.HierarchyEvent skryti = new java.awt.event.HierarchyEvent(p, java.awt.event.HierarchyEvent.HIERARCHY_CHANGED, p, null,
					java.awt.event.HierarchyEvent.SHOWING_CHANGED);
			for (final java.awt.event.HierarchyListener l : p.getHierarchyListeners()) {
				l.hierarchyChanged(skryti);
			}
			Assert.assertEquals(Arrays.asList("zvyrazni cz", "zvyrazni null"), z.volani);
		});
	}

	private static List<JLabel> labels(final Container c) {
		final List<JLabel> v = new ArrayList<>();
		for (final java.awt.Component k : c.getComponents()) {
			if (k instanceof JLabel) {
				v.add((JLabel) k);
			}
			if (k instanceof Container) {
				v.addAll(labels((Container) k));
			}
		}
		return v;
	}

	@Test
	public void akceRadkuPodleStavu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build(), katalog("sk", RadekMapy.Stav.NOVEJSI).datumDat(Instant.EPOCH).build(),
					katalog("at", RadekMapy.Stav.NEMAME).proZobrazeneMisto(true).build(), katalog("pl", RadekMapy.Stav.NEMAME).rozpracovano(true).build(),
					RadekMapy.vlastni("paws.map").build()));
			tlacitko(p, JMapyPopup.AKTUALIZOVAT).doClick();
			tlacitko(p, JMapyPopup.STAHNOUT).doClick();
			tlacitko(p, JMapyPopup.POKRACOVAT).doClick();
			tlacitko(p, JMapyPopup.ZKONTROLOVAT).doClick();
			tlacitko(p, JMapyPopup.OTEVRIT_SLOZKU).doClick();
			Assert.assertEquals(Arrays.asList("stahni sk", "stahni at", "stahni pl", "zkontroluj cz", "zkontroluj sk", "slozka"), z.volani);
		});
	}

	@Test
	public void behemStahovaniNejdeStahovatJinou() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(300 * MB).build(), katalog("pl", RadekMapy.Stav.NEMAME).build()));
			Assert.assertFalse(tlacitko(p, JMapyPopup.STAHNOUT).isEnabled());
			tlacitko(p, JMapyPopup.ZRUSIT).doClick();
			Assert.assertEquals(Collections.singletonList("zrus"), z.volani);
		});
	}

	/** Při stahování chodí změny často: popup se nepřestaví (posuvník seznamu zůstane), jen se přepíše popis. */
	@Test
	public void prubehStahovaniJenPrepisePopis() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final int[] prestaveni = new int[1];
			p.setPoZmeneObsahu(() -> prestaveni[0]++);
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(60 * MB).build()));
			final JButton zrusit = tlacitko(p, JMapyPopup.ZRUSIT);
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(300 * MB).build()));
			Assert.assertSame(zrusit, tlacitko(p, JMapyPopup.ZRUSIT));
			Assert.assertTrue(texty(p).contains("50 % z 600 MB"));
			p.obnov(Arrays.asList(katalog("at", RadekMapy.Stav.AKTUALNI).datumDat(Instant.EPOCH).build()));
			Assert.assertNull(tlacitko(p, JMapyPopup.ZRUSIT));
		});
	}

	private static List<String> texty(final Container c) {
		final List<String> v = new ArrayList<>();
		for (final Component k : c.getComponents()) {
			if (k instanceof JLabel) {
				v.add(((JLabel) k).getText());
			}
			if (k instanceof Container) {
				v.addAll(texty((Container) k));
			}
		}
		return v;
	}

	/** Weby s mapami jen úvodní stránkou: OpenAndroMaps nedovoluje přímé odkazy na stažení. */
	@Test
	public void odkazyVedouNaUvodniStranky() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			int odkazu = 0;
			for (final JButton b : tlacitka(p)) {
				if (b.getToolTipText() != null && (b.getToolTipText().contains("paws") || b.getToolTipText().contains("andromaps"))) {
					final URI u = URI.create(b.getToolTipText());
					Assert.assertTrue(b.getToolTipText(), u.getPath() == null || u.getPath().isEmpty() || "/".equals(u.getPath()));
					odkazu++;
				}
			}
			Assert.assertEquals(2, odkazu);
		});
	}

	@Test
	public void souhrnBlokuBereNejdulezitejsiStav() {
		final RadekMapy ok = katalog("cz", RadekMapy.Stav.AKTUALNI).build();
		final RadekMapy nova = katalog("sk", RadekMapy.Stav.NOVEJSI).build();
		final RadekMapy chyba = RadekMapy.vlastni("x.map").chyba("rozbitá").build();
		final RadekMapy stahuje = katalog("at", RadekMapy.Stav.STAHUJE).build();
		final RadekMapy nemame = katalog("pl", RadekMapy.Stav.NEMAME).build();
		Assert.assertEquals(RadekMapy.Stav.NEMAME, JBlokMap.souhrn(Collections.singletonList(nemame)));
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, JBlokMap.souhrn(Arrays.asList(nemame, ok)));
		Assert.assertEquals(RadekMapy.Stav.NOVEJSI, JBlokMap.souhrn(Arrays.asList(ok, nova)));
		Assert.assertEquals(RadekMapy.Stav.CHYBA, JBlokMap.souhrn(Arrays.asList(ok, nova, chyba)));
		Assert.assertEquals(RadekMapy.Stav.STAHUJE, JBlokMap.souhrn(Arrays.asList(ok, chyba, stahuje)));
	}

	@Test
	public void souboryVeSlozce() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		Files.copy(new File("src/test/resources/offline-mapy/kukov.map").toPath(), new File(slozka, "kukov.map").toPath());
		Files.write(new File(slozka, "rozbita.map").toPath(), new byte[5000]);
		Files.write(new File(slozka, PrehledovaMapa.SOUBOR).toPath(), new byte[10]);
		Files.write(new File(slozka, "tema.zip").toPath(), new byte[10]);
		final List<RadekMapy> radky = SouboryMap.nacti(slozka, KatalogMap.vestaveny());
		Assert.assertEquals(2, radky.size());
		final RadekMapy kukov = radky.get(0);
		Assert.assertEquals("kukov.map", kukov.getNazev());
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, kukov.getStav());
		Assert.assertNotNull(kukov.datumDat);
		Assert.assertTrue(kukov.isVlastni());
		Assert.assertEquals(RadekMapy.Stav.CHYBA, radky.get(1).getStav());
		Assert.assertTrue(radky.get(1).isVeSlozce());
		Assert.assertTrue(SouboryMap.nacti(new File(slozka, "neni"), KatalogMap.vestaveny()).isEmpty());
		Assert.assertEquals("nečitelná mapa nic nepokrývá", 1, SouboryMap.obrysy(slozka, KatalogMap.vestaveny()).size());
	}

	/** Soubor se jménem z katalogu je katalogová mapa: název země a obrys země, ne obdélník z hlavičky. */
	@Test
	public void mapaZKatalogu() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		Files.copy(new File("src/test/resources/offline-mapy/kukov.map").toPath(), new File(slozka, "czech-republic.map").toPath());
		final RadekMapy r = SouboryMap.nacti(slozka, KatalogMap.vestaveny()).get(0);
		Assert.assertFalse(r.isVlastni());
		Assert.assertEquals("europe/czech-republic", r.getId());
		Assert.assertEquals("Česko", r.getNazev());
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, r.getStav());
		final List<PolygonPokryti> obrysy = SouboryMap.obrysy(slozka, KatalogMap.vestaveny());
		Assert.assertEquals(1, obrysy.size());
		Assert.assertSame(KatalogMap.vestaveny().podleId("europe/czech-republic").get().getPokryti(), obrysy.get(0));
		Assert.assertTrue("Praha", obrysy.get(0).obsahuje(new Wgs(50.08, 14.42)));
	}

	/** Dlouhý text chyby popup neroztáhne, celý je v tooltipu. */
	@Test
	public void dlouhaChybaSeZkrati() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final String chyba = "Stahování se přerušilo: " + String.join("", Collections.nCopies(20, "dlouhý technický text "));
			final JMapyPopup p = new JMapyPopup();
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.CHYBA).chyba(chyba).build()));
			final JLabel popis = popisky(p).stream().filter(l -> l.getText() != null && l.getText().startsWith("Stahování")).findFirst().get();
			Assert.assertTrue(popis.getText().endsWith("…"));
			Assert.assertTrue(popis.getPreferredSize().width <= JMapyPopup.SIRKA_POPISU + 10);
			Assert.assertTrue(popis.getToolTipText().contains(chyba.trim()));
		});
	}

	/** Soubor se stejným jménem vyměněný za jiný: řádek se nepřestaví, ale tooltip ukáže nové údaje. */
	@Test
	public void tooltipPoVymeneSouboru() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.obnov(Collections.singletonList(RadekMapy.vlastni("paws.map").velikost(5 * MB).datumDat(Instant.EPOCH).build()));
			p.obnov(Collections.singletonList(RadekMapy.vlastni("paws.map").velikost(2_500 * MB).datumDat(Instant.parse("2026-10-01T00:00:00Z")).build()));
			for (final JLabel l : popisky(p)) {
				if (l.getToolTipText() != null) {
					Assert.assertTrue(l.getToolTipText(), l.getToolTipText().contains("2,5 GB"));
				}
			}
		});
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

	/** U map z katalogu je odkaz na projekt, licenci a atribuci OSM; bez nich (jen vlastní soubory) ne. */
	@Test
	public void zdrojALicenceMapZKatalogu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.obnov(Collections.singletonList(RadekMapy.vlastni("paws.map").build()));
			Assert.assertNull(tlacitko(p, "licence ODbL"));
			p.obnov(Arrays.asList(RadekMapy.vlastni("paws.map").build(), katalog("at", RadekMapy.Stav.NEMAME).build()));
			Assert.assertEquals(JMapyPopup.URL_ODBL, tlacitko(p, "licence ODbL").getToolTipText());
			Assert.assertEquals(JMapyPopup.URL_OSM, tlacitko(p, "© přispěvatelé OpenStreetMap").getToolTipText());
			Assert.assertEquals(JMapyPopup.URL_MAPSFORGE, tlacitko(p, "mapsforge").getToolTipText());
		});
	}

	/** Po Zrušit se až do potvrzení ukazuje „Ruší se…“, i když ještě chodí průběh. */
	@Test
	public void rusiSe() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(60 * MB).build()));
			tlacitko(p, JMapyPopup.ZRUSIT).doClick();
			Assert.assertFalse(tlacitko(p, JMapyPopup.ZRUSIT).isEnabled());
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(120 * MB).build()));
			Assert.assertTrue(texty(p).contains(JMapyPopup.RUSI_SE));
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.NEMAME).rozpracovano(true).stazeno(120 * MB).build()));
			Assert.assertFalse(texty(p).contains(JMapyPopup.RUSI_SE));
			Assert.assertNotNull(tlacitko(p, JMapyPopup.POKRACOVAT));
		});
	}
}
