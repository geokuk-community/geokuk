package cz.geokuk.plugins.cesty.akce.usek;

import javax.swing.Action;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;

/** Tooltip přepnutí vzdušnosti popisuje čáry tak, jak se úseky kreslí. */
public class PrepniVzdusnostUsekuTest {

	private static String tooltip(final boolean vzdusny) {
		final Updator updator = new Updator();
		final Cesta cesta = Cesta.create();
		updator.xadd(new Doc(), cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.00, 14.00).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.01, 14.00).toMou());
		final Usek usek = cesta.getUseky().iterator().next();
		updator.setVzdusny(usek, vzdusny);
		final PrepniVzdusnostUseku akce = new PrepniVzdusnostUseku(usek, usek.getBvzad().getMou());
		akce.nastavJmenoAkce(usek, usek.getBvzad().getMou());
		return (String) akce.getValue(Action.SHORT_DESCRIPTION);
	}

	@Test
	public void naVzdusnyTeckovane() {
		final String t = tooltip(false);
		Assert.assertTrue(t, t.contains("vzdušný (tečkovaná čára)"));
		Assert.assertTrue(t, t.contains("Segment cesty se v něm rozdělí"));
		Assert.assertFalse(t, t.contains("přerušovan") || t.contains("trasy"));
	}

	@Test
	public void zeVzdusnehoPoCestePlneRovnyCarkovane() {
		final String t = tooltip(true);
		Assert.assertTrue(t, t.contains("po cestě (plná čára)") && t.contains("rovný (čárkovaná čára)"));
		Assert.assertTrue(t, t.contains("segmenty cesty") && t.contains("délky cesty"));
		Assert.assertFalse(t, t.contains("běžný") || t.contains("trasy"));
	}
}
