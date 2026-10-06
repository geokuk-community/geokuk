package cz.geokuk.plugins.kesoid.importek;

import cz.geokuk.plugins.kesoid.KesBag;

/** Import GPX pro testy z jiných balíků. */
public final class ImportKesiTestPristup {

	public static String kes(final String kod, final String sym, final String typ, final int hodnoceni, final int bestOf, final int favority) {
		final String tagy = "<gpxg:GeogetExtension xmlns:gpxg=\"https://www.geoget.cz/GpxExtensions/v2\"><gpxg:Tags>" + "<gpxg:Tag Category=\"Hodnoceni\">" + hodnoceni + "%</gpxg:Tag>"
				+ "<gpxg:Tag Category=\"BestOf\">" + bestOf + "</gpxg:Tag><gpxg:Tag Category=\"favorites\">" + favority + "</gpxg:Tag></gpxg:Tags></gpxg:GeogetExtension>";
		return ImportKesiTest.kes(kod, sym, typ, "Cizí", 1, true, false, "2", tagy);
	}

	public static String kesBezHodnoceni(final String kod) {
		return ImportKesiTest.kes(kod, "Geocache", "Traditional Cache", "Cizí", 1, true, false, "2", "");
	}

	/** Keš na zadaném místě; autor „Ja“ s id 42 je vlastní. */
	public static String kesNa(final String kod, final String sym, final String typ, final String autor, final int autorId, final boolean dostupna, final boolean archivovana,
			final double lat, final double lon) {
		return ImportKesiTest.kes(kod, sym, typ, autor, autorId, dostupna, archivovana, "2", "").replace("lat=\"50.1\" lon=\"14.4\"",
				String.format(java.util.Locale.ROOT, "lat=\"%.5f\" lon=\"%.5f\"", lat, lon));
	}

	public static KesBag importuj(final String... wpt) throws Exception {
		return ImportKesiTest.importuj(ImportKesiTest.gpx(wpt));
	}

	/** Import s genomem sady ikon, aby keše dostaly její obrázky. */
	public static KesBag importuj(final cz.geokuk.plugins.kesoid.genetika.Genom genom, final String... wpt) throws Exception {
		return ImportKesiTest.importuj(genom, ImportKesiTest.gpx(wpt));
	}

	private ImportKesiTestPristup() {}
}
