package cz.geokuk.core.ovladani;

import java.awt.Frame;
import java.awt.Window;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import javax.swing.SwingUtilities;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coord.PoziceModel;
import cz.geokuk.core.coord.VyrezModel;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.program.FConst;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.mapy.MapyModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.util.pocitadla.Pocitadlo;
import cz.geokuk.util.pocitadla.SpravcePocitadel;
import lombok.extern.slf4j.Slf4j;

/**
 * Ovládání běžícího programu přes HTTP jen z tohoto počítače. Zapíná se parametrem {@code --ovladani[=port]}. Port a přístupový token zapíše do
 * souboru {@link #SOUBOR}, klient je posílá v hlavičce {@code Authorization: Bearer <token>}.
 *
 * <pre>
 * GET  /stav                              verze, střed mapy, měřítko, podklad, keše, fronty dlaždic, počítadla, okna
 * POST /pozice?lat=50.08&amp;lon=14.42[&amp;meritko=15]
 * POST /podklad?jmeno=TURIST_M
 * POST /kes?kod=GC12345                   vybere keš a vystředí na ni mapu
 * POST /prenacti                          znovu načte keše
 * </pre>
 */
@Slf4j
public class DalkoveOvladani {

	public static final int VYCHOZI_PORT = 48321;
	public static final File SOUBOR = new File(new File(System.getProperty("java.io.tmpdir"), "geokuk"), "ovladani.properties");

	private VyrezModel vyrezModel;
	private PoziceModel poziceModel;
	private MapyModel mapyModel;
	private KesoidModel kesoidModel;

	private HttpServer server;
	private String token;

	public void inject(final VyrezModel vyrezModel) {
		this.vyrezModel = vyrezModel;
	}

	public void inject(final PoziceModel poziceModel) {
		this.poziceModel = poziceModel;
	}

	public void inject(final MapyModel mapyModel) {
		this.mapyModel = mapyModel;
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	/** Port z parametru {@code --ovladani[=port]}, nebo null, když ovládání není zapnuté. */
	public static Integer portZParametru(final String[] args) {
		for (final String a : args) {
			final String s = a.trim();
			if (s.equals("--ovladani")) {
				return VYCHOZI_PORT;
			}
			if (s.startsWith("--ovladani=")) {
				return Integer.valueOf(s.substring("--ovladani=".length()));
			}
		}
		return null;
	}

	public void spust(final int port) throws IOException {
		final byte[] nahodne = new byte[24];
		new SecureRandom().nextBytes(nahodne);
		token = Base64.getUrlEncoder().withoutPadding().encodeToString(nahodne);
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		server.createContext("/", this::obsluz);
		server.setExecutor(Executors.newSingleThreadExecutor(r -> {
			final Thread t = new Thread(r, "Dálkové ovládání");
			t.setDaemon(true);
			return t;
		}));
		server.start();
		final int skutecnyPort = server.getAddress().getPort();
		SOUBOR.getParentFile().mkdirs();
		final Properties p = new Properties();
		p.setProperty("port", String.valueOf(skutecnyPort));
		p.setProperty("token", token);
		try (Writer w = new OutputStreamWriter(new FileOutputStream(SOUBOR), StandardCharsets.UTF_8)) {
			p.store(w, "Geokuk: dalkove ovladani");
		}
		SOUBOR.setReadable(false, false);
		SOUBOR.setReadable(true, true);
		SOUBOR.deleteOnExit();
		log.info("Dálkové ovládání na http://127.0.0.1:{}/", skutecnyPort);
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		try {
			if (!povoleno(ex)) {
				odpovez(ex, 401, "{\"chyba\":\"chybí nebo nesedí token\"}");
				return;
			}
			final String cesta = ex.getRequestURI().getPath();
			final Map<String, String> param = parametry(ex.getRequestURI().getRawQuery());
			final boolean post = "POST".equals(ex.getRequestMethod());
			switch (cesta) {
			case "/stav":
				odpovez(ex, 200, naEdt(this::stav));
				break;
			case "/pozice":
				vyzadujPost(post);
				odpovez(ex, 200, naEdt(() -> pozice(param)));
				break;
			case "/podklad":
				vyzadujPost(post);
				odpovez(ex, 200, naEdt(() -> podklad(param.get("jmeno"))));
				break;
			case "/kes":
				vyzadujPost(post);
				odpovez(ex, 200, naEdt(() -> kes(param.get("kod"))));
				break;
			case "/prenacti":
				vyzadujPost(post);
				odpovez(ex, 200, naEdt(() -> {
					kesoidModel.prenactiKese();
					return "{}";
				}));
				break;
			default:
				odpovez(ex, 404, "{\"chyba\":\"neznámý příkaz\"}");
			}
		} catch (final IllegalArgumentException e) {
			odpovez(ex, 400, "{\"chyba\":" + json(e.getMessage()) + "}");
		} catch (final Exception e) {
			log.warn("Dálkové ovládání: {}", ex.getRequestURI(), e);
			odpovez(ex, 500, "{\"chyba\":" + json(e.toString()) + "}");
		} finally {
			ex.close();
		}
	}

	/** Token a hlavička Host, aby na ovládání nedosáhla webová stránka v prohlížeči (DNS rebinding). */
	private boolean povoleno(final HttpExchange ex) {
		final String host = ex.getRequestHeaders().getFirst("Host");
		final int port = server.getAddress().getPort();
		if (host == null || !(host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port))) {
			return false;
		}
		final String autorizace = ex.getRequestHeaders().getFirst("Authorization");
		return autorizace != null && MessageDigest.isEqual(autorizace.getBytes(StandardCharsets.UTF_8), ("Bearer " + token).getBytes(StandardCharsets.UTF_8));
	}

