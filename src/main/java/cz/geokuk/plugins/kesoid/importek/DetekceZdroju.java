package cz.geokuk.plugins.kesoid.importek;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.geokuk.util.file.Filex;
import cz.geokuk.util.file.IniFile;
import cz.geokuk.util.file.TextovySoubor;
import lombok.extern.slf4j.Slf4j;

/**
 * Datové složky GeoGetu, GSAKu a OpenSAKu v obvyklých umístěních a poslední databáze, se kterou program pracoval. Bez skenování disků
 * a bez registru Windows, čte jen soubory nastavení těch programů.
 */
@Slf4j
public final class DetekceZdroju {

	/** Nalezená datová složka; poslední databáze je null, když ji nejde zjistit nebo neexistuje. */
	public static final class Nalez {
		private final File slozka;
		private final File posledniDatabaze;

		Nalez(final File slozka, final File posledniDatabaze) {
			this.slozka = slozka;
			this.posledniDatabaze = posledniDatabaze;
		}

		public File getSlozka() {
			return slozka;
		}

		public File getPosledniDatabaze() {
			return posledniDatabaze;
		}
	}

	/** Operační systém, domovská složka a proměnné prostředí, podle kterých se hledá. */
	public static final class Prostredi {
		private final String os;
		private final File domov;
		private final Map<String, String> promenne;

		public Prostredi(final String os, final File domov, final Map<String, String> promenne) {
			this.os = os == null ? "" : os;
			this.domov = domov;
			this.promenne = promenne;
		}

		public static Prostredi systemove() {
			return new Prostredi(System.getProperty("os.name"), new File(System.getProperty("user.home")), System.getenv());
		}

		boolean jeWindows() {
			return os.startsWith("Windows");
		}

		boolean jeMac() {
			return os.startsWith("Mac");
		}

		/** Proměnná prostředí, prázdná jako chybějící. */
		String promenna(final String jmeno) {
			final String hodnota = promenne.get(jmeno);
			return hodnota == null || hodnota.trim().isEmpty() ? null : hodnota;
		}

		/** Složka z proměnné prostředí, jinak záložní složka v domovské složce. */
		File slozka(final String promenna, final String vDomove) {
			final String hodnota = promenna(promenna);
			return hodnota != null ? new File(hodnota) : new File(domov, vDomove);
		}
	}

	private static final Pattern ZALOHA_GEOGETU = Pattern.compile("(?i).*\\.[0-9]{8}\\.db3");

	private DetekceZdroju() {}

	// ---------------------------------------------------------------- GeoGet

	/** Složky programu GeoGet, ve kterých může být geogetboot.ini nebo přenosná datová složka. */
	static List<File> slozkyGeogetu(final Prostredi p) {
		final List<File> slozky = new ArrayList<>();
		for (final String promenna : new String[] { "ProgramFiles", "ProgramFiles(x86)" }) {
			final String hodnota = p.promenna(promenna);
			if (hodnota != null) {
				slozky.add(new File(hodnota, "GeoGet"));
			}
		}
		final String disk = p.promenna("SystemDrive");
		slozky.add(new File((disk == null ? "C:" : disk) + File.separator, "GeoGet"));
		return slozky;
	}

	/** Kandidáti na datovou složku GeoGetu v pořadí, ve kterém se zkoušejí; mimo Windows žádní. */
	static List<File> kandidatiGeogetu(final Prostredi p) {
		if (!p.jeWindows()) {
			return Collections.emptyList();
		}
		final Set<File> kandidati = new LinkedHashSet<>();
		final List<File> programy = slozkyGeogetu(p);
		for (final File program : programy) {
			final File zBoot = datadirZBootu(program);
			if (zBoot != null) {
				kandidati.add(zBoot);
			}
		}
		kandidati.add(new File(p.slozka("APPDATA", "AppData/Roaming"), "GeoGet"));
		for (final File program : programy) {
			kandidati.add(new File(program, "data"));
		}
		return new ArrayList<>(kandidati);
	}

