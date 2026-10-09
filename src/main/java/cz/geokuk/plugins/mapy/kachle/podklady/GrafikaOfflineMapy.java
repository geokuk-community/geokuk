package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.mapsforge.core.graphics.*;
import org.mapsforge.core.mapelements.PointTextContainer;
import org.mapsforge.core.mapelements.SymbolContainer;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.awt.graphics.AwtBitmap;
import org.mapsforge.map.awt.graphics.AwtGraphicFactory;
import org.mapsforge.map.awt.graphics.AwtPaintSPameti;

import lombok.extern.slf4j.Slf4j;

/**
 * Grafika mapsforge pro AWT, která si vykreslené symboly SVG tématu pamatuje, i na disku, a u písma šířky textů. Téma vykresluje všechny symboly už při načtení (paws_5 asi 1 400),
 * což je většina doby načtení; podruhé se vezmou ze souboru. Jedna instance na jedno téma, různá témata můžou mít pod stejnou cestou jiný obrázek.
 */
@Slf4j
final class GrafikaOfflineMapy implements GraphicFactory {

	private static final int VERZE_SOUBORU = 1;

	private static final GraphicFactory AWT = AwtGraphicFactory.INSTANCE;

	/** Klíč je hash, kterým mapsforge symbol označuje (cesta, rozměry, zdroj), a měřítko. */
	private final Map<String, BufferedImage> svg = new ConcurrentHashMap<>();

	/** Soubor se symboly, null = jen v paměti. */
	private final File soubor;
	private final AtomicBoolean pribylo = new AtomicBoolean();

	GrafikaOfflineMapy(final File soubor) {
		this.soubor = soubor;
		if (soubor != null && soubor.isFile()) {
			try {
				nacti();
			} catch (final IOException | RuntimeException e) {
				svg.clear();
				log.debug("Symboly tématu nejde načíst ze {}: {}", soubor, e.toString());
			}
		}
	}

	private void nacti() throws IOException {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(soubor))))) {
			if (in.readInt() != VERZE_SOUBORU) {
				return;
			}
			final int pocet = in.readInt();
			for (int i = 0; i < pocet; i++) {
				final String klic = in.readUTF();
				final int w = in.readInt();
				final int h = in.readInt();
				final int[] pixely = new int[w * h];
				for (int j = 0; j < pixely.length; j++) {
					pixely[j] = in.readInt();
				}
				final BufferedImage obrazek = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				obrazek.setRGB(0, 0, w, h, pixely, 0, w);
				svg.put(klic, obrazek);
			}
		}
	}

	File getSoubor() {
		return soubor;
	}

	/** Uloží symboly, jestli nějaké přibyly; soubor se nahradí celý najednou. */
	void uloz() {
		if (soubor == null || !pribylo.getAndSet(false)) {
			return;
		}
		try {
			Files.createDirectories(soubor.getParentFile().toPath());
			final File docasny = new File(soubor.getPath() + ".tmp");
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(docasny))))) {
				final Map<String, BufferedImage> kopie = new java.util.HashMap<>(svg);
				out.writeInt(VERZE_SOUBORU);
				out.writeInt(kopie.size());
				for (final Map.Entry<String, BufferedImage> e : kopie.entrySet()) {
					final BufferedImage obrazek = e.getValue();
					final int w = obrazek.getWidth();
					final int h = obrazek.getHeight();
					out.writeUTF(e.getKey());
					out.writeInt(w);
					out.writeInt(h);
					for (final int p : obrazek.getRGB(0, 0, w, h, null, 0, w)) {
						out.writeInt(p);
					}
				}
			}
			Files.move(docasny.toPath(), soubor.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {
			log.debug("Symboly tématu nejde uložit do {}: {}", soubor, e.toString());
		}
	}

	int pocetSymbolu() {
		return svg.size();
	}

	/** Symbol ze sdíleného obrázku; obrázek se jen čte, každý symbol má vlastní obálku s vlastním počítáním odkazů. */
	private static final class SdilenySymbol extends AwtBitmap implements ResourceBitmap {
		SdilenySymbol(final BufferedImage obrazek) {
			super(obrazek);
		}
	}

	@Override
	public ResourceBitmap renderSvg(final InputStream inputStream, final float scaleFactor, final int width, final int height, final int percent, final int hash) throws IOException {
		final String klic = hash + "/" + scaleFactor + "/" + width + "/" + height + "/" + percent;
		BufferedImage obrazek = svg.get(klic);
		if (obrazek == null) {
			obrazek = AwtGraphicFactory.getBitmap(AWT.renderSvg(inputStream, scaleFactor, width, height, percent, hash));
			svg.put(klic, obrazek);
			pribylo.set(true);
		}
		return new SdilenySymbol(obrazek);
	}

	@Override
	public Bitmap createBitmap(final int width, final int height) {
		return AWT.createBitmap(width, height);
	}

	@Override
	public Bitmap createBitmap(final int width, final int height, final boolean isTransparent) {
		return AWT.createBitmap(width, height, isTransparent);
	}

	@Override
	public Canvas createCanvas() {
		return AWT.createCanvas();
	}

	@Override
	public int createColor(final Color color) {
		return AWT.createColor(color);
	}

	@Override
	public int createColor(final int alpha, final int red, final int green, final int blue) {
		return AWT.createColor(alpha, red, green, blue);
	}

	@Override
	public Matrix createMatrix() {
		return AWT.createMatrix();
	}

	@Override
	public HillshadingBitmap createMonoBitmap(final int width, final int height, final byte[] buffer, final int padding, final BoundingBox area, final int color) {
		return AWT.createMonoBitmap(width, height, buffer, padding, area, color);
	}

	@Override
	public Paint createPaint() {
		return new AwtPaintSPameti();
	}

	@Override
	public Paint createPaint(final Paint paint) {
		return new AwtPaintSPameti(paint);
	}

	@Override
	public Path createPath() {
		return AWT.createPath();
	}

	@Override
	public PointTextContainer createPointTextContainer(final Point xy, final double horizontalOffset, final double verticalOffset, final Display display, final int priority, final String text,
			final Paint paintFront, final Paint paintBack, final SymbolContainer symbolContainer, final Position position, final int maxTextWidth) {
		return AWT.createPointTextContainer(xy, horizontalOffset, verticalOffset, display, priority, text, paintFront, paintBack, symbolContainer, position, maxTextWidth);
	}

	@Override
	public ResourceBitmap createResourceBitmap(final InputStream inputStream, final float scaleFactor, final int width, final int height, final int percent, final int hash) throws IOException {
		return AWT.createResourceBitmap(inputStream, scaleFactor, width, height, percent, hash);
	}

	@Override
	public TileBitmap createTileBitmap(final InputStream inputStream, final int tileSize, final boolean isTransparent) throws IOException {
		return AWT.createTileBitmap(inputStream, tileSize, isTransparent);
	}

	@Override
	public TileBitmap createTileBitmap(final int tileSize, final boolean isTransparent) {
		return AWT.createTileBitmap(tileSize, isTransparent);
	}

	@Override
	public InputStream platformSpecificSources(final String relativePathPrefix, final String src) throws IOException {
		return AWT.platformSpecificSources(relativePathPrefix, src);
	}
}
