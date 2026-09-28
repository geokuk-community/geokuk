package cz.geokuk.plugins.mapy.kachle.data;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import cz.geokuk.core.program.FConst;
import lombok.extern.slf4j.Slf4j;

/**
 * Zdroje dlaždic a hlavičky z mapy.properties: vestavěný, stažený z repozitáře
 * a místní vedle jaru, každý další přepisuje předchozí.
 */
@Slf4j
public final class KonfiguraceMap {

	static final String NAZEV = "mapy.properties";
	private static final String HLAVICKA = "hlavicka.";
	private static final File STAZENA = new File(new File(System.getProperty("java.io.tmpdir"), "geokuk"), NAZEV);

	private static volatile Properties konfigurace = nacti(STAZENA, FConst.JAR_DIR_EXISTUJE ? new File(FConst.JAR_DIR, NAZEV) : null);
	private static volatile String zdroj = popisZdroje(STAZENA, FConst.JAR_DIR_EXISTUJE ? new File(FConst.JAR_DIR, NAZEV) : null);

	static Properties nacti(final File stazena, final File mistni) {
		final Properties p = new Properties();
		try (InputStream in = KonfiguraceMap.class.getResourceAsStream("/" + NAZEV)) {
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (final IOException e) {
			throw new IllegalStateException(e);
		}
		for (final File soubor : Arrays.asList(stazena, mistni)) {
			if (soubor != null && soubor.isFile()) {
				try {
					p.putAll(precti(Files.readAllBytes(soubor.toPath())));
				} catch (final IOException e) {
					log.warn("Konfiguraci map {} nelze načíst: {}", soubor, e.toString());
				}
			}
		}
		return p;
	}

	private static String popisZdroje(final File stazena, final File mistni) {
		String popis = stazena.isFile() ? "stažená " + new Date(stazena.lastModified()) : "vestavěná";
		if (mistni != null && mistni.isFile()) {
			popis += " + místní " + mistni.getName();
		}
		return popis;
	}

	/** Načte a ověří obsah souboru, chybný vyhodí jako IOException. */
	static Properties precti(final byte[] obsah) throws IOException {
		final Properties p = new Properties();
		p.load(new InputStreamReader(new ByteArrayInputStream(obsah), StandardCharsets.UTF_8));
		for (final String klic : p.stringPropertyNames()) {
			if (klic.endsWith(".url") && !p.getProperty(klic).matches("https?://.*\\{z}.*\\{x}.*\\{y}.*")) {
				throw new IOException("Neplatná adresa " + klic + "=" + p.getProperty(klic));
			}
		}
		return p;
	}

	public static String url(final EKaType typ, final KaLoc loc) {
		final String vzor = konfigurace.getProperty(typ.name() + ".url");
		if (vzor == null) {
			throw new IllegalStateException("V " + NAZEV + " chybí " + typ.name() + ".url");
		}
		return vzor.replace("{z}", String.valueOf(loc.getMoumer())).replace("{x}", String.valueOf(loc.getFromSzUnsignedX())).replace("{y}", String.valueOf(loc.getFromSzUnsignedY()));
	}

	/** Hlavičky pro server, prázdná hodnota hlavičku ruší. */
	public static Map<String, String> hlavicky(final String server) {
		return hlavicky(konfigurace, server);
	}

	static Map<String, String> hlavicky(final Properties p, final String server) {
		final Map<String, String> vysledek = new TreeMap<>();
		for (final String klic : p.stringPropertyNames()) {
			if (klic.startsWith(HLAVICKA)) {
				final int tecka = klic.lastIndexOf('.');
				final String konecServeru = klic.substring(HLAVICKA.length(), tecka);
				final String hodnota = p.getProperty(klic).replace("{verze}", FConst.VERSION);
				if (server.endsWith(konecServeru) && !hodnota.isEmpty()) {
					vysledek.put(klic.substring(tecka + 1), hodnota);
				}
			}
		}
		return vysledek;
	}

	public static String zdroj() {
		return zdroj;
	}

	/** Stáhne aktuální konfiguraci z repozitáře a hned ji použije. */
	public static void aktualizuj() {
		final File mistni = FConst.JAR_DIR_EXISTUJE ? new File(FConst.JAR_DIR, NAZEV) : null;
		try {
			final URLConnection connection = new URL(FConst.MAPY_KONFIGURACE_URL).openConnection();
			connection.setRequestProperty("User-Agent", "Geokuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")");
			connection.setConnectTimeout(10000);
			connection.setReadTimeout(10000);
			final byte[] obsah;
			try (InputStream in = connection.getInputStream()) {
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				final byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) {
					out.write(buf, 0, n);
				}
				obsah = out.toByteArray();
			}
			precti(obsah);
			STAZENA.getParentFile().mkdirs();
			final File docasny = new File(STAZENA.getPath() + ".part");
			Files.write(docasny.toPath(), obsah);
			Files.move(docasny.toPath(), STAZENA.toPath(), StandardCopyOption.REPLACE_EXISTING);
			konfigurace = nacti(STAZENA, mistni);
			zdroj = popisZdroje(STAZENA, mistni);
			log.info("Konfigurace map aktualizována z {}", FConst.MAPY_KONFIGURACE_URL);
		} catch (final IOException e) {
			log.warn("Konfiguraci map nelze stáhnout: {}", e.toString());
		}
	}

	private KonfiguraceMap() {}
}
