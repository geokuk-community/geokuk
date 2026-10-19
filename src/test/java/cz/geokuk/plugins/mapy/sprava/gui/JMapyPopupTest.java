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
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

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
		public void smazVrstvu(final String id) {
			volani.add("smazVrstvu " + id);
		}

		@Override
		public void stahniVse(final String nazevZeme, final String celekKlic, final List<String> klice) {
			volani.add("stahniVse " + nazevZeme + " " + celekKlic + " " + klice.size());
		}

		@Override
		public void smazDvojite(final String idCelku, final boolean regiony) {
			volani.add("smazDvojite " + idCelku + " " + regiony);
		}

		@Override
		public void setStinovani(final boolean zapnuto) {
			volani.add("stinovani " + zapnuto);
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
		public void zkontrolujKatalogVrstev() {
			volani.add("zkontroluj vrstvy");
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
				v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, v.getId().startsWith("tourist") ? trasy : cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, faze, 0, null));
	}

	private static RadekMapy cesko(final List<RadekMapy> radky) {
		return radky.stream().filter(r -> "europe/czech-republic".equals(r.id)).findFirst().get();
	}

	/** Každá mapa má buňky vrstev ze svého klíče: Česko trasy staženo, vrstevnice a stínování ke stažení; mapy bez vrstev „—“. */
	@Test
	public void radekMaBunkyVrstev() {
		final List<RadekMapy> radky = mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC);
		final RadekMapy ceskoRadek = cesko(radky);
		Assert.assertEquals(cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky.STAZENO, ceskoRadek.bunka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.TRASY).getStav());
		Assert.assertEquals(25_000_000L, ceskoRadek.bunka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.TRASY).getVelikost());
		Assert.assertEquals(cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky.KE_STAZENI, ceskoRadek.bunka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.VRSTEVNICE).getStav());
		Assert.assertEquals(cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky.KE_STAZENI, ceskoRadek.bunka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.STINOVANI).getStav());
		final RadekMapy jina = radky.stream().filter(r -> "europe/austria".equals(r.id)).findFirst().get();
		Assert.assertEquals(cz.geokuk.plugins.mapy.sprava.MatriceMap.StavBunky.NENI, jina.bunka(cz.geokuk.plugins.mapy.sprava.MatriceMap.Cast.TRASY).getStav());
	}

	/** Stažená vrstva nebo jen vrstva bez základní mapy dá řádek do oddílu Ve složce. */
	@Test
	public void vrstvaBezMapyJeVeSlozce() {
		final List<RadekMapy> radky = RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(),
				v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, v.getId().startsWith("tourist") ? cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, null));
		Assert.assertTrue(cesko(radky).isVeSlozce());
		Assert.assertFalse(radky.stream().filter(r -> "europe/austria".equals(r.id)).findFirst().get().isVeSlozce());
	}

	/** Buňky vrstev v popupu: ke stažení volá stahování vrstvy, mapy dál stahování mapy; hover na řádku ukazuje obrys mapy. */
	@Test
	public void popupMaSloupceVrstevAStahujeJe() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			final List<RadekMapy> radky = mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC);
			p.obnov(radky);
			for (final String sloupec : new String[] { JMapyPopup.SLOUPEC_TRASY, JMapyPopup.SLOUPEC_VRSTEVNICE, JMapyPopup.SLOUPEC_STINOVANI }) {
				Assert.assertTrue(sloupec, labels(p).stream().anyMatch(l -> sloupec.equals(l.getText())));
			}
			final List<JButton> vrstvy = tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ")).collect(java.util.stream.Collectors.toList());
			Assert.assertEquals("trasy, vrstevnice a stínování Česka", 3, vrstvy.size());
			for (final JButton b : vrstvy) {
				b.doClick();
			}
			Assert.assertTrue(z.volani.toString(), z.volani.contains("stahniVrstvu tourist/europe/czech-republic"));
			Assert.assertTrue(z.volani.toString(), z.volani.contains("stahniVrstvu contours/europe/czech-republic"));
			Assert.assertTrue(z.volani.toString(), z.volani.contains("stahniVrstvu dem/europe/czech-republic"));
			for (final JButton b : tlacitkaSTextem(p, JMapyPopup.STAHNOUT)) {
				b.doClick();
			}
			Assert.assertTrue("mapy ke stažení volají stahování mapy", z.volani.stream().anyMatch(v -> v.startsWith("stahni europe/")));
			Assert.assertEquals("europe/czech-republic", JMapyPopup.klicZvyrazneni(cesko(radky)));
		});
	}

	/** Stažená vrstva má ×, vrstva ke stažení a stahovaná ne; během jakéhokoli stahování je × vypnuté. */
	@Test
	public void smazaniVrstvy() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			final List<JButton> smazat = tlacitkaSTextem(p, JMapyPopup.SMAZAT_VRSTVU);
			Assert.assertEquals("jen stažená vrstva (trasy), ne ty ke stažení", 1, smazat.size());
			smazat.get(0).doClick();
			Assert.assertEquals(Collections.singletonList("smazVrstvu tourist/europe/czech-republic"), z.volani);
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertTrue("stahovaná vrstva se nemaže", tlacitkaSTextem(p, JMapyPopup.SMAZAT_VRSTVU).isEmpty());
			final List<RadekMapy> sJinouMapou = new ArrayList<>(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			sJinouMapou.add(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(MB).build());
			p.obnov(sJinouMapou);
			final List<JButton> zakazane = tlacitkaSTextem(p, JMapyPopup.SMAZAT_VRSTVU);
			Assert.assertEquals("tlačítko zůstává vidět", 1, zakazane.size());
			Assert.assertFalse("při stahování jiné mapy je vypnuté", zakazane.get(0).isEnabled());
			Assert.assertEquals(JMapyPopup.BEHEM_STAHOVANI_NEJDE_MAZAT, zakazane.get(0).getToolTipText());
			zakazane.get(0).doClick();
			Assert.assertEquals("vypnuté tlačítko nic nespustí", 1, z.volani.size());
		});
	}

	private static List<RadekMapy> cesko(final cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni mapa, final java.util.function.Function<cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva, cz.geokuk.plugins.mapy.sprava.StavVrstvy> vrstvy) {
		return RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, m.getId().equals("europe/czech-republic") ? mapa : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(), vrstvy);
	}

	/** Stahuje se jen vrstva nestažené mapy: řádek je v oddílu Pro zobrazené místo, má procenta a Zrušit. */
	@Test
	public void stahovaniVrstvyNestazeneMapyJeVidet() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(cesko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME, v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME,
					v.getId().startsWith("tourist") ? cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE : cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, v.getId().startsWith("tourist") ? 7_500_000 : 0, null)));
			Assert.assertEquals("řádek se stahovanou vrstvou je nahoře v oddílu pro zobrazené místo", 1, viditelnychLabelu(p, JMapyPopup.PRO_MISTO));
			Assert.assertFalse(tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
			Assert.assertTrue(labels(p).stream().anyMatch(l -> "30 %".equals(l.getText())));
		});
	}

	/** Stáhnout, Aktualizovat i Zkusit znovu u vrstvy zůstávají zapnuté i při stahování mapy; mapa a vrstvy se stahují vedle sebe. */
	@Test
	public void vrstvyNejdeStahovatPriJinemStahovani() throws Exception {
		final java.util.function.Function<cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva, cz.geokuk.plugins.mapy.sprava.StavVrstvy> vrstvy = v -> {
			if (v.getId().startsWith("tourist")) {
				return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.JINA_VERZE, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, v.getVelikost(), null);
			}
			if (v.getId().startsWith("contours")) {
				return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, null);
			}
			return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.CHYBA, 0, "chyba");
		};
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final List<RadekMapy> radky = cesko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, vrstvy);
			p.obnov(radky);
			final List<JButton> akce = tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ") || JMapyPopup.AKTUALIZOVAT.equals(b.getText()) || JMapyPopup.ZKUSIT_ZNOVU.equals(b.getText()))
					.collect(java.util.stream.Collectors.toList());
			Assert.assertEquals(3, akce.stream().filter(b -> !b.getText().equals(JMapyPopup.STAHNOUT)).count());
			Assert.assertTrue("bez jiného stahování jsou zapnuté", akce.stream().allMatch(JButton::isEnabled));
			final List<RadekMapy> s = new ArrayList<>(radky);
			s.add(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(MB).build());
			p.obnov(s);
			final List<JButton> pri = tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ") || JMapyPopup.AKTUALIZOVAT.equals(b.getText()) || JMapyPopup.ZKUSIT_ZNOVU.equals(b.getText()))
					.collect(java.util.stream.Collectors.toList());
			Assert.assertEquals(3, pri.size());
			for (final JButton b : pri) {
				Assert.assertTrue("vrstvy se stahují i vedle stahované mapy: " + b.getText(), b.isEnabled());
			}
		});
	}

	/** Stažená vrstva má tooltip s datem dat, rozpracovaná vrstva jde smazat. */
	@Test
	public void tooltipStazeneVrstvyARozpracovana() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			final JLabel stazena = labels(p).stream().filter(l -> "25 MB".equals(l.getText())).findFirst().get();
			Assert.assertEquals("Turistická vrstva: staženo, data z 13. 10. 2026", stazena.getToolTipText());
			p.obnov(cesko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME,
					cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, v.getId().startsWith("contours") ? 50_000_000 : 0, null)));
			Assert.assertEquals("jen rozpracovaná vrstevnice má ×", 1, tlacitkaSTextem(p, JMapyPopup.SMAZAT_VRSTVU).size());
		});
	}

	/** Změna volného místa přepočítá i buňky vrstev: ↓ se vypne, když se vrstva nevejde, a zase zapne. */
	@Test
	public void volneMistoPrepocitaBunkyVrstev() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(cesko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME,
					cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, null)));
			final java.util.function.Supplier<List<JButton>> stahnout = () -> tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ")).collect(java.util.stream.Collectors.toList());
			Assert.assertEquals(3, stahnout.get().size());
			Assert.assertTrue(stahnout.get().stream().allMatch(JButton::isEnabled));
			p.setVolneMisto(10 * MB);
			Assert.assertTrue("nic se nevejde", stahnout.get().stream().noneMatch(JButton::isEnabled));
			Assert.assertTrue(stahnout.get().get(0).getToolTipText().startsWith("Na disku je volných jen"));
			p.setVolneMisto(100_000L * MB);
			Assert.assertTrue("zase se vejde", stahnout.get().stream().allMatch(JButton::isEnabled));
		});
	}

	/** Katalogové mapy s danými id jsou stažené, ostatní ne. */
	private static List<RadekMapy> stazene(final String... ids) {
		final Set<String> mame = new HashSet<>(Arrays.asList(ids));
		return RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null);
	}

	/** Je vidět doopravdy: viditelná je i každá nadřazená komponenta. */
	private static boolean jeVidet(final Component c) {
		for (Component k = c; k != null; k = k.getParent()) {
			if (!k.isVisible()) {
				return false;
			}
		}
		return true;
	}

	private static long videtLabelu(final Container p, final String text) {
		return labels(p).stream().filter(l -> text.equals(l.getText()) && jeVidet(l)).count();
	}

	private static final String[] STAZENE_6 = { "europe/czech-republic", "europe/austria", "europe/germany/bayern", "europe/germany/sachsen", "europe/germany/berlin", "europe/united-kingdom/england" };

	/** Oddíl Ve složce je do pěti map rozbalený, od šesti sbalený; volba uživatele se uloží a platí i pro nový popup. */
	@Test
	public void slozkaSeSbalujeAPamatujeSi() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup malo = new JMapyPopup();
			malo.setOvladani(new Zaznam());
			malo.obnov(stazene("europe/czech-republic", "europe/austria"));
			Assert.assertEquals("dvě mapy: rozbaleno", 1, videtLabelu(malo, "Rakousko"));

			final JMapyPopup moc = new JMapyPopup();
			moc.setOvladani(new Zaznam());
			final Boolean[] ulozeno = { null };
			moc.ulozStavSlozky = b -> ulozeno[0] = b;
			moc.obnov(stazene(STAZENE_6));
			Assert.assertEquals("šest map: sbaleno", 0, videtLabelu(moc, "Rakousko"));
			Assert.assertEquals("nadpis oddílu zůstává", 1, videtLabelu(moc, JMapyPopup.VE_SLOZCE));
			moc.prepniSlozku(true);
			Assert.assertEquals(Boolean.TRUE, ulozeno[0]);
			Assert.assertEquals(1, videtLabelu(moc, "Rakousko"));
			moc.prepniSlozku(false);
			Assert.assertEquals(Boolean.FALSE, ulozeno[0]);
			Assert.assertEquals(0, videtLabelu(moc, "Rakousko"));

			final JMapyPopup novy = new JMapyPopup();
			novy.setOvladani(new Zaznam());
			novy.nactiStavSlozky = () -> Optional.of(false);
			novy.obnov(stazene("europe/czech-republic"));
			Assert.assertEquals("zapamatovaná volba má přednost před počtem map", 0, videtLabelu(novy, "Česko"));
			novy.nactiStavSlozky = () -> Optional.of(true);
			final JMapyPopup otevreny = new JMapyPopup();
			otevreny.setOvladani(new Zaznam());
			otevreny.nactiStavSlozky = () -> Optional.of(true);
			otevreny.obnov(stazene(STAZENE_6));
			Assert.assertEquals(1, videtLabelu(otevreny, "Rakousko"));
		});
	}

	/** Stahování, chyba nebo aktualizace ve sbalené složce nezmizí: oddíl i sbalená země se kvůli takovému řádku ukážou. */
	@Test
	public void stahovaniVeSbaleneSlozceJeVidet() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final String[] osm = { "europe/czech-republic", "europe/austria", "europe/germany/bayern", "europe/germany/sachsen", "europe/germany/berlin", "europe/united-kingdom/england", "europe/united-kingdom/wales", "europe/poland" };
			final Set<String> mame = new HashSet<>(Arrays.asList(osm));
			p.obnov(stazene(osm));
			Assert.assertEquals("osm map: sbaleno", 0, videtLabelu(p, "Rakousko"));

			// Stahuje se vrstva stažené mapy.
			final List<RadekMapy> sVrstvou = RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
					m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
							cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
					Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(),
					v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME,
							v.getId().startsWith("tourist") ? cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE : cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, v.getId().startsWith("tourist") ? 7_500_000 : 0, null));
			p.obnov(sVrstvou);
			Assert.assertEquals("řádek se stahovanou vrstvou je vidět i při sbalení", 1, videtLabelu(p, "Česko"));
			Assert.assertTrue(labels(p).stream().anyMatch(l -> "30 %".equals(l.getText()) && jeVidet(l)));
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.ZRUSIT).stream().anyMatch(JMapyPopupTest::jeVidet));

			// Aktualizuje se mapa v regionu sbalené země.
			final List<RadekMapy> region = RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
					m -> "europe/germany/sachsen".equals(m.getId())
							? cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NOVEJSI,
									cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, 30_000_000, 100_000_000, null)
							: cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
									cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
					Collections.emptyList(), null);
			final JMapyPopup q = new JMapyPopup();
			q.setOvladani(new Zaznam());
			q.obnov(region);
			Assert.assertEquals("země s aktualizovaným regionem je rozbalená", 1, videtLabelu(q, "Sasko"));
			Assert.assertEquals("země se rozbalí celá", 1, videtLabelu(q, "Bavorsko"));
			Assert.assertEquals("jiná sbalená země zůstala sbalená", 0, videtLabelu(q, "Anglie"));
		});
	}

	/** Chyba ve sbalené složce zůstane vidět; kontrola aktualizací složku ani zemi nerozbaluje. */
	@Test
	public void chybaVeSbaleneSlozceAKontrola() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final String[] osm = { "europe/czech-republic", "europe/austria", "europe/germany/bayern", "europe/germany/sachsen", "europe/germany/berlin", "europe/united-kingdom/england", "europe/united-kingdom/wales", "europe/poland" };
			final Set<String> mame = new HashSet<>(Arrays.asList(osm));
			final java.util.function.Function<cz.geokuk.plugins.mapy.sprava.StavMapy.Faze, List<RadekMapy>> radky = faze -> RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
					m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, faze == cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.CHYBA && ("europe/austria".equals(m.getId()) || "europe/germany/sachsen".equals(m.getId())) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.POSKOZENA
							: mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
							cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, "europe/austria".equals(m.getId()) || "europe/germany/sachsen".equals(m.getId()) ? faze : cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1,
							faze == cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.CHYBA ? "chyba" : null),
					Collections.emptyList(), null);
			final JMapyPopup chyba = new JMapyPopup();
			chyba.setOvladani(new Zaznam());
			chyba.obnov(radky.apply(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.CHYBA));
			Assert.assertEquals("poškozená mapa je vidět i ve sbalené složce", 1, videtLabelu(chyba, "Rakousko"));
			Assert.assertEquals("země s chybným regionem je rozbalená", 1, videtLabelu(chyba, "Bavorsko"));
			final JMapyPopup kontrola = new JMapyPopup();
			kontrola.setOvladani(new Zaznam());
			kontrola.obnov(radky.apply(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.KONTROLUJE));
			Assert.assertEquals("kontrola aktualizací složku nerozbaluje", 0, videtLabelu(kontrola, "Rakousko"));
		});
	}

	/** Hranice výchozího sbalení: pět map rozbaleno, šest sbaleno. */
	@Test
	public void hranicePetASestMap() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final String[] pet = { "europe/czech-republic", "europe/austria", "europe/poland", "europe/hungary", "europe/slovakia" };
			final JMapyPopup a = new JMapyPopup();
			a.setOvladani(new Zaznam());
			a.obnov(stazene(pet));
			Assert.assertEquals("pět map: rozbaleno", 1, videtLabelu(a, "Rakousko"));
			final JMapyPopup b = new JMapyPopup();
			b.setOvladani(new Zaznam());
			b.obnov(stazene(pet[0], pet[1], pet[2], pet[3], pet[4], "europe/italy/nord-est"));
			Assert.assertEquals("šest map: sbaleno", 0, videtLabelu(b, "Rakousko"));
		});
	}

	/** Stažené regiony jsou ve složce pod zemí se souhrnem a šipkou; rozbalí se klikem, země má v prvním sloupci kroužek jako ostatní. */
	@Test
	public void stazeneRegionyJsouPodZemi() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.nactiStavSlozky = () -> Optional.of(true);
			p.obnov(stazene(STAZENE_6));
			Assert.assertTrue("souhrn regionů u země", labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("3 regiony · ") && jeVidet(l)));
			Assert.assertTrue("souhrn regionů u Spojeného království", labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("1 region") && jeVidet(l)));
			Assert.assertEquals("regiony jsou sbalené", 0, videtLabelu(p, "Bavorsko"));
			p.prepniUzelSlozky("Německo");
			Assert.assertEquals(1, videtLabelu(p, "Bavorsko"));
			Assert.assertEquals(1, videtLabelu(p, "Berlín"));
			Assert.assertEquals("Anglie je pod Spojeným královstvím, to zůstalo sbalené", 0, videtLabelu(p, "Anglie"));
			p.prepniUzelSlozky("Německo");
			Assert.assertEquals(0, videtLabelu(p, "Bavorsko"));
		});
	}

	/** Země, kterou nejde stáhnout celou, má vlevo šedý kroužek jako ostatní země. */
	@Test
	public void zemeBezCelkuMaKrouzek() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(stazene());
			final JLabel zeme = labels(p).stream().filter(l -> "Spojené království".equals(l.getText())).findFirst().get();
			final java.awt.GridBagLayout lm = (java.awt.GridBagLayout) zeme.getParent().getLayout();
			final int radek = lm.getConstraints(zeme).gridy;
			final JLabel prvni = Arrays.stream(zeme.getParent().getComponents()).filter(c -> lm.getConstraints(c).gridy == radek && lm.getConstraints(c).gridx == 0).map(c -> (JLabel) c).findFirst().get();
			Assert.assertNotNull("kroužek vlevo", prvni.getIcon());
			final JLabel jina = labels(p).stream().filter(l -> "Albánie".equals(l.getText())).findFirst().get();
			final int radekJine = ((java.awt.GridBagLayout) jina.getParent().getLayout()).getConstraints(jina).gridy;
			final JLabel prvniJine = Arrays.stream(jina.getParent().getComponents()).filter(c -> ((java.awt.GridBagLayout) jina.getParent().getLayout()).getConstraints(c).gridy == radekJine
					&& ((java.awt.GridBagLayout) jina.getParent().getLayout()).getConstraints(c).gridx == 0).map(c -> (JLabel) c).findFirst().get();
			Assert.assertEquals("stejný kroužek jako u ostatních zemí", prvniJine.getIcon().getIconWidth(), prvni.getIcon().getIconWidth());
		});
	}

	/** Země celá i s regiony: popup to napíše a nabídne smazat regiony, nebo celou zemi. */
	@Test
	public void dvojiteMapy() throws Exception {
		final java.io.File slozka = tmp.newFolder("dvojite");
		final cz.geokuk.plugins.mapy.sprava.KatalogMap katalog = cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny();
		for (final String id : new String[] { "europe/germany", "europe/germany/bayern", "europe/germany/sachsen", "europe/czech-republic" }) {
			Assert.assertTrue(new java.io.File(slozka, katalog.podleId(id).get().getNazevSouboru()).createNewFile());
		}
		final List<cz.geokuk.plugins.mapy.sprava.PokrytiMap.DvojitaMapa> dvojite = cz.geokuk.plugins.mapy.sprava.PokrytiMap.dvojiteMapy(slozka, katalog);
		Assert.assertEquals(1, dvojite.size());
		final java.io.File slozkaUplne = tmp.newFolder("dvojite-uplne");
		Assert.assertTrue(new java.io.File(slozkaUplne, katalog.podleId("europe/germany").get().getNazevSouboru()).createNewFile());
		for (final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa r : katalog.regiony(katalog.podleId("europe/germany").get())) {
			Assert.assertTrue(new java.io.File(slozkaUplne, r.getNazevSouboru()).createNewFile());
		}
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(dalsiMapy());
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.SMAZAT_REGIONY).isEmpty());
			p.setDvojite(dvojite);
			Assert.assertTrue(labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("Německo je ve složce celá i s regiony (Bavorsko, Sasko)")));
			Assert.assertTrue("stažené jsou jen dva z regionů, celá země se smazat nenabídne", tlacitkaSTextem(p, JMapyPopup.SMAZAT_CELEK).isEmpty());
			tlacitkaSTextem(p, JMapyPopup.SMAZAT_REGIONY).get(0).doClick();
			final List<cz.geokuk.plugins.mapy.sprava.PokrytiMap.DvojitaMapa> uplne = cz.geokuk.plugins.mapy.sprava.PokrytiMap.dvojiteMapy(slozkaUplne, katalog);
			p.setDvojite(uplne);
			tlacitkaSTextem(p, JMapyPopup.SMAZAT_CELEK).get(0).doClick();
			Assert.assertEquals(Arrays.asList("smazDvojite europe/germany true", "smazDvojite europe/germany false"), z.volani);
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(MB).build()));
			Assert.assertTrue("během stahování se nemaže", tlacitkaSTextem(p, JMapyPopup.SMAZAT_CELEK).stream().noneMatch(JButton::isEnabled));
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.SMAZAT_REGIONY).stream().noneMatch(JButton::isEnabled));
			p.setDvojite(Collections.emptyList());
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.SMAZAT_REGIONY).isEmpty());
		});
	}

	/** Nová verze vrstvy se nabídne k aktualizaci, chyba má „Stáhnout znovu“; vrstvy se stahují až dvě najednou. */
	@Test
	public void stavyVrstev() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.JINA_VERZE, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertEquals(1, tlacitkaSTextem(p, JMapyPopup.AKTUALIZOVAT).size());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertFalse("během stahování vrstvy jde jen zrušit", tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
			Assert.assertTrue("procenta stahované vrstvy", labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().endsWith(" %")));
			Assert.assertTrue("mapa jde stahovat vedle vrstvy", tlacitkaSTextem(p, JMapyPopup.STAHNOUT).stream().anyMatch(JButton::isEnabled));
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
			Assert.assertEquals("evropská skupina bez celku (Spojené království) má v buňce Mapa „jen regiony“, Afrika a ostatní země ne", 1,
					viditelnychLabelu(p, JMapyPopup.JEN_REGIONY));
			Assert.assertEquals("záhlaví sloupce Mapa", 1, viditelnychLabelu(p, JMapyPopup.SLOUPEC_MAPA));
			p.prepniUzel("Německo");
			Assert.assertEquals(1, viditelnychLabelu(p, "Bavorsko"));
			long viditelnychRadku = labels(p).stream().filter(l -> l.isVisible() && l.getText() != null && !l.getText().isEmpty() && !JMapyPopup.NENI_VRSTVA.equals(l.getText())).count();
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

	private static java.awt.event.KeyEvent klavesa(final int id, final char c, final int modifikatory) {
		return new java.awt.event.KeyEvent(new JPanel(), id, 0, modifikatory, id == java.awt.event.KeyEvent.KEY_TYPED ? java.awt.event.KeyEvent.VK_UNDEFINED : java.awt.event.KeyEvent.VK_O, c);
	}

	/** Změna obsahu popupu (tik průběhu s delším popisem) sama vrací fokus píšícímu uživateli, bez zapojení v JBlokMap. */
	@Test
	public void zmenaObsahuVraciFokusPiscimuUzivateli() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final int[] zmen = { 0 };
			p.setPoZmeneObsahu(() -> zmen[0]++);
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.STAHUJE).stazeno(10 * MB).build()));
			fokusDoPole(p);
			final int pred = p.pozadavkuNaFokus;
			final String dlouhaChyba = "Stahování se přerušilo: velmi dlouhý text chyby, který rozšíří popis řádku nad původní šířku popupu a vynutí jeho znovuzobrazení";
			p.obnov(Collections.singletonList(katalog("at", RadekMapy.Stav.CHYBA).chyba(dlouhaChyba).build()));
			Assert.assertTrue("popup se znovu zobrazil", zmen[0] > 0);
			Assert.assertTrue("a pole žádá o fokus", p.pozadavkuNaFokus > pred);
		});
	}

	/** Písmena psaná v okně, kdy pole o fokus přišlo (popup se na okamžik zobrazí znovu), jdou do pole, ne do hlavního okna; zkratky s Ctrl, Enter a Esc ne. */
	@Test
	public void klavesyBehemZtratyFokusuJdouDoPole() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			p.vidiSe = true;
			Assert.assertFalse("nepíše se, klávesy jdou dál", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'o', 0)));
			fokusDoPole(p);
			Assert.assertFalse("pole má fokus a nic se nežádá, klávesy jdou dál", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'o', 0)));
			p.poZnovuZobrazeni();
			Assert.assertTrue(p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_PRESSED, 'r', 0)));
			Assert.assertEquals("stisk sám text nemění", "", p.getHledat().getText());
			Assert.assertTrue(p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'r', 0)));
			Assert.assertTrue(p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'o', 0)));
			Assert.assertEquals("ro", p.getHledat().getText());
			Assert.assertTrue(p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, '\b', 0)));
			Assert.assertEquals("r", p.getHledat().getText());
			Assert.assertFalse("Ctrl+znak jde dál", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_PRESSED, 'o', java.awt.event.InputEvent.CTRL_DOWN_MASK)));
			Assert.assertFalse("Esc jde dál (zavře popup)", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_PRESSED, '\u001b', 0)));
			Assert.assertFalse("Enter jde dál", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_PRESSED, '\n', 0)));
			Assert.assertFalse("Tab jde dál", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, '\t', 0)));
			Assert.assertFalse("Delete jde dál", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, '\u007f', 0)));
			fokusDoPole(p);
			Assert.assertFalse("po návratu fokusu se nekrade", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'x', 0)));
			p.poZnovuZobrazeni();
			p.vidiSe = false;
			Assert.assertFalse("skrytý popup klávesy nechává", p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'x', 0)));
			Assert.assertEquals("r", p.getHledat().getText());
		});
	}

	/** Je-li fokus v jiném okně (dialog, jiná aplikace), klávesy se do pole nekradou. */
	@Test
	public void klavesyVJinemOkneNejdouDoPole() throws Exception {
		Assume.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
		SwingUtilities.invokeAndWait(() -> {
			final JFrame okno = new JFrame();
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(dalsiMapy());
			okno.add(p);
			p.vidiSe = true;
			fokusDoPole(p);
			p.poZnovuZobrazeni();
			p.aktivniOkno = () -> null;
			Assert.assertFalse(p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'x', 0)));
			p.aktivniOkno = () -> okno;
			Assert.assertTrue(p.klavesy.dispatchKeyEvent(klavesa(java.awt.event.KeyEvent.KEY_TYPED, 'x', 0)));
			Assert.assertEquals("x", p.getHledat().getText());
		});
	}

	/** Dispatcher kláves je zaregistrovaný jen po dobu zobrazení popupu a nikdy dvakrát. */
	@Test
	public void dispatcherSeRegistrujeAOdregistruje() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final List<java.awt.KeyEventDispatcher> zaregistrovano = new ArrayList<>();
			final java.awt.KeyboardFocusManager m = new java.awt.DefaultKeyboardFocusManager() {
				@Override
				public void addKeyEventDispatcher(final java.awt.KeyEventDispatcher d) {
					zaregistrovano.add(d);
				}

				@Override
				public void removeKeyEventDispatcher(final java.awt.KeyEventDispatcher d) {
					zaregistrovano.remove(d);
				}
			};
			p.spravceFokusu = () -> m;
			p.zacniSledovatKlikyMimo();
			p.zacniSledovatKlikyMimo();
			Assert.assertEquals("po zobrazení je zaregistrovaný právě jednou", Collections.singletonList(p.klavesy), zaregistrovano);
			p.prestanSledovatKlikyMimo();
			Assert.assertTrue("po skrytí je odregistrovaný", zaregistrovano.isEmpty());
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
			Assert.assertEquals(Arrays.asList("stahni sk", "stahni at", "stahni pl", "zkontroluj vrstvy", "zkontroluj cz", "zkontroluj sk", "slozka"), z.volani);
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
				if (l.getToolTipText() != null && !JMapyPopup.VE_SLOZCE.equals(l.getText())) {
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

	/** Země s regiony má odkaz Stáhnout vše; klik předá ovládání zemi, její celek a klíče všech řádků. */
	@Test
	public void zemeSRegionyMaStahnoutVse() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			final List<JButton> vse = tlacitka(p).stream().filter(b -> JMapyPopup.STAHNOUT_VSE.equals(b.getText())).collect(java.util.stream.Collectors.toList());
			Assert.assertFalse("aspoň jedna země s regiony", vse.isEmpty());
			Assert.assertTrue(vse.get(0).isEnabled());
			vse.get(0).doClick();
			Assert.assertEquals("země bez regionů s vrstvami ke stažení má Stáhnout vše", Collections.singletonList("stahniVse Česko europe/czech-republic 1"), z.volani);
			vse.get(vse.size() - 1).doClick();
			Assert.assertTrue("země s regiony předá klíče všech řádků: " + z.volani, Integer.parseInt(z.volani.get(1).substring(z.volani.get(1).lastIndexOf(' ') + 1)) > 1);
		});
	}

	/** Začátek textu popisku v buňce názvu: odsazení a šipka rozbalení. */
	private static int zacatekTextu(final JLabel l) {
		return l.getInsets().left + (l.getIcon() == null ? 0 : l.getIcon().getIconWidth() + l.getIconTextGap());
	}

	/** Ve složce jsou země bez regionů odsazené o šipku jako ve Všech mapách, takže jejich názvy lícují se zeměmi se šipkou. */
	@Test
	public void zemeBezRegionuVeSlozceLicujeSeZemiSeSipkou() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.nactiStavSlozky = () -> Optional.of(true);
			p.obnov(stazene(STAZENE_6));
			final java.util.function.Function<String, JLabel> nazev = n -> labels(p).stream().filter(l -> n.equals(l.getText())).findFirst().get();
			final int sipka = zacatekTextu(nazev.apply("Německo"));
			Assert.assertNotNull("Německo ve složce má šipku", nazev.apply("Německo").getIcon());
			Assert.assertEquals("Česko ve složce", sipka, zacatekTextu(nazev.apply("Česko")));
			Assert.assertEquals("Rakousko ve složce", sipka, zacatekTextu(nazev.apply("Rakousko")));
			Assert.assertEquals("Albánie ve Všech mapách", sipka, zacatekTextu(nazev.apply("Albánie")));
		});
	}

	/** V plochém oddílu Pro zobrazené místo je u regionu za popisem země, i po změně stavu na místě; u celé země ne. */
	@Test
	public void regionProMistoMaVPopisuZemi() throws Exception {
		final java.util.function.Function<RadekMapy.Stav, List<RadekMapy>> radky = stav -> Arrays.asList(
				RadekMapy.katalogova("europe/netherlands/noord-brabant", "Severní Brabantsko", stav).skupina("Nizozemsko").velikost(142 * MB).stazeno(stav == RadekMapy.Stav.STAHUJE ? 71 * MB : 0)
						.proZobrazeneMisto(true).build(),
				RadekMapy.katalogova("europe/belgium", "Belgie", RadekMapy.Stav.NEMAME).velikost(300 * MB).proZobrazeneMisto(true).build());
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky.apply(RadekMapy.Stav.NEMAME));
			Assert.assertEquals("jen v oddílu Pro zobrazené místo", 1, labels(p).stream().filter(l -> l.getText() != null && l.getText().endsWith(" · Nizozemsko")).count());
			Assert.assertTrue(labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("~142 MB · Nizozemsko")));
			Assert.assertTrue("celá země bez země v popisu", labels(p).stream().noneMatch(l -> l.getText() != null && l.getText().startsWith("~300 MB ·")));
			final int pred = p.prestaveb;
			p.obnov(radky.apply(RadekMapy.Stav.STAHUJE));
			Assert.assertEquals("změna stavu na místě", pred, p.prestaveb);
			Assert.assertEquals(1, labels(p).stream().filter(l -> l.getText() != null && l.getText().startsWith("50 % z ") && l.getText().endsWith(" · Nizozemsko")).count());
		});
	}

	/** Celá země ve složce: ve Všech mapách má hlavička jejích regionů „celá země ve složce“ (ne „jen regiony“) a regiony jsou sbalené; po stažení se popup přestaví. */
	@Test
	public void celaZemeVeSlozceVeVsechMapach() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(stazene());
			Assert.assertEquals(0, viditelnychLabelu(p, JMapyPopup.CELA_VE_SLOZCE));
			final int pred = p.prestaveb;
			p.obnov(stazene("europe/netherlands"));
			Assert.assertTrue("po stažení se popup přestavěl", p.prestaveb > pred);
			Assert.assertEquals(1, viditelnychLabelu(p, JMapyPopup.CELA_VE_SLOZCE));
			Assert.assertEquals(IkonyMap.ZELENA, labels(p).stream().filter(l -> JMapyPopup.CELA_VE_SLOZCE.equals(l.getText())).findFirst().get().getForeground());
			Assert.assertEquals("jen regiony zůstanou u Spojeného království", 1, viditelnychLabelu(p, JMapyPopup.JEN_REGIONY));
			Assert.assertEquals("regiony sbalené", 0, viditelnychLabelu(p, "Drenthe"));
			p.prepniUzel("Nizozemsko");
			Assert.assertEquals(1, viditelnychLabelu(p, "Drenthe"));
		});
	}

	/** Stáhnout vše je jen u země, kde stáhne víc než jednu položku: ne u země, kde by stáhlo jen mapu nebo jedinou vrstvu; za stahování zůstane. */
	@Test
	public void stahnoutVseJenKdyzStahneViceNezJednuPolozku() throws Exception {
		final java.util.function.BiPredicate<JMapyPopup, String> maVse = (p, zeme) -> komponentyRadku(p, zeme).stream()
				.anyMatch(k -> k instanceof JButton && JMapyPopup.STAHNOUT_VSE.equals(((JButton) k).getText()));
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertTrue("Česko: tři vrstvy", maVse.test(p, "Česko"));
			Assert.assertTrue("Spojené království: tři regiony", maVse.test(p, "Spojené království"));
			Assert.assertFalse("Německo bez vrstev: celá země nahrazuje regiony, stáhne jen svou mapu", maVse.test(p, "Německo"));
			Assert.assertFalse("Albánie: jen mapa, totéž co Stáhnout", maVse.test(p, "Albánie"));
			Assert.assertFalse("Rakousko ve složce bez vrstev", maVse.test(p, "Rakousko"));
			p.obnov(ceskoARakouskoSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC,
					cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertFalse("Česko: chybí jen stínování", maVse.test(p, "Česko"));
			p.obnov(ceskoARakouskoSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE,
					cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertTrue("Česko: trasy se stahují, stínování chybí", maVse.test(p, "Česko"));
		});
	}

	/** Co se vejde, dostane celou výšku; zbytek se dělí mezi sekce, které se nevejdou, každá aspoň minimum. */
	@Test
	public void rozdeleniVysekSekci() {
		Assert.assertArrayEquals("vejde se: beze změny", new int[] { 100, 200, 0 }, JMapyPopup.rozdelVysky(new int[] { 100, 200, 0 }, 500, 80));
		Assert.assertArrayEquals("malá sekce celá, velká zbytek", new int[] { 120, 380 }, JMapyPopup.rozdelVysky(new int[] { 120, 2000 }, 500, 80));
		Assert.assertArrayEquals("dvě velké rovným dílem", new int[] { 200, 200, 100 }, JMapyPopup.rozdelVysky(new int[] { 2000, 3000, 100 }, 500, 80));
		Assert.assertArrayEquals("minimum, i když rozpočet nestačí", new int[] { 80, 80, 80 }, JMapyPopup.rozdelVysky(new int[] { 900, 900, 900 }, 150, 80));
		final JMapyPopup bezOkna = new JMapyPopup();
		Assert.assertTrue("rozpočet bez znalosti okna je rozumný", bezOkna.rozpocetSekci() >= 260 && bezOkna.rozpocetSekci() <= 520);
	}

	/** Mapa z oddílu Pro zobrazené místo je vidět i v seznamu všech map; popisy obou řádků se při stahování přepisují. */
	@Test
	public void mapaProMistoJeVidetISeznamuVsechMap() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final List<RadekMapy> radky = new ArrayList<>(dalsiMapy());
			radky.add(katalog("pm", RadekMapy.Stav.NEMAME).proZobrazeneMisto(true).build());
			p.obnov(radky);
			Assert.assertEquals("v obou oddílech", 2, labels(p).stream().filter(l -> "pm".equals(l.getText())).count());
		});
	}

	/** Dvě vrstvy téže země se stahují: počítají se obě, ostatní tlačítka vrstev jsou vypnutá a odečte se zbytek obou. */
	@Test
	public void dveBeziciVrstvyTehozRadku() throws Exception {
		final java.util.function.Function<cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva, cz.geokuk.plugins.mapy.sprava.StavVrstvy> vrstvy = v -> {
			if (v.getId().startsWith("tourist") || v.getId().startsWith("contours")) {
				return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, 1, null);
			}
			return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, null);
		};
		SwingUtilities.invokeAndWait(() -> {
			final List<RadekMapy> radky = cesko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, vrstvy);
			Assert.assertEquals("obě vrstvy řádku", 2, cesko(radky).bezicVrstvy().size());
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky);
			Assert.assertTrue("třetí vrstva nejde spustit", tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ")).allMatch(b -> !b.isEnabled()));
			Assert.assertTrue("zbývá dostahovat obě vrstvy", JMapyPopup.zbyvaStahnout(radky) > 0);
		});
	}

	/** Stažená celá země (ve složce) má Stáhnout vše se všemi řádky země včetně regionů z jiného oddílu; nahoře je Dostáhnout vrstvy. */
	@Test
	public void stazenaZemeMaStahnoutVseSeVsemiRegiony() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final cz.geokuk.plugins.mapy.sprava.KatalogMap katalog = cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny();
			final List<RadekMapy> radky = RadkyMap.sestav(katalog,
					m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, m.getId().equals("europe/germany") ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
							cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
					Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogNemecko(), v -> cz.geokuk.plugins.mapy.sprava.StavVrstvy.novy(v));
			final JMapyPopup p = new JMapyPopup();
			final Zaznam z = new Zaznam();
			p.setOvladani(z);
			p.prepniSlozku(true);
			p.obnov(radky);
			Assert.assertTrue("nahoře Dostáhnout vrstvy", tlacitka(p).stream().anyMatch(b -> b.getText().startsWith("Dostáhnout vrstvy ke staženým mapám (")));
			tlacitka(p).stream().filter(b -> JMapyPopup.STAHNOUT_VSE.equals(b.getText())).forEach(JButton::doClick);
			Assert.assertTrue(z.volani.toString(), z.volani.stream().anyMatch(v -> v.startsWith("stahniVse Německo europe/germany ") && Integer.parseInt(v.substring(v.lastIndexOf(' ') + 1)) > 2));
		});
	}

	/** Popup se vejde do dostupné výšky nad stavovým řádkem a ve vyšším okně je vyšší, nejvýš po strop; v nízkém má seznamy aspoň minimální rozpočet. */
	@Test
	public void vyskaPopupuPodleDostupneVysky() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final List<RadekMapy> mnoho = new ArrayList<>();
			for (int i = 0; i < 300; i++) {
				mnoho.add(katalog("m/" + i, RadekMapy.Stav.AKTUALNI).veSlozce(true).build());
			}
			final int[] vyskaPodleOkna = new int[3];
			final int[] dostupna = { 400, 600, 1000 };
			for (int k = 0; k < dostupna.length; k++) {
				final int d = dostupna[k];
				final JMapyPopup p = new JMapyPopup();
				p.setDostupnaVyska(() -> d);
				p.prepniSlozku(true);
				p.obnov(mnoho);
				vyskaPodleOkna[k] = p.getPreferredSize().height;
				Assert.assertTrue("vejde se do " + d + ": " + vyskaPodleOkna[k], vyskaPodleOkna[k] <= d);
			}
			Assert.assertTrue("vyšší okno, vyšší popup", vyskaPodleOkna[0] < vyskaPodleOkna[1] && vyskaPodleOkna[1] < vyskaPodleOkna[2]);
			Assert.assertTrue("ve vysokém okně nejvýš strop: " + vyskaPodleOkna[2], vyskaPodleOkna[2] < 1000 - JMapyPopup.MIN_ROZPOCET);
			final JMapyPopup nizke = new JMapyPopup();
			nizke.setDostupnaVyska(() -> 100);
			nizke.obnov(mnoho);
			Assert.assertTrue("v nízkém okně aspoň minimální rozpočet", nizke.rozpocetSekci() >= JMapyPopup.MIN_ROZPOCET);
		});
	}

	/** Za ověřování staženého souboru je v buňce „Ověřuji N %“ a u položky není Zrušit; při stahování jsou procenta a Zrušit. */
	@Test
	public void overovaniVrstvyBezZrusit() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertFalse("při stahování je Zrušit", tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.OVERUJE));
			Assert.assertTrue("za ověřování není Zrušit", tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
			Assert.assertTrue(labels(p).stream().anyMatch(l -> l.getText() != null && l.getText().startsWith("Ověřuji")));
		});
	}

	/** Řádek mapy při ověřování staženého souboru nemá Zrušit; při stahování ano. */
	@Test
	public void overovaniMapyBezZrusit() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(Collections.singletonList(katalog("a", RadekMapy.Stav.STAHUJE).stazeno(MB).build()));
			Assert.assertFalse("při stahování je Zrušit", tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
			p.obnov(Collections.singletonList(katalog("a", RadekMapy.Stav.STAHUJE).stazeno(MB).overuje(true).build()));
			Assert.assertTrue("při ověřování není Zrušit", tlacitkaSTextem(p, JMapyPopup.ZRUSIT).isEmpty());
		});
	}

	/** Souhrn skončené dávky s chybami je v popupu s tlačítky Zkusit znovu a Zavřít; po změně souhrnu se popup přestaví. */
	@Test
	public void souhrnDavkySChybami() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final List<String> souhrn = new ArrayList<>();
			final List<String> volani = new ArrayList<>();
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new OvladaniMap() {
				@Override
				public void otevriSlozku() {}

				@Override
				public List<String> souhrnDavky() {
					return souhrn;
				}

				@Override
				public void zkusZnovuDavku() {
					volani.add("znovu");
				}

				@Override
				public void zavriSouhrnDavky() {
					volani.add("zavri");
				}
			});
			final List<RadekMapy> radky = Collections.singletonList(katalog("a", RadekMapy.Stav.NEMAME).build());
			p.obnov(radky);
			Assert.assertTrue("bez souhrnu nic", tlacitkaSTextem(p, JMapyPopup.ZAVRIT).isEmpty());
			souhrn.addAll(Arrays.asList("Staženo 3, nepodařilo se 1:", "Česko – mapa – chyba"));
			p.obnov(radky);
			Assert.assertTrue(labels(p).stream().anyMatch(l -> "Staženo 3, nepodařilo se 1:".equals(l.getText())));
			tlacitkaSTextem(p, JMapyPopup.ZAVRIT).get(0).doClick();
			tlacitkaSTextem(p, JMapyPopup.ZKUSIT_ZNOVU).get(0).doClick();
			Assert.assertEquals(Arrays.asList("zavri", "znovu"), volani);
			souhrn.clear();
			p.obnov(radky);
			Assert.assertTrue("po zavření souhrn zmizí", tlacitkaSTextem(p, JMapyPopup.ZAVRIT).isEmpty());
		});
	}

	/** Stažené Česko a Rakousko; trasy a vrstevnice Česka v daném stavu, stínování ke stažení. */
	private static List<RadekMapy> ceskoARakouskoSVrstvami(final cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni trasy, final cz.geokuk.plugins.mapy.sprava.StavMapy.Faze fazeTras,
			final cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni vrstevnice, final cz.geokuk.plugins.mapy.sprava.StavMapy.Faze fazeVrstevnic) {
		final Set<String> mame = new HashSet<>(Arrays.asList("europe/czech-republic", "europe/austria"));
		return RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(), v -> {
					if (v.getId().startsWith("tourist")) {
						return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, trasy, fazeTras, fazeTras == cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE ? v.getVelikost() / 2 : 0, null);
					}
					if (v.getId().startsWith("contours")) {
						return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, vrstevnice, fazeVrstevnic, 0, null);
					}
					return cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, null);
				});
	}

	/** Všechny komponenty řádku mřížky, ve kterém je popisek s textem (i vnořené), v pořadí, jak jsou v panelu. */
	private static List<Component> komponentyRadku(final JMapyPopup p, final String nazev) {
		final JLabel l = labels(p).stream().filter(k -> nazev.equals(k.getText())).findFirst().get();
		final Container tabulka = l.getParent();
		final int y = ((java.awt.GridBagLayout) tabulka.getLayout()).getConstraints(l).gridy;
		final List<Component> v = new ArrayList<>();
		for (final Component k : tabulka.getComponents()) {
			if (((java.awt.GridBagLayout) tabulka.getLayout()).getConstraints(k).gridy == y) {
				pridejSPotomky(v, k);
			}
		}
		return v;
	}

	private static void pridejSPotomky(final List<Component> v, final Component k) {
		v.add(k);
		if (k instanceof Container) {
			for (final Component d : ((Container) k).getComponents()) {
				pridejSPotomky(v, d);
			}
		}
	}

	/**
	 * Za dávky vrstev skončí jedna vrstva a začne další: buňka další vrstvy hned ukáže procenta, popup se nepřestaví a řádek jiné mapy zůstane z týchž
	 * komponent.
	 */
	@Test
	public void zmenaStavuVrstvyNaMisteBezPrestavby() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(ceskoARakouskoSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE,
					cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertEquals(1, videtLabelu(p, "50 %"));
			Assert.assertEquals("vrstevnice ke stažení", 1, tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ") && b.getToolTipText().startsWith("Vrstevnice")).count());
			final int pred = p.prestaveb;
			final List<Component> rakousko = komponentyRadku(p, "Rakousko");
			final List<Component> cesko = komponentyRadku(p, "Česko").subList(0, 3);
			p.obnov(ceskoARakouskoSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC,
					cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertEquals("buňka vrstevnic hned ukazuje procenta", 1, videtLabelu(p, "0 %"));
			Assert.assertEquals(0, videtLabelu(p, "50 %"));
			Assert.assertEquals(0, tlacitka(p).stream().filter(b -> b.getText().startsWith("↓ ") && b.getToolTipText().startsWith("Vrstevnice")).count());
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			final List<Component> rakouskoPo = komponentyRadku(p, "Rakousko");
			Assert.assertEquals(rakousko.size(), rakouskoPo.size());
			for (int i = 0; i < rakousko.size(); i++) {
				Assert.assertSame("nezměněný řádek má tytéž komponenty", rakousko.get(i), rakouskoPo.get(i));
			}
			final List<Component> ceskoPo = komponentyRadku(p, "Česko").subList(0, 3);
			for (int i = 0; i < cesko.size(); i++) {
				Assert.assertSame("změněný řádek zůstal na místě", cesko.get(i), ceskoPo.get(i));
			}
		});
	}

	/** Osm stažených map; trasy Česka v dané fázi, ostatní vrstvy ke stažení. */
	private static List<RadekMapy> osmSTrasamiCeska(final cz.geokuk.plugins.mapy.sprava.StavMapy.Faze faze) {
		final Set<String> mame = new HashSet<>(Arrays.asList(STAZENE_8));
		return RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
						cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(),
				v -> cz.geokuk.plugins.mapy.sprava.VrstvyProGui.stav(v, cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME,
						v.getId().startsWith("tourist") ? faze : cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, null));
	}

	/** Stahování, které začalo při zavřeném popupu, se přepíše na místě a oddíl Ve složce rozbalí až při otevření. */
	@Test
	public void stahovaniPriZavrenemPopupuRozbaliPriOtevreni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final boolean[] otevreny = { true };
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.setZobrazeny(() -> otevreny[0]);
			p.obnov(osmSTrasamiCeska(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertEquals("osm map: sbaleno", 0, videtLabelu(p, "Rakousko"));
			otevreny[0] = false;
			p.popupZavren();
			final int pred = p.prestaveb;
			p.obnov(osmSTrasamiCeska(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertEquals("zavřený popup se nepřestavuje", pred, p.prestaveb);
			Assert.assertFalse(p.isZastaraly());
			Assert.assertEquals("dokud je zavřený, nerozbaluje se", 0, videtLabelu(p, "Rakousko"));
			otevreny[0] = true;
			p.pripravKZobrazeni();
			Assert.assertEquals("při otevření se oddíl kvůli stahování rozbalí", 1, videtLabelu(p, "Rakousko"));
		});
	}

	/** Volné místo na disku se promítne do tlačítek na místě, bez přestavby popupu. */
	@Test
	public void volneMistoBezPrestavby() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.NEMAME).build(), katalog("pl", RadekMapy.Stav.NEMAME).velikost(100 * MB).build()));
			final int pred = p.prestaveb;
			p.setVolneMisto(300 * MB);
			Assert.assertFalse(tlacitkaSTextem(p, JMapyPopup.STAHNOUT).get(0).isEnabled());
			Assert.assertTrue(tlacitkaSTextem(p, JMapyPopup.STAHNOUT).get(1).isEnabled());
			Assert.assertEquals(pred, p.prestaveb);
		});
	}

	/** Po přestavbě (třeba po kliku na Stáhnout) zůstane posuvník oddílu Ve složce i seznamu všech map na místě. */
	@Test(timeout = 30000)
	public void prestavbaZachovaPoziciPosuvniku() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JMapyPopup p = new JMapyPopup();
		final List<RadekMapy> radky = new ArrayList<>();
		for (int i = 0; i < 80; i++) {
			radky.add(katalog("f/" + i, RadekMapy.Stav.AKTUALNI).veSlozce(true).build());
		}
		for (int i = 0; i < 120; i++) {
			radky.add(katalog("d/" + i, RadekMapy.Stav.NEMAME).build());
		}
		final int[] pred = new int[2];
		try {
			SwingUtilities.invokeAndWait(() -> {
				p.setDostupnaVyska(() -> 700);
				p.setOvladani(new Zaznam());
				p.prepniSlozku(true);
				p.obnov(radky);
				okno.add(p);
				okno.pack();
				okno.setVisible(true);
				okno.validate();
				Assert.assertNotNull("Ve složce je posuvné", p.getSlozkaPosuvnik());
				p.getSlozkaPosuvnik().getVerticalScrollBar().setValue(300);
				p.seznam().getVerticalScrollBar().setValue(400);
				pred[0] = p.getSlozkaPosuvnik().getVerticalScrollBar().getValue();
				pred[1] = p.seznam().getVerticalScrollBar().getValue();
				Assert.assertTrue(pred[0] > 0 && pred[1] > 0);
				final List<RadekMapy> zmena = new ArrayList<>(radky);
				zmena.set(79, katalog("f/79", RadekMapy.Stav.STAHUJE).veSlozce(true).stazeno(MB).build());
				p.obnov(zmena);
			});
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertEquals("Ve složce zůstalo na místě", pred[0], p.getSlozkaPosuvnik().getVerticalScrollBar().getValue());
				Assert.assertEquals("seznam zůstal na místě", pred[1], p.seznam().getVerticalScrollBar().getValue());
			});
		} finally {
			SwingUtilities.invokeAndWait(okno::dispose);
		}
	}

	/** I přepnutí šipky složky (přestavba mimo obnov) nechá posuvník Ve složce na místě. */
	@Test(timeout = 30000)
	public void prepnutiSlozkyZachovaPosuvnik() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JMapyPopup p = new JMapyPopup();
		final List<RadekMapy> radky = new ArrayList<>();
		for (int i = 0; i < 80; i++) {
			radky.add(katalog("f/" + i, RadekMapy.Stav.AKTUALNI).veSlozce(true).build());
		}
		final int[] pred = new int[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				p.setDostupnaVyska(() -> 500);
				p.setOvladani(new Zaznam());
				p.prepniSlozku(true);
				p.obnov(radky);
				okno.add(p);
				okno.pack();
				okno.setVisible(true);
				okno.validate();
				p.getSlozkaPosuvnik().getVerticalScrollBar().setValue(250);
				pred[0] = p.getSlozkaPosuvnik().getVerticalScrollBar().getValue();
				Assert.assertTrue(pred[0] > 0);
				p.setStinovaniDostupne(true);
			});
			SwingUtilities.invokeAndWait(() -> Assert.assertEquals("po přestavbě mimo obnov zůstalo na místě", pred[0], p.getSlozkaPosuvnik().getVerticalScrollBar().getValue()));
		} finally {
			SwingUtilities.invokeAndWait(okno::dispose);
		}
	}

	/** Změna souhrnu dávky se projeví hned. */
	@Test
	public void zmenaSouhrnuSeProjeviHnedBehemDavky() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final List<String> souhrn = new ArrayList<>();
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new OvladaniMap() {
				@Override
				public void otevriSlozku() {}

				@Override
				public List<String> souhrnDavky() {
					return souhrn;
				}
			});
			final List<RadekMapy> radky = Collections.singletonList(katalog("a", RadekMapy.Stav.NEMAME).build());
			p.obnov(radky);
			souhrn.addAll(Arrays.asList("Staženo 1, nepodařilo se 1:", "x"));
			p.obnov(radky);
			Assert.assertFalse("souhrn je vidět hned", tlacitkaSTextem(p, JMapyPopup.ZAVRIT).isEmpty());
		});
	}

	/** Změny po čtení složky (stínování, volné místo, dvojité mapy, řádky) přestaví popup jednou, ne po každé. */
	@Test
	public void zmenyPoCteniSlozkyPrestaviPopupJednou() throws Exception {
		final java.io.File slozka = tmp.newFolder("dvojite-naraz");
		final cz.geokuk.plugins.mapy.sprava.KatalogMap k = cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny();
		for (final String id : new String[] { "europe/germany", "europe/germany/bayern" }) {
			Assert.assertTrue(new java.io.File(slozka, k.podleId(id).get().getNazevSouboru()).createNewFile());
		}
		final List<cz.geokuk.plugins.mapy.sprava.PokrytiMap.DvojitaMapa> dvojite = cz.geokuk.plugins.mapy.sprava.PokrytiMap.dvojiteMapy(slozka, k);
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(Collections.singletonList(katalog("a", RadekMapy.Stav.NEMAME).build()));
			final int pred = p.prestaveb;
			p.zmenyNaraz(() -> {
				p.setStinovaniDostupne(true);
				p.setVolneMisto(10 * MB);
				p.setDvojite(dvojite);
				p.obnov(Arrays.asList(katalog("a", RadekMapy.Stav.NEMAME).build(), katalog("b", RadekMapy.Stav.NEMAME).build()));
			});
			Assert.assertEquals("jedna přestavba", pred + 1, p.prestaveb);
			Assert.assertEquals(2, tlacitkaSTextem(p, JMapyPopup.STAHNOUT).size());
			Assert.assertTrue("volné místo se projevilo", tlacitkaSTextem(p, JMapyPopup.STAHNOUT).stream().noneMatch(JButton::isEnabled));
		});
	}

	/** Kontrola aktualizací popup nepřestavuje: řádek jen přepíše popis na „Zjišťuji na serveru…“ a zpět. */
	@Test
	public void kontrolaAktualizaciNeprestavujePopup() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.nactiStavSlozky = () -> Optional.of(true);
			final cz.geokuk.plugins.mapy.sprava.KatalogMap k = cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny();
			final java.util.function.Function<cz.geokuk.plugins.mapy.sprava.StavMapy.Faze, List<RadekMapy>> radky = faze -> {
				final List<RadekMapy> r = new ArrayList<>();
				for (final String id : new String[] { "europe/czech-republic", "europe/austria", "europe/poland" }) {
					final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa m = k.podleId(id).get();
					r.add(RadkyMap.radek(m, cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME,
							cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, "europe/austria".equals(id) ? faze : cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null), false));
				}
				return r;
			};
			p.obnov(radky.apply(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			final int pred = p.prestaveb;
			p.obnov(radky.apply(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.KONTROLUJE));
			Assert.assertEquals(1, videtLabelu(p, "Zjišťuji na serveru…"));
			Assert.assertEquals("tlačítko kontroly zůstává", 1, tlacitkaSTextem(p, JMapyPopup.ZKONTROLOVAT).size());
			p.obnov(radky.apply(cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC));
			Assert.assertEquals(0, videtLabelu(p, "Zjišťuji na serveru…"));
			Assert.assertEquals("kontrola popup nepřestavěla", pred, p.prestaveb);
		});
	}

	private static final String[] STAZENE_8 = { "europe/czech-republic", "europe/austria", "europe/germany/bayern", "europe/germany/sachsen", "europe/germany/berlin", "europe/united-kingdom/england", "europe/united-kingdom/wales", "europe/poland" };

	/** Stažené mapy, jedna z nich ve fázi {@code faze} (stahuje se nebo skončila). */
	private static List<RadekMapy> stazeneSFazi(final String id, final cz.geokuk.plugins.mapy.sprava.StavMapy.Faze faze, final String... ids) {
		final Set<String> mame = new HashSet<>(Arrays.asList(ids));
		return RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(),
				m -> cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, mame.contains(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
						id.equals(m.getId()) ? cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NOVEJSI : cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY,
						id.equals(m.getId()) ? faze : cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, id.equals(m.getId()) && faze == cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE ? 30_000_000 : 0,
						id.equals(m.getId()) && faze == cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE ? 100_000_000 : -1, null),
				Collections.emptyList(), null);
	}

	private static void klikNa(final JLabel l) {
		for (final java.awt.event.MouseListener m : l.getMouseListeners()) {
			m.mouseClicked(new java.awt.event.MouseEvent(l, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false));
		}
	}

	/** Šestá stažená mapa oddíl Ve složce nesbalí: výchozí stav podle počtu map platí jen při prvním zobrazení. */
	@Test
	public void sestaMapaNesbaliSlozku() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final Boolean[] ulozeno = { null };
			p.ulozStavSlozky = b -> ulozeno[0] = b;
			p.obnov(stazene("europe/czech-republic", "europe/austria", "europe/poland", "europe/hungary", "europe/slovakia"));
			Assert.assertEquals(1, videtLabelu(p, "Rakousko"));
			p.obnov(stazene("europe/czech-republic", "europe/austria", "europe/poland", "europe/hungary", "europe/slovakia", "europe/slovenia"));
			Assert.assertEquals("po šesté mapě zůstává rozbaleno", 1, videtLabelu(p, "Rakousko"));
			Assert.assertNull("nic se neuložilo", ulozeno[0]);
		});
	}

	/** Oddíl a země rozbalené kvůli stahování zůstanou rozbalené i po jeho konci, do uložené volby se to nepíše; po zavření popupu platí zase volba. */
	@Test
	public void samoRozbaleniPoStahovaniZustane() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final Boolean[] ulozeno = { null };
			p.ulozStavSlozky = b -> ulozeno[0] = b;
			p.obnov(stazene(STAZENE_8));
			Assert.assertEquals("osm map: sbaleno", 0, videtLabelu(p, "Rakousko"));
			p.obnov(stazeneSFazi("europe/germany/sachsen", cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, STAZENE_8));
			Assert.assertEquals(1, videtLabelu(p, "Rakousko"));
			Assert.assertEquals(1, videtLabelu(p, "Bavorsko"));
			p.obnov(stazeneSFazi("europe/germany/sachsen", cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.HOTOVO, STAZENE_8));
			Assert.assertEquals("po stažení se oddíl nesbalí", 1, videtLabelu(p, "Rakousko"));
			Assert.assertEquals("ani země", 1, videtLabelu(p, "Bavorsko"));
			Assert.assertNull("samo rozbalení se neukládá", ulozeno[0]);
			p.popupZavren();
			p.obnov(stazene(STAZENE_8));
			Assert.assertEquals("po zavření popupu zase podle volby", 0, videtLabelu(p, "Rakousko"));
		});
	}

	/** Klik na nadpis sám rozbaleného oddílu ho sbalí a uloží sbalení; během dalšího stahování téže mapy se znovu nerozbalí. */
	@Test
	public void klikNaSamRozbalenyOddilHoSbali() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final Boolean[] ulozeno = { null };
			p.ulozStavSlozky = b -> ulozeno[0] = b;
			final List<RadekMapy> stahuje = stazeneSFazi("europe/austria", cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, STAZENE_8);
			p.obnov(stahuje);
			Assert.assertEquals(1, videtLabelu(p, "Polsko"));
			klikNa(labels(p).stream().filter(l -> JMapyPopup.VE_SLOZCE.equals(l.getText())).findFirst().get());
			Assert.assertEquals(Boolean.FALSE, ulozeno[0]);
			Assert.assertEquals("sbaleno", 0, videtLabelu(p, "Polsko"));
			p.obnov(stahuje);
			Assert.assertEquals("stahování oddíl znovu nerozbalí", 0, videtLabelu(p, "Polsko"));
		});
	}

	/** Nový výsledek kontroly se ukáže i bez přestavby popupu. */
	@Test
	public void novyVysledekKontrolyBezPrestavby() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.nactiStavSlozky = () -> Optional.of(true);
			final Instant ted = Instant.now();
			final List<RadekMapy> prvni = Arrays.asList(katalog("cz", RadekMapy.Stav.AKTUALNI).zkontrolovano(ted.minusSeconds(2 * 3600)).build());
			final List<RadekMapy> druha = Arrays.asList(katalog("cz", RadekMapy.Stav.AKTUALNI).zkontrolovano(ted.minusSeconds(3600)).build());
			p.obnov(prvni);
			final int pred = p.prestaveb;
			final String predtim = p.vysledekKontroly(prvni);
			final String potom = p.vysledekKontroly(druha);
			Assert.assertNotEquals(predtim, potom);
			Assert.assertEquals(1, videtLabelu(p, predtim));
			p.obnov(druha);
			Assert.assertEquals("kontrola popup nepřestavěla", pred, p.prestaveb);
			Assert.assertEquals("je vidět nový čas kontroly", 1, videtLabelu(p, potom));
			Assert.assertEquals(0, videtLabelu(p, predtim));
		});
	}

	/** Během kontroly novější mapy není vidět Aktualizovat, po kontrole se vrátí. */
	@Test
	public void kontrolaNovejsiMapySkryjeAktualizovat() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.nactiStavSlozky = () -> Optional.of(true);
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.NOVEJSI).build()));
			Assert.assertEquals(1, tlacitkaSTextem(p, JMapyPopup.AKTUALIZOVAT).size());
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.NOVEJSI).kontroluje(true).build()));
			Assert.assertEquals("během kontroly", 0, tlacitkaSTextem(p, JMapyPopup.AKTUALIZOVAT).size());
			p.obnov(Arrays.asList(katalog("cz", RadekMapy.Stav.NOVEJSI).build()));
			Assert.assertEquals("po kontrole", 1, tlacitkaSTextem(p, JMapyPopup.AKTUALIZOVAT).size());
		});
	}

	/** Po zavření popupu rozbalí oddíl znovu i mapa, která ho rozbalila už předtím. */
	@Test
	public void poZavreniPopupuStahovaniZnovuRozbali() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			final List<RadekMapy> stahuje = stazeneSFazi("europe/austria", cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, STAZENE_8);
			p.obnov(stahuje);
			Assert.assertEquals(1, videtLabelu(p, "Polsko"));
			p.popupZavren();
			p.obnov(stazene(STAZENE_8));
			Assert.assertEquals("osm map: sbaleno", 0, videtLabelu(p, "Polsko"));
			p.obnov(stahuje);
			Assert.assertEquals("po zavření popupu stahování oddíl zase rozbalí", 1, videtLabelu(p, "Polsko"));
		});
	}

	/** Klik na zemi, kterou rozbalilo stahování, ji sbalí; další klik ji zase rozbalí. */
	@Test
	public void klikNaSamRozbalenouZemiJiSbali() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(stazeneSFazi("europe/germany/sachsen", cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, STAZENE_8));
			Assert.assertEquals(1, videtLabelu(p, "Bavorsko"));
			p.prepniUzelSlozky("Německo");
			Assert.assertEquals("sbaleno", 0, videtLabelu(p, "Bavorsko"));
			p.prepniUzelSlozky("Německo");
			Assert.assertEquals("zase rozbaleno", 1, videtLabelu(p, "Bavorsko"));
		});
	}

	/** Chyba stahování rozbalí oddíl, i když ho uživatel během stahování sbalil. */
	@Test
	public void chybaPoStahovaniZnovuRozbali() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE));
			Assert.assertEquals(1, videtLabelu(p, "Česko"));
			p.prepniSlozku(false);
			Assert.assertEquals("sbaleno", 0, videtLabelu(p, "Česko"));
			final List<RadekMapy> chyba = mapaSVrstvami(cz.geokuk.plugins.mapy.sprava.StavVrstvy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.CHYBA);
			Assert.assertEquals(RadekMapy.Stav.CHYBA, cesko(chyba).celkovyStav());
			p.obnov(chyba);
			Assert.assertEquals("chyba oddíl rozbalí", 1, videtLabelu(p, "Česko"));
		});
	}

	/** Katalog, ve kterém je Česko ve složce a Rakousko v daném stavu. */
	private static List<RadekMapy> ceskoARakousko(final cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni rakousko, final cz.geokuk.plugins.mapy.sprava.StavMapy.Faze faze, final long stazeno) {
		return RadkyMap.sestav(cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny(), m -> {
			if (m.getId().equals("europe/austria")) {
				return cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, rakousko, cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, faze, stazeno, 100 * MB, null);
			}
			return cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(m, m.getId().equals("europe/czech-republic") ? cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME : cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME,
					cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0, -1, null);
		}, Collections.emptyList(), null, cz.geokuk.plugins.mapy.sprava.VrstvyProGui.katalogCesko(), cz.geokuk.plugins.mapy.sprava.StavVrstvy::novy);
	}

	/** S jedinou mapou ve složce je popup nízký: seznam všech map ke stažení nevyplní celou dostupnou výšku. */
	@Test
	public void popupSJednouMapouJeNizky() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.setDostupnaVyska(() -> 1000);
			p.obnov(cesko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavVrstvy::novy));
			Assert.assertEquals(1, videtLabelu(p, "Česko"));
			Assert.assertTrue("výška " + p.getPreferredSize().height, p.getPreferredSize().height < 500);
		});
	}

	/** Popup nad blokem zapojený jako v JBlokMap. */
	private static cz.geokuk.util.gui.PopupNadBlokem popupNad(final JPanel kotva, final JMapyPopup p) {
		final cz.geokuk.util.gui.PopupNadBlokem popup = new cz.geokuk.util.gui.PopupNadBlokem(kotva, () -> {});
		p.setDostupnaVyska(() -> kotva.getLocationOnScreen().y - popup.horniHranice());
		p.setPoZmeneObsahu(() -> {
			if (popup.viditelny() == p) {
				popup.ukaz(p, kotva, true);
			}
		});
		return popup;
	}

	/**
	 * Stahování za otevřeného popupu (řádek přibude v Pro zobrazené místo, pak se přesune do složky a zmizí mu Stáhnout vše) ani smazání mapy velikost popupu
	 * nemění a popup se znovu nezobrazí; při novém otevření se velikost řídí obsahem.
	 */
	@Test(timeout = 30000)
	public void stahovaniNemeniVelikostPopupu() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JPanel kotva = new JPanel();
		final JMapyPopup p = new JMapyPopup();
		final cz.geokuk.util.gui.PopupNadBlokem popup = popupNad(kotva, p);
		try {
			SwingUtilities.invokeAndWait(() -> {
				okno.setBounds(0, 0, 1100, 800);
				okno.getContentPane().add(kotva, java.awt.BorderLayout.SOUTH);
				okno.setVisible(true);
			});
			SwingUtilities.invokeAndWait(() -> {
				p.setOvladani(new Zaznam());
				p.obnov(ceskoARakousko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0));
				popup.ukaz(p, kotva, false);
				final java.awt.Dimension pred = p.getSize();
				final int[] zobrazeni = { 0 };
				p.addHierarchyListener(e -> {
					if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
						zobrazeni[0]++;
					}
				});
				final int prestaveb = p.prestaveb;
				p.obnov(ceskoARakousko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, 30 * MB));
				Assert.assertEquals("Rakousko se stahuje v Pro zobrazené místo", 1, viditelnychLabelu(p, JMapyPopup.PRO_MISTO));
				Assert.assertEquals(pred, p.getPreferredSize());
				p.obnov(ceskoARakousko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.STAHUJE, 60 * MB));
				p.obnov(ceskoARakousko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0));
				Assert.assertEquals("Rakousko je ve složce", 0, viditelnychLabelu(p, JMapyPopup.PRO_MISTO));
				Assert.assertTrue("struktura se změnila", p.prestaveb >= prestaveb + 2);
				Assert.assertEquals(pred, p.getPreferredSize());
				Assert.assertEquals(pred, p.getSize());
				Assert.assertEquals("popup se znovu nezobrazil", 0, zobrazeni[0]);
				Assert.assertTrue(p.isShowing());
				popup.zavri();
				p.pripravKZobrazeni();
				popup.ukaz(p, kotva, false);
				Assert.assertTrue("po novém otevření se výška řídí obsahem: o řádek ve složce víc", p.getHeight() > pred.height);
				final java.awt.Dimension dva = p.getSize();
				p.obnov(ceskoARakousko(cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.NEMAME, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.NIC, 0));
				Assert.assertEquals("smazaná mapa ubyla, velikost zůstala", dva, p.getPreferredSize());
				Assert.assertEquals(dva, p.getSize());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				popup.zastav();
				okno.dispose();
			});
		}
	}

	/** Název řádku, který je v posuvníku nahoře. */
	private static String prvniViditelny(final JScrollPane sp) {
		final java.awt.Component pohled = sp.getViewport().getView();
		final int y = sp.getViewport().getViewPosition().y;
		JLabel prvni = null;
		int yPrvni = Integer.MAX_VALUE;
		for (final JLabel l : labels((Container) pohled)) {
			final int ly = SwingUtilities.convertPoint(l.getParent(), l.getLocation(), pohled).y;
			if (l.getText() != null && l.getText().contains("/") && ly + l.getHeight() > y && ly < yPrvni) {
				prvni = l;
				yPrvni = ly;
			}
		}
		return prvni == null ? null : prvni.getText();
	}

	/** Přibude-li nad viditelnými řádky oddílu řádek, posuvník po přestavbě drží tentýž řádek nahoře, ne stejný počet pixelů. */
	@Test(timeout = 30000)
	public void posuvnikDrziRadekPoPridaniRadkuNad() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JMapyPopup p = new JMapyPopup();
		final List<RadekMapy> radky = new ArrayList<>();
		for (int i = 0; i < 80; i++) {
			radky.add(katalog(String.format("f/%02d", i), RadekMapy.Stav.AKTUALNI).veSlozce(true).build());
		}
		final String[] pred = new String[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				p.setDostupnaVyska(() -> 500);
				p.setOvladani(new Zaznam());
				p.prepniSlozku(true);
				p.obnov(radky);
				okno.add(p);
				okno.pack();
				okno.setVisible(true);
				okno.validate();
				final JLabel f40 = labels(p).stream().filter(l -> "f/40".equals(l.getText())).findFirst().get();
				p.getSlozkaPosuvnik().getVerticalScrollBar().setValue(SwingUtilities.convertPoint(f40.getParent(), f40.getLocation(), p.getSlozkaPosuvnik().getViewport().getView()).y);
				pred[0] = prvniViditelny(p.getSlozkaPosuvnik());
				Assert.assertEquals("f/40", pred[0]);
				final List<RadekMapy> vic = new ArrayList<>(radky);
				vic.add(katalog("a/00", RadekMapy.Stav.AKTUALNI).veSlozce(true).build());
				p.obnov(vic);
			});
			SwingUtilities.invokeAndWait(() -> Assert.assertEquals("nahoře zůstal tentýž řádek", pred[0], prvniViditelny(p.getSlozkaPosuvnik())));
		} finally {
			SwingUtilities.invokeAndWait(okno::dispose);
		}
	}

	/** Po sbalení oddílu kliknutím se popup zmenší a horní okraj klesne pod myš; popup se tím nezavře, zavře se až po odjetí myši. */
	@Test(timeout = 30000)
	public void sbaleniOddiluKliknutimPopupNezavre() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final JFrame okno = new JFrame();
		final JPanel kotva = new JPanel();
		final JMapyPopup p = new JMapyPopup();
		final cz.geokuk.util.gui.PopupNadBlokem popup = popupNad(kotva, p);
		final List<RadekMapy> radky = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			radky.add(katalog("f/" + i, RadekMapy.Stav.AKTUALNI).veSlozce(true).build());
		}
		try {
			SwingUtilities.invokeAndWait(() -> {
				okno.setBounds(0, 0, 1100, 800);
				okno.getContentPane().add(kotva, java.awt.BorderLayout.SOUTH);
				okno.setVisible(true);
			});
			SwingUtilities.invokeAndWait(() -> {
				p.setOvladani(new Zaznam());
				p.prepniSlozku(true);
				p.obnov(radky);
				popup.ukaz(p, kotva, false);
				p.validate();
				final JLabel nadpis = labels(p).stream().filter(l -> JMapyPopup.VE_SLOZCE.equals(l.getText())).findFirst().get();
				final java.awt.Point klik = nadpis.getLocationOnScreen();
				klik.translate(5, nadpis.getHeight() / 2);
				final int vyska = p.getHeight();
				nadpis.dispatchEvent(new java.awt.event.MouseEvent(nadpis, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, nadpis.getHeight() / 2, 1, false));
				Assert.assertTrue("popup se zmenšil", p.isShowing() && p.getHeight() < vyska);
				Assert.assertFalse("myš je nad horním okrajem", new java.awt.Rectangle(p.getLocationOnScreen(), p.getSize()).contains(klik));
				popup.tik(klik, 0);
				popup.tik(klik, 1000);
				Assert.assertTrue("myš zůstala na místě kliku, popup je otevřený", p.isShowing());
				final java.awt.Point mimo = new java.awt.Point(okno.getLocationOnScreen().x + 5, okno.getLocationOnScreen().y + 5);
				popup.tik(mimo, 2000);
				popup.tik(mimo, 3000);
				Assert.assertFalse("myš odjela, popup se zavřel", p.isShowing());
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				popup.zastav();
				okno.dispose();
			});
		}
	}

	/** Tlačítko s textem v řádku mapy s názvem. */
	private static JButton tlacitkoRadku(final JMapyPopup p, final String nazev, final String text) {
		return komponentyRadku(p, nazev).stream().filter(k -> k instanceof JButton && text.equals(((JButton) k).getText())).map(k -> (JButton) k).findFirst().orElse(null);
	}

	/** Za stahování vrstvy začne stahování mapy ve složce: Stáhnout u ostatních map se na místě vypne. */
	@Test
	public void stahovaniMapyZaVrstvyVypneStahnoutNaMiste() throws Exception {
		final java.util.function.Function<RadekMapy.Stav, List<RadekMapy>> radky = stav -> {
			final List<RadekMapy> r = new ArrayList<>(cesko(StavMapy.Mistni.MAME,
					v -> VrstvyProGui.stav(v, StavVrstvy.Mistni.NEMAME, v.getId().startsWith("tourist") ? StavMapy.Faze.STAHUJE : StavMapy.Faze.NIC, 0, null)));
			r.add(katalog("x", stav).veSlozce(true).build());
			return r;
		};
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky.apply(RadekMapy.Stav.CHYBA));
			Assert.assertTrue("stahování vrstvy mapy nevypíná", tlacitkoRadku(p, "Rakousko", JMapyPopup.STAHNOUT).isEnabled());
			final int pred = p.prestaveb;
			p.obnov(radky.apply(RadekMapy.Stav.STAHUJE));
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			final JButton stahnout = tlacitkoRadku(p, "Rakousko", JMapyPopup.STAHNOUT);
			Assert.assertFalse(stahnout.isEnabled());
			Assert.assertEquals(JMapyPopup.STAHUJE_SE_JINA, stahnout.getToolTipText());
		});
	}

	/** Začne stahování vrstvy jiné mapy: smazání stažené vrstvy se na místě vypne. */
	@Test
	public void stahovaniVrstvyVypneMazaniNaMiste() throws Exception {
		final java.util.function.Function<StavMapy.Faze, List<RadekMapy>> radky = faze -> RadkyMap.sestav(KatalogMap.vestaveny(),
				m -> StavMapyProGui.stav(m, m.getId().equals("europe/germany") || m.getId().equals("europe/germany/bayern") ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME,
						StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, VrstvyProGui.katalogNemecko(), v -> v.getId().startsWith("tourist") ? VrstvyProGui.stav(v, StavVrstvy.Mistni.MAME, StavMapy.Faze.NIC, 0, null)
						: VrstvyProGui.stav(v, StavVrstvy.Mistni.NEMAME, v.getId().endsWith("bayern") ? faze : StavMapy.Faze.NIC, 0, null));
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky.apply(StavMapy.Faze.NIC));
			p.prepniUzelSlozky("Německo");
			final List<JButton> smazat = tlacitkaSTextem(p, JMapyPopup.SMAZAT_VRSTVU);
			Assert.assertEquals("trasy Německa", 1, smazat.size());
			Assert.assertTrue(smazat.get(0).isEnabled());
			final int pred = p.prestaveb;
			p.obnov(radky.apply(StavMapy.Faze.STAHUJE));
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			final List<JButton> po = tlacitkaSTextem(p, JMapyPopup.SMAZAT_VRSTVU);
			Assert.assertEquals(1, po.size());
			Assert.assertFalse(po.get(0).isEnabled());
			Assert.assertEquals(JMapyPopup.BEHEM_STAHOVANI_NEJDE_MAZAT, po.get(0).getToolTipText());
		});
	}

	/** Zrušené stahování mapy ve složce, po kterém se popup nepřestaví, už neukazuje „Ruší se…“. */
	@Test
	public void zruseniMapyVeSlozceNaMiste() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(Collections.singletonList(katalog("cz", RadekMapy.Stav.STAHUJE).veSlozce(true).stazeno(60 * MB).build()));
			tlacitko(p, JMapyPopup.ZRUSIT).doClick();
			Assert.assertTrue(texty(p).contains(JMapyPopup.RUSI_SE));
			final int pred = p.prestaveb;
			p.obnov(Collections.singletonList(katalog("cz", RadekMapy.Stav.NEMAME).veSlozce(true).rozpracovano(true).stazeno(60 * MB).build()));
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			Assert.assertFalse(texty(p).contains(JMapyPopup.RUSI_SE));
		});
	}

	/** Řádek, který na místě přejde do chyby, má červený popis; po novém stahování zase šedý. */
	@Test
	public void chybaNaMisteZcervenaPopis() throws Exception {
		final java.util.function.Function<RadekMapy.Stav, List<RadekMapy>> radky = stav -> Collections
				.singletonList(katalog("cz", stav).veSlozce(true).chyba(stav == RadekMapy.Stav.CHYBA ? "Spojení se přerušilo." : null).build());
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky.apply(RadekMapy.Stav.STAHUJE));
			final JLabel popis = (JLabel) komponentyRadku(p, "cz").get(2);
			Assert.assertNotEquals(IkonyMap.CERVENA, popis.getForeground());
			final int pred = p.prestaveb;
			p.obnov(radky.apply(RadekMapy.Stav.CHYBA));
			Assert.assertEquals(IkonyMap.CERVENA, popis.getForeground());
			p.obnov(radky.apply(RadekMapy.Stav.STAHUJE));
			Assert.assertNotEquals(IkonyMap.CERVENA, popis.getForeground());
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
		});
	}

	/** Když se při aktualizaci na místě změní preferovaná velikost popupu, ohlásí se změna obsahu; jinak ne. */
	@Test
	public void zmenaVelikostiNaMisteOhlasiZmenuObsahu() throws Exception {
		final java.util.function.Function<String, List<RadekMapy>> radky = chyba -> Collections.singletonList(katalog("cz", RadekMapy.Stav.CHYBA).veSlozce(true).chyba(chyba).build());
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky.apply("x"));
			// Popisek bez pevné šířky se s delším textem rozšíří.
			((JLabel) komponentyRadku(p, "cz").get(2)).setPreferredSize(null);
			final int[] zmen = new int[1];
			p.setPoZmeneObsahu(() -> zmen[0]++);
			final int pred = p.prestaveb;
			final java.awt.Dimension predtim = p.getPreferredSize();
			p.obnov(radky.apply("Server neodpovídá, spojení se přerušilo"));
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			Assert.assertNotEquals(predtim, p.getPreferredSize());
			Assert.assertEquals(1, zmen[0]);
			p.obnov(radky.apply("Server neodpovídá, spojení se přerušilo"));
			Assert.assertEquals("beze změny velikosti nic", 1, zmen[0]);
		});
	}

	/** Během kontroly aktualizací se odkaz Dostáhnout vrstvy nemění, aby neblikal. */
	@Test
	public void kontrolaNemeniOdkazDostahnoutVrstvy() throws Exception {
		final java.util.function.Function<StavMapy.Faze, List<RadekMapy>> radky = faze -> RadkyMap.sestav(KatalogMap.vestaveny(),
				m -> StavMapyProGui.stav(m, m.getId().equals("europe/czech-republic") ? StavMapy.Mistni.MAME : StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY,
						m.getId().equals("europe/czech-republic") ? faze : StavMapy.Faze.NIC, 0, -1, null),
				Collections.emptyList(), null, VrstvyProGui.katalogCesko(),
				v -> VrstvyProGui.stav(v, v.getId().startsWith("tourist") ? StavVrstvy.Mistni.JINA_VERZE : StavVrstvy.Mistni.MAME, StavMapy.Faze.NIC, 0, null));
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.obnov(radky.apply(StavMapy.Faze.NIC));
			final JButton odkaz = tlacitka(p).stream().filter(b -> b.getText().startsWith("Dostáhnout vrstvy")).findFirst().get();
			Assert.assertTrue(odkaz.isVisible());
			final String text = odkaz.getText();
			final int pred = p.prestaveb;
			p.obnov(radky.apply(StavMapy.Faze.KONTROLUJE));
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			Assert.assertTrue("během kontroly odkaz nezmizí", odkaz.isVisible());
			Assert.assertEquals(text, odkaz.getText());
		});
	}

	/** Za stahování vrstvy ukazuje tooltip mapy, která se nevejde, volné místo bez toho, co se ještě stáhne. */
	@Test
	public void tooltipMistaZaStahovaniVrstvy() throws Exception {
		final java.util.function.LongFunction<List<RadekMapy>> radky = stazeno -> cesko(StavMapy.Mistni.MAME,
				v -> VrstvyProGui.stav(v, StavVrstvy.Mistni.NEMAME, v.getId().startsWith("tourist") ? StavMapy.Faze.STAHUJE : StavMapy.Faze.NIC, v.getId().startsWith("tourist") ? stazeno : 0, null));
		SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.setOvladani(new Zaznam());
			p.setVolneMisto(100 * MB);
			p.obnov(radky.apply(5 * MB));
			final JButton stahnout = tlacitkoRadku(p, "Rakousko", JMapyPopup.STAHNOUT);
			Assert.assertFalse(stahnout.isEnabled());
			Assert.assertTrue(stahnout.getToolTipText(), stahnout.getToolTipText().contains("volných jen 80 MB"));
			final int pred = p.prestaveb;
			p.obnov(radky.apply(20 * MB));
			Assert.assertEquals("popup se nepřestavěl", pred, p.prestaveb);
			final String tooltip = tlacitkoRadku(p, "Rakousko", JMapyPopup.STAHNOUT).getToolTipText();
			Assert.assertTrue(tooltip, tooltip.contains("volných jen 95 MB"));
		});
	}
}
