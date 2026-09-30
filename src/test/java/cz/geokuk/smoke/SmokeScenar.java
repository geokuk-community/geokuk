package cz.geokuk.smoke;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;
import javax.swing.*;

import cz.geokuk.core.coord.*;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.napoveda.Diagnostika;
import cz.geokuk.core.program.CloseAction;
import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.OknoUmisteniDto;
import cz.geokuk.core.program.GeokukMain;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.mapy.PodkladAction;
import cz.geokuk.util.file.Filex;
import cz.geokuk.util.pocitadla.Pocitadlo;
import cz.geokuk.util.pocitadla.SpravcePocitadel;

/**
 * Scénář smoke testu. Běží v samostatném JVM s displejem (xvfb), spustí Geokuk, projede měřítka a posune mapu nad mapou „Smoke“ z falešného
 * serveru a do souboru zapíše, co viděl. Hodnotí to {@link SmokeIT}.
 */
public class SmokeScenar {

	static final String MAPA = "Smoke";
	private static final long LIMIT_KLIDU_MS = 60_000;

	private final Properties zprava = new Properties();
	private final List<String> nezachycene = new CopyOnWriteArrayList<>();
	private final List<String> chyby = new ArrayList<>();
	private final List<String> varovani = new ArrayList<>();
	private JFrame hlavniOkno;
	private HlidacEdt hlidac;
	private final List<Action> akce = new ArrayList<>();
	private final Map<Action, JMenuItem> polozky = new HashMap<>();
	private final List<JMenuItem> polozkyMenu = new ArrayList<>();

	public static void main(final String[] args) throws Exception {
		final File soubor = new File(args[0]);
		final File snimek = new File(args[1]);
		final String[] kroky = args.length > 2 ? args[2].split(",") : new String[] { "meritka", "posun" };
		final SmokeScenar s = new SmokeScenar();
		try {
			s.proved(kroky);
		} catch (final Throwable t) {
			s.chyby.add("Scénář spadl: " + vypis(t));
		}
		s.snimek(snimek);
		s.zapis(soubor);
		s.ukonci();
	}

	/** Ukončí program jako uživatel přes Soubor > Konec, pojistka pro případ, že se Konec zasekne na dotazu. */
	private void ukonci() {
		final Thread pojistka = new Thread(() -> {
			try {
				Thread.sleep(30_000);
			} catch (final InterruptedException e) {
				// konec tak jako tak
			}
			System.exit(0);
		}, "Pojistka ukončení");
		pojistka.setDaemon(true);
		pojistka.start();
		try {
			final Action konec = akce(CloseAction.class);
			SwingUtilities.invokeLater(() -> konec.actionPerformed(null));
		} catch (final IllegalStateException e) {
			System.exit(0);
		}
	}

	private void proved(final String[] kroky) throws Exception {
		MyPreferences.current().node(FPref.VSEOBECNE_node).putLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, Long.MAX_VALUE);
		if (Boolean.getBoolean("smoke.zmeneneProstredi")) {
			zmenProstredi();
		}
		hlidac = HlidacEdt.zapni(500);
		final long start = System.currentTimeMillis();
		new GeokukMain().execute(new String[0]);
		final Thread.UncaughtExceptionHandler puvodni = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((vlakno, t) -> {
			nezachycene.add(vlakno.getName() + ": " + vypis(t));
			if (puvodni != null) {
				puvodni.uncaughtException(vlakno, t);
			}
		});

