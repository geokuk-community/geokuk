package cz.geokuk.core.ovladani;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

import javax.swing.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cz.geokuk.core.coord.PoziceModel;
import cz.geokuk.core.coord.VyrezModel;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.napoveda.Restart;
import cz.geokuk.core.program.FConst;
import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.UmisteniProgramu;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.mapy.MapyModel;
import cz.geokuk.plugins.mapy.kachle.data.EKaType;
import cz.geokuk.util.pocitadla.Pocitadlo;
import cz.geokuk.util.pocitadla.SpravcePocitadel;
import lombok.extern.slf4j.Slf4j;

/**
 * Ovládání běžícího programu přes HTTP jen z tohoto počítače. Zapíná se volbou v menu nebo parametrem {@code --ovladani[=port]}. Požadavky
 * s hlavičkou {@code Origin} nebo s jinou hlavičkou {@code Host} než 127.0.0.1 a localhost odmítá, aby na ovládání nedosáhla webová stránka
 * v prohlížeči.
 *
 * Veřejná část, bez tokenu:
 *
 * <pre>
 * GET  /stav                              verze, střed mapy, měřítko, podklad, počet waypointů
 * POST /pozice?lat=50.08&amp;lon=14.42[&amp;meritko=15]
 * POST /podklad?jmeno=TURIST_M
 * POST /kes?kod=GC12345                   vybere keš a vystředí na ni mapu
 * POST /prenacti                          znovu načte keše
 * </pre>
 *
 * Vývojová část pro testy, zapíná se parametrem {@code --ovladani-devel}. Port a token zapíše do souboru {@link #SOUBOR}, klient token posílá
 * v hlavičce {@code Authorization: Bearer <token>}:
 *
 * <pre>
 * GET  /stav[?gc=ano]                     navíc fronty dlaždic, počítadla, okna, obsazená paměť (po úklidu)
 * GET  /menu                              položky menu hlavního okna s cestou, povolením, zkratkou a stavem přepínače
 * POST /menu?cesta=Soubor%20%3E%20Servis...   spustí položku menu jako kliknutí (nečeká na zavření dialogu)
 * GET  /okna                              otevřená okna s titulkem, velikostí, textem hlášek a tlačítky
 * POST /okna/zavri[?titulek=...]          zavře okno s titulkem, bez titulku všechna kromě hlavního
 * POST /okna/tlacitko?titulek=...&amp;text=...   stiskne tlačítko s textem v okně s titulkem
 * </pre>
 */
@Slf4j
public class DalkoveOvladani {

	public static final int VYCHOZI_PORT = 48321;
	private static final Set<String> VYVOJOVE = new HashSet<>(Arrays.asList("/menu", "/okna", "/okna/zavri", "/okna/tlacitko", "/restart"));
	private static final String ZAPNUTO_value = "dalkoveOvladani";
	public static final String VYVOJOVA_PARAMETR = "--ovladani-devel";
	/** Port a token vývojové části, vedle složky logu: v datové složce, když do ní nejde zapisovat, v dočasné složce systému. */
	public static final File SOUBOR = soubor(UmisteniProgramu.log());

	private VyrezModel vyrezModel;
	private PoziceModel poziceModel;
	private MapyModel mapyModel;
	private KesoidModel kesoidModel;

	private HttpServer server;
	private ExecutorService vlakno;
	private boolean uklidPriKonci;
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

	/** Zapnuté volbou v menu, platí pro každé spuštění. */
	public static boolean jeZapnuteVNastaveni() {
		return MyPreferences.current().node(FPref.VSEOBECNE_node).getBoolean(ZAPNUTO_value, false);
	}

	public static void setZapnuteVNastaveni(final boolean zapnuto) {
		MyPreferences.current().node(FPref.VSEOBECNE_node).putBoolean(ZAPNUTO_value, zapnuto);
	}

	public boolean bezi() {
		return server != null;
	}

