package cz.geokuk.plugins.kesoid;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.Assert;

/**
 * Porovná vykreslený obrázek s etalonem v {@code src/test/resources/golden}. Malé rozdíly (vyhlazování na jiném systému) projdou.
 * Etalon se přepíše s {@code -Dgolden.prepsat=true}.
 */
final class GoldenSnimek {

	/** Největší rozdíl jednoho kanálu, který se ještě nepočítá. */
	static final int TOLERANCE_KANALU = 8;
	/** Kolik pixelů smí být jiných. */
	static final double PODIL_JINYCH = 0.0001;

	private static final File ETALONY = new File("src/test/resources/golden");
	private static final File VYSTUPY = new File("target/golden");

	static void porovnej(final String jmeno, final BufferedImage skutecny) throws IOException {
		final File etalon = new File(ETALONY, jmeno + ".png");
		if (Boolean.getBoolean("golden.prepsat")) {
			ETALONY.mkdirs();
			ImageIO.write(skutecny, "png", etalon);
			return;
		}
		Assert.assertTrue("chybí etalon " + etalon + ", vytvoří ho -Dgolden.prepsat=true", etalon.isFile());
		final BufferedImage ocekavany = ImageIO.read(etalon);
		final String rozdil = rozdil(ocekavany, skutecny);
		if (rozdil != null) {
			VYSTUPY.mkdirs();
			ImageIO.write(skutecny, "png", new File(VYSTUPY, jmeno + "-skutecny.png"));
			ImageIO.write(mapaRozdilu(ocekavany, skutecny), "png", new File(VYSTUPY, jmeno + "-rozdil.png"));
			Assert.fail(jmeno + ": " + rozdil + " (skutečný obrázek a rozdíl v " + VYSTUPY + ")");
		}
	}

	/** Popis rozdílu, nebo null, když se obrázky shodují v toleranci. */
	static String rozdil(final BufferedImage a, final BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return "jiný rozměr " + b.getWidth() + "×" + b.getHeight() + " místo " + a.getWidth() + "×" + a.getHeight();
		}
		int jinych = 0;
		for (int y = 0; y < a.getHeight(); y++) {
			for (int x = 0; x < a.getWidth(); x++) {
				if (jiny(a.getRGB(x, y), b.getRGB(x, y))) {
					jinych++;
				}
			}
		}
		final int povoleno = (int) (a.getWidth() * a.getHeight() * PODIL_JINYCH);
		return jinych > povoleno ? jinych + " jiných pixelů, povoleno " + povoleno : null;
	}

	private static boolean jiny(final int p, final int q) {
		for (int posun = 0; posun < 32; posun += 8) {
			if (Math.abs((p >>> posun & 0xff) - (q >>> posun & 0xff)) > TOLERANCE_KANALU) {
				return true;
			}
		}
		return false;
	}

	private static BufferedImage mapaRozdilu(final BufferedImage a, final BufferedImage b) {
		final BufferedImage m = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < Math.min(a.getHeight(), b.getHeight()); y++) {
			for (int x = 0; x < Math.min(a.getWidth(), b.getWidth()); x++) {
				m.setRGB(x, y, jiny(a.getRGB(x, y), b.getRGB(x, y)) ? 0xff0000 : 0xffffff);
			}
		}
		return m;
	}

	private GoldenSnimek() {}
}
