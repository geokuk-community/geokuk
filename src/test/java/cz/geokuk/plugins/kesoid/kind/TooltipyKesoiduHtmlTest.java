package cz.geokuk.plugins.kesoid.kind;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.Wpt;
import cz.geokuk.plugins.kesoid.kind.cgp.CzechGeodeticPoint;
import cz.geokuk.plugins.kesoid.kind.munzee.Munzee;
import cz.geokuk.plugins.kesoid.kind.photo.Photo;
import cz.geokuk.plugins.kesoid.kind.simplewaypoint.SimpleWaypoint;
import cz.geokuk.plugins.kesoid.kind.waymark.Waymark;

/** Názvy, kódy a typy z dat se v bublině ostatních druhů kešoidů zobrazí jako text, ne jako značky. */
public class TooltipyKesoiduHtmlTest {

	private static Wpt wpt(final String name, final String nazev, final String sym) {
		final Wpt wpt = new Wpt();
		wpt.setName(name);
		wpt.setNazev(nazev);
		wpt.setSym(sym);
		return wpt;
	}

	private static void zkontroluj(final Kesoid kesoid) {
		kesoid.setIdentifier("ID<b>1");
		kesoid.addWpt(wpt("HL<s>1", "Hlavní <img src=\"http://sledovac/h\">", "Typ <u>h</u>"));
		final Wpt dalsi = wpt("<qDA<s>2", "Další <img src=\"http://sledovac/d\">", "Typ <u>d</u>");
		kesoid.addWpt(dalsi);
		for (final Wpt w : Arrays.asList(kesoid.getFirstWpt(), dalsi)) {
			final StringBuilder sb = new StringBuilder();
			kesoid.prispejDoTooltipu(sb, w);
			final String html = sb.toString();
			final String druh = kesoid.getClass().getSimpleName() + ": " + html;
			Assert.assertFalse(druh, html.contains("<img"));
			Assert.assertFalse(druh, html.contains("<s>"));
			Assert.assertFalse(druh, html.contains("<u>"));
			Assert.assertFalse(druh, html.contains("<q"));
			Assert.assertFalse(druh, html.contains("ID<b>"));
			Assert.assertTrue(druh, html.contains("&lt;"));
		}
	}

	@Test
	public void munzee() {
		zkontroluj(new Munzee());
	}

	@Test
	public void waymark() {
		zkontroluj(new Waymark());
	}

	@Test
	public void simpleWaypoint() {
		zkontroluj(new SimpleWaypoint());
	}

	@Test
	public void ceskyGeodetickyBod() {
		zkontroluj(new CzechGeodeticPoint());
	}

	@Test
	public void fotka() {
		zkontroluj(new Photo());
	}
}
