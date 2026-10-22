package cz.geokuk.plugins.kesoid.mapicon;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import javax.imageio.ImageIO;

import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.pocitadla.*;

public class ImageProviderCached implements ImageProvider {

	private static Pocitadlo pocitSourceImaguZasah = new PocitadloRoste("Zdrojové obrázky - zásah cache", "");
	private final Pocitadlo pocitSourceImagu;

	/** Klíčem je adresa jako text, URL.equals by u adres s hostitelem dělalo DNS dotaz. */
	private final Map<String, BufferedImage> sourceImageCache = Collections.synchronizedMap(new HashMap<String, BufferedImage>());

	/** Jméno skla odliší počitadla jednotlivých skel. */
	public ImageProviderCached(final String jmenoSkla) {
		pocitSourceImagu = new PocitadloMalo("Zdrojové obrázky - počet (" + jmenoSkla + ")", "Kolik vlastně máme typů konkrétních vzhledů ikon");
	}

	@Override
	public BufferedImage getImage(final URL url) {
		try {
			BufferedImage bi = sourceImageCache.get(url.toExternalForm());
			if (bi == null) {
				bi = ImageIO.read(url);
				if (bi == null) {
					FExceptionDumper.dump(new IOException("Neznámý formát obrázku"), EExceptionSeverity.WORKARROUND, "Obrázek ikony nejde přečíst: " + url);
					bi = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
				}
				sourceImageCache.put(url.toExternalForm(), bi);
				pocitSourceImagu.set(sourceImageCache.size());
			} else {
				pocitSourceImaguZasah.inc();
			}
			return bi;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

	}

}
