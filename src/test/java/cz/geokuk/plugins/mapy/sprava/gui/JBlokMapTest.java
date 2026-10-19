package cz.geokuk.plugins.mapy.sprava.gui;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Blok Mapy ve stavovém řádku: pevná šířka, čtení složky mimo EDT a při otevření popupu, otevření bez sítě. */
public class JBlokMapTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Procenta při stahování nesmí blok rozšířit, jinak poskočí Výlety a Zdroje. */
	@Test
	public void sirkaSeStahovanimNemeni() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JBlokMap blok = new JBlokMap();
			final int klid = blok.getPreferredSize().width;
			blok.obnov(Collections.singletonList(RadekMapy.katalogova("at", "Rakousko", RadekMapy.Stav.STAHUJE).velikost(1000).stazeno(1000).build()));
			Assert.assertEquals(JBlokMap.TEXT + " 100 %", blok.getText());
			Assert.assertEquals(klid, blok.getPreferredSize().width);
			blok.obnov(Collections.emptyList());
			Assert.assertEquals(klid, blok.getPreferredSize().width);
		});
	}

	/** Mapu nakopírovanou při online podkladu (bez hlídání složky) ukáže popup hned po otevření; čte se mimo EDT a bez sítě. */
	@Test
	public void otevreniPopupuPrecteSlozku() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		final List<Boolean> naEdt = Collections.synchronizedList(new ArrayList<>());
		final List<String> volani = Collections.synchronizedList(new ArrayList<>());
		final JBlokMap[] blok = new JBlokMap[1];
		SwingUtilities.invokeAndWait(() -> {
			blok[0] = new JBlokMap();
			blok[0].setZdrojSlozky(() -> slozka);
			blok[0].cteniSlozky = f -> {
				naEdt.add(SwingUtilities.isEventDispatchThread());
				return SouboryMap.nacti(f, cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny());
			};
			blok[0].setOvladani(new OvladaniMap() {
				@Override
				public void stahni(final String id) {
					volani.add("stahni");
				}

				@Override
				public void zkontroluj(final String id) {
					volani.add("zkontroluj");
				}

				@Override
				public void otevriSlozku() {
					volani.add("slozka");
				}
			});
		});
		Files.copy(new File("src/test/resources/offline-mapy/kukov.map").toPath(), new File(slozka, "kukov.map").toPath());
		SwingUtilities.invokeAndWait(() -> blok[0].ukaz());
		final long konec = System.currentTimeMillis() + 10_000;
		while (System.currentTimeMillis() < konec && blok[0].getObsah().getRadky().isEmpty()) {
			Thread.sleep(20);
			SwingUtilities.invokeAndWait(() -> {});
		}
		Assert.assertEquals(1, blok[0].getObsah().getRadky().size());
		Assert.assertEquals(Collections.singletonList(Boolean.FALSE), naEdt);
		Assert.assertTrue("otevření popupu nic nestahuje ani nekontroluje: " + volani, volani.isEmpty());
	}

	/** Sbalení oddílu Ve složce se ukládá do nastavení a dá se zase načíst; původní hodnotu test vrátí. */
	@Test
	public void stavSlozkyVNastaveni() {
		final java.util.Optional<Boolean> puvodni = JBlokMap.nactiStavSlozky();
		try {
			JBlokMap.ulozStavSlozky(false);
			Assert.assertEquals(java.util.Optional.of(false), JBlokMap.nactiStavSlozky());
			JBlokMap.ulozStavSlozky(true);
			Assert.assertEquals(java.util.Optional.of(true), JBlokMap.nactiStavSlozky());
		} finally {
			if (puvodni.isPresent()) {
				JBlokMap.ulozStavSlozky(puvodni.get());
			} else {
				cz.geokuk.framework.MyPreferences.current().node(JBlokMap.PREFE_NODE).putBoolean(JBlokMap.PREFE_SLOZKA_VOLBA, false);
			}
		}
	}

	private static List<RadekMapy> veSlozce(final String chybaPrvni) {
		final List<RadekMapy> r = new ArrayList<>();
		for (final String id : new String[] { "a", "b", "c" }) {
			r.add(RadekMapy.katalogova(id, id, RadekMapy.Stav.AKTUALNI).velikost(600_000_000L).veSlozce(true).chyba("a".equals(id) ? chybaPrvni : null).build());
		}
		return r;
	}

	/** Zavřený popup se při změnách nepřestavuje, jen se označí jako zastaralý; přestaví se jednou před otevřením. */
	@Test
	public void zavrenyPopupSeNeprestavuje() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JBlokMap blok = new JBlokMap();
			final JMapyPopup obsah = blok.getObsah();
			final int pred = obsah.prestaveb;
			blok.obnov(veSlozce(null));
			blok.obnov(veSlozce("chyba"));
			blok.obnov(veSlozce(null));
			obsah.zmenyNaraz(() -> obsah.setStinovaniDostupne(true));
			Assert.assertEquals("zavřený popup se nepřestavuje", pred, obsah.prestaveb);
			Assert.assertTrue(obsah.isZastaraly());
			obsah.pripravKZobrazeni();
			Assert.assertEquals("před otevřením jedna přestavba", pred + 1, obsah.prestaveb);
			Assert.assertFalse(obsah.isZastaraly());
			Assert.assertEquals(3, obsah.getRadky().size());
		});
	}

	/** Posun mapy, po kterém pokrývají střed tytéž mapy, řádky nepřestavuje; přechod do jiné země ano. */
	@Test
	public void posunMapyBezZmenyMistaNeprestavuje() throws Exception {
		final cz.geokuk.plugins.mapy.sprava.SpravaMapModel sprava = new cz.geokuk.plugins.mapy.sprava.SpravaMapModel();
		SwingUtilities.invokeAndWait(() -> {
			final JBlokMap blok = new JBlokMap();
			blok.inject(sprava);
			blok.setStred(new cz.geokuk.core.coordinates.Wgs(50.08, 14.42));
			final List<RadekMapy> praha = blok.getObsah().getRadky();
			Assert.assertTrue(praha.stream().anyMatch(r -> "europe/czech-republic".equals(r.id) && r.proZobrazeneMisto));
			blok.setStred(new cz.geokuk.core.coordinates.Wgs(50.09, 14.45));
			Assert.assertSame("posun v Česku řádky nesestavuje", praha, blok.getObsah().getRadky());
			blok.setStred(new cz.geokuk.core.coordinates.Wgs(48.2, 16.37));
			Assert.assertTrue(blok.getObsah().getRadky().stream().anyMatch(r -> "europe/austria".equals(r.id) && r.proZobrazeneMisto));
		});
	}

	/** Změna obsahu otevřeného popupu, která nemění jeho velikost, popup znovu nezobrazuje (neblikne). */
	@Test(timeout = 30000)
	public void otevrenyPopupSeBezZmenyVelikostiNezobraziZnovu() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final File slozka = tmp.newFolder("prazdna");
		final JFrame okno = new JFrame();
		final JBlokMap[] blok = new JBlokMap[1];
		final int[] zobrazeni = new int[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				blok[0] = new JBlokMap();
				blok[0].setZdrojSlozky(() -> slozka);
				blok[0].cteniSlozky = f -> veSlozce(null);
				final JPanel lista = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
				lista.add(blok[0]);
				okno.add(lista, java.awt.BorderLayout.SOUTH);
				okno.setBounds(0, 0, 1300, 850);
				okno.setVisible(true);
				blok[0].getObsah().addHierarchyListener(e -> {
					if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && blok[0].getObsah().isShowing()) {
						zobrazeni[0]++;
					}
				});
				blok[0].obnov(veSlozce(null));
			});
			final long konec = System.currentTimeMillis() + 10_000;
			while (System.currentTimeMillis() < konec) {
				final boolean[] videt = new boolean[1];
				SwingUtilities.invokeAndWait(() -> {
					if (blok[0].isShowing()) {
						blok[0].ukaz();
					}
					videt[0] = blok[0].getObsah().isShowing();
				});
				if (videt[0]) {
					break;
				}
				Thread.sleep(50);
			}
			Thread.sleep(300);
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertTrue(blok[0].getObsah().isShowing());
				zobrazeni[0] = 0;
				final int pred = blok[0].getObsah().prestaveb;
				blok[0].obnov(veSlozce("chyba"));
				blok[0].obnov(veSlozce(null));
				Assert.assertEquals("chyba řádku se přepsala na místě", pred, blok[0].getObsah().prestaveb);
			});
			SwingUtilities.invokeAndWait(() -> Assert.assertEquals("bez nového zobrazení", 0, zobrazeni[0]));
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				blok[0].zavri();
				okno.dispose();
			});
		}
	}

	/** Při kontrole aktualizací blok neukazuje procenta ani točítko stahování, ale že se kontroluje. */
	@Test
	public void kontrolaBezProcent() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JBlokMap blok = new JBlokMap();
			final int sirka = blok.getPreferredSize().width;
			final cz.geokuk.plugins.mapy.sprava.KatalogovaMapa cesko = cz.geokuk.plugins.mapy.sprava.KatalogMap.vestaveny().podleId("europe/czech-republic").get();
			blok.obnov(Collections.singletonList(RadkyMap.radek(cesko, cz.geokuk.plugins.mapy.sprava.StavMapyProGui.stav(cesko, cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.MAME,
					cz.geokuk.plugins.mapy.sprava.StavMapy.Server.NEZNAMY, cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.KONTROLUJE, 0, -1, null), false)));
			Assert.assertEquals(JBlokMap.KONTROLUJE, blok.getText());
			Assert.assertEquals(JBlokMap.KONTROLUJE_TIP, blok.getToolTipText());
			Assert.assertNotSame("bez ikony stahování", IkonyMap.pro(RadekMapy.Stav.STAHUJE), blok.getIcon());
			Assert.assertEquals("šířka bloku se nemění", sirka, blok.getPreferredSize().width);
		});
	}

	/** Tooltip bloku by překryl Zdroje; při otevírání popupu (najetí myší) se neukazuje, po odjetí zase ano. */
	@Test
	public void priOtevireniPopupuBezTooltipu() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final JBlokMap blok = new JBlokMap();
			blok.obnov(Collections.singletonList(RadekMapy.katalogova("at", "Rakousko", RadekMapy.Stav.STAHUJE).velikost(1000).stazeno(500).build()));
			final java.awt.event.MouseEvent mys = new java.awt.event.MouseEvent(blok, java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false);
			Assert.assertEquals("Stahuje se mapa Rakousko.", blok.getToolTipText(mys));
			blok.dispatchEvent(new java.awt.event.MouseEvent(blok, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 5, 5, 0, false));
			Assert.assertNull(blok.getToolTipText(mys));
			blok.dispatchEvent(new java.awt.event.MouseEvent(blok, java.awt.event.MouseEvent.MOUSE_EXITED, 0, 0, -5, -5, 0, false));
			Assert.assertEquals("Stahuje se mapa Rakousko.", blok.getToolTipText(mys));
			blok.removeNotify(); // zastaví hlídání myši, bez displeje nejde zjistit její polohu
		});
	}

	/** Popup se otevře za 150 ms, tooltip by přišel později: s otevřeným popupem se neukazuje. */
	@Test(timeout = 30000)
	public void sOtevrenymPopupemBezTooltipu() throws Exception {
		Assume.assumeFalse("bez displeje", java.awt.GraphicsEnvironment.isHeadless());
		final File slozka = tmp.newFolder("prazdna");
		final JFrame okno = new JFrame();
		final JBlokMap[] blok = new JBlokMap[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				blok[0] = new JBlokMap();
				blok[0].setZdrojSlozky(() -> slozka);
				blok[0].cteniSlozky = f -> veSlozce(null);
				final JPanel lista = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
				lista.add(blok[0]);
				okno.add(lista, java.awt.BorderLayout.SOUTH);
				okno.setBounds(0, 0, 1300, 850);
				okno.setVisible(true);
			});
			final java.awt.event.MouseEvent[] mys = new java.awt.event.MouseEvent[1];
			final long konec = System.currentTimeMillis() + 10_000;
			boolean videt = false;
			while (!videt && System.currentTimeMillis() < konec) {
				final boolean[] v = new boolean[1];
				SwingUtilities.invokeAndWait(() -> {
					if (blok[0].isShowing()) {
						mys[0] = new java.awt.event.MouseEvent(blok[0], java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0, 5, 5, 0, false);
						Assert.assertNotNull("zavřený popup: tooltip je", blok[0].getToolTipText(mys[0]));
						blok[0].ukaz();
					}
					v[0] = blok[0].getObsah().isShowing();
				});
				videt = v[0];
				Thread.sleep(50);
			}
			Assert.assertTrue(videt);
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertNull(blok[0].getToolTipText(mys[0]));
				blok[0].zavri();
				Assert.assertNotNull(blok[0].getToolTipText(mys[0]));
			});
		} finally {
			SwingUtilities.invokeAndWait(() -> {
				blok[0].zavri();
				okno.dispose();
			});
		}
	}
}
