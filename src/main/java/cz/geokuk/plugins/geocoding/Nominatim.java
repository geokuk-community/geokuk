package cz.geokuk.plugins.geocoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.program.FConst;

/**
 * Hledání adres a zpětný geocoding přes Nominatim nad daty OpenStreetMap. Podmínky služby: nejvýš jeden dotaz za sekundu, jen na pokyn
 * uživatele, identifikace programu v User-Agent, výsledky se pamatují.
 */
public final class Nominatim {

	static final String ZAKLAD = "https://nominatim.openstreetmap.org/";
	public static final String ATRIBUCE = "Hledá Nominatim, data © přispěvatelé OpenStreetMap";

	private static final long ROZESTUP_MS = 1100;
	private static final int PAMET = 200;

	private static long posledniDotaz;
	private static final Map<String, byte[]> pamet = new LinkedHashMap<String, byte[]>(16, 0.75f, true) {
		private static final long serialVersionUID = 1L;

		@Override
		protected boolean removeEldestEntry(final Map.Entry<String, byte[]> nejstarsi) {
			return size() > PAMET;
		}
	};

	private Nominatim() {}

	/** Adresy odpovídající textu, přednost mají místa blízko středu. */
	public static List<Nalezenec> hledej(final String dotaz, final Wgs stred) throws IOException {
		final StringBuilder url = new StringBuilder(ZAKLAD).append("search?format=xml&addressdetails=1&limit=10&accept-language=cs&q=").append(kod(dotaz));
		if (stred != null) {
			url.append(String.format(Locale.ROOT, "&viewbox=%.4f,%.4f,%.4f,%.4f", stred.lon - 1, stred.lat + 1, stred.lon + 1, stred.lat - 1));
		}
		return ctiHledani(new ByteArrayInputStream(stahni(url.toString())));
	}

	/** Adresa místa, nebo prázdný seznam, když tam žádná není. */
	public static List<Nalezenec> zpetne(final Wgs wgs) throws IOException {
		// Zaokrouhlení na zhruba 10 m, ať se blízká místa berou z paměti.
		final String url = String.format(Locale.ROOT, "%sreverse?format=xml&addressdetails=1&zoom=18&accept-language=cs&lat=%.4f&lon=%.4f", ZAKLAD, wgs.lat, wgs.lon);
		final Nalezenec nalezenec = ctiZpetne(new ByteArrayInputStream(stahni(url)));
		return nalezenec == null ? Collections.emptyList() : Collections.singletonList(nalezenec);
	}

	/** Adresa místa na webu OpenStreetMap. */
	public static String odkazNaMapu(final Wgs wgs) {
		return String.format(Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=17/%.6f/%.6f", wgs.lat, wgs.lon, wgs.lat, wgs.lon);
	}

	static List<Nalezenec> ctiHledani(final InputStream xml) throws IOException {
		final List<Nalezenec> vysledek = new ArrayList<>();
		final NodeList mista = dokument(xml).getElementsByTagName("place");
		for (int i = 0; i < mista.getLength(); i++) {
			final Element misto = (Element) mista.item(i);
			vysledek.add(nalezenec(misto, misto.getAttribute("display_name"), misto));
		}
		return vysledek;
	}

	static Nalezenec ctiZpetne(final InputStream xml) throws IOException {
		final Document doc = dokument(xml);
		final NodeList vysledky = doc.getElementsByTagName("result");
		if (vysledky.getLength() == 0) {
			return null;
		}
		final Element vysledek = (Element) vysledky.item(0);
		final NodeList casti = doc.getElementsByTagName("addressparts");
		return nalezenec(vysledek, vysledek.getTextContent().trim(), casti.getLength() == 0 ? null : (Element) casti.item(0));
	}

	private static Nalezenec nalezenec(final Element poloha, final String adresa, final Element casti) {
		final Nalezenec n = new Nalezenec();
		n.adresa = adresa;
		n.wgs = new Wgs(Double.parseDouble(poloha.getAttribute("lat")), Double.parseDouble(poloha.getAttribute("lon")));
		final String trida = poloha.getAttribute("class");
		final String typ = poloha.getAttribute("type");
		n.locationType = trida.isEmpty() ? typ : typ.isEmpty() ? trida : trida + "/" + typ;
		if (casti != null) {
			n.administrativeArea = prvni(casti, "state", "region");
			n.subAdministrativeArea = prvni(casti, "county", "district", "city_district", "municipality");
			n.locality = prvni(casti, "city", "town", "village", "hamlet", "suburb", "neighbourhood");
			final String ulice = prvni(casti, "road", "pedestrian", "square");
			final String cislo = prvni(casti, "house_number");
			n.thoroughfare = ulice == null ? cislo : cislo == null ? ulice : ulice + " " + cislo;
		}
		return n;
	}

	private static String prvni(final Element casti, final String... jmena) {
		for (final String jmeno : jmena) {
			final NodeList uzly = casti.getElementsByTagName(jmeno);
			if (uzly.getLength() > 0) {
				final String text = uzly.item(0).getTextContent().trim();
				if (!text.isEmpty()) {
					return text;
				}
			}
		}
		return null;
	}

	private static Document dokument(final InputStream xml) throws IOException {
		try {
			final DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
			f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			f.setExpandEntityReferences(false);
			return f.newDocumentBuilder().parse(xml);
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Nominatim vrátil nečitelnou odpověď", e);
		}
	}

	private static byte[] stahni(final String url) throws IOException {
		synchronized (pamet) {
			final byte[] znamy = pamet.get(url);
			if (znamy != null) {
				return znamy;
			}
		}
		synchronized (Nominatim.class) {
			final long cekat = posledniDotaz + ROZESTUP_MS - System.currentTimeMillis();
			if (cekat > 0) {
				try {
					Thread.sleep(cekat);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IOException("Hledání přerušeno", e);
				}
			}
			posledniDotaz = System.currentTimeMillis();
		}
		final HttpURLConnection spojeni = (HttpURLConnection) new URL(url).openConnection();
		spojeni.setConnectTimeout(10_000);
		spojeni.setReadTimeout(20_000);
		spojeni.setRequestProperty("User-Agent", "Geokuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")");
		if (spojeni.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new IOException("Nominatim odpověděl " + spojeni.getResponseCode());
		}
		final ByteArrayOutputStream data = new ByteArrayOutputStream();
		try (InputStream is = spojeni.getInputStream()) {
			final byte[] buf = new byte[8192];
			for (int n; (n = is.read(buf)) > 0;) {
				data.write(buf, 0, n);
			}
		}
		final byte[] odpoved = data.toByteArray();
		synchronized (pamet) {
			pamet.put(url, odpoved);
		}
		return odpoved;
	}

	private static String kod(final String text) {
		try {
			return URLEncoder.encode(text, "UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}
}
