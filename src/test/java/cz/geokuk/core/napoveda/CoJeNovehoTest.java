package cz.geokuk.core.napoveda;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.junit.Assert;
import org.junit.Test;

public class CoJeNovehoTest {

	private static List<String> verze(final List<CoJeNoveho.Vydani> v) {
		return v.stream().map(CoJeNoveho.Vydani::getVerze).collect(Collectors.toList());
	}

	private static List<CoJeNoveho.Vydani> vydani(final String... verze) {
		final List<CoJeNoveho.Vydani> v = new ArrayList<>();
		for (final String s : verze) {
			v.add(new CoJeNoveho.Vydani(s, "- " + s));
		}
		return v;
	}

	@Test
	public void changelogJeVProgramu() {
		final List<CoJeNoveho.Vydani> v = CoJeNoveho.zProgramu();
		Assert.assertTrue("CHANGELOG.md v jaru", v.size() > 5);
		Assert.assertTrue(v.get(0).getVerze().matches("\\d+\\.\\d+\\.\\d+.*"));
		Assert.assertTrue(v.get(0).getMarkdown().contains("- "));
		Assert.assertFalse("nadpis souboru není verze", verze(v).contains("Změny"));
	}

	@Test
	public void oddilyChangelogu() {
		final List<CoJeNoveho.Vydani> v = CoJeNoveho.zChangelogu("# Změny\n\n## 6.5.0\n\n### Změny\n- a\n  pokračování\n\n## 6.4.0\n- b\n");
		Assert.assertEquals(Arrays.asList("6.5.0", "6.4.0"), verze(v));
		Assert.assertEquals("### Změny\n- a\n  pokračování", v.get(0).getMarkdown());
	}

	@Test
	public void markdownNaHtml() {
		final String html = CoJeNoveho.naHtml("### Změny\n- První řádek\n  pokračuje `kód` a **tučně**.\n- Odkaz [web](https://example.org/a?b=1&c=2)\n\n### Opravy\n- Holý https://osm.paws.cz.\n\nOdstavec <script>x</script>");
		Assert.assertEquals("<h3>Změny</h3><ul><li>První řádek pokračuje <code>kód</code> a <b>tučně</b>.</li>"
				+ "<li>Odkaz <a href=\"https://example.org/a?b=1&amp;c=2\">web</a></li></ul>"
				+ "<h3>Opravy</h3><ul><li>Holý <a href=\"https://osm.paws.cz\">https://osm.paws.cz</a>.</li></ul>"
				+ "<p>Odstavec &lt;script&gt;x&lt;/script&gt;</p>", html);
	}

	@Test
	public void verzeMeziPouzivanouANovou() {
		final List<CoJeNoveho.Vydani> v = vydani("6.5.0", "6.5.0-beta.3", "6.5.0-beta.2", "6.4.1", "6.4.0", "6.3.0");
		Assert.assertEquals(Arrays.asList("6.5.0", "6.4.1", "6.4.0"), verze(CoJeNoveho.mezi(v, "6.3.0", "6.5.0", false)));
		Assert.assertEquals("beta kanál: z řady jen nejnovější", Arrays.asList("6.5.0-beta.3", "6.4.1"), verze(CoJeNoveho.mezi(v, "6.4.0", "6.5.0-beta.3", true)));
		Assert.assertEquals("nabízená testovací verze i bez beta kanálu", Arrays.asList("6.5.0-beta.3", "6.4.1"),
				verze(CoJeNoveho.mezi(v, "6.4.0", "6.5.0-beta.3", false)));
		Assert.assertEquals(Collections.singletonList("6.5.0-beta.3"), verze(CoJeNoveho.mezi(v, "6.5.0-beta.1", "6.5.0-beta.3", true)));
		Assert.assertEquals(Collections.singletonList("6.5.0"), verze(CoJeNoveho.mezi(v, "6.5.0-beta.2", "6.5.0", false)));
	}

	@Test
	public void vydaniZGitHubu() throws IOException {
		final String json = "[{\"tag_name\":\"v6.5.0-beta.2\",\"prerelease\":true,\"draft\":false,\"body\":\"### Změny\\r\\n- a \\u00e9\"},"
				+ "{\"tag_name\":\"v6.6.0\",\"draft\":true,\"body\":\"x\"},{\"tag_name\":\"v6.4.0\",\"body\":null}]";
		final List<CoJeNoveho.Vydani> v = CoJeNoveho.zGitHubu(json);
		Assert.assertEquals(Arrays.asList("6.5.0-beta.2", "6.4.0"), verze(v));
		Assert.assertEquals("### Změny\r\n- a é", v.get(0).getMarkdown());
		Assert.assertEquals("", v.get(1).getMarkdown());
	}

