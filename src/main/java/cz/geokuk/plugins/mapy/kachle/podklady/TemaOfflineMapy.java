package cz.geokuk.plugins.mapy.kachle.podklady;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
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

	/** Průhledné téma s turistickými a cyklotrasami pro překryv přes jiný podklad; kreslí jen trasy z map osm.paws.cz. */
	public static final TemaOfflineMapy TRASY = new TemaOfflineMapy(null, null, null, VolbaVrstev.AUTOR, "trasy.xml");

	/** Vestavěná témata, která má smysl nabízet; výškové stínování potřebuje data, která nemáme. */
	public static final List<TemaOfflineMapy> VESTAVENA = Collections.unmodifiableList(Arrays.asList(VYCHOZI, new TemaOfflineMapy(MapsforgeThemes.OSMARENDER, null, null),
			new TemaOfflineMapy(MapsforgeThemes.BIKER, null, null), new TemaOfflineMapy(MapsforgeThemes.MOTORIDER, null, null)));

	private static final char ODDELOVAC = '|';

	private final MapsforgeThemes vestavene;
	private final File soubor;
	/** Téma uvnitř zipu, null = první podle abecedy. */
	private final String xmlVZipu;
	private final VolbaVrstev vrstvy;
	/** Téma přibalené v programu (soubor vedle této třídy), null u ostatních. */
	private final String zdroj;
	private String otiskZdroje;

	private TemaOfflineMapy(final MapsforgeThemes vestavene, final File soubor, final String xmlVZipu) {
		this(vestavene, soubor, xmlVZipu, VolbaVrstev.AUTOR, null);
	}

	private TemaOfflineMapy(final MapsforgeThemes vestavene, final File soubor, final String xmlVZipu, final VolbaVrstev vrstvy, final String zdroj) {
		this.vestavene = vestavene;
		this.soubor = soubor;
		this.xmlVZipu = xmlVZipu;
		this.vrstvy = vrstvy;
		this.zdroj = zdroj;
	}

	/** Totéž téma s výběrem vrstev uživatele. */
	public TemaOfflineMapy sVrstvami(final VolbaVrstev volba) {
		return new TemaOfflineMapy(vestavene, soubor, xmlVZipu, volba, zdroj);
	}

	public VolbaVrstev getVrstvy() {
		return vrstvy;
	}

	/** Klíč pro uložení nastavení tématu (výběr vrstev). */
	public String klicNastaveni() {
		return OfflineRenderer.klic(naText());
	}

	/** Stejný soubor tématu, na výběru vrstev nezáleží. */
	public boolean stejnyZdroj(final TemaOfflineMapy t) {
		return t != null && naText().equals(t.naText());
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
		if (zdroj != null) {
			return "zdroj:" + zdroj;
		}
		return xmlVZipu == null ? soubor.getPath() : soubor.getPath() + ODDELOVAC + xmlVZipu;
	}

	public boolean isVestavene() {
		return vestavene != null || zdroj != null;
	}

	/** Téma z osm.paws.cz (soubor paws…); turistické a cyklotrasy kreslí samo. */
	public boolean isPaws() {
		return soubor != null && soubor.getName().toLowerCase(Locale.ROOT).startsWith("paws");
	}

	public File getSoubor() {
		return soubor;
	}

	/** Název do menu. */
	public String getNazev() {
		if (zdroj != null) {
			return "Turistické a cyklotrasy";
		}
		if (vestavene != null) {
			switch (vestavene) {
			case DEFAULT:
				return "Výchozí";
			case OSMARENDER:
				return "Osmarender";
			case BIKER:
				return "Cyklo (Biker)";
			case MOTORIDER:
				return "Motorkářské (Motorider)";
			default:
				return vestavene.name();
			}
		}
		return xmlVZipu == null ? soubor.getName() : soubor.getName() + " – " + xmlVZipu;
	}

	/**
	 * Témata ve složce: soubory .xml a z každého .zip všechna témata uvnitř (jen jejich jména, obsah se nečte). Zip s jedním tématem je jedna položka bez jména
	 * tématu.
	 */
	public static List<TemaOfflineMapy> temataVeSlozce(final File slozka) {
		final File[] soubory = slozka == null ? null : slozka.listFiles(File::isFile);
		if (soubory == null) {
			return Collections.emptyList();
		}
		Arrays.sort(soubory, Comparator.comparing(File::getName));
		final List<TemaOfflineMapy> temata = new ArrayList<>();
		for (final File f : soubory) {
			final String jmeno = f.getName().toLowerCase(Locale.ROOT);
			if (jmeno.endsWith(".xml")) {
				temata.add(zeSouboru(f, null));
			} else if (jmeno.endsWith(".zip")) {
				final List<String> xml = new ArrayList<>();
				try (ZipFile zf = new ZipFile(f)) {
					final Enumeration<? extends ZipEntry> polozky = zf.entries();
					while (polozky.hasMoreElements()) {
						final ZipEntry e = polozky.nextElement();
						if (!e.isDirectory() && e.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
							xml.add(ZdrojeZeZipu.bezLomitka(e.getName()));
						}
					}
				} catch (final IOException e) {
					continue; // poškozený nebo jiný zip téma nenabízí
				}
				Collections.sort(xml);
				if (xml.size() == 1) {
					temata.add(zeSouboru(f, null));
				} else {
					for (final String x : xml) {
						temata.add(zeSouboru(f, x));
					}
				}
			}
		}
		return temata;
	}

	/** Co určuje vzhled dlaždic: soubor tématu a výběr vrstev. */
	String otisk() {
		return vrstvy.isAutor() ? otiskSouboru() : otiskSouboru() + "\nvolba:" + vrstvy.naText();
	}

	/** Soubor tématu bez výběru vrstev: při jeho změně se dlaždice i symboly vykreslí znovu. */
	String otiskSouboru() {
		if (vestavene != null) {
			return "vestavene:" + vestavene.name();
		}
		if (zdroj != null) {
			synchronized (this) {
				if (otiskZdroje == null) {
					otiskZdroje = otiskZdroje(zdroj);
				}
				return naText() + ':' + otiskZdroje;
			}
		}
		return naText() + ':' + soubor.length() + ':' + soubor.lastModified();
	}

	/** Téma s výběrem vrstev: od uživatele, jinak od autora tématu. Zvolené vrstvy a nabídku vrstev zapíše do {@code vyber}. */
	XmlRenderTheme vytvor(final VyberVrstev vyber) throws IOException {
		return new SVrstvami(vytvor(), vyber);
	}

	private XmlRenderTheme vytvor() throws IOException {
		if (vestavene != null) {
			return vestavene;
		}
		if (zdroj != null) {
			return new StreamRenderTheme("", otevriZdroj(zdroj));
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

	private static InputStream otevriZdroj(final String zdroj) throws IOException {
		final InputStream in = TemaOfflineMapy.class.getResourceAsStream(zdroj);
		if (in == null) {
			throw new FileNotFoundException("V programu chybí téma " + zdroj);
		}
		return in;
	}

	/** Obsah přibaleného tématu se s verzí programu může změnit, dlaždice se pak vykreslí znovu. */
	private static String otiskZdroje(final String zdroj) {
		final CRC32 crc = new CRC32();
		try (InputStream in = otevriZdroj(zdroj)) {
			crc.update(ByteStreams.toByteArray(in));
		} catch (final IOException e) {
			return "chybi";
		}
		return Long.toHexString(crc.getValue());
	}

	/**
	 * Výběr vrstev (stylemenu) tématu: styl a vrstvy podle volby uživatele, co nezvolil, podle autora. Bez výběru by mapsforge kreslil všechny
	 * vrstvy najednou.
	 */
	static final class VyberVrstev implements XmlRenderThemeMenuCallback {
		private final VolbaVrstev volba;
		private String popis;
		private XmlRenderThemeStyleMenu menu;

		VyberVrstev(final VolbaVrstev volba) {
			this.volba = volba;
		}

		@Override
		public Set<String> getCategories(final XmlRenderThemeStyleMenu menu) {
			this.menu = menu;
			XmlRenderThemeStyleLayer styl = volba.getStyl() == null ? null : menu.getLayer(volba.getStyl());
			if (styl == null || !styl.isVisible()) {
				styl = menu.getLayer(menu.getDefaultValue());
			}
			if (styl == null) {
				return null;
			}
			final Set<String> kategorie = new HashSet<>(styl.getCategories());
			final StringBuilder sb = new StringBuilder(menu.getId()).append(':').append(styl.getId());
			for (final XmlRenderThemeStyleLayer vrstva : styl.getOverlays()) {
				if (volba.jeZapnuta(vrstva.getId(), vrstva.isEnabled())) {
					kategorie.addAll(vrstva.getCategories());
					sb.append(',').append(vrstva.getId());
				}
			}
			popis = sb.toString();
			return kategorie;
		}

		/** Zvolený styl a vrstvy, null když téma vrstvy nemá. */
		String getPopis() {
			return popis;
		}

		/** Nabídka vrstev tématu, null když ji téma nemá. */
		XmlRenderThemeStyleMenu getMenu() {
			return menu;
		}
	}

	/** Téma s vlastním výběrem vrstev; vestavěná témata jsou sdílené konstanty, proto se jim výběr nenastavuje přímo. */
	private static final class SVrstvami implements XmlRenderTheme {
		private final XmlRenderTheme tema;
		private XmlRenderThemeMenuCallback vrstvy;

		SVrstvami(final XmlRenderTheme tema, final XmlRenderThemeMenuCallback vrstvy) {
			this.tema = tema;
			this.vrstvy = vrstvy;
		}

		@Override
		public XmlRenderThemeMenuCallback getMenuCallback() {
			return vrstvy;
		}

		@Override
		public String getRelativePathPrefix() {
			return tema.getRelativePathPrefix();
		}

		@Override
		public InputStream getRenderThemeAsStream() throws IOException {
			return tema.getRenderThemeAsStream();
		}

		@Override
		public XmlThemeResourceProvider getResourceProvider() {
			return tema.getResourceProvider();
		}

		@Override
		public void setMenuCallback(final XmlRenderThemeMenuCallback vrstvy) {
			this.vrstvy = vrstvy;
		}

		@Override
		public void setResourceProvider(final XmlThemeResourceProvider zdroje) {
			tema.setResourceProvider(zdroje);
		}
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
		return obj instanceof TemaOfflineMapy && naText().equals(((TemaOfflineMapy) obj).naText()) && vrstvy.equals(((TemaOfflineMapy) obj).vrstvy);
	}

	@Override
	public int hashCode() {
		return naText().hashCode() * 31 + vrstvy.hashCode();
	}

	@Override
	public String toString() {
		return naText();
	}
}
