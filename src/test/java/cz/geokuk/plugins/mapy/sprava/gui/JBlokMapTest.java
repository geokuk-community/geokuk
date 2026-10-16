package cz.geokuk.plugins.mapy.sprava.gui;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import javax.swing.*;

import org.junit.Assert;
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
}
