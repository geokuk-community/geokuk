package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.plugins.mapy.kachle.data.*;

/**
 * Stáhne dlaždice ze všech mapových podkladů a ověří, že vracejí obrázek. Běží jen podle rozvrhu
 * (workflow mapove-podklady), při běžném buildu ne. Výsledek zapíše do target/mapove-podklady.md.
 */
public class MapovePodkladyIT {

	private static final Wgs PRAHA = new Wgs(50.0875, 14.4213);
	private static final Wgs BRATISLAVA = new Wgs(48.1486, 17.1077);
	private static final int[] ZOOMY = { 8, 13, 16 };
	private static final int TIMEOUT_S = 30;

	@Test
	public void vsechnyPodkladyVraceji() throws Exception {
		final KachloDownloader downloader = new KachloDownloader();
		final ExecutorService executor = Executors.newCachedThreadPool();
		final StringBuilder report = new StringBuilder("| Vrstva | Výsledek |\n|---|---|\n");
		final List<String> nefunkcni = new ArrayList<>();
		try {
			for (final EKaType typ : EKaType.values()) {
				final Wgs misto = typ.name().startsWith("TUR_FREEMAP_SK") ? BRATISLAVA : PRAHA;
				String chyba = null;
				for (final int zoom : ZOOMY) {
					final int z = typ.fitMoumer(zoom);
					final URL url = new Ka(KaLoc.ofJZ(misto.toMou(), z), typ).getUrl();
					try {
						final Future<ImageWithData> stazeni = executor.submit(() -> downloader.downloadImage(url, typ.getHlavicky()));
						final ImageWithData img;
						try {
							img = stazeni.get(TIMEOUT_S, TimeUnit.SECONDS);
						} finally {
							stazeni.cancel(true);
						}
						if (jednobarevny(img)) {
							chyba = "z" + z + ": prázdná dlaždice";
						}
					} catch (final TimeoutException e) {
						chyba = "z" + z + ": bez odpovědi do " + TIMEOUT_S + " s";
					} catch (final ExecutionException e) {
						chyba = "z" + z + ": " + e.getCause();
					}
					if (chyba != null) {
						break;
					}
				}
				report.append("| ").append(typ.name()).append(" (").append(typ.getNazev()).append(") | ").append(chyba == null ? "OK" : chyba.replace("|", "/")).append(" |\n");
				if (chyba != null) {
					nefunkcni.add(typ.name());
				}
			}
		} finally {
			executor.shutdownNow();
			final File soubor = new File("target/mapove-podklady.md");
			Files.write(soubor.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
			System.out.println(report);
		}
		Assert.assertTrue("Nefunkční podklady: " + nefunkcni, nefunkcni.isEmpty());
	}

	private static boolean jednobarevny(final ImageWithData img) {
		if (!(img.getImg() instanceof BufferedImage)) {
			return false;
		}
		final BufferedImage bi = (BufferedImage) img.getImg();
		final int prvni = bi.getRGB(0, 0);
		for (int y = 0; y < bi.getHeight(); y += 4) {
			for (int x = 0; x < bi.getWidth(); x += 4) {
				if (bi.getRGB(x, y) != prvni) {
					return false;
				}
			}
		}
		return true;
	}
}
