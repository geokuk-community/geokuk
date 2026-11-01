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