	/** Datová složka podle geogetboot.ini ve složce programu (oddíl [boot], klíč datadir); relativní cesta je vůči složce programu. */
	static File datadirZBootu(final File program) {
		final File boot = new File(program, "geogetboot.ini");
		if (!boot.isFile()) {
			return null;
		}
		try {
			final String datadir = IniFile.read(boot).get("boot", "datadir");
			if (datadir == null || datadir.isEmpty()) {
				return null;
			}
			// Cesta je ve tvaru Windows (.\data); oddělovač podle systému, aby šla stejně vyhodnotit všude.
			final String cesta = datadir.replace('\\', File.separatorChar);
			final File slozka = new File(cesta);
			return slozka.isAbsolute() ? slozka : new File(program, cesta);
		} catch (final IOException | RuntimeException e) {
			log.warn("Soubor {} nejde přečíst", boot, e);
			return null;
		}
	}

	static boolean maDatabazeGeogetu(final File slozka) {
		final File[] soubory = slozka.listFiles();
		if (soubory == null) {
			return false;
		}
		for (final File f : soubory) {
			final String jmeno = f.getName().toLowerCase();
			if (f.isFile() && jmeno.endsWith(".db3") && !ZALOHA_GEOGETU.matcher(jmeno).matches()) {
				return true;
			}
		}
		return false;
	}

	/** Poslední databáze GeoGetu podle geoget.ini v datové složce (oddíl [geoget], klíč lastDB, jméno bez přípony). */
	static File posledniDatabazeGeogetu(final File slozka) {
		final File ini = new File(slozka, "geoget.ini");
		if (!ini.isFile()) {
			return null;
		}
		try {
			final String jmeno = IniFile.read(ini).get("geoget", "lastDB");
			if (jmeno == null || jmeno.isEmpty() || jmeno.contains("/") || jmeno.contains("\\")) {
				return null;
			}
			return existujici(new File(slozka, jmeno.toLowerCase().endsWith(".db3") ? jmeno : jmeno + ".db3"));
		} catch (final IOException | RuntimeException e) {
			log.warn("Soubor {} nejde přečíst", ini, e);
			return null;
		}
	}