		cekej("hlavní okno", 60_000, () -> najdiHlavniOkno() != null);
		zprava.setProperty("start.oknoMs", String.valueOf(System.currentTimeMillis() - start));
		naEdt(() -> {
			final Rectangle obrazovka = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			final Rectangle okno = najdiHlavniOkno().getBounds();
			zprava.setProperty("okno.hlavni", okno.x + "," + okno.y + " " + okno.width + "x" + okno.height);
			if (!obrazovka.intersects(okno) || obrazovka.intersection(okno).width < 200 || obrazovka.intersection(okno).height < 100) {
				chyby.add("Hlavní okno není na obrazovce: " + okno + ", obrazovka " + obrazovka);
			}
		});
		naEdt(() -> {
			hlavniOkno = najdiHlavniOkno();
			for (int i = 0; i < hlavniOkno.getJMenuBar().getMenuCount(); i++) {
				sesbirejAkce(hlavniOkno.getJMenuBar().getMenu(i));
			}
		});
		final KesoidModel kesoidModel = bean(KesoidModel.class);
		final Field vsechny = KesoidModel.class.getDeclaredField("vsechny");
		vsechny.setAccessible(true);
		cekej("načtení keší", 120_000, () -> {
			try {
				return vsechny.get(kesoidModel) != null;
			} catch (final IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		});
		zprava.setProperty("start.keseMs", String.valueOf(System.currentTimeMillis() - start));
		zprava.setProperty("kese.wpt", String.valueOf(((KesBag) vsechny.get(kesoidModel)).getWpts().size()));
		zapniMapu();
		pockejNaKlid("start");
		for (final String krok : kroky) {
			switch (krok) {
			case "meritka":
				projedMeritka();
				break;
			case "posun":
				posouvej();
				break;
			case "menu":
				projdiMenu();
				break;
			case "vzhled":
				prepniVzhledy();
				break;
			case "zbesile":
				zbesile();
				break;
			case "okno":
				menOkno();
				break;
			default:
				throw new IllegalArgumentException(krok);
			}
		}
	}

	private void zapniMapu() throws Exception {
		naEdt(() -> {
			for (final Action a : akce) {
				if (a instanceof PodkladAction && MAPA.equals(((PodkladAction) a).getPodklad().getNazev())) {
					polozky.get(a).doClick();
					return;
				}
			}
			throw new IllegalStateException("V menu chybí mapa " + MAPA);
		});
	}

	private void projedMeritka() throws Exception {
		final VyrezModel vyrez = vyrezModel();
		// Pozice se pamatuje z minula, každý běh má ale projet stejné dlaždice.
		naEdt(() -> {
			vyrez.presunMapuNaMoustred(new Wgs(50.08, 14.42).toMou());
			vyrez.setMeritkoMapy(vyrez.nejvzdalenejsiMeritko());
		});
		pockejNaKlid("měřítko nejvzdálenější");
		final Action pribliz = akce(PriblizMapuAction.class);
		for (int i = 0; i < 30; i++) {
			final int[] meritko = new int[1];
			naEdt(() -> {
				pribliz.actionPerformed(null);
				meritko[0] = vyrez.getMoord().getMoumer();
			});
			pockejNaKlid("měřítko " + meritko[0]);
			if (!pribliz.isEnabled()) {
				break;
			}
		}
		zprava.setProperty("meritko.nejblizsi", String.valueOf(vyrez.getMoord().getMoumer()));
		naEdt(() -> vyrez.setMeritkoMapy(15));
		pockejNaKlid("měřítko 15");
	}

	private void posouvej() throws Exception {
		final String[][] trasa = { { "ctrl RIGHT", "4" }, { "ctrl DOWN", "2" }, { "ctrl LEFT", "4" }, { "ctrl UP", "2" }, { "RIGHT", "10" } };
		for (final String[] usek : trasa) {
			final Action a = akce(KeyStroke.getKeyStroke(usek[0]));
			for (int i = 0; i < Integer.parseInt(usek[1]); i++) {
				naEdt(() -> a.actionPerformed(null));
				pockejNaKlid("posun " + usek[0]);
			}
		}
	}

	/** Akce, které se v průchodu menu nespouštějí: ukončí program, otevřou prohlížeč nebo sahají na internet, přepnou vzhled či mapu. */
	private static final Set<String> NESPOUSTET = new HashSet<>(Arrays.asList("CloseAction", "FullScreenAction", "NapovedaAction", "WebovaStrankaAction", "ZadatProblemAction",
			"ZkontrolovatAktualizaceAction", "ChangeLookAndFeelAction", "ChangeThemeAction", "PodkladAction", "OnlineModeAction"));

