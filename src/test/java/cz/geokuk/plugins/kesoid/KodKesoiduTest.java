package cz.geokuk.plugins.kesoid;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class KodKesoiduTest {

	private static void kandidati(final String text, final String... ocekavane) {
		assertEquals("vstup: " + text, Arrays.asList(ocekavane), KodKesoidu.kandidati(text));
	}

	@Test
	public void odkazyOdVedouciho() {
		kandidati("https://coord.info/GCBZJGC", "GCBZJGC");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?wp=GCBZA31", "GCBZA31");
		kandidati("https://www.geocaching.com/geocache/GC19ACP", "GC19ACP");
	}

	@Test
	public void kodKonciPredPodtrzitkemSNazvem() {
		kandidati("https://coord.info/GCBZJGC_název-cache", "GCBZJGC");
		kandidati("https://www.geocaching.com/geocache/GC19ACP_nazev-cache", "GC19ACP");
		kandidati("https://www.geocaching.com/geocache/GC19ACP_nazev-cache?utm_source=x#log", "GC19ACP");
	}

	@Test
	public void dalsiTvaryOdkazu() {
		kandidati("http://coord.info/gcbzjgc/", "GCBZJGC");
		kandidati("coord.info/GCBZJGC", "GCBZJGC");
		kandidati("https://geocaching.com/geocache/GC19ACP/", "GC19ACP");
		kandidati("https://coord.info/GCBZJGC?foo=1#x", "GCBZJGC");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?guid=x&wp=GCBZA31", "GCBZA31");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?wp=gcbza31&log=y", "GCBZA31");
		kandidati("https://www.opencaching.cz/viewcache.php?wp=OC1234", "OC1234");
		kandidati("https://opencaching.de/OC1234", "OC1234");
		kandidati("https://www.waymarking.com/waymarks/WM1ABC_Nejaky-nazev", "WM1ABC");
		kandidati("  https://coord.info/GCBZJGC \n", "GCBZJGC");
	}

	@Test
	public void odkazUprostredTextu() {
		kandidati("Podívej se na https://coord.info/GCBZJGC, je zajímavá.", "GCBZJGC");
		kandidati("(https://www.geocaching.com/geocache/GC19ACP)", "GC19ACP");
		kandidati("Odkaz: <https://coord.info/GCBZJGC>", "GCBZJGC");
	}

	@Test
	public void viceOdkazuVPoradiVTextu() {
		kandidati("https://coord.info/GCBBB11 a potom https://www.geocaching.com/geocache/GCAAA22", "GCBBB11", "GCAAA22");
		kandidati("https://www.geocaching.com/geocache/GCAAA22 https://coord.info/GCBBB11", "GCAAA22", "GCBBB11");
		kandidati("https://coord.info/GCBZJGC GCBZJGC", "GCBZJGC");
	}

	@Test
	public void holyKod() {
		kandidati("GC19ACP", "GC19ACP");
		kandidati("gc19acp", "GC19ACP");
		kandidati(" GCBZJGC ", "GCBZJGC");
		kandidati("OC12345", "OC12345");
		kandidati("WM1ABC", "WM1ABC");
		kandidati("MU12345", "MU12345");
		kandidati("Našel jsem GC19ACP včera", "GC19ACP");
		kandidati("GC19ACP_nazev", "GC19ACP");
		kandidati("OC12", "OC12");
		kandidati("GC1", "GC1");
	}

	@Test
	public void negativniPripadyALookalikes() {
		kandidati(null);
		kandidati("");
		kandidati("   ");
		kandidati("123456");
		kandidati("Hello world");
		kandidati("LOGCABIN");
		kandidati("Cabin");
		kandidati("XGC19ACP");
		kandidati("abcGC123");
		kandidati("GC");
		kandidati("https://coord.info/GCBZJGCABCDEFGHIJKL");
		kandidati("https://www.geocaching.com/geocache/GC19ACPXYZ0123456789");
		kandidati("https://www.geocaching.com/");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?guid=a1b2c3d4-e5f6-4789-a012-b3c4d5e6f708");
	}

	/** Tvary odkazů ověřené u poskytovatele: z nich se kód vždy vytáhne. */
	@Test
	public void tvaryOdkazuKterePlati() {
		for (final String url : new String[] { "http://coord.info/GC19ACP", "https://www.coord.info/gc19acp", "https://coord.info/GC19ACP/", "https://coord.info/GC19ACP?x=1", "https://coord.info/GC19ACP_nazev-cache",
				"https://www.geocaching.com/geocache/GC19ACP", "https://www.geocaching.com/geocache/GC19ACP_slug", "https://www.geocaching.com/geocache/gc19acp", "https://www.geocaching.com/geocache/GC19ACP/",
				"https://www.geocaching.com/geocache/GC19ACP_slug/log", "https://www.geocaching.com/geocache/GC19ACP_slug/logs", "https://www.geocaching.com/geocache/GC19ACP_slug/photos",
				"https://www.geocaching.com/geocache/GC19ACP?decrypt=y", "https://www.geocaching.com/geocache/GC19ACP_slug?view=map", "https://www.geocaching.com/geocache/GC19ACP?ctl=1",
				"https://www.geocaching.com/geocache/GC19ACP_slug#kotva", "http://geocaching.com/geocache/GC19ACP",
				"https://www.geocaching.com/seek/cache_details.aspx?wp=GC19ACP", "https://www.geocaching.com/seek/cache_details.aspx?wp=gc19acp",
				"https://www.geocaching.com/seek/cache_details.aspx?wp=GC19ACP&amp;log=y", "https://www.geocaching.com/seek/cache_details.aspx?wp=GC19ACP&log=y",
				"https://www.geocaching.com/seek/cache_details.aspx?wp=GC19ACP&amp;pf=y", "https://www.geocaching.com/seek/cache_details.aspx?wp=GC19ACP&amp;title=nejaky-nazev",
				"https://www.geocaching.com/seek/cache_details.aspx?wp=GC19ACP&amp;view=map", "https://www.geocaching.com/seek/cdpf.aspx?wp=GC19ACP",
				"https://m.geocaching.com/seek/cache_details.aspx?wp=GC19ACP", "https://www.geocaching.com/seek/log.aspx?wp=GC19ACP", "https://www.geocaching.com/seek/gallery.aspx?wp=GC19ACP",
				"https://www.geocaching.com/map/default.aspx?wp=GC19ACP", "https://www.geocaching.com/play/map/GC19ACP", "https://www.geocaching.com/play/map?gc=GC19ACP" }) {
			kandidati(url, "GC19ACP");
		}
	}

	/** Tvary, které na kešoid nevedou nebo nejsou naše: kód se z nich nebere ani jako holý kód. */
	@Test
	public void tvaryOdkazuKtereNeplati() {
		for (final String url : new String[] { "https://coord.info/gc/GC19ACP", "https://www.geocaching.com/seek/cache_details.aspx?code=GC19ACP", "https://www.geocaching.com/seek/cache_details.aspx/GC19ACP",
				"https://www.geocaching.com/geocache/?wp=GC19ACP", "https://www.geocaching.com/play/geocache/GC19ACP", "https://gc.com/GC19ACP", "https://www.gc.com/GC19ACP", "https://coord.ch/GC19ACP",
				"https://www.geocaching.com/seek/cache_details.aspx?guid=a1b2c3d4-e5f6-4789-a012-b3c4d5e6f708", "https://evilgeocaching.com/geocache/GC19ACP", "https://notcoord.info/GC19ACP", "https://www.xopencaching.cz/OC1234", "https://xwaymarking.com/waymarks/WM1ABC", "https://geocaching.com.example.org/geocache/GC19ACP" }) {
			kandidati(url);
		}
	}

	@Test
	public void jinaIDDomenyCoordInfoNejsouKese() {
		kandidati("https://coord.info/TB1234");
		kandidati("https://coord.info/GL1234");
		kandidati("https://coord.info/PR1234");
		kandidati("https://coord.info/WM1ABC", "WM1ABC");
	}

	@Test
	public void ciselneIDKeseSePrevedeJenKdyzJeToJiste() {
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?id=1000", "GC3E8");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?wp=GCBZA31&id=1000", "GCBZA31", "GC3E8");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?id=0");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?id=65536");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?id=abc");
		kandidati("https://www.geocaching.com/seek/cache_details.aspx?id=1234567");
	}

	@Test
	public void abecedaGcKoduNemaIlos() {
		kandidati("https://coord.info/GCIL1");
		kandidati("https://coord.info/GCO0S1U");
		kandidati("https://coord.info/GC0123456789");
		kandidati("GC19ACP", "GC19ACP");
		kandidati("https://coord.info/GCZZZZZZZ", "GCZZZZZZZ");
	}

	@Test
	public void dlouhyAPatologickyVstupSeZpracujeRychle() {
		final int milion = 1_000_000;
		final String[] vstupy = { opakuj("opencaching.", 20_000), opakuj("opencaching.", 100_000), opakuj("a.", 500_000), opakuj("GC", 500_000), opakuj("wp=", 300_000),
				opakuj("geocaching.com/seek/cache_details.aspx?", 30_000), opakuj("https://coord.info/", 50_000), opakuj("x", milion), opakuj(" ", milion), opakuj("?", milion),
				opakuj("&amp;wp=", 200_000), opakuj("GC19ACP ", 200_000), opakuj("www.opencaching.opencaching.opencaching.", 30_000) + "/OC1" };
		for (final String vstup : vstupy) {
			final long zacatek = System.nanoTime();
			KodKesoidu.kandidati(vstup);
			final long ms = (System.nanoTime() - zacatek) / 1_000_000;
			assertTrue("vstup délky " + vstup.length() + " trval " + ms + " ms", ms < 500);
		}
	}

	@Test
	public void textSeOriznePoNejvicZnacich() {
		final String vypln = opakuj("x ", KodKesoidu.NEJVIC_ZNAKU / 2);
		kandidati(vypln + "GC19ACP");
		kandidati(vypln.substring(0, KodKesoidu.NEJVIC_ZNAKU - 8) + " GC19ACP", "GC19ACP");
	}

	@Test
	public void najdiPrednostMaOdkazPredHolymKodem() {
		final List<String> data = Arrays.asList("GC222", "GC111");
		assertEquals("odkaz má přednost před holým kódem, i když je v textu později", "GC111", KodKesoidu.najdi("GC222 pak https://coord.info/GC111", data, s -> s));
		kandidati("GC222 pak https://coord.info/GC111", "GC111", "GC222");
	}

	private static String opakuj(final String s, final int n) {
		final StringBuilder sb = new StringBuilder(s.length() * n);
		for (int i = 0; i < n; i++) {
			sb.append(s);
		}
		return sb.toString();
	}

	@Test
	public void najdiPodleKoduVDatech() {
		final List<String> data = Arrays.asList("GC19ACP", "GCBZJGC", "OC12345", null, "gcbza31");
		assertEquals("GCBZJGC", KodKesoidu.najdi("https://coord.info/GCBZJGC_nazev", data, s -> s));
		assertEquals("GC19ACP", KodKesoidu.najdi("https://www.geocaching.com/geocache/GC19ACP", data, s -> s));
		assertEquals("OC12345", KodKesoidu.najdi("OC12345", data, s -> s));
		assertEquals("identifikátor velikostí písmen nezáleží", "gcbza31", KodKesoidu.najdi("https://www.geocaching.com/seek/cache_details.aspx?wp=GCBZA31", data, s -> s));
	}

	@Test
	public void najdiPodleKoduKtereVDatechNeniVraciNull() {
		final List<String> data = Arrays.asList("GC19ACP", "OC12345");
		assertNull(KodKesoidu.najdi("https://coord.info/GCBZJGC", data, s -> s));
		assertNull("kód, který vypadá jako běžný text", KodKesoidu.najdi("OC12", data, s -> s));
		assertNull(KodKesoidu.najdi("obyčejný text", data, s -> s));
		assertNull(KodKesoidu.najdi(null, data, s -> s));
		assertNull(KodKesoidu.najdi("GC19ACP", Collections.<String>emptyList(), s -> s));
	}

	@Test
	public void najdiPreferujeKodDrivVTextu() {
		final List<String> data = Arrays.asList("GC19ACP", "GCBZJGC");
		assertEquals("GCBZJGC", KodKesoidu.najdi("GCBZJGC GC19ACP", data, s -> s));
		assertEquals("GC19ACP", KodKesoidu.najdi("GC19ACP GCBZJGC", data, s -> s));
		assertEquals("odkaz má přednost před holým kódem", "GCBZJGC", KodKesoidu.najdi("GC19ACP https://coord.info/GCBZJGC", data, s -> s));
	}
}
