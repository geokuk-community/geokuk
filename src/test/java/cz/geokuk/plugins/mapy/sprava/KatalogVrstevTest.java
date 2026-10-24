package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Format;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Soubor;
import cz.geokuk.plugins.mapy.sprava.KatalogovaVrstva.Typ;

public class KatalogVrstevTest {

	static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	static KatalogMap mapy() throws IOException {
		return KatalogMap.nacti("{\"zdroj\": \"test\", \"licence\": \"ODbL 1.0\", \"atribuce\": \"© OSM\", \"mapy\": \"https://example.org/maps/v5/\","
				+ " \"polygony\": \"https://example.org/maps/poly/\", \"polozky\": ["
				+ "{\"id\": \"europe/czech-republic\", \"nazev\": \"Česko\", \"velikost\": 100, \"obdelnik\": [48.5, 12, 51.1, 18.9]},"
				+ "{\"id\": \"europe/germany\", \"nazev\": \"Německo\", \"velikost\": 100, \"obdelnik\": [47, 5, 55, 15]},"
				+ "{\"id\": \"europe/germany/bayern\", \"nazev\": \"Bavorsko\", \"skupina\": \"Německo\", \"velikost\": 100, \"obdelnik\": [47, 9, 50.6, 13.9]},"
				+ "{\"id\": \"europe/germany/berlin\", \"nazev\": \"Berlín\", \"skupina\": \"Německo\", \"velikost\": 100, \"obdelnik\": [52.3, 13, 52.7, 13.8]}"
				+ "]}", id -> null);
	}

	static String vrstva(final String id, final String typ, final String region, final String format, final String soubory, final String navic) {
		final String cesty = soubory.replace(CESTA, region + "." + typ).replace(PRIPONA, "hgt-zip".equals(format) ? ".zip" : "." + format);
		return "{\"id\": \"" + id + "\", \"type\": \"" + typ + "\", \"region\": \"" + region + "\", \"name\": \"Vrstva " + id + "\", \"format\": \"" + format
				+ "\", \"files\": [" + cesty + "], \"license\": \"ODbL 1.0\", \"attribution\": \"© přispěvatelé OpenStreetMap\"" + navic + "}";
	}

	/** Zástupci v cestě souboru, {@link #vrstva} je nahradí klíčem a typem vrstvy a příponou formátu. */
	static final String CESTA = "@CESTA@";
	static final String PRIPONA = "@PRIPONA@";

	/** Soubor s cestou „klíč.typ.část.přípona“, kde část je jméno souboru bez přípony. */
	static String soubor(final String nazev, final String url, final long velikost, final String sha) {
		final String cast = (nazev.contains(".") ? nazev.substring(0, nazev.lastIndexOf('.')) : nazev).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
		return soubor(nazev, CESTA + "." + cast + PRIPONA, url, velikost, sha);
	}

	static String soubor(final String nazev, final String cesta, final String url, final long velikost, final String sha) {
		return "{\"name\": \"" + nazev + "\", \"path\": \"" + cesta + "\", \"url\": \"" + url + "\", \"size\": " + velikost + ", \"sha256\": \"" + sha
				+ "\"}";
	}

	static String katalog(final String... vrstvy) {
		return "{\"version\": 1, \"generated\": \"2026-10-16T08:12:00Z\", \"sources\": [\"https://a.example.org/rel/\", \"https://b.example.org/zaloha/\"],"
				+ " \"layers\": [" + String.join(",", vrstvy) + "]}";
	}

	private static KatalogVrstev nacti(final String... vrstvy) throws IOException {
		return KatalogVrstev.nacti(katalog(vrstvy), mapy());
	}

	static String jednoducha(final String id, final String typ, final String region) {
		return vrstva(id, typ, region, "map", soubor(id.replace('/', '_') + ".map", "v1/" + id.replace('/', '_') + ".map", 10, SHA), "");
	}

	private static List<String> idcka(final List<KatalogovaVrstva> vrstvy) {
		return vrstvy.stream().map(KatalogovaVrstva::getId).collect(Collectors.toList());
	}

