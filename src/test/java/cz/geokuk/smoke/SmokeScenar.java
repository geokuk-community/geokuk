package cz.geokuk.smoke;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import javax.imageio.ImageIO;
import javax.swing.*;

import cz.geokuk.core.coord.*;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.core.program.FPref;
import cz.geokuk.core.program.GeokukMain;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.mapy.PodkladAction;
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
	private JFrame hlavniOkno;
	private final List<Action> akce = new ArrayList<>();
	private final Map<Action, JMenuItem> polozky = new HashMap<>();

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
		System.exit(0);
	}

	private void proved(final String[] kroky) throws Exception {
		MyPreferences.current().node(FPref.VSEOBECNE_node).putLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, Long.MAX_VALUE);
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
		int i = 0;
		for (final Window w : Window.getWindows()) {
			if (w.isShowing()) {
				zprava.setProperty("okno." + i++, w.getClass().getName() + " " + w.getWidth() + "x" + w.getHeight());
			}
		}
		final Runtime rt = Runtime.getRuntime();
		System.gc();
		zprava.setProperty("pamet.mb", String.valueOf((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024));
		try (Writer w = new OutputStreamWriter(new FileOutputStream(soubor), StandardCharsets.UTF_8)) {
			zprava.store(w, "Smoke test");
		}
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