	/**
	 * Klikne na každou položku menu. Na každém okně, které se tím otevře, ověří, že je vidět, má rozumnou velikost a nějaký obsah, a zase ho
	 * zavře. Zaškrtávací položky vrátí do původního stavu.
	 */
	private void projdiMenu() throws Exception {
		naEdt(this::zkontrolujMnemoniky);
		int n = 0;
		for (final JMenuItem polozka : polozkyMenu) {
			final Action a = polozka.getAction();
			if (a == null) {
				continue; // vzhledy a témata, ty přepíná krok vzhled
			}
			final String jmeno = n++ + " " + textPolozky(polozka) + " (" + a.getClass().getSimpleName() + ")";
			if (NESPOUSTET.contains(a.getClass().getSimpleName())) {
				continue;
			}
			final boolean[] povolena = new boolean[1];
			naEdt(() -> povolena[0] = polozka.isEnabled());
			if (!povolena[0]) {
				zprava.setProperty("menu." + jmeno, "nepovolená");
				continue;
			}
			final List<String> okna = klikni(polozka, jmeno);
			if (polozka instanceof JCheckBoxMenuItem) {
				okna.addAll(klikni(polozka, jmeno + " zpět"));
			}
			zprava.setProperty("menu." + jmeno, okna.isEmpty() ? "bez okna" : String.join(" | ", okna));
		}
	}

	/** Přepne postupně všechny vzhledy (menu Skin) a vrátí ten původní. */
	private void prepniVzhledy() throws Exception {
		final List<JMenuItem> vzhledy = new ArrayList<>();
		JMenuItem puvodni = null;
		for (final JMenuItem p : polozkyMenu) {
			if (Arrays.stream(p.getActionListeners()).anyMatch(l -> l.getClass().getSimpleName().equals("ChangeLookAndFeelAction"))) {
				vzhledy.add(p);
				if (p.isSelected()) {
					puvodni = p;
				}
			}
		}
		if (vzhledy.isEmpty()) {
			chyby.add("V menu nejsou žádné vzhledy");
			return;
		}
		if (puvodni != null) {
			vzhledy.add(puvodni);
		}
		for (final JMenuItem p : vzhledy) {
			final List<String> okna = klikni(p, "vzhled " + p.getText());
			zprava.setProperty("vzhled." + p.getText(), okna.isEmpty() ? "bez okna" : String.join(" | ", okna));
		}
	}

	/** Podtržené písmeno smí být v liště i v každém menu jen jednou, jinak Alt+písmeno jen přepíná mezi položkami. */
	private void zkontrolujMnemoniky() {
		final JMenuBar lista = hlavniOkno.getJMenuBar();
		final List<JMenuItem> menu = new ArrayList<>();
		for (int i = 0; i < lista.getMenuCount(); i++) {
			if (lista.getMenu(i) != null) {
				menu.add(lista.getMenu(i));
			}
		}
		zkontrolujMnemoniky("lišta menu", menu, chyby);
		for (final JMenuItem m : menu) {
			final List<JMenuItem> polozkyJednohoMenu = new ArrayList<>();
			for (final Component c : ((JMenu) m).getMenuComponents()) {
				if (c instanceof JMenuItem) {
					polozkyJednohoMenu.add((JMenuItem) c);
				}
			}
			// Uvnitř menu shodné písmeno jen přepíná mezi položkami, proto jen varování.
			zkontrolujMnemoniky("menu " + m.getText(), polozkyJednohoMenu, varovani);
		}
	}

	private static void zkontrolujMnemoniky(final String kde, final List<JMenuItem> polozkyMenu, final List<String> kam) {
		final Map<Integer, List<String>> podlePismene = new TreeMap<>();
		for (final JMenuItem p : polozkyMenu) {
			if (p.getMnemonic() != 0) {
				podlePismene.computeIfAbsent(p.getMnemonic(), k -> new ArrayList<>()).add(p.getText());
			}
		}
		podlePismene.forEach((pismeno, texty) -> {
			if (texty.size() > 1) {
				kam.add("V " + kde + " má víc položek podtržené " + java.awt.event.KeyEvent.getKeyText(pismeno) + ": " + texty);
			}
		});
	}