	@Test
	public void vsechnaPole() throws IOException {
		final KatalogMap mapy = mapy();
		final KatalogVrstev k = KatalogVrstev.nacti(katalog(vrstva("contours/europe/czech-republic", "contours", "europe/czech-republic", "map",
				soubor("europe_czech-republic.contours.map", "europe/czech-republic.contours.map", "v1/europe_czech-republic.contours.map", 1000, SHA.toUpperCase()) + ","
						+ soubor("cz_vrstevnice_2.map", "europe/czech-republic.contours.2.map", "https://jinde.example.org/cz_vrstevnice_2.map", 234, SHA),
				", \"dataDate\": \"2021-12-31\", \"produced\": \"2026-10-16T09:40:00Z\", \"dataSource\": \"Copernicus\", \"zoom\": {\"min\": 12, \"max\": 21},"
						+ " \"contourInterval\": 10, \"attributionFull\": \"plné znění\", \"theme\": \"contours\", \"note\": \"DSM\", \"neznamePole\": [1, 2]")), mapy);
		assertEquals(1, k.getVrstvy().size());
		final KatalogovaVrstva v = k.getVrstvy().get(0);
		assertEquals("contours/europe/czech-republic", v.getId());
		assertEquals(Typ.VRSTEVNICE, v.getTyp());
		assertEquals(Format.MAP, v.getFormat());
		assertEquals("europe/czech-republic", v.getRegion());
		assertEquals(1234, v.getVelikost());
		assertEquals(LocalDate.of(2021, 12, 31), v.getDatumDat());
		assertEquals(Instant.parse("2026-10-16T09:40:00Z"), v.getVyrobeno());
		assertEquals("Copernicus", v.getZdrojDat());
		assertEquals(12, v.getZoomMin());
		assertEquals(21, v.getZoomMax());
		assertEquals(10, v.getKrokVrstevnic());
		assertEquals("plné znění", v.getAtribuceDlouha());
		assertEquals("contours", v.getTema());
		assertEquals("DSM", v.getPoznamka());
		assertEquals("© přispěvatelé OpenStreetMap", v.getAtribuce());
		final Soubor s1 = v.getSoubory().get(0);
		assertEquals("SHA malými písmeny", SHA, s1.getSha256());
		assertEquals("europe_czech-republic.contours.map", s1.getNazev());
		assertEquals("cesta podle klíče mapy", "europe/czech-republic.contours.map", s1.getCesta());
		assertEquals("europe/czech-republic", v.getKlic());
		assertEquals(Arrays.asList(URI.create("https://a.example.org/rel/v1/europe_czech-republic.contours.map"),
				URI.create("https://b.example.org/zaloha/v1/europe_czech-republic.contours.map")), s1.getUrly());
		assertEquals(Arrays.asList(URI.create("https://jinde.example.org/cz_vrstevnice_2.map")), v.getSoubory().get(1).getUrly());
		assertSame("obrys z katalogu map", mapy.podleId("europe/czech-republic").get().getPokryti(), v.getPokryti());
		assertTrue(v.getPokryti().obsahuje(new Wgs(50, 15)));
		assertSame(v, k.podleCesty("Europe/Czech-Republic.contours.2.map").get());
		assertSame(v, k.podleId("contours/europe/czech-republic").get());
		assertFalse(k.podleCesty("czech-republic.map").isPresent());
		assertSame(v, k.vrstva("europe/czech-republic", Typ.VRSTEVNICE).get());
		assertFalse(k.vrstva("europe/czech-republic", Typ.TRASY).isPresent());
		assertFalse(k.vrstva("europe/germany", Typ.VRSTEVNICE).isPresent());
	}

	@Test
	public void volitelnaPoleChybi() throws IOException {
		final KatalogovaVrstva v = nacti(jednoducha("tourist/europe/czech-republic", "tourist", "europe/czech-republic")).getVrstvy().get(0);
		assertNull(v.getDatumDat());
		assertNull(v.getVyrobeno());
		assertNull(v.getAtribuceDlouha());
		assertNull(v.getTema());
		assertEquals(-1, v.getZoomMin());
		assertEquals(0, v.getKrokVrstevnic());
	}

