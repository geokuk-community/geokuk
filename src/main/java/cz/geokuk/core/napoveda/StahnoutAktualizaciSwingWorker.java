package cz.geokuk.core.napoveda;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MySwingWorker0;
import lombok.extern.slf4j.Slf4j;

/**
 * Stáhne novou verzi. V přenosné verzi ji uloží jako geokuk.jar.new, kterou při příštím spuštění vymění spouštěč start.jar, a rovnou nahradí i
 * start.jar. Jinde než ve Windows jde běžící jar nahradit hned, starý zůstane jako .bak.
 */
@Slf4j
public class StahnoutAktualizaciSwingWorker extends MySwingWorker0<Void, Void> {

	static final String JAR = "geokuk.jar";
	static final String START = "start.jar";

	/** Nová verze potřebuje novější Javu, než je přibalená. */
	static class YNovaJava extends IOException {
		private static final long serialVersionUID = 1L;

		YNovaJava(final String minimalni) {
			super("Nová verze potřebuje Javu " + minimalni + ", program běží v Javě " + VerzeJavy.aktualni() + ".");
		}
	}

	private final String verze;

	public StahnoutAktualizaciSwingWorker(final String verze) {
		this.verze = verze;
	}

	static boolean prenosna(final File adresar) {
		return new File(adresar, START).isFile();
	}

	private static boolean windows() {
		return System.getProperty("os.name", "").startsWith("Windows");
	}

	/** Ve Windows jar vymění jen spouštěč přenosné verze. */
	static boolean lzeInstalovat() {
		return FConst.JAR_DIR_EXISTUJE && (prenosna(FConst.JAR_DIR) || !windows());
	}

	static void stahni(final String zakladUrl, final File adresar) throws IOException {
		final String minimalni = minimalniJava(zakladUrl);
		if (VerzeJavy.jeStarsi(VerzeJavy.aktualni(), minimalni)) {
			throw new YNovaJava(minimalni);
		}
		final File jar = stahniOverene(zakladUrl, JAR, adresar);
		if (prenosna(adresar)) {
			Files.move(jar.toPath(), new File(adresar, JAR + ".new").toPath(), StandardCopyOption.REPLACE_EXISTING);
			final File start = stahniOverene(zakladUrl, START, adresar);
			Files.move(start.toPath(), new File(adresar, START).toPath(), StandardCopyOption.REPLACE_EXISTING);
		} else {
			final File stary = new File(adresar, JAR);
			if (stary.isFile()) {
				Files.copy(stary.toPath(), new File(adresar, JAR + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
			Files.move(jar.toPath(), stary.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Nejnižší Java nové verze, starší vydání soubor nemají. */
	private static String minimalniJava(final String zakladUrl) {
		try (InputStream in = otevri(zakladUrl + VerzeJavy.SOUBOR)) {
			final Properties p = new Properties();
			p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
			return p.getProperty("minimalni");
		} catch (final IOException e) {
			return null;
		}
	}

	/** Stáhne soubor jako .part a ověří kontrolní součet. */
	private static File stahniOverene(final String zakladUrl, final String jmeno, final File adresar) throws IOException {
		final String ocekavanySoucet = precti(zakladUrl + jmeno + ".sha256").trim().split("\\s+")[0];
		final File docasny = new File(adresar, jmeno + ".part");
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try (InputStream in = new DigestInputStream(otevri(zakladUrl + jmeno), md)) {
			Files.copy(in, docasny.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		final String soucet = String.format("%064x", new BigInteger(1, md.digest()));
		if (!soucet.equalsIgnoreCase(ocekavanySoucet)) {
			Files.delete(docasny.toPath());
			throw new IOException("Kontrolní součet staženého souboru " + jmeno + " nesouhlasí.");
		}
		return docasny;
	}

	private static InputStream otevri(final String url) throws IOException {
		final URLConnection connection = new URL(url).openConnection();
		connection.setRequestProperty("User-Agent", "Geokuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")");
		connection.setConnectTimeout(60000);
		connection.setReadTimeout(60000);
		return connection.getInputStream();
	}

	private static String precti(final String url) throws IOException {
		try (Scanner sc = new Scanner(otevri(url), "UTF-8").useDelimiter("\\A")) {
			return sc.hasNext() ? sc.next() : "";
		}
	}

	@Override
	protected Void doInBackground() throws Exception {
		stahni(FConst.RELEASE_DOWNLOAD_URL + "v" + verze + "/", FConst.JAR_DIR);
		return null;
	}

	@Override
	protected void donex() throws Exception {
		try {
			get();
			Diagnostika.zaznamenej("Stažena verze " + verze);
			if (!Restart.lze()) {
				Dlg.info("Verze " + verze + " je stažená. Nainstaluje se, až GeoKuk ukončíte a spustíte znovu.", "Aktualizace");
				return;
			}
			final Object[] volby = { "Restartovat", "Později" };
			final int volba = JOptionPane.showOptionDialog(Dlg.parentFrame(), "Verze " + verze + " je stažená a nainstaluje se při příštím spuštění.\nRestartovat GeoKuk teď?",
					"Aktualizace", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, volby, volby[0]);
			if (volba == 0) {
				Diagnostika.zaznamenej("Restart po aktualizaci");
				Restart.restartuj();
			}
		} catch (final ExecutionException e) {
			if (e.getCause() instanceof YNovaJava) {
				Diagnostika.zaznamenej("Verze " + verze + " potřebuje novější Javu");
				Dlg.info(e.getCause().getMessage() + "\nStáhněte nový zip s programem GeoKuk z " + FConst.RELEASE_TAG_URL + verze
						+ "\na rozbalte ho přes stávající složku s programem, data a nastavení zůstanou.", "Aktualizace");
				return;
			}
			log.error("Stažení aktualizace selhalo.", e.getCause());
			Diagnostika.zaznamenej("Stažení verze " + verze + " selhalo: " + e.getCause());
			Dlg.error("Novou verzi se nepodařilo stáhnout: " + e.getCause().getMessage() + "\nStáhněte ji ručně z " + FConst.RELEASE_TAG_URL + verze);
		}
	}
}
