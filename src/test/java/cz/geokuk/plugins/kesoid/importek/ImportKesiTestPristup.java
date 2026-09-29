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

	public static KesBag importuj(final String... wpt) throws Exception {
		return ImportKesiTest.importuj(ImportKesiTest.gpx(wpt));
	}

	private ImportKesiTestPristup() {}
}
