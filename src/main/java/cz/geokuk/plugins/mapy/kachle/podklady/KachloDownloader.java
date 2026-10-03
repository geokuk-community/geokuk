package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import cz.geokuk.core.program.FConst;
import cz.geokuk.util.pocitadla.Pocitadlo;
import cz.geokuk.util.pocitadla.PocitadloRoste;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KachloDownloader {

	/** Bez limitu by nedostupný server držel stahovací frontu navždy. */
	private static final int TIMEOUT_PRIPOJENI = 15000;
	private static final int TIMEOUT_CTENI = 30000;
	static final int TIMEOUT_CELKEM = 60000;
	private static final int MAX_PRESMEROVANI = 5;

	/** Mapové servery vyžadují User-Agent, který program jednoznačně identifikuje. */
	static final String USER_AGENT = "Geokuk/" + FConst.VERSION;

	/**
	 * Nepřejmenovávat hodnoty, odvozuje se z něj název resourcu
	 */
	public static enum EPraznyObrazek {
		OFFLINE, ERROR;

		/**
		 * @return
		 */
		public String getRecourceName() {
			return name() + ".png";
		}
	}



	/** Server odpověděl chybou místo dlaždice. */
	public static class ChybaServeru extends IOException {
		private static final long serialVersionUID = 1L;

		private final int kod;

		public ChybaServeru(final int kod, final String zprava) {
			super("Server mapy vrátil chybu HTTP " + kod + (zprava == null ? "" : " " + zprava) + ".");
			this.kod = kod;
		}

		public int getKod() {
			return kod;
		}

		/** Server žádá, abychom stahovali méně nebo počkali. */
		public boolean jeOmezeni() {
			return kod == 429 || kod == 503;
		}
	}

	static class UseknutaDlazdice extends IOException {
		private static final long serialVersionUID = 1L;

		UseknutaDlazdice(final String varovani) {
			super("Dlaždice je useknutá: " + varovani);
		}
	}

	private final Pocitadlo pocitDownloadleDlazdice = new PocitadloRoste("Downloadlé dlaždice", "Počet dlaždic, které byly downloadovány.");

	private final EnumMap<EPraznyObrazek, Image> prazdneObrazky = new EnumMap<>(EPraznyObrazek.class);

	public KachloDownloader() {
	}

	public ImageWithData downloadImage(final URL url) throws IOException {
		return downloadImage(url, Collections.emptyMap());
	}

	public ImageWithData downloadImage(final URL url, final Map<String, String> hlavicky) throws IOException {
		log.debug("Loading kachle from URL: \"{}\"", url);

		HttpURLConnection conn = otevri(url, hlavicky);
		int kod = conn.getResponseCode();
		// Řada serverů už http přesměrovává na https.
		for (int presmerovani = 0; presmerovani < MAX_PRESMEROVANI && kod >= 300 && kod < 400 && conn.getHeaderField("Location") != null; presmerovani++) {
			final URL kam = new URL(url, conn.getHeaderField("Location"));
			conn.disconnect();
			if (!"https".equals(kam.getProtocol()) && !kam.getProtocol().equals(url.getProtocol())) {
				break;
			}
			// Hlavičky uživatelské mapy (třeba klíč API) patří jen jejímu serveru.
			conn = otevri(kam, kam.getHost().equalsIgnoreCase(url.getHost()) ? hlavicky : Collections.emptyMap());
			kod = conn.getResponseCode();
		}
		if (kod >= 300) {
			conn.disconnect();
			throw new ChybaServeru(kod, conn.getResponseMessage());
		}
		final DataHoldingInputStream dhis = new DataHoldingInputStream(conn.getInputStream(), TIMEOUT_CELKEM);
		final Image img;
		try (InputStream stm = new BufferedInputStream(dhis)) {
			img = precti(stm);
			if (img == null) {
				throw new IOException("Server místo obrázku poslal něco jiného, třeba přihlašovací stránku Wi-Fi.");
			}
			docti(stm);
		}
		zkontrolujUplnost(conn, dhis.getData());
		final ImageWithData imda = new ImageWithData(img, dhis.getData());
		pocitDownloadleDlazdice.inc();
		log.debug("Loaded {} bytes", imda.getData().length);

		return imda;

	}

	private static HttpURLConnection otevri(final URL url, final Map<String, String> hlavicky) throws IOException {
		final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(TIMEOUT_PRIPOJENI);
		conn.setReadTimeout(TIMEOUT_CTENI);
		// Všechna přesměrování řeší downloadImage, Java by hlavičky poslala i na jiný server.
		conn.setInstanceFollowRedirects(false);
		conn.setRequestProperty("User-Agent", USER_AGENT);
		if (url.getHost().endsWith("mapy.cz")) {
			// Pro mapy.cz je nutný referer, jinak se vrací 403
			conn.setRequestProperty("Referer", "https://en.mapy.com/");
		}
		hlavicky.forEach(conn::setRequestProperty);
		return conn;
	}

	/** Dekodér JPEG useknutá data nepovažuje za chybu a zbytek dlaždice doplní šedou, ohlásí to jen varováním. */
	static Image precti(final InputStream stm) throws IOException {
		try (ImageInputStream iis = ImageIO.createImageInputStream(stm)) {
			final Iterator<ImageReader> readery = ImageIO.getImageReaders(iis);
			if (!readery.hasNext()) {
				return null;
			}
			final ImageReader reader = readery.next();
			try {
				final java.util.List<String> useknuto = new ArrayList<>();
				reader.addIIOReadWarningListener((zdroj, varovani) -> {
					final String v = varovani.toLowerCase(Locale.ROOT);
					if (v.contains("truncated") || v.contains("premature end") || v.contains("missing eoi")) {
						useknuto.add(varovani);
					}
				});
				reader.setInput(iis, true, true);
				final BufferedImage img = reader.read(0);
				if (!useknuto.isEmpty()) {
					throw new UseknutaDlazdice(useknuto.get(0));
				}
				return img;
			} finally {
				reader.dispose();
			}
		}
	}

	/** Dekodér obrázku se zastaví, jakmile má obrázek; zbytek těla odpovědi je potřeba dočíst, aby šlo poznat useknutou dlaždici. */
	private static void docti(final InputStream stm) throws IOException {
		final byte[] zbytek = new byte[4096];
		while (stm.read(zbytek) >= 0) {
			// jen dočíst do konce
		}
	}

	/** Useknutou dlaždici někdy obrázek přijme a uložila by se do cache poškozená. */
	private static void zkontrolujUplnost(final HttpURLConnection conn, final byte[] data) throws IOException {
		final int ocekavano = conn.getContentLength();
		if (ocekavano >= 0 && conn.getContentEncoding() == null && data.length != ocekavano) {
			throw new IOException("Stažená dlaždice je neúplná: " + data.length + " z " + ocekavano + " bajtů.");
		}
	}

	/**
	 *
	 */
	public synchronized Image fejkovyObrazek(final EPraznyObrazek typPrazdnehoObrazku) {
		// TODO Offline image by měl být průhledný a rozhodně s lepší grafikou.

		Image image = prazdneObrazky.get(typPrazdnehoObrazku);
		InputStream istm = null;
		if (image == null) {
			try {
				istm = getClass().getResourceAsStream(typPrazdnehoObrazku.getRecourceName());
				if (istm != null) {
					image = ImageIO.read(istm);
				}
			} catch (final IOException e) {
				log.error("Nelze nacist prazdny obrazek! " + typPrazdnehoObrazku.getRecourceName(), e);
			} finally {
				if (istm != null) {
					try {
						istm.close();
					} catch (final IOException e) {
						log.error("Nelze zavrit stream pro " + typPrazdnehoObrazku.getRecourceName(), e);
					}
				}
			}
			if (image == null) {
				// K tomu pravděpodobně nedojde, ale co když ,tak vyplníme nesmyslem
				image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB_PRE);
				final Graphics2D g = (Graphics2D) image.getGraphics();
				g.setColor(new Color(128, 128, 128, 128));
				g.fillOval(30, 30, 196, 196);
			}

			prazdneObrazky.put(typPrazdnehoObrazku, image);
		}
		return image;
	}
}
