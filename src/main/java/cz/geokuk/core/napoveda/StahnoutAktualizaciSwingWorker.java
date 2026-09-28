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
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MySwingWorker0;
import lombok.extern.slf4j.Slf4j;

/**
 * Stáhne novou verzi jako geokuk.jar.new vedle běžícího jaru a uloží k ní
 * spouštěč geokuk.cmd, který jar při příštím spuštění vymění.
 */
@Slf4j
public class StahnoutAktualizaciSwingWorker extends MySwingWorker0<Void, Void> {

	static final String JAR = "geokuk.jar";
	static final String SPOUSTEC = "geokuk.cmd";

	private final String verze;

	public StahnoutAktualizaciSwingWorker(final String verze) {
		this.verze = verze;
	}

	/** Výměnu jaru umí jen spouštěč pro Windows. */
	static boolean lzeInstalovat() {
		return FConst.JAR_DIR_EXISTUJE && System.getProperty("os.name", "").startsWith("Windows");
	}

	/** Uloží spouštěč vedle geokuk.jar, pokud tam ještě není. */
	public static void vytvorSpoustecPokudChybi() {
		if (lzeInstalovat()) {
			vytvorSpoustecPokudChybi(FConst.JAR_DIR);
		}
	}

	static void vytvorSpoustecPokudChybi(final File adresar) {
		final File spoustec = new File(adresar, SPOUSTEC);
		if (spoustec.exists() || !new File(adresar, JAR).isFile()) {
			return;
		}
		try (InputStream in = StahnoutAktualizaciSwingWorker.class.getResourceAsStream("/" + SPOUSTEC)) {
			if (in != null) {
				Files.copy(in, spoustec.toPath());
				log.info("Vytvořen spouštěč {}", spoustec);
			}
		} catch (final IOException e) {
			log.warn("Spouštěč " + spoustec + " nelze vytvořit.", e);
		}
	}

	static void stahni(final String zakladUrl, final File adresar) throws IOException {
		final String ocekavanySoucet = precti(zakladUrl + JAR + ".sha256").trim().split("\\s+")[0];
		final File docasny = new File(adresar, JAR + ".part");
		final MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
		try (InputStream in = new DigestInputStream(otevri(zakladUrl + JAR), md)) {
			Files.copy(in, docasny.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		final String soucet = String.format("%064x", new BigInteger(1, md.digest()));
		if (!soucet.equalsIgnoreCase(ocekavanySoucet)) {
			Files.delete(docasny.toPath());
			throw new IOException("Kontrolní součet staženého souboru nesouhlasí.");
		}
		try (ZipFile zip = new ZipFile(docasny)) {
			final ZipEntry spoustec = zip.getEntry(SPOUSTEC);
			if (spoustec != null) {
				try (InputStream in = zip.getInputStream(spoustec)) {
					Files.copy(in, new File(adresar, SPOUSTEC).toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		Files.move(docasny.toPath(), new File(adresar, JAR + ".new").toPath(), StandardCopyOption.REPLACE_EXISTING);
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
			Dlg.info("Verze " + verze + " je stažená. Nainstaluje se, až Geokuk ukončíte a spustíte znovu přes " + new File(FConst.JAR_DIR, SPOUSTEC) + ".", "Aktualizace");
		} catch (final ExecutionException e) {
			log.error("Stažení aktualizace selhalo.", e.getCause());
			Dlg.error("Novou verzi se nepodařilo stáhnout: " + e.getCause().getMessage() + "\nStáhněte ji ručně z " + FConst.LATEST_RELEASE_URL);
		}
	}
}
