package cz.geokuk.core.program;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.CRC32;

import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import lombok.extern.slf4j.Slf4j;

/**
 * Přehledová mapa světa ve složce offline map přenosného GeoKuku. Aktualizace vymění jen jar, proto nová verze mapu z jaru doplní nebo starší
 * verzi nahradí. Mapu, kterou uživatel smazal, znovu nepřidá: otisk naposledy přidané verze je v nastavení.
 */
@Slf4j
public final class PrehledovaMapa {

	public static final String SOUBOR = "prehled-svet-ne.map";
	private static final String ZDROJ = "/offline-mapy/" + SOUBOR;
	private static final String OTISK_value = "prehledovaMapaOtisk";

	/** Doplní nebo obnoví mapu v přenosném GeoKuku; jinde nedělá nic. Chybu jen zaloguje. */
	public static void doplnPriStartu() {
		if (!FConst.JAR_DIR_EXISTUJE || FConst.KOREN.equals(FConst.JAR_DIR)) {
			return;
		}
		try (InputStream in = PrehledovaMapa.class.getResourceAsStream(ZDROJ)) {
			if (in == null) {
				return;
			}
			final MyPreferences pref = MyPreferences.current().node(FPref.VSEOBECNE_node);
			final String otisk = doplni(KachleUmisteniSouboru.OFFLINE_MAPY_DIR.getFile(), cti(in), pref.get(OTISK_value, null));
			if (otisk != null) {
				pref.put(OTISK_value, otisk);
			}
		} catch (final IOException | RuntimeException e) {
			log.warn("Přehledovou mapu světa nelze doplnit", e);
		}
	}

	/**
	 * @param ulozenyOtisk
	 *            otisk naposledy přidané verze, null = mapa ještě nebyla přidána
	 * @return otisk mapy, který si zapamatovat, nebo null, když se nic nemění
	 */
	static String doplni(final File slozka, final byte[] mapa, final String ulozenyOtisk) throws IOException {
		final String otisk = otisk(mapa);
		final File cil = new File(slozka, SOUBOR);
		if (!cil.isFile()) {
			if (ulozenyOtisk != null) {
				return null; // uživatel ji smazal
			}
			zapis(cil, mapa);
			return otisk;
		}
		if (otisk.equals(ulozenyOtisk) && cil.length() == mapa.length) {
			return null;
		}
		final byte[] stavajici = Files.readAllBytes(cil.toPath());
		if (Arrays.equals(stavajici, mapa)) {
			return otisk;
		}
		// Jiný obsah je starší verze z dřívějška; soubor, který si uživatel nahradil vlastním, zůstává.
		if (ulozenyOtisk == null || ulozenyOtisk.equals(otisk(stavajici))) {
			zapis(cil, mapa);
			return otisk;
		}
		return null;
	}

	static String otisk(final byte[] data) {
		final CRC32 crc = new CRC32();
		crc.update(data);
		return data.length + ":" + Long.toHexString(crc.getValue());
	}

	/** Zapíše přes dočasný soubor, aby offline mapa nenačetla napůl zapsanou mapu. */
	private static void zapis(final File cil, final byte[] mapa) throws IOException {
		Files.createDirectories(cil.getParentFile().toPath());
		final Path docasny = new File(cil.getParentFile(), SOUBOR + ".tmp").toPath();
		Files.write(docasny, mapa);
		Files.move(docasny, cil.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		log.info("Přehledová mapa světa zapsána do {}", cil);
	}

	private static byte[] cti(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 22);
		final byte[] buffer = new byte[1 << 16];
		for (int n; (n = in.read(buffer)) > 0;) {
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}

	private PrehledovaMapa() {}
}
