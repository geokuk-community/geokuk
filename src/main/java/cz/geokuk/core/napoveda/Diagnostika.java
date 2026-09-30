package cz.geokuk.core.napoveda;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.*;

import com.jcabi.manifests.Manifests;

import cz.geokuk.core.program.FConst;
import cz.geokuk.util.exception.FExceptionDumper;
import cz.geokuk.util.pocitadla.Pocitadlo;
import cz.geokuk.util.pocitadla.SpravcePocitadel;
import lombok.extern.slf4j.Slf4j;

/**
 * Informace pro hlášení chyby: verze, prostředí a posledních pár událostí v programu.
 */
@Slf4j
public final class Diagnostika {

	private static final int MAX_ZAZNAMU = 100;
	private static final int RADKU_LOGU = 50;
	/** Stejné umístění jako v logback.xml. */
	static final File LOG = new File(new File(System.getProperty("java.io.tmpdir"), "geokuk"), "geokuk.log");
	private static final long START = System.currentTimeMillis();
	private static final Deque<String> udalosti = new ArrayDeque<>();
	private static final Deque<String> chyby = new ArrayDeque<>();
	private static int pocetChyb;

	public static final String COMMIT = Manifests.exists("Geokuk-Commit") ? Manifests.read("Geokuk-Commit") : "";

	/** Soubor beta vedle jaru zapne nabízení testovacích verzí. */
	public static boolean betaKanal() {
		return FConst.JAR_DIR_EXISTUJE && new File(FConst.JAR_DIR, "beta").exists();
	}

	/** Testovací verze a instalace s beta kanálem ukazují verzi trvale v okně. */
	public static boolean zobrazovatVerzi() {
		return FConst.VERSION.contains("-") || betaKanal();
	}

	public static String popisVerze() {
		final String verze = COMMIT.isEmpty() ? FConst.VERSION : FConst.VERSION + " (" + COMMIT + ")";
		return betaKanal() ? verze + " · beta kanál" : verze;
	}

	public static void zaznamenej(final String udalost) {
		synchronized (udalosti) {
			dokonciMapu();
			zapis(udalost);
		}
	}

	private static void zapis(final String udalost) {
		log.info("Událost: {}", udalost);
		pridej(udalosti, udalost);
	}

	// Práce s mapou se zapisuje souhrnně, jinak by každý posun vytlačil ostatní události.
	private static int posunu;
	private static int meritkoOd = -1;
	private static int meritko = -1;
	private static Object stred;

	/** Změna výřezu mapy, bez polohy. */
	public static void zaznamenejVyrez(final int noveMeritko, final Object novyStred) {
		synchronized (udalosti) {
			if (meritkoOd < 0) {
				meritkoOd = meritko < 0 ? noveMeritko : meritko;
			}
			if (meritko == noveMeritko && !Objects.equals(stred, novyStred)) {
				posunu++;
			}
			meritko = noveMeritko;
			stred = novyStred;
		}
	}

	private static void dokonciMapu() {
		if (meritkoOd >= 0 && (posunu > 0 || meritkoOd != meritko)) {
			zapis("Mapa: " + (posunu > 0 ? posunu + "× posun, " : "") + (meritkoOd != meritko ? "měřítko " + meritkoOd + " → " + meritko : "měřítko " + meritko));
		}
		posunu = 0;
		meritkoOd = -1;
	}

	public static void zaznamenejChybu(final String chyba) {
		synchronized (chyby) {
			pocetChyb++;
		}
		pridej(chyby, chyba);
	}

	private static void pridej(final Deque<String> seznam, final String text) {
		final String radek = new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + text;
		synchronized (seznam) {
			seznam.addLast(radek);
			if (seznam.size() > MAX_ZAZNAMU) {
				seznam.removeFirst();
			}
		}
	}

