package cz.geokuk.smoke;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Dlaždicový server pro smoke test. Dlaždice kreslí na počkání (s číslem z/x/y, ať jde poznat posun) a počítá, kolikrát si kdo o kterou řekl.
 * Umí adresy {@code /z/x/y.png} a jako HTTP proxy i adresy Mapy.cz ({@code http://mapserver.mapy.cz/vrstva/z-x-y}).
 */
public class FalesnyDlazdicovyServer implements AutoCloseable {

	private static final Pattern CESTA = Pattern.compile(".*/(\\d+)[/-](\\d+)[/-](\\d+)(\\.png)?");

	private final HttpServer server;
	private final Map<String, AtomicInteger> pozadavky = new ConcurrentHashMap<>();
	private final Map<String, List<Long>> casyPozadavku = new ConcurrentHashMap<>();
	private volatile boolean zlobi;

	public FalesnyDlazdicovyServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::obsluz);
		server.setExecutor(Executors.newFixedThreadPool(8));
		server.start();
	}

	/**
	 * Když zlobí, vždy desetina dlaždic vrací 404, 500, useknuté tělo, přijde až po 2 s, místo obrázku HTML stránku (přihlášení k Wi-Fi, proxy)
	 * a prázdné tělo.
	 */
	public void setZlobi(final boolean zlobi) {
		this.zlobi = zlobi;
	}

	/** Jak dlaždice na dané cestě zlobí, nebo null. */
	public static String zlobeni(final String cesta) {
		switch (Math.floorMod(cesta.hashCode(), 10)) {
		case 0:
			return "404";
		case 1:
			return "500";
		case 2:
			return "useknutá";
		case 3:
			return "pomalá";
		case 4:
			return "html";
		case 5:
			return "prázdná";
		default:
			return null;
		}
	}

	public int getPort() {
		return server.getAddress().getPort();
	}

	public String getUrl() {
		return "http://127.0.0.1:" + getPort() + "/{z}/{x}/{y}.png";
	}

	/** Kopie počtu požadavků podle adresy (přes proxy i se serverem). */
	public Map<String, Integer> getPozadavky() {
		final Map<String, Integer> kopie = new TreeMap<>();
		pozadavky.forEach((cesta, pocet) -> kopie.put(cesta, pocet.get()));
		return kopie;
	}

	/** Nejvyšší počet požadavků na cestu v libovolném okně dané délky. */
	public Map<String, Integer> getNejvicPozadavkuZaDobu(final long oknoMs) {
		final Map<String, Integer> vysledek = new TreeMap<>();
		casyPozadavku.forEach((cesta, casy) -> {
			final List<Long> serazene;
			synchronized (casy) {
				serazene = new ArrayList<>(casy);
			}
			Collections.sort(serazene);
			int nejvic = 0;
			for (int od = 0, i = 0; i < serazene.size(); i++) {
				while (serazene.get(i) - serazene.get(od) >= oknoMs) {
					od++;
				}
				nejvic = Math.max(nejvic, i - od + 1);
			}
			vysledek.put(cesta, nejvic);
		});
		return vysledek;
	}

	public int getPocetPozadavku() {
		return pozadavky.values().stream().mapToInt(AtomicInteger::get).sum();
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		try {
			final String host = ex.getRequestURI().getHost();
			final String cesta = ex.getRequestURI().getPath();
			final String klic = host == null ? cesta : host + cesta;
			pozadavky.computeIfAbsent(klic, k -> new AtomicInteger()).incrementAndGet();
			casyPozadavku.computeIfAbsent(klic, k -> Collections.synchronizedList(new ArrayList<>())).add(System.currentTimeMillis());
			if ("maps.googleapis.com".equals(host)) {
				// Geokódování Googlu bez API klíče odpovídá takhle.
				final byte[] odpoved = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<GeocodeResponse>\n <status>REQUEST_DENIED</status>\n"
						+ " <error_message>You must use an API key to authenticate each request to Google Maps Platform APIs.</error_message>\n</GeocodeResponse>\n").getBytes(StandardCharsets.UTF_8);
				ex.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
				ex.sendResponseHeaders(200, odpoved.length);
				try (OutputStream os = ex.getResponseBody()) {
					os.write(odpoved);
				}
				return;
			}
			final Matcher m = CESTA.matcher(cesta);
			if (!m.matches()) {
				ex.sendResponseHeaders(404, -1);
				return;
			}
			// Mapy.cz posílají JPEG, u něj dekodér nedočte konec těla odpovědi.
			final String format = m.group(4) == null ? "jpeg" : "png";
			final byte[] obrazek = nakresli(m.group(1) + "/" + m.group(2) + "/" + m.group(3), format);
			final String zlobeni = zlobi ? zlobeni(cesta) : null;
			if ("404".equals(zlobeni) || "500".equals(zlobeni)) {
				ex.sendResponseHeaders(Integer.parseInt(zlobeni), -1);
				return;
			}
			if ("useknutá".equals(zlobeni)) {
				ex.getResponseHeaders().set("Content-Type", "image/" + format);
				ex.sendResponseHeaders(200, obrazek.length);
				// Pošle půlku a spojení zavře, klient dostane méně, než slibuje Content-Length.
				ex.getResponseBody().write(obrazek, 0, obrazek.length / 2);
				ex.getResponseBody().flush();
				ex.close();
				return;
			}
			if ("html".equals(zlobeni) || "prázdná".equals(zlobeni)) {
				final byte[] telo = "html".equals(zlobeni) ? "<!DOCTYPE html><html><body>Přihlaste se k síti</body></html>".getBytes(StandardCharsets.UTF_8) : new byte[0];
				ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
				ex.sendResponseHeaders(200, telo.length == 0 ? -1 : telo.length);
				try (OutputStream os = ex.getResponseBody()) {
					os.write(telo);
				}
				return;
			}
			if ("pomalá".equals(zlobeni)) {
				try {
					Thread.sleep(2000);
				} catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			ex.getResponseHeaders().set("Content-Type", "image/" + format);
			ex.sendResponseHeaders(200, obrazek.length);
			// Konec těla dorazí zvlášť jako po skutečné síti, ať se pozná, kdo ho nedočte.
			final int konec = Math.min(16, obrazek.length);
			try (OutputStream os = ex.getResponseBody()) {
				os.write(obrazek, 0, obrazek.length - konec);
				os.flush();
				Thread.sleep(20);
				os.write(obrazek, obrazek.length - konec, konec);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		} finally {
			ex.close();
		}
	}

	static byte[] nakresli(final String text, final String format) throws IOException {
		final BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = img.createGraphics();
		g.setColor(new Color(0xE8F0E0));
		g.fillRect(0, 0, 256, 256);
		g.setColor(Color.GRAY);
		g.drawRect(0, 0, 255, 255);
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
		g.drawString(text, 20, 128);
		g.dispose();
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, format, baos);
		return baos.toByteArray();
	}
}
