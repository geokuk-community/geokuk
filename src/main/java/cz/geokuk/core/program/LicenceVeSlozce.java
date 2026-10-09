package cz.geokuk.core.program;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Složka {@code licence} přenosného GeoKuku vedle složky programu. Aktualizace vymění jen jar, takže nová verze složku při startu doplní
 * a obnoví z textů ve svém jaru.
 */
@Slf4j
public final class LicenceVeSlozce {

	static final String SLOZKA = "licence";

	/** Jméno souboru ve složce licence a cesta k jeho textu v jaru. */
	private static final List<String[]> SOUBORY = Arrays.asList(new String[] { "README.txt", "/licence/README.txt" }, new String[] { "LICENSE", "/LICENSE" },
			new String[] { "THIRD-PARTY.txt", "/THIRD-PARTY.txt" }, new String[] { "MAPOVE-PODKLADY.txt", "/MAPOVE-PODKLADY.txt" });

	/** Doplní složku licence v přenosném GeoKuku; jinde nedělá nic. Chybu jen zaloguje. */
	public static void doplnPriStartu() {
		if (!FConst.JAR_DIR_EXISTUJE || FConst.KOREN.equals(FConst.JAR_DIR)) {
			return;
		}
		doplni(new File(FConst.KOREN, SLOZKA));
	}

	static void doplni(final File slozka) {
		try {
			for (final String[] soubor : SOUBORY) {
				final byte[] text = precti(soubor[1]);
				if (text == null) {
					continue;
				}
				final File cil = new File(slozka, soubor[0]);
				if (!cil.isFile() || !Arrays.equals(Files.readAllBytes(cil.toPath()), text)) {
					Files.createDirectories(slozka.toPath());
					Files.write(cil.toPath(), text);
				}
			}
		} catch (final IOException | RuntimeException e) {
			log.warn("Složku s licencemi {} nelze doplnit", slozka, e);
		}
	}

	private static byte[] precti(final String zdroj) throws IOException {
		try (InputStream in = LicenceVeSlozce.class.getResourceAsStream(zdroj)) {
			if (in == null) {
				return null;
			}
			final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			final byte[] buffer = new byte[8192];
			for (int n; (n = in.read(buffer)) > 0;) {
				out.write(buffer, 0, n);
			}
			return out.toByteArray();
		}
	}

	private LicenceVeSlozce() {}
}