	/** Zaznamenává kliknutí na tlačítka a otevírání a zavírání oken. Položky menu sleduje {@link #sledujMenu(JMenuBar)}. */
	public static void sledujKliknuti() {
		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			if (event.getID() == MouseEvent.MOUSE_RELEASED && event.getSource() instanceof AbstractButton && !(event.getSource() instanceof JMenuItem)) {
				final AbstractButton button = (AbstractButton) event.getSource();
				if (button.isEnabled()) {
					// Stav přepínače je známý až po obsloužení kliknutí.
					SwingUtilities.invokeLater(() -> zaznamenej("Klik: " + popis(button) + stav(button)));
				}
			}
		}, AWTEvent.MOUSE_EVENT_MASK);
		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			if (event.getID() == WindowEvent.WINDOW_OPENED) {
				zaznamenej("Otevřeno okno: " + popisOkna(((WindowEvent) event).getWindow()));
			} else if (event.getID() == WindowEvent.WINDOW_CLOSED) {
				zaznamenej("Zavřeno okno: " + titulek(((WindowEvent) event).getWindow()));
			}
		}, AWTEvent.WINDOW_EVENT_MASK);
	}

	/** Zaznamenává spuštění položek menu i s cestou v menu, způsobem spuštění a stavem přepínače. */
	public static void sledujMenu(final JMenuBar lista) {
		for (int i = 0; i < lista.getMenuCount(); i++) {
			if (lista.getMenu(i) != null) {
				sledujMenu(lista.getMenu(i));
			}
		}
	}

	private static void sledujMenu(final JMenu menu) {
		for (final Component c : menu.getMenuComponents()) {
			sledujPolozku(c);
		}
		menu.getPopupMenu().addContainerListener(new ContainerAdapter() {
			@Override
			public void componentAdded(final ContainerEvent e) {
				sledujPolozku(e.getChild());
			}
		});
	}

	private static void sledujPolozku(final Component c) {
		if (c instanceof JMenu) {
			sledujMenu((JMenu) c);
		} else if (c instanceof JMenuItem) {
			final JMenuItem polozka = (JMenuItem) c;
			polozka.addActionListener(e -> zaznamenej(zpusob(polozka) + ": " + cesta(polozka) + stav(polozka)));
		}
	}

	private static String zpusob(final JMenuItem polozka) {
		final AWTEvent udalost = EventQueue.getCurrentEvent();
		if (udalost instanceof KeyEvent) {
			final KeyStroke zkratka = polozka.getAccelerator();
			return zkratka != null && zkratka.equals(KeyStroke.getKeyStrokeForEvent((KeyEvent) udalost)) ? "Klávesa " + popisZkratky(zkratka) : "Menu klávesnicí";
		}
		return "Menu";
	}

	private static String popisZkratky(final KeyStroke zkratka) {
		final String modifikatory = InputEvent.getModifiersExText(zkratka.getModifiers());
		final String klavesa = zkratka.getKeyCode() == KeyEvent.VK_UNDEFINED ? String.valueOf(zkratka.getKeyChar()) : KeyEvent.getKeyText(zkratka.getKeyCode());
		return modifikatory.isEmpty() ? klavesa : modifikatory + "+" + klavesa;
	}

	static String cesta(final JMenuItem polozka) {
		final Deque<String> cesta = new ArrayDeque<>();
		Component c = polozka;
		while (c instanceof JMenuItem) {
			cesta.addFirst(text((JMenuItem) c));
			c = c.getParent() instanceof JPopupMenu ? ((JPopupMenu) c.getParent()).getInvoker() : null;
		}
		return String.join(" > ", cesta);
	}

	private static String text(final AbstractButton b) {
		return popis(b).replaceAll("<[^>]*>", "").trim();
	}

	private static String stav(final AbstractButton b) {
		return b instanceof JCheckBoxMenuItem || b instanceof JToggleButton ? (b.isSelected() ? " → zapnuto" : " → vypnuto") : "";
	}

	private static String titulek(final Window w) {
		final String titulek = w instanceof Frame ? ((Frame) w).getTitle() : w instanceof Dialog ? ((Dialog) w).getTitle() : null;
		return titulek == null || titulek.isEmpty() ? w.getClass().getSimpleName() : titulek;
	}

	private static String popisOkna(final Window w) {
		final StringBuilder zprava = new StringBuilder();
		if (w instanceof RootPaneContainer) {
			hledejZpravu(((RootPaneContainer) w).getContentPane(), zprava);
		}
		final String text = zprava.toString().replaceAll("\\s+", " ").trim();
		return titulek(w) + (text.isEmpty() ? "" : " – " + (text.length() > 200 ? text.substring(0, 200) + "…" : text));
	}

	private static void hledejZpravu(final Container kde, final StringBuilder zprava) {
		for (final Component c : kde.getComponents()) {
			if (c instanceof JOptionPane) {
				final Object m = ((JOptionPane) c).getMessage();
				zprava.append(m instanceof Object[] ? Arrays.toString((Object[]) m) : String.valueOf(m)).append(' ');
			} else if (c instanceof Container) {
				hledejZpravu((Container) c, zprava);
			}
		}
	}

	private static String popis(final AbstractButton button) {
		if (button.getText() != null && !button.getText().isEmpty()) {
			return button.getText();
		}
		final Action action = button.getAction();
		if (action != null && action.getValue(Action.NAME) != null) {
			return String.valueOf(action.getValue(Action.NAME));
		}
		return button.getToolTipText() != null ? button.getToolTipText() : button.getClass().getSimpleName();
	}

	public static String text() {
		final StringBuilder sb = new StringBuilder();
		final Runtime runtime = Runtime.getRuntime();
		final long mb = 1024 * 1024;
		sb.append("Geokuk ").append(popisVerze()).append('\n');
		sb.append("Beta kanál: ").append(ano(betaKanal())).append('\n');
		sb.append("Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")\n");
		sb.append("Systém: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append(' ').append(System.getProperty("os.arch")).append('\n');
		sb.append("Paměť: ").append((runtime.totalMemory() - runtime.freeMemory()) / mb).append(" MB z max ").append(runtime.maxMemory() / mb).append(" MB\n");
		if (!GraphicsEnvironment.isHeadless()) {
			final Dimension obrazovka = Toolkit.getDefaultToolkit().getScreenSize();
			sb.append("Obrazovka: ").append(obrazovka.width).append('x').append(obrazovka.height).append(", monitorů ").append(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length).append('\n');
		}
		sb.append("Složka programu: ").append(bezDomova(FConst.JAR_DIR)).append(FConst.JAR_DIR_EXISTUJE ? "" : " (nerozpoznána)").append('\n');
		sb.append("Spouštěč geokuk.cmd: ").append(ano(new File(FConst.JAR_DIR, "geokuk.cmd").exists())).append('\n');
		sb.append("Výpisy chyb: ").append(bezDomova(FExceptionDumper.getExcrepFolder())).append('\n');
		sb.append("Log: ").append(bezDomova(LOG)).append('\n');
		sb.append("Běží: ").append((System.currentTimeMillis() - START) / 60000).append(" min\n");
		synchronized (udalosti) {
			dokonciMapu();
		}
		vypis(sb, "Poslední události", udalosti);
		synchronized (chyby) {
			vypis(sb, "Poslední chyby (celkem " + pocetChyb + ")", chyby);
		}
		vypisPocitadla(sb);
		vypis(sb, "Konec logu", konecLogu(LOG, RADKU_LOGU));
		return sb.toString();
	}

	/** Totéž, co ukazuje servisní okno, ať to uživatel nemusí opisovat ze snímku obrazovky. */
	private static void vypisPocitadla(final StringBuilder sb) {
		final Map<String, java.util.List<Pocitadlo>> podleTypu = new TreeMap<>();
		for (final Pocitadlo pocitadlo : new ArrayList<>(SpravcePocitadel.getPocitadla())) {
			podleTypu.computeIfAbsent(pocitadlo.getTextovyPopisTypu(), typ -> new ArrayList<>()).add(pocitadlo);
		}
		if (podleTypu.isEmpty()) {
			return;
		}
		sb.append("\nServisní hodnoty:\n");
		for (final Map.Entry<String, java.util.List<Pocitadlo>> skupina : podleTypu.entrySet()) {
			sb.append("  ").append(skupina.getKey()).append(":\n");
			skupina.getValue().sort(Comparator.comparing(Pocitadlo::getName));
			for (final Pocitadlo pocitadlo : skupina.getValue()) {
				sb.append("    ").append(pocitadlo.getName()).append(": ").append(pocitadlo.get()).append('\n');
			}
		}
	}

	private static void vypis(final StringBuilder sb, final String nadpis, final Deque<String> seznam) {
		sb.append('\n').append(nadpis).append(":\n");
		synchronized (seznam) {
			if (seznam.isEmpty()) {
				sb.append("(žádné)\n");
			}
			for (final String s : seznam) {
				sb.append(s).append('\n');
			}
		}
	}

	static Deque<String> konecLogu(final File soubor, final int radku) {
		final Deque<String> konec = new ArrayDeque<>();
		if (!soubor.isFile()) {
			return konec;
		}
		final String domov = FConst.HOME_DIR.getAbsolutePath();
		try (BufferedReader reader = Files.newBufferedReader(soubor.toPath(), StandardCharsets.UTF_8)) {
			String radek;
			while ((radek = reader.readLine()) != null) {
				konec.addLast(radek.replace(domov, "~"));
				if (konec.size() > radku) {
					konec.removeFirst();
				}
			}
		} catch (final IOException e) {
			konec.addLast("Log nelze přečíst: " + e);
		}
		return konec;
	}

	private static String ano(final boolean b) {
		return b ? "ano" : "ne";
	}

	/** Domovskou složku zkrátí na ~, ať text neobsahuje jméno uživatele. */
	static String bezDomova(final File soubor) {
		final String cesta = soubor.getAbsolutePath();
		final String domov = FConst.HOME_DIR.getAbsolutePath();
		return cesta.startsWith(domov) ? "~" + cesta.substring(domov.length()) : cesta;
	}

	private Diagnostika() {}
}
