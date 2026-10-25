package cz.geokuk.start;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Archiv tříd programu (AppCDS) pro rychlejší start, jen od Javy 19. Hotový archiv se nikdy nepřepisuje, běžící program ho má namapovaný. První běh bez archivu ho při
 * ukončení zapíše do souboru {@code nove-…}, spouštěč ho při dalším startu ověří a přejmenuje. Jméno hotového archivu obsahuje Javu a jar, jiný jar nebo jiná Java
 * archiv nepoužije a Java ho sama nepřegeneruje. Jakákoli chyba znamená start bez archivu.
 *
 * Nesmí používat nic z ostatních tříd programu, do {@code start.jar} se dávají jen třídy tohoto balíčku.
 */
final class Cds {

	static final String SLOZKA = "cds";
	static final String PREDPONA_HOTOVEHO = "geokuk-";
	static final String PREDPONA_NOVEHO = "nove-";
	static final String PRIPONA = ".jsa";
	/** Vedle hotového archivu je soubor s jeho délkou z doby ověření; oříznutý nebo jinak změněný archiv Java mlčky ignoruje a nikdy by se neopravil. */
	static final String PRIPONA_DELKY = ".delka";
	/** Menší soubor není použitelný archiv. */
	static final long MIN_ARCHIV_B = 5L << 20;
	/** Rozepsaný archiv mladší než toto se nechá být, program do něj ještě může zapisovat. */
	static final long ROZEPSANY_MS = 30_000;
	static final long OVERENI_MAX_MS = 20_000;
	/** Při méně volném místě se archiv nezapisuje, plný disk by zkoušel zápis při každém ukončení. */
	static final long MIN_VOLNE_MISTO_B = 150L << 20;
	/** Kontrolní součet archivu; bez něj Java archiv s nulami na konci (po výpadku proudu) použije a spadne. */
	static final String KONTROLA = "-XX:+VerifySharedSpaces";
	static final int PRVNI_VERZE_JAVY = 19;

	/** Zkouška, že Java archiv přijme. */
	interface Overeni {
		boolean overit(File archiv);
	}

	private Cds() {
	}

	/** Přepínače JVM pro archiv; nikdy nevyhodí výjimku. */
	static List<String> prepinace(final String verzeJavy, final File javaHome, final String jmenoJavy, final File data, final File jar, final File zamekInstance, final Overeni overeni,
			final long ted) {
		return prepinace(verzeJavy, javaHome, jmenoJavy, data, jar, zamekInstance, overeni, ted, () -> new File(data, SLOZKA).getUsableSpace());
	}

	static List<String> prepinace(final String verzeJavy, final File javaHome, final String jmenoJavy, final File data, final File jar, final File zamekInstance, final Overeni overeni,
			final long ted, final LongSupplier volneMisto) {
		final List<String> prikaz = new ArrayList<>();
		try {
			if (!jeJavaDostatecneNova(verzeJavy)) {
				return prikaz;
			}
			final File slozka = new File(data, SLOZKA);
			if (!Start.lzeZapsat(slozka)) {
				return prikaz;
			}
			final String id = id(jmenoJavy, javaHome, jar);
			final File hotovy = new File(slozka, PREDPONA_HOTOVEHO + id + PRIPONA);
			uklid(slozka, hotovy, id, overeni, ted);
			if (hotovy.isFile()) {
				prikaz.add(KONTROLA);
				prikaz.add("-XX:SharedArchiveFile=" + hotovy.getPath());
			} else if (maZakladniArchiv(javaHome) && volneMisto.getAsLong() >= MIN_VOLNE_MISTO_B && !Start.jeZamceno(zamekInstance) && !jeCerstvyRozepsany(slozka, id, ted)) {
				prikaz.add(KONTROLA);
				// Archiv vznikne jen v prvním běhu; druhá instance, která hned skončí, dostane vlastní soubor a zůstane nepovýšená.
				prikaz.add("-XX:ArchiveClassesAtExit=" + new File(slozka, PREDPONA_NOVEHO + id + "-" + UUID.randomUUID() + PRIPONA).getPath());
			}
		} catch (final RuntimeException e) {
			prikaz.clear();
		}
		return prikaz;
	}