	private List<String> klikni(final JMenuItem polozka, final String jmeno) throws Exception {
		final Set<Window> predtim = new HashSet<>(Arrays.asList(Window.getWindows()));
		SwingUtilities.invokeLater(() -> polozka.doClick(0));
		// Modální dialog má vlastní smyčku událostí, značka za kliknutím proto doběhne i s otevřeným dialogem.
		final boolean[] znacka = new boolean[1];
		SwingUtilities.invokeLater(() -> znacka[0] = true);
		try {
			cekej("obsluha kliknutí", 20_000, () -> znacka[0]);
		} catch (final IllegalStateException e) {
			chyby.add("Kliknutí na " + jmeno + " zablokovalo EDT");
			throw e;
		}
		Thread.sleep(500); // některé akce otevírají okno až ze SwingWorkeru
		final List<String> popisy = new ArrayList<>();
		naEdt(() -> {
			for (final Window w : Window.getWindows()) {
				if (!predtim.contains(w) && w.isShowing()) {
					final String popis = popisOkna(w);
					popisy.add(popis);
					final int obsah = w instanceof RootPaneContainer ? ((RootPaneContainer) w).getContentPane().getComponentCount() : w.getComponentCount();
					if (w.getWidth() < 100 || w.getHeight() < 50 || obsah == 0) {
						chyby.add("Po kliknutí na " + jmeno + " je okno prázdné nebo malé: " + popis);
					}
				}
			}
		});
		zavriNovaOkna(predtim);
		pockejNaKlid(jmeno);
		return popisy;
	}

	private void zavriNovaOkna(final Set<Window> predtim) throws Exception {
		for (int pokus = 0; pokus < 3; pokus++) {
			final List<Window> nova = new ArrayList<>();
			naEdt(() -> {
				for (final Window w : Window.getWindows()) {
					if (!predtim.contains(w) && w.isShowing()) {
						nova.add(w);
					}
				}
			});
			if (nova.isEmpty()) {
				return;
			}
			for (final Window w : nova) {
				// Zavřít jako uživatel křížkem, ať proběhne i úklid okna. Modální dialog vrátí řízení až po zavření, proto invokeLater.
				SwingUtilities.invokeLater(() -> {
					w.dispatchEvent(new java.awt.event.WindowEvent(w, java.awt.event.WindowEvent.WINDOW_CLOSING));
					if (w.isShowing()) {
						w.dispose();
					}
				});
			}
			Thread.sleep(300);
		}
		chyby.add("Nová okna nejdou zavřít");
	}

	private static String popisOkna(final Window w) {
		String titulek = w instanceof Dialog ? ((Dialog) w).getTitle() : w instanceof Frame ? ((Frame) w).getTitle() : "";
		final StringBuilder texty = new StringBuilder();
		sesbirejTexty(w, texty);
		return w.getClass().getSimpleName() + " \"" + titulek + "\" " + w.getWidth() + "x" + w.getHeight() + (texty.length() > 0 ? " [" + texty.toString().trim() + "]" : "");
	}

	/** Texty z hlášek (JOptionPane), ať je ve zprávě vidět, co program řekl. */
	private static void sesbirejTexty(final Container kde, final StringBuilder texty) {
		for (final Component c : kde.getComponents()) {
			if (c instanceof JOptionPane) {
				texty.append(String.valueOf(((JOptionPane) c).getMessage()).replace('\n', ' ')).append(' ');
			} else if (c instanceof Container) {
				sesbirejTexty((Container) c, texty);
			}
		}
	}

	private static String textPolozky(final JMenuItem polozka) {
		final Container menu = polozka.getParent() instanceof JPopupMenu ? (Container) ((JPopupMenu) polozka.getParent()).getInvoker() : null;
		return (menu instanceof JMenu ? ((JMenu) menu).getText() + " > " : "") + polozka.getText();
	}

	/** Od minula se změnil počítač: odpojený druhý monitor, odpojený disk s daty a cache, přesunutý GeoGet. */
	private static void zmenProstredi() throws IOException {
		// Pod obyčejným souborem složku nevytvoří nikdo, ani root.
		final File odpojeny = new File(System.getProperty("java.io.tmpdir"), "odpojeny-disk");
		odpojeny.createNewFile();
		final OknoUmisteniDto okno = new OknoUmisteniDto();
		okno.setPozice(new Point(3000, 2000));
		okno.setVelikost(new Dimension(1200, 800));
		MyPreferences.current().putStructure(FPref.OKNO_structure_node, okno);
		final MyPreferences umisteni = MyPreferences.current().node(FPref.UMISTENI_SOUBORU_node);
		umisteni.putFilex("kesDir", new Filex(new File(odpojeny, "geokuk"), false, true));
		umisteni.putFilex("geogetDataDir", new Filex(new File(odpojeny, "geoget"), false, true));
		umisteni.putFilex(FPref.KACHLE_CACHE_DIR_value, new Filex(new File(odpojeny, "kachle"), false, true));
	}

