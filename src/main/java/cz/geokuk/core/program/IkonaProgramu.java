package cz.geokuk.core.program;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

/** Ikona programu pro zástupce ve Windows: soubor geokuk.ico ve složce s programem se drží shodný s ikonou v geokuk.jar. */
@Slf4j
final class IkonaProgramu {

	static final String NAZEV = "geokuk.ico";

	private IkonaProgramu() {
	}

	/** Na pozadí po zobrazení okna; jen přenosná verze ve Windows. */
	static void aktualizujVeSlozceProgramuNaPozadi() {
		if (!VytvoritZastupceAction.lzeVytvorit()) {
			return;
		}
		final Thread vlakno = new Thread(() -> aktualizuj(FConst.JAR_DIR.toPath()), "Ikona programu");
		vlakno.setDaemon(true);
		vlakno.start();
	}

	static void aktualizuj(final Path slozka) {
		try (InputStream in = IkonaProgramu.class.getResourceAsStream("/" + NAZEV)) {
			if (in == null) {
				log.warn("Ikona {} v programu chybí", NAZEV);
				return;
			}
			zapis(slozka, precti(in));
		} catch (final IOException | RuntimeException e) {
			log.warn("Ikonu programu ve složce {} nelze aktualizovat", slozka, e);
		}
	}

	private static byte[] precti(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buf = new byte[8192];
		for (int n; (n = in.read(buf)) > 0;) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	/** Zapíše ikonu, jen když chybí nebo se liší; přes dočasný soubor, ať zástupce nikdy nevidí rozepsaný soubor. */
	static boolean zapis(final Path slozka, final byte[] ikona) throws IOException {
		final Path cil = slozka.resolve(NAZEV);
		if (Files.isRegularFile(cil) && Arrays.equals(Files.readAllBytes(cil), ikona)) {
			return false;
		}
		final Path docasny = Files.createTempFile(slozka, "geokuk", ".ico.tmp");
		try {
			Files.write(docasny, ikona);
			Files.move(docasny, cil, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} finally {
			Files.deleteIfExists(docasny);
		}
	}
}