	@Test
	public void obdelnikURegionuMimoKatalogMap() throws IOException {
		final KatalogovaVrstva v = nacti(vrstva("dem/europe/alpy", "dem", "europe/alpy", "hgt-zip", soubor("alpy.zip", "alpy.zip", 5, SHA),
				", \"coverage\": {\"poly\": \"poly/europe/alpy.poly\", \"bbox\": [45, 5, 48, 16]}")).getVrstvy().get(0);
		assertEquals(Format.HGT_ZIP, v.getFormat());
		assertTrue(v.getPokryti().obsahuje(new Wgs(46, 10)));
		assertFalse(v.getPokryti().obsahuje(new Wgs(50, 10)));
		try {
			nacti(vrstva("dem/europe/alpy", "dem", "europe/alpy", "hgt-zip", soubor("alpy.zip", "alpy.zip", 5, SHA), ""));
			fail("bez obrysu i obdélníku");
		} catch (final IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains("obrys"));
		}
	}

	@Test
	public void datumDatPodleRfc1123() throws IOException {
		final KatalogovaVrstva v = nacti(vrstva("tourist/europe/czech-republic", "tourist", "europe/czech-republic", "map", soubor("t.map", "t.map", 1, SHA),
				", \"dataDate\": \"Tue, 13 Oct 2026 21:04:00 GMT\"")).getVrstvy().get(0);
		assertEquals(LocalDate.of(2026, 10, 13), v.getDatumDat());
	}

	@Test
	public void neznamyTypAFormatSePreskoci() throws IOException {
		final KatalogVrstev k = nacti(jednoducha("lyzovani/europe/czech-republic", "lyzovani", "europe/czech-republic"),
				vrstva("tourist/europe/germany", "tourist", "europe/germany", "pmtiles", soubor("de.pmtiles", "de.pmtiles", 1, SHA), ""),
				jednoducha("tourist/europe/czech-republic", "tourist", "europe/czech-republic"));
		assertEquals(Arrays.asList("tourist/europe/czech-republic"), idcka(k.getVrstvy()));
	}

	@Test
	public void vrstvaRoutesSePreskoci() throws IOException {
		final KatalogVrstev k = nacti(jednoducha("routes/europe/czech-republic", "routes", "europe/czech-republic"),
				jednoducha("tourist/europe/czech-republic", "tourist", "europe/czech-republic"));
		assertEquals(Arrays.asList("tourist/europe/czech-republic"), idcka(k.getVrstvy()));
		assertTrue(k.getVrstvy().get(0).getSoubory().get(0).getCesta().startsWith("europe/czech-republic.tourist."));
	}

	@Test
	public void vadnyKatalog() throws IOException {
		final String ok = jednoducha("tourist/europe/czech-republic", "tourist", "europe/czech-republic");
		vada("{\"version\": 2, \"sources\": [\"https://a.example.org/\"], \"layers\": []}", "novějším");
		vada("{\"sources\": [\"https://a.example.org/\"], \"layers\": []}", "version");
		vada("{\"version\": 1, \"sources\": [], \"layers\": []}", "sources");
		vada("{\"version\": 1, \"sources\": [\"ftp://a.example.org/\"], \"layers\": []}", "zdroj");
		vada(katalog(ok, ok), "dvakrát");
		vada(katalog(ok, vrstva("trasy2/europe/czech-republic", "tourist", "europe/czech-republic", "map", soubor("jiny.map", "europe/czech-republic.tourist.map", "x.map", 1, SHA), ""),
				vrstva("trasy3/europe/czech-republic", "tourist", "europe/czech-republic", "map", soubor("jiny2.map", "europe/czech-republic.tourist.map", "y.map", 1, SHA), "")),
				"dvakrát");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "europe/y.tourist.map", "x.map", 1, SHA), "")), "cestu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "europe/x.contours.map", "x.map", 1, SHA), "")), "cestu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "europe/x.tourist.zip", "x.map", 1, SHA), "")), "cestu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "europe/x.tourist.map/../../x.map", "x.map", 1, SHA), "")), "cestu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "/europe/x.tourist.map", "x.map", 1, SHA), "")), "cestu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", "{\"name\": \"x.map\", \"url\": \"x.map\", \"size\": 1, \"sha256\": \"" + SHA + "\"}", "")), "path");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("../x.map", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map.stahuje", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("X.MAP.STAHUJE", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map.sha256", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map.stara", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("CON.map", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("nul", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map.", "x.map", 1, SHA), "")), "jméno");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", "{\"name\": \"x.map\", \"path\": \"europe/x.tourist.map\", \"url\": \"x.map\", \"size\": 1.5, \"sha256\": \"" + SHA + "\"}", "")), "velikost");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "../jinam/x.map", 1, SHA), "")), "adresu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "/x.map", 1, SHA), "")), "adresu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "file:///etc/x.map", 1, SHA), "")), "adresu");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "x.map", 1, "abc"), "")), "sha256");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", soubor("x.map", "x.map", 0, SHA), "")), "velikost");
		vada(katalog(vrstva("tourist/europe/x", "tourist", "europe/x", "map", "", "")), "soubory");
		vada(katalog(vrstva("tourist/Europe/X", "tourist", "europe/x", "map", soubor("x.map", "x.map", 1, SHA), "")), "id");
		vada(katalog(vrstva("tourist/europe/czech-republic", "tourist", "europe/czech-republic", "map", soubor("x.map", "x.map", 1, SHA), ", \"dataDate\": \"včera\"")),
				"datum");
		vada(katalog(vrstva("tourist/europe/czech-republic", "tourist", "europe/czech-republic", "map", soubor("x.map", "x.map", 1, SHA), ", \"zoom\": {\"min\": 1.5}")),
				"zoom");
	}

	private static void vada(final String json, final String hlaska) throws IOException {
		try {
			KatalogVrstev.nacti(json, mapy());
			fail("Čekám chybu „" + hlaska + "“: " + json);
		} catch (final IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains(hlaska));
		}
	}

	@Test
	public void vrstvyProMapu() throws IOException {
		final KatalogMap mapy = mapy();
		final KatalogVrstev k = KatalogVrstev.nacti(katalog(jednoducha("tourist/europe/czech-republic", "tourist", "europe/czech-republic"),
				jednoducha("dem/europe/czech-republic", "dem", "europe/czech-republic"), jednoducha("tourist/europe/germany", "tourist", "europe/germany"),
				jednoducha("tourist/europe/germany/bayern", "tourist", "europe/germany/bayern"),
				jednoducha("contours/europe/germany/bayern", "contours", "europe/germany/bayern"),
				jednoducha("contours/europe/germany/berlin", "contours", "europe/germany/berlin"), vrstva("dem/europe", "dem", "europe", "hgt-zip", soubor("eu.zip", "eu.zip", 1, SHA), ", \"coverage\": {\"bbox\": [35, -10, 70, 40]}")), mapy);
		assertEquals(Arrays.asList("tourist/europe/czech-republic", "dem/europe/czech-republic"),
				idcka(k.vrstvyProMapu(mapy.podleId("europe/czech-republic").get())));
		assertEquals("vlastní trasy, vrstevnice regionů", Arrays.asList("tourist/europe/germany", "contours/europe/germany/bayern", "contours/europe/germany/berlin"),
				idcka(k.vrstvyProMapu(mapy.podleId("europe/germany").get())));
		assertEquals("vlastní vrstvy, chybějící z celé země", Arrays.asList("tourist/europe/germany/bayern", "contours/europe/germany/bayern"),
				idcka(k.vrstvyProMapu(mapy.podleId("europe/germany/bayern").get())));
		assertEquals("trasy celé země, vlastní vrstevnice", Arrays.asList("contours/europe/germany/berlin", "tourist/europe/germany"),
				idcka(k.vrstvyProMapu(mapy.podleId("europe/germany/berlin").get())));
	}

	@Test
	public void vestavenyKatalog() {
		final KatalogVrstev k = KatalogVrstev.vestaveny();
		assertSame(k, KatalogVrstev.vestaveny());
		assertEquals("https://github.com/geokuk-community/geokuk-maps/releases/latest/download/layers.json", KatalogVrstev.URL_KATALOGU.toString());
		for (final KatalogovaVrstva v : k.getVrstvy()) {
			assertTrue("vrstva " + v + " patří k mapě z katalogu", KatalogMap.vestaveny().podleId(v.getRegion()).isPresent());
		}
	}

	/** Katalog tak, jak ho vyrábí scripts/build-catalog.py v geokuk-maps (Lucembursko: trasy, vrstevnice, výškový model). */
	@Test
	public void katalogZGeokukMaps() throws IOException {
		final String json;
		try (InputStream in = KatalogVrstevTest.class.getResourceAsStream("layers-geokuk-maps.json")) {
			json = new String(com.google.common.io.ByteStreams.toByteArray(in), java.nio.charset.StandardCharsets.UTF_8);
		}
		final KatalogMap mapy = KatalogMap.vestaveny();
		final KatalogVrstev k = KatalogVrstev.nacti(json, mapy);
		assertEquals(Arrays.asList("contours/europe/luxembourg", "dem/europe/luxembourg", "tourist/europe/luxembourg"), idcka(k.getVrstvy()));
		final KatalogovaMapa lucembursko = mapy.podleId("europe/luxembourg").get();
		final KatalogovaVrstva trasy = k.vrstva(lucembursko.getId(), Typ.TRASY).get();
		assertEquals("europe/luxembourg.tourist.map", trasy.getSoubory().get(0).getCesta());
		assertEquals("europe_luxembourg.tourist.map", trasy.getSoubory().get(0).getNazev());
		assertEquals(URI.create("https://github.com/geokuk-community/geokuk-maps/releases/download/layers-2026-10/europe_luxembourg.tourist.map"),
				trasy.getSoubory().get(0).getUrly().get(0));
		assertEquals("tourist", trasy.getTema());
		assertEquals("© OpenStreetMap contributors", trasy.getAtribuce());
		assertNotNull(trasy.getLicenceText());
		assertEquals(LocalDate.of(2026, 10, 15), trasy.getDatumDat());
		assertSame("obrys z katalogu map, ne .poly z release", lucembursko.getPokryti(), trasy.getPokryti());
		final KatalogovaVrstva vrstevnice = k.vrstva(lucembursko.getId(), Typ.VRSTEVNICE).get();
		assertEquals(10, vrstevnice.getKrokVrstevnic());
		assertNotNull(vrstevnice.getAtribuceDlouha());
		assertEquals("europe/luxembourg.contours.map", vrstevnice.getSoubory().get(0).getCesta());
		final KatalogovaVrstva dem = k.vrstva(lucembursko.getId(), Typ.DEM).get();
		assertEquals(Format.HGT_ZIP, dem.getFormat());
		assertEquals("europe/luxembourg.dem.zip", dem.getSoubory().get(0).getCesta());
	}
}