	public static Nalez geoget(final Prostredi p) {
		for (final File slozka : kandidatiGeogetu(p)) {
			if (maDatabazeGeogetu(slozka)) {
				return nalez(slozka, posledniDatabazeGeogetu(slozka));
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- GSAK

	static List<File> kandidatiGsaku(final Prostredi p) {
		if (!p.jeWindows()) {
			return Collections.emptyList();
		}
		return Collections.singletonList(new File(new File(p.slozka("APPDATA", "AppData/Roaming"), "gsak"), "data"));
	}

	/** Databáze GSAKu je podsložka se souborem sqlite.db3. */
	static boolean maDatabazeGsaku(final File slozka) {
		final File[] podslozky = slozka.listFiles(File::isDirectory);
		if (podslozky == null) {
			return false;
		}
		for (final File d : podslozky) {
			if (new File(d, "sqlite.db3").isFile()) {
				return true;
			}
		}
		return false;
	}

	public static Nalez gsak(final Prostredi p) {
		for (final File slozka : kandidatiGsaku(p)) {
			if (maDatabazeGsaku(slozka)) {
				return nalez(slozka, null);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- OpenSAK

	/** Soubor bootstrap.json, který ukazuje na složku instalace OpenSAKu. */
	static File bootstrapOpensaku(final Prostredi p) {
		final File zaklad;
		if (p.jeWindows()) {
			zaklad = p.slozka("APPDATA", "AppData/Roaming");
		} else if (p.jeMac()) {
			zaklad = new File(p.domov, "Library/Application Support");
		} else {
			zaklad = p.slozka("XDG_CONFIG_HOME", ".config");
		}
		return new File(new File(zaklad, "opensak"), "bootstrap.json");
	}

	/** Výchozí složka instalace OpenSAKu; instalace z Microsoft Store má data v Dokumentech. */
	static List<File> vychoziSlozkyOpensaku(final Prostredi p) {
		final List<File> slozky = new ArrayList<>();
		if (p.jeWindows()) {
			slozky.add(new File(p.domov, "Documents/opensak"));
			slozky.add(new File(p.slozka("APPDATA", "AppData/Roaming"), "opensak"));
		} else if (p.jeMac()) {
			slozky.add(new File(p.domov, "Library/Application Support/opensak"));
		} else {
			slozky.add(new File(p.slozka("XDG_DATA_HOME", ".local/share"), "opensak"));
		}
		return slozky;
	}

	static List<File> slozkyInstalaceOpensaku(final Prostredi p) {
		final Set<File> slozky = new LinkedHashSet<>();
		final String instalace = jsonText(bootstrapOpensaku(p), "install_dir");
		if (instalace != null) {
			slozky.add(new File(instalace));
		}
		slozky.addAll(vychoziSlozkyOpensaku(p));
		return new ArrayList<>(slozky);
	}

	static boolean maDatabazeOpensaku(final File slozka) {
		final File[] soubory = slozka.listFiles();
		if (soubory == null) {
			return false;
		}
		for (final File f : soubory) {
			if (f.isFile() && f.getName().toLowerCase().endsWith(".db")) {
				return true;
			}
		}
		return false;
	}

	public static Nalez opensak(final Prostredi p) {
		for (final File instalace : slozkyInstalaceOpensaku(p)) {
			final File nastaveni = new File(instalace, "opensak.json");
			final String aktivni = jsonText(nastaveni, "databases.active");
			final File databaze = aktivni == null ? null : existujici(new File(aktivni));
			if (databaze != null && databaze.getParentFile() != null) {
				return nalez(databaze.getParentFile(), databaze);
			}
			final String slozkaDatabazi = jsonText(nastaveni, "databases.dir");
			if (slozkaDatabazi != null && maDatabazeOpensaku(new File(slozkaDatabazi))) {
				return nalez(new File(slozkaDatabazi), null);
			}
			if (maDatabazeOpensaku(instalace)) {
				return nalez(instalace, null);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- společné

	private static Nalez nalez(final File slozka, final File databaze) {
		final File s = Filex.canonize(slozka.getAbsoluteFile());
		return new Nalez(s, databaze == null ? null : new File(s, databaze.getName()));
	}

	private static File existujici(final File soubor) {
		return soubor.isFile() ? soubor : null;
	}

	/**
	 * Textová hodnota klíče nejvyšší úrovně z malého souboru JSON (bootstrap.json, opensak.json mají plochý objekt); chybějící soubor, klíč nebo
	 * jiný typ hodnoty je null.
	 */
	static String jsonText(final File soubor, final String klic) {
		if (!soubor.isFile()) {
			return null;
		}
		try {
			final Matcher m = Pattern.compile("\"" + Pattern.quote(klic) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(TextovySoubor.nacti(soubor));
			return m.find() ? bezEscape(m.group(1)) : null;
		} catch (final IOException | RuntimeException e) {
			log.warn("Soubor {} nejde přečíst", soubor, e);
			return null;
		}
	}

	private static String bezEscape(final String s) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			final char z = s.charAt(i);
			if (z != '\\' || i + 1 >= s.length()) {
				sb.append(z);
				continue;
			}
			final char dalsi = s.charAt(++i);
			switch (dalsi) {
			case 'n':
				sb.append('\n');
				break;
			case 't':
				sb.append('\t');
				break;
			case 'r':
				sb.append('\r');
				break;
			case 'b':
				sb.append('\b');
				break;
			case 'f':
				sb.append('\f');
				break;
			case 'u':
				if (i + 4 < s.length()) {
					sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
					i += 4;
				}
				break;
			default:
				sb.append(dalsi);
			}
		}
		return sb.toString();
	}
}