	public synchronized void zastav() {
		if (server != null) {
			server.stop(0);
			server = null;
			vlakno.shutdownNow();
			smazSouborJestliJeNas();
			token = null;
			log.info("Dálkové ovládání vypnuto");
		}
	}

	/** Port z parametru {@code --ovladani[=port]}, nebo null, když ovládání není zapnuté. */
	public static Integer portZParametru(final String[] args) {
		for (final String a : args) {
			final String s = a.trim();
			if (s.equals("--ovladani")) {
				return VYCHOZI_PORT;
			}
			if (s.startsWith("--ovladani=")) {
				try {
					final int port = Integer.parseInt(s.substring("--ovladani=".length()));
					if (port >= 0 && port <= 65535) {
						return port;
					}
				} catch (final NumberFormatException e) {
					// ohlásí se níže
				}
				log.warn("Neplatný port dálkového ovládání: {}", s);
				return null;
			}
		}
		return null;
	}

	/** Vývojová část zapnutá parametrem {@value #VYVOJOVA_PARAMETR}. */
	public static boolean vyvojovaZParametru(final String[] args) {
		for (final String a : args) {
			if (a.trim().equals(VYVOJOVA_PARAMETR)) {
				return true;
			}
		}
		return false;
	}

	public synchronized void spust(final int port) throws IOException {
		spust(port, false);
	}

	public static File soubor(final File slozkaLogu) {
		return new File(slozkaLogu.getParentFile(), "ovladani.properties");
	}

	/** Hlášení pro uživatele, když ovládání nejde spustit. */
	public static String popisChyby(final int port, final Exception e) {
		final String duvod;
		if (e instanceof BindException) {
			duvod = "Port " + port + " už používá jiný program. Ukončete ho, nebo zvolte jiný port parametrem --ovladani=ČÍSLO.";
		} else if (e instanceof FileSystemException) {
			duvod = "Nejde zapsat soubor " + ((FileSystemException) e).getFile() + ".";
		} else {
			duvod = e.getMessage() != null ? e.getMessage() : e.toString();
		}
		return "Dálkové ovládání nejde spustit. " + duvod;
	}

	/** Spustí ovládání, s vývojovou částí vytvoří token a zapíše ho do {@link #SOUBOR}. Běžícímu ovládání vývojovou část jen přidá. */
	public synchronized void spust(final int port, final boolean vyvojova) throws IOException {
		if (server != null) {
			if (vyvojova && token == null) {
				zapnoutVyvojovou(server.getAddress().getPort());
			}
			return;
		}
		final HttpServer novy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		final int skutecnyPort = novy.getAddress().getPort();
		if (vyvojova) {
			try {
				zapnoutVyvojovou(skutecnyPort);
			} catch (final IOException | RuntimeException e) {
				novy.stop(0);
				throw e;
			}
		}
		novy.createContext("/", this::obsluz);
		vlakno = Executors.newSingleThreadExecutor(r -> {
			final Thread t = new Thread(r, "Dálkové ovládání");
			t.setDaemon(true);
			return t;
		});
		novy.setExecutor(vlakno);
		novy.start();
		server = novy;
		if (!uklidPriKonci) {
			uklidPriKonci = true;
			Runtime.getRuntime().addShutdownHook(new Thread(this::smazSouborJestliJeNas, "Úklid dálkového ovládání"));
		}
		log.info("Dálkové ovládání na http://127.0.0.1:{}/", skutecnyPort);
	}

	private void zapnoutVyvojovou(final int port) throws IOException {
		final byte[] nahodne = new byte[24];
		new SecureRandom().nextBytes(nahodne);
		token = Base64.getUrlEncoder().withoutPadding().encodeToString(nahodne);
		try {
			zapisSoubor(port);
		} catch (final IOException | RuntimeException e) {
			token = null;
			throw e;
		}
		log.info("Port a token dálkového ovládání v {}", SOUBOR);
	}

