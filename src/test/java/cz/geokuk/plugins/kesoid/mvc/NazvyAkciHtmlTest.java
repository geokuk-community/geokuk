package cz.geokuk.plugins.kesoid.mvc;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.genetika.Genom;

/** Texty z GPX v názvech položek nabídky se zobrazí jako text, ne jako značky. */
public class NazvyAkciHtmlTest {

	@Test
	public void identifikatorSeEscapuje() {
		final String nazev = KesoidCodeToClipboard.nazev("GC1<img src=http://sledovac/x>");
		Assert.assertFalse(nazev, nazev.contains("<img"));
		Assert.assertTrue(nazev, nazev.contains("GC1&lt;img src=http://sledovac/x&gt;"));
	}

	@Test
	public void symbolZGpxSeEscapuje() {
		final Alela alela = new Genom().symGen.alela("Sym <img src=http://sledovac/x>");
		final String jmeno = SwitchKesoidUrciteAlelyAction.jmeno(alela, 3);
		Assert.assertFalse(jmeno, jmeno.contains("<img"));
		Assert.assertTrue(jmeno, jmeno.contains("<b>Sym &lt;img src=http://sledovac/x&gt;</b>"));
		Assert.assertTrue(jmeno, jmeno.endsWith("<i>(3)</i>"));
	}
}
