package cz.geokuk.plugins.geocoding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cz.geokuk.core.coordinates.SJtsk;
import cz.geokuk.core.coordinates.Wgs;

/** Odkazy na místo na webech s mapami, které se otevírají v prohlížeči. Pořadí odpovídá pořadí podkladů v nabídce Mapy. */
public final class OdkazyNaMapy {

	/** Odkaz s názvem položky nabídky. */
	public static final class Odkaz {
		public final String nazev;
		public final String url;

		Odkaz(final String nazev, final String url) {
			this.nazev = nazev;
			this.url = url;
		}
	}

	/** Orientační obdélník, ve kterém má smysl převod do S-JTSK. */
	private static final double MIN_LAT = 48.4, MAX_LAT = 51.2, MIN_LON = 11.9, MAX_LON = 19.1;

	/** Název značky v Geoprohlížeči, „Keška“ zakódované v UTF-8. */
	private static final String NAZEV_ZNACKY = "Ke%C5%A1ka";

	private OdkazyNaMapy() {}

	/** Turistická mapa na Mapy.com se značkou v místě; mapa se na místo vycentruje podle x a y. */
	public static String mapyCom(final Wgs wgs) {
		final String lon = String.format(Locale.ROOT, "%.6f", wgs.lon);
		final String lat = String.format(Locale.ROOT, "%.6f", wgs.lat);
		return "https://mapy.com/cs/turisticka?source=coor&id=" + lon + "%2C" + lat + "&x=" + lon + "&y=" + lat + "&z=16";
	}

	public static String google(final Wgs wgs) {
		return String.format(Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", wgs.lat, wgs.lon);
	}

	/** Geoprohlížeč ČÚZK přímo na stránce mapy; souřadnice jsou S-JTSK, nejdřív osa Y. Mimo území České republiky null. */
	public static String cuzk(final Wgs wgs) {
		if (wgs.lat < MIN_LAT || wgs.lat > MAX_LAT || wgs.lon < MIN_LON || wgs.lon > MAX_LON) {
			return null;
		}
		final SJtsk.Bod bod = SJtsk.zWgs(wgs.lat, wgs.lon);
		return String.format(Locale.ROOT, "https://ags.cuzk.gov.cz/geoprohlizec/mapa.html?xy=%.2f,%.2f&name=", bod.y, bod.x) + NAZEV_ZNACKY + "&b=zm";
	}

	/** Položky nabídky pro dané místo; ČÚZK jen tam, kde se dá použít. */
	public static List<Odkaz> pro(final Wgs wgs) {
		final List<Odkaz> odkazy = new ArrayList<>();
		odkazy.add(new Odkaz("Mapy.com – Turistická", mapyCom(wgs)));
		odkazy.add(new Odkaz("OpenStreetMap", Nominatim.odkazNaMapu(wgs)));
		final String cuzk = cuzk(wgs);
		if (cuzk != null) {
			odkazy.add(new Odkaz("ČÚZK Geoprohlížeč", cuzk));
		}
		odkazy.add(new Odkaz("Google Mapy", google(wgs)));
		return odkazy;
	}
}