	/** Uživatel mačká klávesy rychleji, než se dlaždice stihnou načíst. */
	private void zbesile() throws Exception {
		final VyrezModel vyrez = vyrezModel();
		naEdt(() -> {
			vyrez.presunMapuNaMoustred(new Wgs(50.08, 14.42).toMou());
			vyrez.setMeritkoMapy(12);
		});
		final String[] zkratky = { "PAGE_UP", "PAGE_DOWN", "UP", "DOWN", "LEFT", "RIGHT", "ctrl UP", "ctrl DOWN", "ctrl LEFT", "ctrl RIGHT" };
		final Random r = new Random(1);
		for (int i = 0; i < 300; i++) {
			final Action a = akce(KeyStroke.getKeyStroke(zkratky[r.nextInt(zkratky.length)]));
			SwingUtilities.invokeLater(() -> {
				if (a.isEnabled()) {
					a.actionPerformed(null);
				}
			});
			Thread.sleep(r.nextInt(30));
		}
		pockejNaKlid("zběsilé klikání");
	}

	/** Změny velikosti okna za běhu, i na nesmyslně malé a zpět. */
	private void menOkno() throws Exception {
		final int[][] velikosti = { { 1300, 850 }, { 120, 80 }, { 1, 1 }, { 400, 900 }, { 1400, 200 }, { 1050, 675 } };
		for (final int[] v : velikosti) {
			naEdt(() -> {
				hlavniOkno.setExtendedState(Frame.NORMAL);
				hlavniOkno.setSize(v[0], v[1]);
				hlavniOkno.validate();
			});
			pockejNaKlid("okno " + v[0] + "x" + v[1]);
		}
		naEdt(() -> hlavniOkno.setExtendedState(Frame.MAXIMIZED_BOTH));
		pockejNaKlid("okno maximalizované");
		naEdt(() -> hlavniOkno.setExtendedState(Frame.ICONIFIED));
		pockejNaKlid("okno minimalizované");
		naEdt(() -> hlavniOkno.setExtendedState(Frame.NORMAL));
		pockejNaKlid("okno obnovené");
	}

	/** Čeká, až jsou všechny fronty dlaždic prázdné aspoň sekundu v kuse. */
	private void pockejNaKlid(final String kde) throws Exception {
		final long start = System.currentTimeMillis();
		long klidOd = -1;
		while (System.currentTimeMillis() - start < LIMIT_KLIDU_MS) {
			naEdt(() -> {}); // i EDT musí mít hotovo
			if (frontyPrazdne()) {
				if (klidOd < 0) {
					klidOd = System.currentTimeMillis();
				} else if (System.currentTimeMillis() - klidOd >= 1000) {
					return;
				}
			} else {
				klidOd = -1;
			}
			Thread.sleep(100);
		}
		chyby.add("Fronty dlaždic se nevyprázdnily do " + LIMIT_KLIDU_MS / 1000 + " s: " + kde);
	}

	private static boolean frontyPrazdne() {
		for (final Pocitadlo p : pocitadla()) {
			if (p.getName().matches("ka[123] .*") && p.get() != 0) {
				return false;
			}
		}
		return true;
	}

	private static List<Pocitadlo> pocitadla() {
		synchronized (SpravcePocitadel.getPocitadla()) {
			return new ArrayList<>(SpravcePocitadel.getPocitadla());
		}
	}

	private VyrezModel vyrezModel() throws Exception {
		return bean(VyrezModel.class);
	}

	/** Najde model programu v některé akci z menu, akce ho mají injektovaný. */
	private <T> T bean(final Class<T> trida) throws IllegalAccessException {
		for (final Action a : akce) {
			for (Class<?> c = a.getClass(); c != null; c = c.getSuperclass()) {
				for (final Field f : c.getDeclaredFields()) {
					if (f.getType() == trida) {
						f.setAccessible(true);
						final Object hodnota = f.get(a);
						if (hodnota != null) {
							return trida.cast(hodnota);
						}
					}
				}
			}
		}
		throw new IllegalStateException("Žádná akce nemá " + trida.getSimpleName());
	}

