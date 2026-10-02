package cz.geokuk.start;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
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
	static final long MB = 1024L * 1024;
	static final int MIN_PAMET_MB = 1024;
	static final int MAX_PAMET_MB = 3072;
	/** Klíč nastavení v uzlu {@code geokuk/current/vseobecne}, 0 = zvolí spouštěč. */
	public static final String PAMET_KLIC = "pametMb";

	public static void main(final String[] args) {
		try {
			final File adresar = adresarSpoustece();
			vymenJar(adresar);
			final File jar = new File(adresar, JAR);
			if (!jar.isFile()) {
				chyba("Ve složce " + adresar + " chybí soubor " + JAR + ".\nRozbalte znovu celý zip s programem GeoKuk.");
				return;
			}
			final List<String> prikaz = new ArrayList<>();
			prikaz.add(java().getPath());
			prikaz.add("-Xmx" + pametMb(new File(new File(adresar, "data"), "nastaveni.xml"), fyzickaPametMb()) + "m");
			prikaz.add("-Djava.net.useSystemProxies=true");
			// Mimo složku programu nic: dočasné soubory do data/tmp, bez hsperfdata v systémovém TEMP.
			final File docasne = new File(new File(adresar, "data"), "tmp");
			docasne.mkdirs();
			prikaz.add("-Djava.io.tmpdir=" + docasne.getPath());
			prikaz.add("-XX:-UsePerfData");
			prikaz.add("-jar");
			prikaz.add(jar.getPath());
			prikaz.addAll(Arrays.asList(args));
			final File nic = new File(System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null");
			new ProcessBuilder(prikaz).directory(adresar).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(nic)).start();
		} catch (final Exception e) {
			chyba("GeoKuk se nepodařilo spustit:\n" + e);
		}
	}

	static File adresarSpoustece() throws URISyntaxException {
		final File umisteni = new File(Start.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		return umisteni.isFile() ? umisteni.getParentFile() : new File("").getAbsoluteFile();
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
