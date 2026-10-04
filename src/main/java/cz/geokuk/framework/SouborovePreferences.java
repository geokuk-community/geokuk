package cz.geokuk.framework;

import java.io.*;
import java.util.*;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import cz.geokuk.util.file.BezpecnyZapis;
import lombok.extern.slf4j.Slf4j;

/**
 * Nastavení v jednom souboru místo registru Windows. Drží se v paměti, změny se zapisují nejpozději za dvě sekundy, při {@link #flush()} a při ukončení
 * programu. Soubor má formát exportu {@link Preferences#exportSubtree(OutputStream)}.
 */
@Slf4j
public final class SouborovePreferences extends AbstractPreferences {

	private static final long ODLOZENI_ZAPISU_MS = 2000;

	/** Soubor a odložený zápis, společné pro celý strom. */
	private static final class Uloziste {
		private final File soubor;
		private final SouborovePreferences koren;
		private Timer casovac;
		private boolean zmeneno;
		private boolean naplanovano;
		private boolean chybaOhlasena;

		Uloziste(final File soubor, final SouborovePreferences koren) {
			this.soubor = soubor;
			this.koren = koren;
		}

		synchronized void zmeneno() {
			zmeneno = true;
			if (naplanovano) {
				return;
			}
			naplanovano = true;
			if (casovac == null) {
				casovac = new Timer("Zápis nastavení", true);
			}
			casovac.schedule(new TimerTask() {
				@Override
				public void run() {
					synchronized (Uloziste.this) {
						naplanovano = false;
					}
					try {
						uloz(false);
					} catch (final IOException | RuntimeException e) {
						// ohlásí se v uloz, časovač musí přežít
					}
				}
			}, ODLOZENI_ZAPISU_MS);
		}

		void uloz(final boolean vzdy) throws IOException {
			synchronized (this) {
				if (!zmeneno && !vzdy) {
					return;
				}
				zmeneno = false;
			}
			try {
				final Document doc = koren.doDokumentu();
				BezpecnyZapis.zapis(soubor, out -> zapisDokument(doc, out));
				synchronized (this) {
					chybaOhlasena = false;
				}
			} catch (final IOException | RuntimeException e) {
				synchronized (this) {
					zmeneno = true;
					if (!chybaOhlasena) {
						chybaOhlasena = true;
						log.warn("Nastavení nelze zapsat do {}", soubor, e);
					}
				}
				throw e instanceof IOException ? (IOException) e : new IOException(e);
			}
		}
	}

	private final Map<String, String> hodnoty = new TreeMap<>();
	private final Map<String, SouborovePreferences> deti = new TreeMap<>();
	private final Uloziste uloziste;

	/** Prázdné nastavení, které se zapisuje do daného souboru. */
	public static SouborovePreferences prazdne(final File soubor) {
		return new SouborovePreferences(soubor);
	}

	/** Nastavení načtené ze souboru ve formátu exportu Java Preferences. */
	public static SouborovePreferences nacti(final File soubor) throws IOException {
		final SouborovePreferences koren = new SouborovePreferences(soubor);
		koren.importuj(soubor);
		return koren;
	}

	private SouborovePreferences(final File soubor) {
		super(null, "");
		uloziste = new Uloziste(soubor, this);
	}

	private SouborovePreferences(final SouborovePreferences rodic, final String jmeno) {
		super(rodic, jmeno);
		uloziste = rodic.uloziste;
	}

	public File getSoubor() {
		return uloziste.soubor;
	}

	/** Zapíše nastavení do souboru, i když se nezměnilo. */
	public void ulozHned() throws IOException {
		uloziste.uloz(true);
	}

	/** Zkopíruje do tohoto uzlu celý podstrom jiného nastavení, třeba z registru. */
	public void zkopirujZ(final Preferences zdroj) throws BackingStoreException {
		for (final String klic : zdroj.keys()) {
			final String hodnota = zdroj.get(klic, null);
			if (hodnota != null) {
				put(klic, hodnota);
			}
		}
		for (final String dite : zdroj.childrenNames()) {
			((SouborovePreferences) node(dite)).zkopirujZ(zdroj.node(dite));
		}
	}