	private Action akce(final Class<?> trida) {
		return akce.stream().filter(trida::isInstance).findFirst().orElseThrow(() -> new IllegalStateException("V menu chybí " + trida.getSimpleName()));
	}

	private Action akce(final KeyStroke zkratka) {
		return akce.stream().filter(a -> zkratka.equals(a.getValue(Action.ACCELERATOR_KEY))).findFirst()
				.orElseThrow(() -> new IllegalStateException("V menu chybí akce se zkratkou " + zkratka));
	}

	private void sesbirejAkce(final JMenuItem polozka) {
		if (polozka == null) {
			return;
		}
		if (polozka.getAction() != null) {
			akce.add(polozka.getAction());
			polozky.put(polozka.getAction(), polozka);
		}
		if (!(polozka instanceof JMenu)) {
			polozkyMenu.add(polozka);
		}
		if (polozka instanceof JMenu) {
			for (final Component c : ((JMenu) polozka).getMenuComponents()) {
				if (c instanceof JMenuItem) {
					sesbirejAkce((JMenuItem) c);
				}
			}
		}
	}

	private static JFrame najdiHlavniOkno() {
		for (final Frame f : Frame.getFrames()) {
			if (f.getClass().getSimpleName().equals("JMainFrame") && f.isShowing()) {
				return (JFrame) f;
			}
		}
		return null;
	}

	private void snimek(final File soubor) {
		try {
			final Rectangle obrazovka = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
			final BufferedImage img = new Robot().createScreenCapture(obrazovka);
			ImageIO.write(img, "png", soubor);
		} catch (final Throwable t) {
			chyby.add("Snímek obrazovky: " + t);
		}
	}

	private void zapis(final File soubor) throws IOException {
		for (final Pocitadlo p : pocitadla()) {
			// Stejné jméno může mít víc instancí, sečtou se.
			final String klic = "pocitadlo." + p.getName();
			final long dosud = Long.parseLong(zprava.getProperty(klic, "0"));
			zprava.setProperty(klic, String.valueOf(dosud + p.get()));
		}
		for (int i = 0; i < nezachycene.size(); i++) {
			zprava.setProperty("nezachycena." + i, nezachycene.get(i));
		}
		for (int i = 0; i < chyby.size(); i++) {
			zprava.setProperty("chyba." + i, chyby.get(i));
		}
		for (int i = 0; i < varovani.size(); i++) {
			zprava.setProperty("varovani." + i, varovani.get(i));
		}
		if (hlidac != null) {
			zprava.setProperty("edt.nejdelsiMs", String.valueOf(hlidac.getNejdelsiMs()));
			final List<String> pomale = hlidac.getPomale();
			for (int j = 0; j < pomale.size(); j++) {
				zprava.setProperty("edt.pomala." + j, pomale.get(j));
			}
		}
		int i = 0;
		for (final Window w : Window.getWindows()) {
			if (w.isShowing()) {
				zprava.setProperty("okno." + i++, popisOkna(w));
			}
		}
		final Runtime rt = Runtime.getRuntime();
		System.gc();
		zprava.setProperty("pamet.mb", String.valueOf((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024));
		try (Writer w = new OutputStreamWriter(new FileOutputStream(soubor), StandardCharsets.UTF_8)) {
			zprava.store(w, "Smoke test");
		}
		Files.write(new File(soubor.getPath().replace(".properties", "-diagnostika.txt")).toPath(), Diagnostika.text().getBytes(StandardCharsets.UTF_8));
	}

	private void cekej(final String co, final long limitMs, final BooleanSupplier podminka) throws Exception {
		final long start = System.currentTimeMillis();
		while (!podminka.getAsBoolean()) {
			if (System.currentTimeMillis() - start > limitMs) {
				throw new IllegalStateException("Nedočkal jsem se: " + co);
			}
			Thread.sleep(100);
		}
	}

	private static void naEdt(final Runnable r) throws Exception {
		SwingUtilities.invokeAndWait(r);
	}

	private static String vypis(final Throwable t) {
		final StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}
}