	@Test(expected = IOException.class)
	public void chybnaOdpovedServeru() throws IOException {
		CoJeNoveho.zGitHubu("{\"message\":\"API rate limit exceeded\"}");
	}

	/** Bez sítě dialog ukáže hlášku s odkazem na web, ne výjimku ani věčné načítání. */
	@Test(timeout = 20000)
	public void bezSite() throws Exception {
		final JCoJeNoveho[] p = new JCoJeNoveho[1];
		SwingUtilities.invokeAndWait(() -> {
			p[0] = new JCoJeNoveho();
			p[0].zGitHubu(() -> {
				throw new IOException("bez sítě");
			}, "6.4.0", "6.5.0", false);
		});
		String html;
		do {
			final String[] h = new String[1];
			SwingUtilities.invokeAndWait(() -> h[0] = p[0].getHtml());
			html = h[0];
		} while (html.contains("Načítám"));
		Assert.assertTrue(html, html.contains("nepodařilo načíst"));
		Assert.assertTrue(html, html.contains("releases/tag/v6.5.0"));
	}

	@Test(timeout = 20000)
	public void zGitHubuJenVybraneVerze() throws Exception {
		final String json = "[{\"tag_name\":\"v6.5.0\",\"body\":\"- nové\"},{\"tag_name\":\"v6.4.0\",\"body\":\"- staré\"}]";
		final JCoJeNoveho[] p = new JCoJeNoveho[1];
		SwingUtilities.invokeAndWait(() -> {
			p[0] = new JCoJeNoveho();
			p[0].zGitHubu(() -> json, "6.4.0", "6.5.0", false);
		});
		String html;
		do {
			final String[] h = new String[1];
			SwingUtilities.invokeAndWait(() -> h[0] = p[0].getHtml());
			html = h[0];
		} while (html.contains("Načítám"));
		Assert.assertTrue(html, html.contains("nové"));
		Assert.assertFalse(html, html.contains("staré"));
	}

	/** Odkazem je jen http(s); adresa uvnitř odkazu se nepřevádí podruhé; uvozovky se escapují. */
	@Test
	public void odkazyJenHttpABezDvojihoPrevodu() {
		Assert.assertEquals("<p>[a](javascript:alert(1)) [b](ftp://x/y) &quot;uvozovky&quot;</p>", CoJeNoveho.naHtml("[a](javascript:alert(1)) [b](ftp://x/y) \"uvozovky\""));
		Assert.assertEquals("<p><a href=\"https://a/https://b\">x</a></p>", CoJeNoveho.naHtml("[x](https://a/https://b)"));
	}

	private static String pockejNaVysledek(final JCoJeNoveho p) throws Exception {
		String html;
		do {
			final String[] h = new String[1];
			SwingUtilities.invokeAndWait(() -> h[0] = p.getHtml());
			html = h[0];
		} while (html.contains("Načítám"));
		return html;
	}

	/** Server odpoví, ale pro verze není popis: jiná hláška než při chybě sítě; stahuje se mimo EDT. */
	@Test(timeout = 20000)
	public void bezPopisuJinaHlaskaNezChyba() throws Exception {
		final JCoJeNoveho[] p = new JCoJeNoveho[1];
		final boolean[] naEdt = new boolean[1];
		SwingUtilities.invokeAndWait(() -> {
			p[0] = new JCoJeNoveho();
			p[0].zGitHubu(() -> {
				naEdt[0] = SwingUtilities.isEventDispatchThread();
				return "[{\"tag_name\":\"v6.0.0\",\"body\":\"- staré\"}]";
			}, "6.4.0", "6.5.0", false);
		});
		final String html = pockejNaVysledek(p[0]);
		Assert.assertFalse("stahuje se mimo EDT", naEdt[0]);
		Assert.assertTrue(html, html.contains("žádný popis"));
		Assert.assertFalse(html, html.contains("nepodařilo načíst"));
		Assert.assertTrue(html, html.contains("releases/tag/v6.5.0"));
	}

	@Test
	public void prilisVelkaOdpovedSeNectePoLimitu() throws Exception {
		Assert.assertEquals("[]", JCoJeNoveho.precti(new java.io.ByteArrayInputStream("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		try {
			JCoJeNoveho.precti(new java.io.ByteArrayInputStream(new byte[JCoJeNoveho.MAX_ODPOVED + 1]));
			Assert.fail("limit");
		} catch (final IOException e) {
			Assert.assertTrue(e.getMessage(), e.getMessage().contains("příliš velká"));
		}
	}
}
