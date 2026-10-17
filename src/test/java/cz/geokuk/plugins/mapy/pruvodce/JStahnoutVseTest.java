package cz.geokuk.plugins.mapy.pruvodce;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import cz.geokuk.plugins.mapy.pruvodce.PlanPruvodce.Polozka;
import cz.geokuk.plugins.mapy.sprava.KatalogMap;
import cz.geokuk.plugins.mapy.sprava.MatriceMap;
import cz.geokuk.plugins.mapy.sprava.StavMapy;
import cz.geokuk.plugins.mapy.sprava.StavMapyProGui;
import cz.geokuk.plugins.mapy.sprava.StavVrstvy;
import cz.geokuk.plugins.mapy.sprava.VrstvyProGui;

/** Souhrn Stáhnout vše: nabídne části ke stažení, hlídá volné místo a po potvrzení pustí stahování bez přepnutí zobrazení. */
public class JStahnoutVseTest {

	private static final String CZ = "europe/czech-republic";

	private static final class Kontext implements PruvodceKontext {
		long volne = 84_000_000_000L;
		final List<String> stazene = new ArrayList<>();
		int prepnuti;

		@Override
		public KatalogMap getKatalog() {
			return KatalogMap.vestaveny();
		}

		@Override
		public List<MatriceMap.Radek> matice() {
			return MatriceMap.sestav(KatalogMap.vestaveny(), m -> StavMapyProGui.stav(m, StavMapy.Mistni.NEMAME, StavMapy.Server.NEZNAMY, StavMapy.Faze.NIC, 0, -1, null), VrstvyProGui.katalogCesko(),
					StavVrstvy::novy);
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
		public void zrusStahovani() {}

		@Override
		public void prepni(final PruvodceVolba volba) {
			prepnuti++;
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
		return tlacitka(c).stream().filter(b -> b.getText() != null && b.getText().startsWith(text)).findFirst().orElse(null);
	}

	@Test
	public void nabidneCastiAPoPotvrzeniStahujeBezPrepnuti() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final Kontext k = new Kontext();
		final JStahnoutVse[] okno = new JStahnoutVse[1];
		SwingUtilities.invokeAndWait(() -> okno[0] = new JStahnoutVse(null, k, "Česko", CZ, Collections.singletonList(CZ)));
		try {
			SwingUtilities.invokeAndWait(() -> {
				final JCheckBox mapa = (JCheckBox) tlacitko(okno[0], "Offline mapa");
				Assert.assertTrue(mapa.isSelected());
				Assert.assertTrue("stínování se předvybere s mapou", ((JCheckBox) tlacitko(okno[0], "Stínování")).isSelected() == ((JCheckBox) tlacitko(okno[0], "Stínování")).isEnabled());
				Assert.assertTrue(tlacitko(okno[0], "Stáhnout").isEnabled());
				tlacitko(okno[0], "Stáhnout").doClick();
			});
			Assert.assertFalse("něco se spustilo", k.stazene.isEmpty());
			Assert.assertEquals("zobrazení se nepřepíná", 0, k.prepnuti);
		} finally {
			SwingUtilities.invokeAndWait(() -> okno[0].dispose());
		}
	}

	@Test
	public void priNedostatkuMistaSeNestahne() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final Kontext k = new Kontext();
		k.volne = 1_000_000L;
		final JStahnoutVse[] okno = new JStahnoutVse[1];
		SwingUtilities.invokeAndWait(() -> okno[0] = new JStahnoutVse(null, k, "Česko", CZ, Collections.singletonList(CZ)));
		try {
			SwingUtilities.invokeAndWait(() -> Assert.assertFalse(tlacitko(okno[0], "Stáhnout").isEnabled()));
		} finally {
			SwingUtilities.invokeAndWait(() -> okno[0].dispose());
		}
	}
}
