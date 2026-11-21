package cz.geokuk.plugins.cesty.akce.usek;

import javax.swing.Action;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.CestyModel;
import cz.geokuk.plugins.cesty.data.*;

/** Tooltip přepnutí vzdušnosti popisuje čáry tak, jak se úseky kreslí. */
public class PrepniVzdusnostUsekuTest {

	private static PrepniVzdusnostUseku akce(final boolean vzdusny, final TypUseku typNovych) {
		final Updator updator = new Updator();
		final Cesta cesta = Cesta.create();
		updator.xadd(new Doc(), cesta);
		updator.pridejNaKonec(cesta, new Wgs(50.00, 14.00).toMou());
		updator.pridejNaKonec(cesta, new Wgs(50.01, 14.00).toMou());
		final Usek usek = cesta.getUseky().iterator().next();
		updator.setVzdusny(usek, vzdusny);
		final PrepniVzdusnostUseku akce = new PrepniVzdusnostUseku(usek, usek.getBvzad().getMou());
		akce.inject(new CestyModel() {
			@Override
			public TypUseku getTypNovychUseku() {
				return typNovych;
			}
		});
		akce.nastavJmenoAkce(usek, usek.getBvzad().getMou());
		return akce;
	}

	private static String tooltip(final boolean vzdusny) {
		return (String) akce(vzdusny, TypUseku.PO_CESTE).getValue(Action.SHORT_DESCRIPTION);
	}

	private static String nazev(final boolean vzdusny, final TypUseku typNovych) {
		return (String) akce(vzdusny, typNovych).getValue(Action.NAME);
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

	/** Název říká, na jaký typ se vzdušný úsek přepne: podle volby Nové úseky. */
	@Test
	public void nazevPodleTypuNovychUseku() {
		Assert.assertTrue(nazev(false, TypUseku.PO_CESTE).endsWith(" na vzdušný"));
		Assert.assertTrue(nazev(true, TypUseku.PO_CESTE).endsWith(" na úsek po cestě"));
		Assert.assertTrue(nazev(true, TypUseku.ROVNE).endsWith(" na rovný"));
	}
}
