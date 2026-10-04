package cz.geokuk.plugins.kesoid.detail;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.plugins.kesoid.EKesStatus;

/** Název keše z dat se v detailu zobrazí jako text při každém stavu keše. */
public class JKesoidDetailContainerHtmlTest {

	@Test
	public void nazevJeText() {
		for (final EKesStatus status : EKesStatus.values()) {
			final String html = JKesoidDetailContainer.formatuj("Keš <img src=http://sledovac/d> & <s>", status);
			Assert.assertTrue(status + ": " + html, html.startsWith("<html>"));
			Assert.assertTrue(status + ": " + html, html.contains("Keš &lt;img src=http://sledovac/d&gt; &amp; &lt;s&gt;"));
			Assert.assertFalse(status + ": " + html, html.contains("<img"));
		}
	}
}
