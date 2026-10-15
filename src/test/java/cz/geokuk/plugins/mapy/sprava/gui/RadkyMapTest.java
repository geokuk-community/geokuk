package cz.geokuk.plugins.mapy.sprava.gui;

import static cz.geokuk.plugins.mapy.sprava.StavMapy.Faze.*;
import static cz.geokuk.plugins.mapy.sprava.StavMapy.Mistni.*;
import static cz.geokuk.plugins.mapy.sprava.StavMapy.Server.*;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.*;

/** Stav katalogové mapy z jádra se v popupu ukáže jednou ikonou, popisem a správnou akcí. */
public class RadkyMapTest {

	private static final KatalogMap KATALOG = KatalogMap.vestaveny();
	private static final KatalogovaMapa CESKO = KATALOG.podleId("europe/czech-republic").get();

	private static RadekMapy radek(final StavMapy.Mistni m, final StavMapy.Server s, final StavMapy.Faze f, final long stazeno, final long celkem, final String chyba) {
		return RadkyMap.radek(CESKO, StavMapyProGui.stav(CESKO, m, s, f, stazeno, celkem, chyba), false);
	}

	@Test
	public void ikonaPodleStavu() {
		Assert.assertEquals(RadekMapy.Stav.NEMAME, radek(NEMAME, NEZNAMY, NIC, 0, -1, null).getStav());
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, radek(MAME, NEZNAMY, NIC, 0, -1, null).getStav());
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, radek(MAME, StavMapy.Server.AKTUALNI, HOTOVO, 0, -1, null).getStav());
		Assert.assertEquals(RadekMapy.Stav.NOVEJSI, radek(MAME, StavMapy.Server.NOVEJSI, HOTOVO, 0, -1, null).getStav());
		Assert.assertEquals(RadekMapy.Stav.CHYBA, radek(POSKOZENA, NEZNAMY, NIC, 0, -1, null).getStav());
		Assert.assertEquals(RadekMapy.Stav.CHYBA, radek(NEMAME, NEDOSTUPNY, StavMapy.Faze.CHYBA, 0, -1, "Server je nedostupný.").getStav());
		Assert.assertEquals(RadekMapy.Stav.STAHUJE, radek(NEMAME, NEZNAMY, STAHUJE, 1, 100, null).getStav());
		Assert.assertEquals(RadekMapy.Stav.STAHUJE, radek(MAME, NEZNAMY, KONTROLUJE, 0, -1, null).getStav());
	}

	/** Nepovedená kontrola stažené mapy ji neoznačí za poškozenou: zůstane zelená, chyba je v popisu. */
	@Test
	public void chybaKontrolyStazeneMapy() {
		final RadekMapy r = radek(MAME, NEDOSTUPNY, StavMapy.Faze.CHYBA, 0, -1, "Server je nedostupný.");
		Assert.assertEquals(RadekMapy.Stav.AKTUALNI, r.getStav());
		Assert.assertTrue(r.isVeSlozce());
		Assert.assertEquals("Server je nedostupný.", r.popis());
	}

	@Test
	public void popisPrubehu() {
		Assert.assertEquals("25 % z 400 MB", radek(NEMAME, NEZNAMY, STAHUJE, 100_000_000L, 400_000_000L, null).popis());
		Assert.assertEquals("Ověřuji…", radek(NEMAME, NEZNAMY, OVERUJE, 400_000_000L, 400_000_000L, null).popis());
		Assert.assertEquals("Zjišťuji na serveru…", radek(MAME, NEZNAMY, KONTROLUJE, 0, -1, null).popis());
		final RadekMapy rozpracovana = radek(NEMAME, NEZNAMY, ZRUSENO, 100_000_000L, 400_000_000L, null);
		Assert.assertEquals(RadekMapy.Stav.NEMAME, rozpracovana.getStav());
		Assert.assertTrue(rozpracovana.popis(), rozpracovana.popis().startsWith("Staženo 100 MB z "));
		Assert.assertTrue(radek(POSKOZENA, NEZNAMY, NIC, 0, -1, null).isVeSlozce());
	}

	/** Kontrola na serveru se nedá zrušit a nebrání stažení jiné mapy. */
	@Test
	public void kontrolaNeniStahovani() throws Exception {
		javax.swing.SwingUtilities.invokeAndWait(() -> {
			final JMapyPopup p = new JMapyPopup();
			p.obnov(Arrays.asList(radek(MAME, NEZNAMY, KONTROLUJE, 0, -1, null), RadekMapy.katalogova("europe/austria", "Rakousko", RadekMapy.Stav.NEMAME).build()));
			final List<String> texty = new ArrayList<>();
			for (final javax.swing.JButton b : JMapyPopupTest.tlacitkaV(p)) {
				texty.add(b.getText() + (b.isEnabled() ? "" : " (zašedlé)"));
			}
			Assert.assertFalse(texty.toString(), texty.contains(JMapyPopup.ZRUSIT));
			Assert.assertTrue(texty.toString(), texty.contains(JMapyPopup.STAHNOUT));
		});
	}

	@Test
	public void proZobrazeneMistoPodlePokryti() {
		final List<RadekMapy> radky = RadkyMap.sestav(KATALOG, StavMapy::novy, Collections.singletonList(RadekMapy.vlastni("paws.map").build()), new Wgs(50.08, 14.42));
		final List<String> proMisto = new ArrayList<>();
		for (final RadekMapy r : radky) {
			if (r.proZobrazeneMisto) {
				proMisto.add(r.getNazev());
			}
		}
		Assert.assertEquals(Collections.singletonList("Česko"), proMisto);
		Assert.assertEquals(KATALOG.getMapy().size() + 1, radky.size());
		Assert.assertTrue(radky.get(radky.size() - 1).isVlastni());
		Assert.assertTrue("katalogové soubory ze čtení složky se nezdvojí",
				RadkyMap.sestav(KATALOG, StavMapy::novy, Collections.singletonList(RadekMapy.katalogova("europe/czech-republic", "Česko", RadekMapy.Stav.AKTUALNI).build()), null)
						.stream().noneMatch(r -> r.proZobrazeneMisto) && RadkyMap.sestav(KATALOG, StavMapy::novy, Collections.emptyList(), null).size() == KATALOG.getMapy().size());
	}
}
