package cz.geokuk.util.file;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;

/**
 * Zápis souboru, po kterém je na disku buď celý nový obsah, nebo nedotčený
 * ten původní. Píše se vedle do dočasného souboru, který se nakonec přejmenuje.
 */
public final class BezpecnyZapis {

	/** Zapisuje obsah souboru do proudu. */
	public interface Obsah {
		void zapis(OutputStream out) throws IOException;
	}

	/** Zapisuje obsah textového souboru. */
	public interface TextovyObsah {
		void zapis(PrintWriter out) throws IOException;
	}

	/** Přesun souboru, v testech podvržitelný. */
	interface Presun {
		void presun(Path zdroj, Path cil, CopyOption... volby) throws IOException;
	}

	private static final int POKUSU_PREJMENOVANI = 5;

	static Presun presun = Files::move;

	public static void zapis(final File soubor, final Obsah obsah) throws IOException {
		final File slozka = soubor.getAbsoluteFile().getParentFile();
		if (slozka != null && !slozka.isDirectory() && !slozka.mkdirs()) {
			throw new IOException("Složku " + slozka + " nelze vytvořit.");
		}
		final File docasny = File.createTempFile(soubor.getName() + ".", ".tmp", slozka);
		try {
			try (FileOutputStream fos = new FileOutputStream(docasny)) {
				final BufferedOutputStream bos = new BufferedOutputStream(fos);
				obsah.zapis(bos);
				bos.flush();
				// data musí být na disku dřív, než soubor přejmenujeme
				fos.getFD().sync();
			}
			prejmenuj(docasny, soubor);
		} finally {
			// po úspěšném přejmenování už dočasný soubor neexistuje
			Soubory.smaz(docasny);
		}
	}

	public static void zapisText(final File soubor, final Charset kodovani, final TextovyObsah obsah) throws IOException {
		zapis(soubor, out -> {
			final PrintWriter wrt = new PrintWriter(new OutputStreamWriter(out, kodovani));
			obsah.zapis(wrt);
			wrt.flush();
			// PrintWriter výjimky polyká, jinak by se chyba zápisu ztratila
			if (wrt.checkError()) {
				throw new IOException("Zápis souboru " + soubor + " se nezdařil.");
			}
		});
	}

	private static void prejmenuj(final File zdroj, final File cil) throws IOException {
		for (int pokus = 1;; pokus++) {
			try {
				prejmenujJednou(zdroj.toPath(), cil.toPath());
				return;
			} catch (final AccessDeniedException e) {
				// Ve Windows cíl chvíli drží jiný proces (antivir, indexace), přístup se brzy uvolní.
				if (pokus >= POKUSU_PREJMENOVANI) {
					throw e;
				}
				try {
					Thread.sleep(50L * pokus);
				} catch (final InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	private static void prejmenujJednou(final Path zdroj, final Path cil) throws IOException {
		try {
			presun.presun(zdroj, cil, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (final AtomicMoveNotSupportedException e) {
			presun.presun(zdroj, cil, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private BezpecnyZapis() {}
}
