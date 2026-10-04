package cz.geokuk.start;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

/**
 * Spouštěč přenosného GeoKuku ({@code start.jar}). Vymění staženou novou verzi {@code geokuk.jar}, zvolí paměť a spustí GeoKuk stejnou Javou,
 * jakou byl spuštěn sám, pak skončí. Běžící {@code geokuk.jar} by Windows přepsat nedovolily.
 *
 * Nesmí používat nic z ostatních tříd programu, do {@code start.jar} se dávají jen třídy tohoto balíčku.
 */
public final class Start {

	static final String JAR = "geokuk.jar";
	/** Podsložka přenosného GeoKuku s programem a Javou, vedle ní je složka data. */
	public static final String SLOZKA_PROGRAMU = "program";
	static final long MB = 1024L * 1024;
	static final int MIN_PAMET_MB = 1024;
	static final int MAX_PAMET_MB = 3072;
	/** Spustit GeoKuk, až skončí ten, který spouštěč pustil (restart po aktualizaci). */
	public static final String PO_UKONCENI = "--po-ukonceni";
	/** Zámek, který běžící GeoKuk drží ve složce data. */
	public static final String ZAMEK = "bezi.lock";
	static final long CEKANI_NA_UKONCENI_MS = 60_000;
	/** Klíč nastavení v uzlu {@code geokuk/current/vseobecne}, 0 = zvolí spouštěč. */
	public static final String PAMET_KLIC = "pametMb";

	public static void main(final String[] args) {
		try {
			final File adresar = adresarSpoustece();
			final List<String> parametry = new ArrayList<>(Arrays.asList(args));
			if (parametry.remove(PO_UKONCENI) && !pockejNaUkonceni(new File(new File(koren(adresar), "data"), ZAMEK), CEKANI_NA_UKONCENI_MS)) {
				chyba("GeoKuk se neukončil, novou verzi nejde spustit.\nUkončete GeoKuk a spusťte ho znovu.");
				return;
			}
			final File jar = vyberJar(adresar, Start::vymenJar);
			if (jar == null) {
				chyba(new File(adresar, JAR + ".new").isFile()
						? "Novou verzi GeoKuku se nepodařilo nainstalovat.\nZavřete GeoKuk, pokud ještě běží, a spusťte ho znovu."
						: "Ve složce " + adresar + " chybí soubor " + JAR + ".\nRozbalte znovu celý zip s programem GeoKuk.");
				return;
			}
			final List<String> prikaz = new ArrayList<>();
			prikaz.add(java().getPath());
			final File data = new File(koren(adresar), "data");
			prikaz.addAll(volbyJvm(data, pametMb(new File(data, "nastaveni.xml"), fyzickaPametMb())));
			prikaz.add("-jar");
			prikaz.add(jar.getPath());
			prikaz.addAll(parametry);
			final File nic = new File(System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null");
			new ProcessBuilder(prikaz).directory(adresar).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(nic)).start();
		} catch (final Exception e) {
			chyba("GeoKuk se nepodařilo spustit:\n" + e);
		}
	}

	static List<String> volbyJvm(final File data, final long pametMb) {
		final List<String> volby = new ArrayList<>();
		volby.add("-Xmx" + pametMb + "m");
		volby.add("-Djava.net.useSystemProxies=true");
		pridejDocasnouSlozku(volby, data);
		volby.add("-XX:-UsePerfData");
		// Stejné texty keší (autor, typ, země) sdílí jedno pole znaků; při přenačtení i se starou sadou.
		volby.add("-XX:+UseStringDeduplication");
		return volby;
	}

	/** Mimo složku programu nic: dočasné soubory do data/tmp, bez hsperfdata v systémovém TEMP. */
	static void pridejDocasnouSlozku(final List<String> prikaz, final File data) {
		final File docasne = new File(data, "tmp");
		// Do nezapisovatelné data/tmp by sqlite-jdbc nerozbalil knihovnu, pak zůstane systémový TEMP.
		if (lzeZapsat(docasne)) {
			prikaz.add("-Djava.io.tmpdir=" + docasne.getPath());
		}
	}

	/** Složku vytvoří, když chybí, a zkusí do ní zapsat soubor. */
	public static boolean lzeZapsat(final File slozka) {
		try {
			Files.createDirectories(slozka.toPath());
			final File zkouska = File.createTempFile("zapis", ".tmp", slozka);
			return zkouska.delete();
		} catch (final IOException | RuntimeException e) {
			return false;
		}
	}

