package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.mapsforge.map.rendertheme.*;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import com.google.common.io.ByteStreams;

/**
 * Vykreslovací téma offline mapy: vestavěné z knihovny mapsforge, nebo soubor uživatele (.xml, nebo neupravený .zip).
 * Textová podoba pro nastavení: jméno vestavěného tématu, nebo cesta k souboru, u zipu případně {@code cesta|téma.xml}.
 */
public final class TemaOfflineMapy {

	public static final TemaOfflineMapy VYCHOZI = new TemaOfflineMapy(MapsforgeThemes.DEFAULT, null, null);

	/** Vestavěná témata, která má smysl nabízet; výškové stínování potřebuje data, která nemáme. */
	public static final List<TemaOfflineMapy> VESTAVENA = Collections.unmodifiableList(Arrays.asList(VYCHOZI, new TemaOfflineMapy(MapsforgeThemes.OSMARENDER, null, null),
			new TemaOfflineMapy(MapsforgeThemes.BIKER, null, null), new TemaOfflineMapy(MapsforgeThemes.MOTORIDER, null, null)));

	private static final char ODDELOVAC = '|';

	private final MapsforgeThemes vestavene;
	private final File soubor;
	/** Téma uvnitř zipu, null = první podle abecedy. */
	private final String xmlVZipu;

	private TemaOfflineMapy(final MapsforgeThemes vestavene, final File soubor, final String xmlVZipu) {
		this.vestavene = vestavene;
		this.soubor = soubor;
		this.xmlVZipu = xmlVZipu;
	}

	public static TemaOfflineMapy zeSouboru(final File soubor, final String xmlVZipu) {
		return new TemaOfflineMapy(null, soubor, xmlVZipu);
	}

	/** Téma z textu v nastavení; prázdný nebo neznámý text je výchozí téma. */
	public static TemaOfflineMapy zTextu(final String text) {
		if (text == null || text.trim().isEmpty()) {
			return VYCHOZI;
		}
		for (final TemaOfflineMapy t : VESTAVENA) {
			if (t.vestavene.name().equals(text)) {
				return t;
			}
		}
		final int i = text.indexOf(ODDELOVAC);
		if (i < 0) {
			return zeSouboru(new File(text), null);
		}
		return zeSouboru(new File(text.substring(0, i)), text.substring(i + 1));
	}

	public String naText() {
		if (vestavene != null) {
			return vestavene.name();
		}
		return xmlVZipu == null ? soubor.getPath() : soubor.getPath() + ODDELOVAC + xmlVZipu;
	}

	public boolean isVestavene() {
		return vestavene != null;
	}

	public File getSoubor() {
		return soubor;
	}

	/** Co určuje vzhled dlaždic: při změně souboru tématu se dlaždice vykreslí znovu. */
	String otisk() {
		if (vestavene != null) {
			return "vestavene:" + vestavene.name();
		}
		return naText() + ':' + soubor.length() + ':' + soubor.lastModified();
	}

	XmlRenderTheme vytvor() throws IOException {
		if (vestavene != null) {
			return vestavene;
		}
		if (!soubor.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
			return new ExternalRenderTheme(soubor);
		}
		final ZdrojeZeZipu zdroje = new ZdrojeZeZipu(soubor);
		final List<String> temata = zdroje.temata();
		if (temata.isEmpty()) {
			throw new IOException("V souboru " + soubor + " není žádné téma (.xml).");
		}
		final String xml = xmlVZipu == null ? temata.get(0) : xmlVZipu;
		if (!temata.contains(xml)) {
			throw new IOException("V souboru " + soubor + " není téma " + xml + ".");
		}
		return new ZipRenderTheme(xml, zdroje);
	}

	/** Soubory tématu ze zipu v paměti; čte adresář zipu, takže zvládne i položky bez velikosti v hlavičce. */
	private static final class ZdrojeZeZipu implements XmlThemeResourceProvider {
		private final Map<String, byte[]> soubory = new HashMap<>();

		ZdrojeZeZipu(final File zip) throws IOException {
			try (ZipFile zf = new ZipFile(zip)) {
				final Enumeration<? extends ZipEntry> polozky = zf.entries();
				while (polozky.hasMoreElements()) {
					final ZipEntry e = polozky.nextElement();
					if (e.isDirectory()) {
						continue;
					}
					try (InputStream in = zf.getInputStream(e)) {
						soubory.put(bezLomitka(e.getName()), ByteStreams.toByteArray(in));
					}
				}
			}
		}

		List<String> temata() {
			final List<String> temata = new ArrayList<>();
			for (final String jmeno : soubory.keySet()) {
				if (jmeno.toLowerCase(Locale.ROOT).endsWith(".xml")) {
					temata.add(jmeno);
				}
			}
			Collections.sort(temata);
			return temata;
		}

		private static String bezLomitka(final String cesta) {
			return cesta.startsWith("/") ? cesta.substring(1) : cesta;
		}

		/** Stejné hledání jako {@code ZipXmlThemeResourceProvider} z mapsforge. */
		@Override
		public InputStream createInputStream(final String relativePath, final String source) {
			String klic = source.startsWith("file:") ? source.substring("file:".length()) : source;
			klic = bezLomitka(klic);
			if (relativePath != null) {
				String cesta = bezLomitka(relativePath);
				if (cesta.endsWith("/")) {
					cesta = cesta.substring(0, cesta.length() - 1);
				}
				klic = cesta.isEmpty() ? klic : cesta + "/" + klic;
			}
			final byte[] data = soubory.get(klic);
			return data == null ? null : new ByteArrayInputStream(data);
		}
	}

	@Override
	public boolean equals(final Object obj) {
		return obj instanceof TemaOfflineMapy && naText().equals(((TemaOfflineMapy) obj).naText());
	}

	@Override
	public int hashCode() {
		return naText().hashCode();
	}

	@Override
	public String toString() {
		return naText();
	}
}
