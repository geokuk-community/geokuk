package cz.geokuk.plugins.kesoid.kind.kes;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.Wpt;

/** Názvy z GPX se v HTML bublině zobrazí jako text, ne jako značky. */
public class KesTooltipTest {

	private static Wpt wpt(final String name, final String nazev) {
		final Wpt wpt = new Wpt();
		wpt.setName(name);
		wpt.setNazev(nazev);
		wpt.setSym("Geocache|Traditional Cache");
		return wpt;
	}

	@Test
	public void nazvySeEscapuji() {
		final Kes kes = new Kes();
		kes.setIdentifier("GC1<b>");
		kes.addWpt(wpt("GC12345", "Keš <img src=\"http://sledovac/x\">"));
		final Wpt finalka = wpt("FN<u>12345", "Finálka & <a href=x>");
		kes.addWpt(finalka);
		final StringBuilder sb = new StringBuilder();
		kes.prispejDoTooltipu(sb, finalka);
		final String html = sb.toString();
		Assert.assertFalse(html, html.contains("<img"));
		Assert.assertFalse(html, html.contains("<a "));
		Assert.assertFalse(html, html.contains("GC1<b>"));
		Assert.assertTrue(html, html.contains("(FN&lt;u&gt;12345)"));
		Assert.assertTrue(html, html.contains("Keš &lt;img src=&quot;http://sledovac/x&quot;&gt;"));
		Assert.assertTrue(html, html.contains("Finálka &amp; &lt;a href=x&gt;"));
	}
}