	static File adresarSpoustece() throws URISyntaxException {
		final File umisteni = new File(Start.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		return umisteni.isFile() ? umisteni.getParentFile() : new File("").getAbsoluteFile();
	}

	/** Složka, kterou uživatel vidí: nad podsložkou program přenosného GeoKuku, jinak složka programu. */
	public static File koren(final File adresarProgramu) {
		final File nad = adresarProgramu.getParentFile();
		return nad != null && SLOZKA_PROGRAMU.equalsIgnoreCase(adresarProgramu.getName()) && new File(adresarProgramu, "start.jar").isFile() ? nad : adresarProgramu;
	}

	/** Zamkne zámek běžícího programu na celou dobu běhu, nebo vrátí null, když ho drží jiná instance. */
	public static FileLock zamkni(final File zamek) {
		try {
			zamek.getParentFile().mkdirs();
			@SuppressWarnings("resource") // kanál musí zůstat otevřený, dokud program běží
			final FileChannel kanal = new RandomAccessFile(zamek, "rw").getChannel();
			final FileLock lock = kanal.tryLock();
			if (lock == null) {
				kanal.close();
			}
			return lock;
		} catch (final IOException | OverlappingFileLockException e) {
			return null;
		}
	}

	/** Počká, až zámek nikdo nedrží. */
	static boolean pockejNaUkonceni(final File zamek, final long maxMs) throws InterruptedException {
		final long konec = System.currentTimeMillis() + maxMs;
		do {
			if (!jeZamceno(zamek)) {
				return true;
			}
			Thread.sleep(200);
		} while (System.currentTimeMillis() < konec);
		return false;
	}

	/** Zámek drží jiná instance. Zámek, který nejde ani vytvořit (nezapisovatelná složka), nikdo nedrží. */
	public static boolean jeZamceno(final File zamek) {
		try (FileChannel kanal = new RandomAccessFile(zamek, "rw").getChannel()) {
			final FileLock lock = kanal.tryLock();
			if (lock == null) {
				return true;
			}
			lock.release();
			return false;
		} catch (final OverlappingFileLockException e) {
			return true;
		} catch (final IOException e) {
			return false;
		}
	}

	interface Vymena {
		void vymen(File adresar) throws IOException;
	}

	/**
	 * Vymění staženou novou verzi a vrátí jar ke spuštění. Když výměna selže (soubor drží jiný program), spustí se stávající verze, a když
	 * chybí, předchozí verze z .bak; nová verze se zkusí nainstalovat při dalším spuštění. Vrátí null, když není co spustit.
	 */
	static File vyberJar(final File adresar, final Vymena vymena) {
		try {
			vymena.vymen(adresar);
		} catch (final IOException e) {
			System.err.println("Výměna " + JAR + " selhala: " + e);
		}
		final File jar = new File(adresar, JAR);
		if (jar.isFile()) {
			return jar;
		}
		final File bak = new File(adresar, JAR + ".bak");
		return bak.isFile() ? bak : null;
	}

	/** Stažená nová verze nahradí starou, ta zůstane jako .bak. */
	static void vymenJar(final File adresar) throws IOException {
		final Path nova = new File(adresar, JAR + ".new").toPath();
		if (!Files.isRegularFile(nova)) {
			return;
		}
		final Path jar = new File(adresar, JAR).toPath();
		if (Files.exists(jar)) {
			Files.move(jar, new File(adresar, JAR + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(nova, jar);
	}

	private static File java() {
		final File bin = new File(System.getProperty("java.home"), "bin");
		final File javaw = new File(bin, "javaw.exe");
		if (javaw.isFile()) {
			return javaw;
		}
		final File javaExe = new File(bin, "java.exe");
		return javaExe.isFile() ? javaExe : new File(bin, "java");
	}

	/** Paměť z nastavení, jinak polovina fyzické paměti, nejméně 1 GB a nejvýš 3 GB. */
	static int pametMb(final File nastaveni, final long fyzickaMb) {
		final int zNastaveni = pametZNastaveni(nastaveni);
		if (zNastaveni >= 256) {
			return zNastaveni;
		}
		return (int) Math.max(MIN_PAMET_MB, Math.min(MAX_PAMET_MB, fyzickaMb / 2));
	}

	static int pametZNastaveni(final File nastaveni) {
		if (!nastaveni.isFile()) {
			return 0;
		}
		try (InputStream in = new FileInputStream(nastaveni)) {
			final DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
			db.setErrorHandler(null);
			Element uzel = (Element) db.parse(in).getDocumentElement().getElementsByTagName("root").item(0);
			for (final String jmeno : new String[] { "geokuk", "current", "vseobecne" }) {
				uzel = dite(uzel, "node", jmeno);
				if (uzel == null) {
					return 0;
				}
			}
			for (Node map = uzel.getFirstChild(); map != null; map = map.getNextSibling()) {
				if (map instanceof Element && "map".equals(((Element) map).getTagName())) {
					for (Node e = map.getFirstChild(); e != null; e = e.getNextSibling()) {
						if (e instanceof Element && PAMET_KLIC.equals(((Element) e).getAttribute("key"))) {
							return Integer.parseInt(((Element) e).getAttribute("value").trim());
						}
					}
				}
			}
		} catch (final Exception e) {
			// poškozené nastavení ohlásí GeoKuk, paměť zvolí spouštěč
		}
		return 0;
	}

	private static Element dite(final Element rodic, final String tag, final String jmeno) {
		if (rodic == null) {
			return null;
		}
		for (Node n = rodic.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element && tag.equals(((Element) n).getTagName()) && jmeno.equals(((Element) n).getAttribute("name"))) {
				return (Element) n;
			}
		}
		return null;
	}

	public static long fyzickaPametMb() {
		final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		for (final String metoda : new String[] { "getTotalMemorySize", "getTotalPhysicalMemorySize" }) {
			try {
				final Method m = Class.forName("com.sun.management.OperatingSystemMXBean").getMethod(metoda);
				return ((Long) m.invoke(os)) / MB;
			} catch (final Exception | LinkageError e) {
				// zkusí se další
			}
		}
		return 2L * MIN_PAMET_MB;
	}

	private static void chyba(final String text) {
		try {
			JOptionPane.showMessageDialog(null, text, "GeoKuk", JOptionPane.ERROR_MESSAGE);
		} catch (final Throwable t) {
			System.err.println(text);
		}
	}

	private Start() {}
}
