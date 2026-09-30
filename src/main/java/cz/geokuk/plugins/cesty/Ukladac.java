package cz.geokuk.plugins.cesty;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.google.common.xml.XmlEscapers;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.util.file.BezpecnyZapis;

public class Ukladac {

	private static final String CREATOR = "creator=\"http://geokuk.cz\"";

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
			p("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" " + CREATOR + " >");
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
		p("  <trkseg>");
		for (final Bod b : cesta.getBody()) {
			uloz(b.getMouable().getMou());
			if (b.getUvpred() != null && b.getUvpred().isVzdusny()) {
				p("  </trkseg>");
				p("  <trkseg>");
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
}
