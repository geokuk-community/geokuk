package cz.geokuk.core.napoveda;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.swing.AbstractButton;
import javax.swing.Action;

import com.jcabi.manifests.Manifests;

import cz.geokuk.core.program.FConst;
import cz.geokuk.util.exception.FExceptionDumper;

/**
 * Informace pro hlášení chyby: verze, prostředí a posledních pár událostí v programu.
 */
public final class Diagnostika {

	private static final int MAX_ZAZNAMU = 30;
	private static final long START = System.currentTimeMillis();
	private static final Deque<String> udalosti = new ArrayDeque<>();
	private static final Deque<String> chyby = new ArrayDeque<>();
	private static int pocetChyb;

	public static final String COMMIT = Manifests.exists("Geokuk-Commit") ? Manifests.read("Geokuk-Commit") : "";

	/** Verze s příponou (beta, vývojové sestavení) se ukazuje trvale v okně. */
	public static boolean jeTestovaciVerze() {
		return FConst.VERSION.contains("-");
	}

	public static String popisVerze() {
		return COMMIT.isEmpty() ? FConst.VERSION : FConst.VERSION + " (" + COMMIT + ")";
	}

	public static void zaznamenej(final String udalost) {
		pridej(udalosti, udalost);
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

	/** Zaznamenává kliknutí na tlačítka a položky menu. */
	public static void sledujKliknuti() {
		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			if (event.getID() == MouseEvent.MOUSE_RELEASED && event.getSource() instanceof AbstractButton) {
				final AbstractButton button = (AbstractButton) event.getSource();
				if (button.isEnabled()) {
					zaznamenej("Klik: " + popis(button));
				}
			}
		}, AWTEvent.MOUSE_EVENT_MASK);
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
		sb.append("Beta kanál: ").append(ano(FConst.JAR_DIR_EXISTUJE && new File(FConst.JAR_DIR, "beta").exists())).append('\n');
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
		sb.append("Běží: ").append((System.currentTimeMillis() - START) / 60000).append(" min\n");
		vypis(sb, "Poslední události", udalosti);
		synchronized (chyby) {
			vypis(sb, "Poslední chyby (celkem " + pocetChyb + ")", chyby);
		}
		return sb.toString();
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