	private static void vyzadujPost(final boolean post) {
		if (!post) {
			throw new IllegalArgumentException("příkaz se posílá metodou POST");
		}
	}

	private String stav() {
		final StringBuilder sb = new StringBuilder("{");
		final Wgs stred = vyrezModel.getMoord().getMoustred().toWgs();
		sb.append("\"verze\":").append(json(FConst.VERSION));
		sb.append(",\"lat\":").append(stred.lat).append(",\"lon\":").append(stred.lon);
		sb.append(",\"meritko\":").append(vyrezModel.getMoord().getMoumer());
		sb.append(",\"podklad\":").append(json(mapyModel.getPodklad() == null ? null : mapyModel.getPodklad().name()));
		final KesBag kese = kesoidModel.getVsechnyKesoidy();
		sb.append(",\"waypointu\":").append(kese == null ? -1 : kese.getWpts().size());
		long fronty = 0;
		final StringBuilder pocitadla = new StringBuilder();
		final List<Pocitadlo> seznam;
		synchronized (SpravcePocitadel.getPocitadla()) {
			seznam = new ArrayList<>(SpravcePocitadel.getPocitadla());
		}
		seznam.sort(Comparator.comparing(Pocitadlo::getName));
		for (final Pocitadlo p : seznam) {
			if (p.getName().matches("ka[123] .*")) {
				fronty += p.get();
			}
			pocitadla.append(pocitadla.length() == 0 ? "" : ",").append("[").append(json(p.getName())).append(",").append(p.get()).append("]");
		}
		sb.append(",\"frontyDlazdic\":").append(fronty);
		sb.append(",\"pocitadla\":[").append(pocitadla).append("]");
		sb.append(",\"okna\":[");
		boolean prvni = true;
		for (final Window w : Window.getWindows()) {
			if (w.isShowing()) {
				final String titulek = w instanceof Frame ? ((Frame) w).getTitle() : w instanceof java.awt.Dialog ? ((java.awt.Dialog) w).getTitle() : "";
				sb.append(prvni ? "" : ",").append(json(titulek));
				prvni = false;
			}
		}
		return sb.append("]}").toString();
	}

	private String pozice(final Map<String, String> param) {
		final Wgs wgs = new Wgs(cislo(param, "lat"), cislo(param, "lon"));
		vyrezModel.presunMapuNaMoustred(wgs.toMou());
		if (param.containsKey("meritko")) {
			vyrezModel.setMeritkoMapy((int) cislo(param, "meritko"));
		}
		return stav();
	}

	private String podklad(final String jmeno) {
		final EKaType ka = jmeno == null ? null : EKaType.podleJmena(jmeno);
		if (ka == null) {
			throw new IllegalArgumentException("neznámý podklad " + jmeno);
		}
		mapyModel.setPodklad(ka);
		return stav();
	}

	private String kes(final String kod) {
		final KesBag kese = kesoidModel.getVsechnyKesoidy();
		if (kod == null || kese == null) {
			throw new IllegalArgumentException("keš " + kod + " není načtená");
		}
		for (final Kesoid k : kese.getKesoidy()) {
			if (kod.equalsIgnoreCase(k.getIdentifier())) {
				poziceModel.setPozice(k.getMainWpt());
				vyrezModel.vystredovatNaPozici();
				return stav();
			}
		}
		throw new IllegalArgumentException("keš " + kod + " není načtená");
	}

	private static double cislo(final Map<String, String> param, final String jmeno) {
		try {
			return Double.parseDouble(param.get(jmeno));
		} catch (final NullPointerException | NumberFormatException e) {
			throw new IllegalArgumentException("parametr " + jmeno + " musí být číslo");
		}
	}

	private static Map<String, String> parametry(final String query) throws UnsupportedEncodingException {
		final Map<String, String> vysledek = new HashMap<>();
		if (query != null) {
			for (final String dvojice : query.split("&")) {
				final int rovnase = dvojice.indexOf('=');
				if (rovnase > 0) {
					vysledek.put(URLDecoder.decode(dvojice.substring(0, rovnase), "UTF-8"), URLDecoder.decode(dvojice.substring(rovnase + 1), "UTF-8"));
				}
			}
		}
		return vysledek;
	}

	private static String naEdt(final Callable<String> ukol) throws Exception {
		final FutureTask<String> f = new FutureTask<>(ukol);
		SwingUtilities.invokeLater(f);
		try {
			return f.get();
		} catch (final java.util.concurrent.ExecutionException e) {
			if (e.getCause() instanceof Exception) {
				throw (Exception) e.getCause();
			}
			throw e;
		}
	}

	private static void odpovez(final HttpExchange ex, final int kod, final String telo) throws IOException {
		final byte[] b = telo.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		ex.sendResponseHeaders(kod, b.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(b);
		}
	}

	static String json(final String s) {
		if (s == null) {
			return "null";
		}
		final StringBuilder sb = new StringBuilder("\"");
		for (final char c : s.toCharArray()) {
			switch (c) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			default:
				if (c < 0x20) {
					sb.append(String.format("\\u%04x", (int) c));
				} else {
					sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}
}
