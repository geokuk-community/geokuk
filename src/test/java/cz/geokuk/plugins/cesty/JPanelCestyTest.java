package cz.geokuk.plugins.cesty;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.data.*;

/** Panel cesty: údaje o vybrané cestě a kdy je vidět. */
public class JPanelCestyTest {

	private static final Mou A = new Wgs(50.00, 14.00).toMou();
	private static final Mou B = new Wgs(50.01, 14.00).toMou();
	private static final Mou C = new Wgs(50.01, 14.02).toMou();

	private final Updator updator = new Updator();

	private Cesta cesta(final Mou... body) {
		final Cesta cesta = Cesta.create();
		updator.xadd(new Doc(), cesta);
		for (final Mou mou : body) {
			updator.pridejNaKonec(cesta, mou);
		}
		return cesta;
	}

	private static JPanelCesty panel(final Cesta curta) {
		final JPanelCesty panel = new JPanelCesty();
		panel.inject(new CestyModel() {
			@Override
			public Cesta getCurta() {
				return curta;
			}

			@Override
			protected cz.geokuk.framework.MyPreferences currPrefe() {
				return cz.geokuk.framework.MyPreferences.current().node("test-panel-cesty");
			}
		});
		panel.obnov();
		return panel;
	}

	@Test
	public void udajeOVybraneCeste() {
		final Cesta cesta = cesta(A, B, C);
		final JPanelCesty panel = panel(cesta);
		Assert.assertTrue(panel.isVisible());
		Assert.assertEquals("Cesta", panel.jNazev.getText());
		Assert.assertEquals("Délka " + JPanelCesty.km(cesta.dalka()), panel.jDelka.getText());
		Assert.assertTrue(panel.jDelka.getText(), panel.jDelka.getText().matches("Délka \\d+,\\d km"));
		Assert.assertEquals("Body: 3", panel.jBody.getText());
		Assert.assertFalse(panel.jVzdusne.isVisible());
	}

	@Test
	public void vzdusnyUsek() {
		final Cesta cesta = cesta(A, B, C);
		final Iterator<Usek> useky = cesta.getUseky().iterator();
		updator.setVzdusny(useky.next(), true);
		final JPanelCesty panel = panel(cesta);
		Assert.assertTrue(panel.jVzdusne.isVisible());
		Assert.assertEquals("Vzdušné úseky: 1 (do délky se nepočítají)", panel.jVzdusne.getText());
	}

	@Test
	public void bezVybraneCestyNeniVidet() {
		Assert.assertFalse(panel(null).isVisible());
		Assert.assertFalse(panel(Cesta.create()).isVisible());
	}

	@Test
	public void vypnutyPanelNeniVidet() {
		final JPanelCesty panel = panel(cesta(A, B));
		panel.onEvent(new PanelCestyEvent(false));
		Assert.assertFalse(panel.isVisible());
		panel.onEvent(new PanelCestyEvent(true));
		Assert.assertTrue(panel.isVisible());
	}

	@Test
	public void tvaryKesi() {
		Assert.assertEquals("keš nebo waypoint", JPanelCesty.kesi(1));
		Assert.assertEquals("keše a waypointy", JPanelCesty.kesi(3));
		Assert.assertEquals("keší a waypointů", JPanelCesty.kesi(5));
	}
}