	/** Předchozí běh právě zapsal archiv a čeká na povýšení; další běh ho nezapisuje znovu a nezdržuje tím ukončení. */
	private static boolean jeCerstvyRozepsany(final File slozka, final String id, final long ted) {
		final File[] soubory = slozka.listFiles();
		if (soubory != null) {
			for (final File f : soubory) {
				if (f.getName().startsWith(PREDPONA_NOVEHO + id + "-") && ted - f.lastModified() < ROZEPSANY_MS) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean jeJavaDostatecneNova(final String verzeJavy) {
		try {
			final String[] casti = verzeJavy.split("\\.");
			return Integer.parseInt("1".equals(casti[0]) && casti.length > 1 ? casti[1] : casti[0]) >= PRVNI_VERZE_JAVY;
		} catch (final RuntimeException e) {
			return false;
		}
	}

	/** Dynamický archiv se staví nad základním archivem Javy; přibalená Java bez něj archiv vytvořit neumí. Na Linuxu je v lib/server, na Windows v bin/server. */
	static boolean maZakladniArchiv(final File javaHome) {
		for (final String slozka : new String[] { "lib", "bin" }) {
			final File server = new File(new File(javaHome, slozka), "server");
			if (new File(server, "classes.jsa").isFile() || new File(server, "classes_nocoops.jsa").isFile()) {
				return true;
			}
		}
		return false;
	}

	/** Archiv platí jen pro tutéž Javu a tentýž jar na témže místě. */
	static String id(final String jmenoJavy, final File javaHome, final File jar) {
		final String cesta = javaHome.getAbsolutePath() + '|' + jar.getAbsolutePath();
		return jmenoJavy.replaceAll("[^A-Za-z0-9.+_]", "_") + "-" + jar.length() + "-" + Long.toHexString(jar.lastModified()) + "-" + Integer.toHexString(cesta.hashCode());
	}

	/** Povýší nejlepší rozepsaný archiv a smaže, co už nikdo nepotřebuje. */
	static void uklid(final File slozka, final File hotovy, final String id, final Overeni overeni, final long ted) {
		final File[] soubory = slozka.listFiles();
		if (soubory == null) {
			return;
		}
		if (hotovy.isFile() && (hotovy.length() < MIN_ARCHIV_B || !jeDelkaZapsana(hotovy))) {
			smaz(hotovy);
			smaz(souborDelky(hotovy));
		}
		if (!hotovy.isFile()) {
			File nejlepsi = null;
			for (final File f : soubory) {
				if (f.getName().startsWith(PREDPONA_NOVEHO + id + "-") && f.getName().endsWith(PRIPONA) && ted - f.lastModified() >= ROZEPSANY_MS && f.length() >= MIN_ARCHIV_B
						&& (nejlepsi == null || f.length() > nejlepsi.length())) {
					nejlepsi = f;
				}
			}
			if (nejlepsi != null) {
				povys(nejlepsi, hotovy, overeni);
			}
		}
		for (final File f : soubory) {
			final String jmeno = f.getName();
			if (jmeno.endsWith(PRIPONA_DELKY) && !new File(slozka, jmeno.substring(0, jmeno.length() - PRIPONA_DELKY.length())).isFile()) {
				smaz(f);
			}
			if (!f.isFile() || !jmeno.endsWith(PRIPONA) || f.equals(hotovy)) {
				continue;
			}
			final boolean stareHotove = jmeno.startsWith(PREDPONA_HOTOVEHO);
			final boolean dokoncenyRozepsany = jmeno.startsWith(PREDPONA_NOVEHO) && ted - f.lastModified() >= ROZEPSANY_MS;
			if (stareHotove || dokoncenyRozepsany) {
				smaz(f);
			}
		}
	}

	private static void povys(final File kandidat, final File hotovy, final Overeni overeni) {
		if (!overeni.overit(kandidat)) {
			return;
		}
		try {
			final long delka = kandidat.length();
			Files.move(kandidat.toPath(), hotovy.toPath(), StandardCopyOption.ATOMIC_MOVE);
			Files.write(souborDelky(hotovy).toPath(), Long.toString(delka).getBytes(StandardCharsets.UTF_8));
		} catch (final IOException | RuntimeException e) {
			// zůstane rozepsaný, příště se zkusí znovu
		}
	}

	static File souborDelky(final File hotovy) {
		return new File(hotovy.getPath() + PRIPONA_DELKY);
	}

	private static boolean jeDelkaZapsana(final File hotovy) {
		try {
			return Long.parseLong(new String(Files.readAllBytes(souborDelky(hotovy).toPath()), StandardCharsets.UTF_8).trim()) == hotovy.length();
		} catch (final IOException | RuntimeException e) {
			return false;
		}
	}

	private static void smaz(final File f) {
		try {
			// Java zapisuje archiv jen pro čtení a Windows takový soubor nesmažou.
			final DosFileAttributeView dos = Files.getFileAttributeView(f.toPath(), DosFileAttributeView.class);
			if (dos != null) {
				dos.setReadOnly(false);
			}
			Files.deleteIfExists(f.toPath());
		} catch (final IOException | RuntimeException e) {
			// smaže se příště
		}
	}

	/**
	 * Spustí Javu s archivem a pozná, jestli ho opravdu použila: bez varování z CDS a s kódem 0. Poškozený archiv Java při {@code -XX:+VerifySharedSpaces} mlčky odmítne,
	 * {@code -Xshare:auto} nezobrazí okno s chybou ani ve {@code javaw}; záznam o případném pádu patří do logu programu, ne do složky programu.
	 */
	static Overeni overeniJavou(final File java, final File jar, final File pracovni, final File logSlozka) {
		return archiv -> {
			File varovani = null;
			try {
				Files.createDirectories(logSlozka.toPath());
				// Soubor se nezakládá předem, Java by ho při startu přejmenovala na .0.
				varovani = new File(logSlozka, "cds-overeni-" + UUID.randomUUID() + ".log");
				final File nic = new File(System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null");
				final Process p = new ProcessBuilder(java.getPath(), "-Xshare:auto", KONTROLA, "-XX:SharedArchiveFile=" + archiv.getPath(), "-Xlog:cds*=warning:file=" + varovani.getPath(),
						"-XX:ErrorFile=" + new File(logSlozka, "hs_err_cds_%p.log").getPath(), "-cp", jar.getPath(), "-version").directory(pracovni).redirectErrorStream(true)
								.redirectOutput(ProcessBuilder.Redirect.appendTo(nic)).start();
				if (!p.waitFor(OVERENI_MAX_MS, TimeUnit.MILLISECONDS)) {
					p.destroyForcibly();
					return false;
				}
				return p.exitValue() == 0 && (!varovani.exists() || varovani.length() == 0);
			} catch (final IOException | RuntimeException e) {
				return false;
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			} finally {
				if (varovani != null) {
					smaz(varovani);
					smaz(new File(varovani.getPath() + ".0"));
				}
			}
		};
	}
}