	/** Soubor vznikne rovnou jen pro vlastníka a na místo se přesune celý, i přes podvržený odkaz. */
	private void zapisSoubor(final int port) throws IOException {
		final Path adresar = SOUBOR.getParentFile().toPath();
		final boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
		if (!Files.isDirectory(adresar)) {
			if (posix) {
				Files.createDirectories(adresar, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
			} else {
				Files.createDirectories(adresar);
			}
		}
		if (posix) {
			overSlozku(adresar);
		}
		final Path tmp = Files.createTempFile(adresar, "ovladani", ".tmp");
		try {
			final Properties p = new Properties();
			p.setProperty("port", String.valueOf(port));
			p.setProperty("token", token);
			try (Writer w = new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8)) {
				p.store(w, "Geokuk: dalkove ovladani");
			}
			Files.move(tmp, SOUBOR.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	/** Složku mohl předem založit jiný uživatel (cizí nebo sdílená datová složka) a soubor s tokenem pak podvrhnout. */
	static void overSlozku(final Path adresar) throws IOException {
		if (Files.isSymbolicLink(adresar)) {
			throw new IOException("Složka " + adresar + " je odkaz, soubor dálkového ovládání do ní nezapíšu.");
		}
		if (!vlastnikNovehoSouboru(adresar).equals(Files.getOwner(adresar, LinkOption.NOFOLLOW_LINKS))) {
			throw new IOException("Složka " + adresar + " patří jinému uživateli, soubor dálkového ovládání do ní nezapíšu.");
		}
		if (Files.getPosixFilePermissions(adresar, LinkOption.NOFOLLOW_LINKS).contains(PosixFilePermission.OTHERS_WRITE)) {
			throw new IOException("Do složky " + adresar + " mohou zapisovat všichni, soubor dálkového ovládání do ní nezapíšu.");
		}
	}

	/** Uživatel nemusí mít v systému jméno (kontejner), proto vlastník souboru, který jsme právě vytvořili. */
	private static UserPrincipal vlastnikNovehoSouboru(final Path adresar) throws IOException {
		final Path soubor;
		try {
			soubor = Files.createTempFile(adresar, "vlastnik", ".tmp");
		} catch (final AccessDeniedException e) {
			throw new IOException("Složka " + adresar + " patří jinému uživateli, soubor dálkového ovládání do ní nezapíšu.", e);
		}
		try {
			return Files.getOwner(soubor, LinkOption.NOFOLLOW_LINKS);
		} finally {
			Files.deleteIfExists(soubor);
		}
	}

	/** Druhá instance mohla soubor přepsat svým tokenem, ten jí nesmíme smazat. */
	private synchronized void smazSouborJestliJeNas() {
		final Properties p = new Properties();
		try (Reader r = new InputStreamReader(new FileInputStream(SOUBOR), StandardCharsets.UTF_8)) {
			p.load(r);
		} catch (final IOException e) {
			return;
		}
		if (token != null && token.equals(p.getProperty("token"))) {
			SOUBOR.delete();
		}
	}

	private void obsluz(final HttpExchange ex) throws IOException {
		try {
			if (!zTohotoPocitace(ex)) {
				odpovez(ex, 403, "{\"chyba\":\"požadavek z prohlížeče nebo z jiného počítače\"}");
				return;
			}
			final String autorizace = ex.getRequestHeaders().getFirst("Authorization");
			final boolean vyvojovy = spravnyToken(autorizace);
			if (autorizace != null && !vyvojovy) {
				odpovez(ex, 401, "{\"chyba\":\"nesedí token\"}");
				return;
			}
			final String cesta = ex.getRequestURI().getPath();
			final Map<String, String> param = parametry(ex.getRequestURI().getRawQuery());
			final boolean post = "POST".equals(ex.getRequestMethod());
			if (VYVOJOVE.contains(cesta) && !vyvojovy) {
				odpovez(ex, 401, "{\"chyba\":\"příkaz jen pro vývoj, chybí token\"}");
				return;
			}
			switch (cesta) {
			case "/stav":
				if (vyvojovy && "ano".equals(param.get("gc"))) {
					System.gc();
				}
				odpovez(ex, 200, naEdt(() -> stav(vyvojovy)));
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
			case "/menu":
				if (post) {
					odpovez(ex, 200, naEdt(() -> spustMenu(param.get("cesta"))));
				} else {
					odpovez(ex, 200, naEdt(this::menu));
				}
				break;
			case "/okna":
				odpovez(ex, 200, naEdt(DalkoveOvladani::okna));
				break;
			case "/okna/zavri":
				vyzadujPost(post);
				odpovez(ex, 200, naEdt(() -> zavriOkna(param.get("titulek"))));
				break;
			case "/okna/tlacitko":
				vyzadujPost(post);
				odpovez(ex, 200, naEdt(() -> stiskni(param.get("titulek"), param.get("text"))));
				break;
			case "/restart":
				vyzadujPost(post);
				SwingUtilities.invokeLater(Restart::restartuj);
				odpovez(ex, 200, "{}");
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

	/** Hlavičky Origin a Host prohlížeč podvrhnout nedovolí: tak se pozná webová stránka (CSRF) i DNS rebinding. */
	private boolean zTohotoPocitace(final HttpExchange ex) {
		if (ex.getRequestHeaders().containsKey("Origin")) {
			return false;
		}
		final String host = ex.getRequestHeaders().getFirst("Host");
		final HttpServer s = server;
		if (s == null) {
			return false;
		}
		final int port = s.getAddress().getPort();
		return host != null && (host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port));
	}

	private boolean spravnyToken(final String autorizace) {
		final String t = token;
		return t != null && autorizace != null && MessageDigest.isEqual(autorizace.getBytes(StandardCharsets.UTF_8), ("Bearer " + t).getBytes(StandardCharsets.UTF_8));
	}

	private static void vyzadujPost(final boolean post) {
		if (!post) {
			throw new IllegalArgumentException("příkaz se posílá metodou POST");
		}
	}

	private String stav(final boolean vyvojovy) {
		final StringBuilder sb = new StringBuilder("{");
		final Wgs stred = vyrezModel.getMoord().getMoustred().toWgs();
		sb.append("\"verze\":").append(json(FConst.VERSION));
		sb.append(",\"lat\":").append(stred.lat).append(",\"lon\":").append(stred.lon);
		sb.append(",\"meritko\":").append(vyrezModel.getMoord().getMoumer());
		sb.append(",\"podklad\":").append(json(mapyModel.getPodklad() == null ? null : mapyModel.getPodklad().name()));
		final KesBag kese = kesoidModel.getVsechnyKesoidy();
		sb.append(",\"waypointu\":").append(kese == null ? -1 : kese.getWpts().size());
		if (!vyvojovy) {
			return sb.append("}").toString();
		}
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
		final Runtime rt = Runtime.getRuntime();
		sb.append(",\"pametMb\":").append((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
		sb.append(",\"pocitadla\":[").append(pocitadla).append("]");
		sb.append(",\"okna\":[");
		boolean prvni = true;
		for (final Window w : Window.getWindows()) {
			if (w.isShowing()) {
				sb.append(prvni ? "" : ",").append(json(titulek(w)));
				prvni = false;
			}
		}
		return sb.append("]}").toString();
	}

	private static JFrame hlavniOkno() {
		for (final Frame f : Frame.getFrames()) {
			if (f instanceof JFrame && f.isShowing() && ((JFrame) f).getJMenuBar() != null) {
				return (JFrame) f;
			}
		}
		throw new IllegalArgumentException("hlavní okno není otevřené");
	}

	private static List<JMenuItem> polozkyMenu() {
		final List<JMenuItem> polozky = new ArrayList<>();
		final JMenuBar lista = hlavniOkno().getJMenuBar();
		for (int i = 0; i < lista.getMenuCount(); i++) {
			if (lista.getMenu(i) != null) {
				pridejPolozky(lista.getMenu(i), polozky);
			}
		}
		return polozky;
	}

	private static void pridejPolozky(final JMenu menu, final List<JMenuItem> polozky) {
		for (final Component c : menu.getMenuComponents()) {
			if (c instanceof JMenu) {
				pridejPolozky((JMenu) c, polozky);
			} else if (c instanceof JMenuItem) {
				polozky.add((JMenuItem) c);
			}
		}
	}

	static String cesta(final JMenuItem polozka) {
		final Deque<String> cesta = new ArrayDeque<>();
		Component c = polozka;
		while (c instanceof JMenuItem) {
			cesta.addFirst(String.valueOf(((JMenuItem) c).getText()).replaceAll("<[^>]*>", "").trim());
			c = c.getParent() instanceof JPopupMenu ? ((JPopupMenu) c.getParent()).getInvoker() : null;
		}
		return String.join(" > ", cesta);
	}

	private String menu() {
		final StringBuilder sb = new StringBuilder("[");
		for (final JMenuItem p : polozkyMenu()) {
			sb.append(sb.length() == 1 ? "" : ",").append("{\"cesta\":").append(json(cesta(p))).append(",\"povoleno\":").append(p.isEnabled());
			sb.append(",\"zkratka\":").append(json(p.getAccelerator() == null ? null : p.getAccelerator().toString()));
			sb.append(",\"pismeno\":").append(json(p.getMnemonic() == 0 ? null : String.valueOf((char) p.getMnemonic())));
			if (p instanceof JCheckBoxMenuItem || p instanceof JRadioButtonMenuItem) {
				sb.append(",\"zaskrtnuto\":").append(p.isSelected());
			}
			sb.append('}');
		}
		return sb.append(']').toString();
	}

	private String spustMenu(final String cesta) {
		for (final JMenuItem p : polozkyMenu()) {
			if (cesta(p).equals(cesta)) {
				if (!p.isEnabled()) {
					throw new IllegalArgumentException("položka " + cesta + " není povolená");
				}
				// Dialog otevřený položkou může být modální, odpověď proto na jeho zavření nečeká.
				SwingUtilities.invokeLater(() -> p.doClick(0));
				return "{\"spusteno\":" + json(cesta) + "}";
			}
		}
		throw new IllegalArgumentException("v menu není " + cesta);
	}

	private static String okna() {
		final StringBuilder sb = new StringBuilder("[");
		for (final Window w : Window.getWindows()) {
			if (!w.isShowing()) {
				continue;
			}
			final StringBuilder texty = new StringBuilder();
			if (w instanceof RootPaneContainer) {
				hledejTexty(((RootPaneContainer) w).getContentPane(), texty);
			}
			final int obsah = w instanceof RootPaneContainer ? ((RootPaneContainer) w).getContentPane().getComponentCount() : w.getComponentCount();
			sb.append(sb.length() == 1 ? "" : ",").append("{\"titulek\":").append(json(titulek(w))).append(",\"trida\":").append(json(w.getClass().getSimpleName()));
			sb.append(",\"sirka\":").append(w.getWidth()).append(",\"vyska\":").append(w.getHeight()).append(",\"komponent\":").append(obsah);
			sb.append(",\"modalni\":").append(w instanceof Dialog && ((Dialog) w).isModal()).append(",\"text\":").append(json(texty.toString().trim()));
			final List<String> tlacitka = new ArrayList<>();
			hledejTlacitka(w, tlacitka);
			sb.append(",\"tlacitka\":[");
			for (int i = 0; i < tlacitka.size(); i++) {
				sb.append(i == 0 ? "" : ",").append(json(tlacitka.get(i)));
			}
			sb.append("]}");
		}
		return sb.append(']').toString();
	}

	private static void hledejTexty(final Container kde, final StringBuilder texty) {
		for (final Component c : kde.getComponents()) {
			if (c instanceof JOptionPane) {
				final Object m = ((JOptionPane) c).getMessage();
				texty.append(m instanceof Object[] ? Arrays.toString((Object[]) m) : String.valueOf(m)).append(' ');
			} else if (c instanceof Container) {
				hledejTexty((Container) c, texty);
			}
		}
	}

	private static void hledejTlacitka(final Container kde, final List<String> tlacitka) {
		for (final Component c : kde.getComponents()) {
			if (c instanceof JButton && c.isShowing() && ((JButton) c).getText() != null && !((JButton) c).getText().isEmpty()) {
				tlacitka.add(((JButton) c).getText());
			} else if (c instanceof Container) {
				hledejTlacitka((Container) c, tlacitka);
			}
		}
	}

	private static String titulek(final Window w) {
		return w instanceof Frame ? ((Frame) w).getTitle() : w instanceof Dialog ? ((Dialog) w).getTitle() : "";
	}

	private static String stiskni(final String titulek, final String text) {
		for (final Window w : Window.getWindows()) {
			if (w.isShowing() && Objects.equals(titulek, titulek(w))) {
				final AbstractButton tlacitko = najdiTlacitko(w, text);
				if (tlacitko != null) {
					SwingUtilities.invokeLater(() -> tlacitko.doClick(0));
					return "{\"stisknuto\":" + json(text) + "}";
				}
			}
		}
		throw new IllegalArgumentException("okno " + titulek + " s tlačítkem " + text + " není otevřené");
	}

	private static AbstractButton najdiTlacitko(final Container kde, final String text) {
		for (final Component c : kde.getComponents()) {
			if (c instanceof AbstractButton && c.isShowing() && text != null && text.equals(((AbstractButton) c).getText())) {
				return (AbstractButton) c;
			}
			if (c instanceof Container) {
				final AbstractButton b = najdiTlacitko((Container) c, text);
				if (b != null) {
					return b;
				}
			}
		}
		return null;
	}

	private static String zavriOkna(final String titulek) {
		final JFrame hlavni = hlavniOkno();
		int zavreno = 0;
		for (final Window w : Window.getWindows()) {
			if (w.isShowing() && w != hlavni && (titulek == null || titulek.equals(titulek(w)))) {
				// Jako křížkem, ať proběhne i úklid okna; modální dialog vrací řízení až po zavření.
				SwingUtilities.invokeLater(() -> {
					w.dispatchEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSING));
					if (w.isShowing()) {
						w.dispose();
					}
				});
				zavreno++;
			}
		}
		return "{\"zavreno\":" + zavreno + "}";
	}

	private String pozice(final Map<String, String> param) {
		final double lat = cislo(param, "lat");
		final double lon = cislo(param, "lon");
		if (!(Math.abs(lat) <= 85) || !(Math.abs(lon) <= 180)) {
			throw new IllegalArgumentException("lat musí být v rozsahu -85 až 85 a lon -180 až 180");
		}
		final Wgs wgs = new Wgs(lat, lon);
		vyrezModel.presunMapuNaMoustred(wgs.toMou());
		if (param.containsKey("meritko")) {
			vyrezModel.setMeritkoMapy((int) cislo(param, "meritko"));
		}
		return stav(false);
	}

	private String podklad(final String jmeno) {
		final EKaType ka = jmeno == null ? null : EKaType.podleJmena(jmeno);
		if (ka == null) {
			throw new IllegalArgumentException("neznámý podklad " + jmeno);
		}
		mapyModel.setPodklad(ka);
		return stav(false);
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
				return stav(false);
			}
		}
		throw new IllegalArgumentException("keš " + kod + " není načtená");
	}

	private static double cislo(final Map<String, String> param, final String jmeno) {
		try {
			final double d = Double.parseDouble(param.get(jmeno));
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new NumberFormatException();
			}
			return d;
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
