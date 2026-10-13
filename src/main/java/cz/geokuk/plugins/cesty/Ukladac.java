package cz.geokuk.plugins.cesty;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.google.common.xml.XmlEscapers;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.util.file.BezpecnyZapis;

public class Ukladac {

	private static final String CREATOR = "creator=\"http://geokuk.cz\"";
	/** Jmenný prostor rozšíření GeoKuku v GPX; {@code <gk:bod/>} označuje řídicí bod cesty mezi body trasy po cestách. */
	public static final String GK_NAMESPACE = "http://geokuk.cz/gpx/1";

	private PrintWriter pwrt;

	/** Soubor zapsal Geokuk, takže se jeho uložením nic neztratí. */
	public static boolean jeZGeokuku(final File f) {
		final char[] buf = new char[1024];
		try (Reader rdr = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
			final int n = rdr.read(buf);
			return n > 0 && new String(buf, 0, n).contains(CREATOR);
		} catch (final IOException e) {
			return false;
		}
	}

	public void uloz(final File f, final Doc doc) throws IOException {
		BezpecnyZapis.zapisText(f, StandardCharsets.UTF_8, wrt -> {
			pwrt = wrt;
			p("<?xml version=\"1.0\" encoding=\"UTF-8\"  ?>");
			p("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\"" + (maTrasy(doc) ? " xmlns:gk=\"" + GK_NAMESPACE + "\"" : "") + " version=\"1.1\" " + CREATOR + " >");
			uloz(doc);
			p("</gpx>");
		});
	}

	private void p(final String format, final Object... p) {
		pwrt.printf(Locale.US, format, p);
		pwrt.println();
	}

	private void uloz(final Cesta cesta) {
		p("<trk>");
		if (cesta.getNazev() != null) {
			p("  <name>%s</name>", XmlEscapers.xmlContentEscaper().escape(cesta.getNazev()));
		}
		// Body tras po cestách se zapisují jako obyčejné body; řídicí body cesty se pak odliší rozšířením.
		final boolean oznacitBody = maTrasy(cesta);
		p("  <trkseg>");
		for (final Bod b : cesta.getBody()) {
			if (oznacitBody) {
				ulozRidici(b.getMouable().getMou());
			} else {
				uloz(b.getMouable().getMou());
			}
			final Usek usek = b.getUvpred();
			if (usek == null) {
				continue;
			}
			if (usek.isVzdusny()) {
				p("  </trkseg>");
				p("  <trkseg>");
			}
			final List<Mou> trasa = usek.getTrasa();
			if (trasa != null) {
				for (int i = 1; i < trasa.size() - 1; i++) {
					uloz(trasa.get(i));
				}
			}
		}
		p("  </trkseg>");
		p("</trk>");

	}

	private void uloz(final Doc doc) {
		for (final Cesta cesta : doc.getCesty()) {
			uloz(cesta);
		}
	}

	private void uloz(final Mou mou) {
		final Wgs wgs = mou.toWgs();
		p("    <trkpt lat=\"%f\" lon=\"%f\" />", wgs.lat, wgs.lon);
	}

	private void ulozRidici(final Mou mou) {
		final Wgs wgs = mou.toWgs();
		p("    <trkpt lat=\"%f\" lon=\"%f\"><extensions><gk:bod/></extensions></trkpt>", wgs.lat, wgs.lon);
	}

	private static boolean maTrasy(final Doc doc) {
		for (final Cesta cesta : doc.getCesty()) {
			if (maTrasy(cesta)) {
				return true;
			}
		}
		return false;
	}

	private static boolean maTrasy(final Cesta cesta) {
		for (final Usek usek : cesta.getUseky()) {
			if (usek.getTrasa() != null) {
				return true;
			}
		}
		return false;
	}
}