	/** Při ukončení programu zapíše neuložené změny. */
	public void ulozitPriUkonceni() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				uloziste.uloz(false);
			} catch (final IOException e) {
				// už ohlášeno do logu
			}
		}, "Zápis nastavení při ukončení"));
	}

	@Override
	protected void putSpi(final String key, final String value) {
		if (!value.equals(hodnoty.put(key, value))) {
			uloziste.zmeneno();
		}
	}

	@Override
	protected String getSpi(final String key) {
		return hodnoty.get(key);
	}

	@Override
	protected void removeSpi(final String key) {
		if (hodnoty.remove(key) != null) {
			uloziste.zmeneno();
		}
	}

	@Override
	protected void removeNodeSpi() throws BackingStoreException {
		final SouborovePreferences rodic = (SouborovePreferences) parent();
		rodic.deti.remove(name());
		uloziste.zmeneno();
	}

	@Override
	protected String[] keysSpi() {
		return hodnoty.keySet().toArray(new String[0]);
	}

	@Override
	protected String[] childrenNamesSpi() {
		return deti.keySet().toArray(new String[0]);
	}

	@Override
	protected AbstractPreferences childSpi(final String name) {
		SouborovePreferences dite = deti.get(name);
		if (dite == null) {
			dite = new SouborovePreferences(this, name);
			deti.put(name, dite);
		}
		return dite;
	}

	@Override
	protected void syncSpi() throws BackingStoreException {
		// Soubor patří jen tomuto programu, není co načítat.
	}

	@Override
	public void flush() throws BackingStoreException {
		try {
			uloziste.uloz(false);
		} catch (final IOException e) {
			throw new BackingStoreException(e);
		}
	}

	@Override
	protected void flushSpi() throws BackingStoreException {
		// zapisuje flush() celý strom najednou
	}

	private Document doDokumentu() {
		try {
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			final Element preferences = doc.createElement("preferences");
			preferences.setAttribute("EXTERNAL_XML_VERSION", "1.0");
			doc.appendChild(preferences);
			final Element root = doc.createElement("root");
			root.setAttribute("type", "user");
			preferences.appendChild(root);
			zapisUzel(doc, root);
			return doc;
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void zapisUzel(final Document doc, final Element element) {
		final Map<String, String> kopieHodnot;
		final List<SouborovePreferences> kopieDeti;
		synchronized (lock) {
			kopieHodnot = new TreeMap<>(hodnoty);
			kopieDeti = new ArrayList<>(deti.values());
		}
		final Element map = doc.createElement("map");
		element.appendChild(map);
		for (final Map.Entry<String, String> e : kopieHodnot.entrySet()) {
			final Element entry = doc.createElement("entry");
			entry.setAttribute("key", platneXml(e.getKey()));
			entry.setAttribute("value", platneXml(e.getValue()));
			map.appendChild(entry);
		}
		for (final SouborovePreferences dite : kopieDeti) {
			final Element node = doc.createElement("node");
			node.setAttribute("name", platneXml(dite.name()));
			element.appendChild(node);
			dite.zapisUzel(doc, node);
		}
	}

	/** Vynechá znaky, které XML 1.0 nedovolí; jinak by nešlo nastavení zapsat nebo znovu načíst. */
	static String platneXml(final String s) {
		StringBuilder sb = null;
		for (int i = 0; i < s.length();) {
			final int cp = s.codePointAt(i);
			final int delka = Character.charCount(cp);
			final boolean platny = cp == 0x9 || cp == 0xA || cp == 0xD || cp >= 0x20 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF;
			if (!platny && sb == null) {
				sb = new StringBuilder(s.length()).append(s, 0, i);
			} else if (platny && sb != null) {
				sb.appendCodePoint(cp);
			}
			i += delka;
		}
		return sb == null ? s : sb.toString();
	}

	private static void zapisDokument(final Document doc, final OutputStream out) throws IOException {
		try {
			final Transformer t = TransformerFactory.newInstance().newTransformer();
			t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			t.setOutputProperty(OutputKeys.INDENT, "yes");
			t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			t.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "http://java.sun.com/dtd/preferences.dtd");
			t.transform(new DOMSource(doc), new StreamResult(out));
		} catch (final Exception e) {
			throw new IOException(e);
		}
	}

	private void importuj(final File soubor) throws IOException {
		final Document doc;
		try (InputStream in = new BufferedInputStream(new FileInputStream(soubor))) {
			final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setExpandEntityReferences(false);
			final DocumentBuilder db = dbf.newDocumentBuilder();
			// DTD se nestahuje, formát je pevný.
			db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
			db.setErrorHandler(null);
			doc = db.parse(in);
		} catch (final IOException e) {
			throw e;
		} catch (final Exception e) {
			throw new IOException("Soubor " + soubor + " nemá formát nastavení: " + e.getMessage(), e);
		}
		final Element preferences = doc.getDocumentElement();
		if (preferences == null || !"preferences".equals(preferences.getTagName())) {
			throw new IOException("Soubor " + soubor + " nemá formát nastavení.");
		}
		for (final Element root : potomci(preferences, "root")) {
			nactiUzel(root);
		}
	}

	private void nactiUzel(final Element element) {
		for (final Element map : potomci(element, "map")) {
			for (final Element entry : potomci(map, "entry")) {
				hodnoty.put(entry.getAttribute("key"), entry.getAttribute("value"));
			}
		}
		for (final Element node : potomci(element, "node")) {
			final String jmeno = node.getAttribute("name");
			if (!jmeno.isEmpty() && jmeno.indexOf('/') < 0) {
				((SouborovePreferences) node(jmeno)).nactiUzel(node);
			}
		}
	}

	private static List<Element> potomci(final Element rodic, final String jmeno) {
		final List<Element> vysledek = new ArrayList<>();
		for (Node n = rodic.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element && jmeno.equals(((Element) n).getTagName())) {
				vysledek.add((Element) n);
			}
		}
		return vysledek;
	}
}
