package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.bridge.SLF4JBridgeHandler;

import cz.geokuk.core.lookandfeel.LafSupport;
import cz.geokuk.core.napoveda.Diagnostika;
import cz.geokuk.core.ovladani.DalkoveOvladani;
import cz.geokuk.core.profile.Nastaveni;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MyPreferences;
import cz.geokuk.start.Start;
import cz.geokuk.util.exception.*;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 *
 */
@Slf4j
public class GeokukMain {

	/** Drží se po celý běh, spouštěč podle něj pozná, že GeoKuk skončil. */
	@SuppressWarnings("unused")
	private static FileLock zamek;

	public static void main(final String[] args) {
		new GeokukMain().execute(args);
	}

	public void execute(final String[] args) {
		FConst.logInit();
		AppUserModelId.nastav();
		presmerujJulDoSlf4j();
		final File souborZamku = new File(FConst.DATA_DIR, Start.ZAMEK);
		zamek = zamkni(souborZamku, Start::zamkni);
		if (uzBezi(zamek, souborZamku)) {
			log.info("GeoKuk nad složkou {} už běží, druhá instance končí.", FConst.DATA_DIR);
			try {
				if (!GraphicsEnvironment.isHeadless()) {
					JOptionPane.showMessageDialog(null, "GeoKuk už běží. Přepněte se do jeho okna.", "GeoKuk", JOptionPane.INFORMATION_MESSAGE);
				}
			} finally {
				System.exit(0);
			}
		}
		// Obrázky číst v paměti: s cache v TEMP by při plném disku nešly načíst ikony ani dlaždice.
		ImageIO.setUseCache(false);
		Diagnostika.sledujKliknuti();
		log.info("Default character encoding: {}", Charset.defaultCharset());
		final String zaloha = System.getProperty(Start.ZALOHA);
		if (zaloha != null) {
			log.warn("Spouštěč spustil zálohu geokuk.jar.bak, důvod: {}", zaloha);
		}
		nastavSkin();
		Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler());
		promazPreferencePokudJeToPrikazano(args);
		final Integer portOvladani = DalkoveOvladani.portZParametru(args);
		final boolean vyvojoveOvladani = DalkoveOvladani.vyvojovaZParametru(args);

		SwingUtilities.invokeLater(() -> {
			final Inicializator inicializator = new Inicializator();
			inicializator.inicializace();
			final JMainFrame mainFrame = new JMainFrame();
			inicializator.setMainFrame(mainFrame);
			mainFrame.init();
			mainFrame.setVisible(true);
			final String varovani = Nastaveni.prevzitVarovani();
			if (varovani != null) {
				Dlg.error(varovani);
			}
			if (zaloha != null) {
				Dlg.upozorneni(textZalohy());
			}
			SwingUtilities.invokeLater(KontrolaUmisteni::zkontroluj);
			VytvoritZastupceAction.aktualizujZastupceVeSlozce();
			if (portOvladani != null) {
				inicializator.spustDalkoveOvladani(portOvladani, vyvojoveOvladani);
			} else if (DalkoveOvladani.jeZapnuteVNastaveni()) {
				inicializator.spustDalkoveOvladani(DalkoveOvladani.VYCHOZI_PORT, vyvojoveOvladani);
			} else if (vyvojoveOvladani) {
				inicializator.spustDalkoveOvladani(0, true);
			}
			inicializator.zkontrolovatAktualizace();
		});
	}

	/** Hláška pro uživatele, když spouštěč spustil předchozí verzi ze zálohy. */
	static String textZalohy() {
		return "Novou verzi GeoKuku se nepodařilo správně nainstalovat, proto se spustila předchozí verze. Můžete s ní normálně pracovat.\n"
				+ "Novou verzi nainstalujte znovu přes Nápověda > Zkontrolovat aktualizace.\n"
				+ "Když to nepomůže, stáhněte zip s programem z " + FConst.WEB_PAGE_URL + "/releases/latest a rozbalte ho přes složku s programem. Data a nastavení zůstanou.";
	}

	/** Zámek; když ho jiná instance pustila mezi pokusem a kontrolou, zkusí se ještě jednou. */
	static FileLock zamkni(final File souborZamku, final Function<File, FileLock> zamykac) {
		final FileLock prvni = zamykac.apply(souborZamku);
		return prvni != null || Start.jeZamceno(souborZamku) ? prvni : zamykac.apply(souborZamku);
	}

	/** Druhá instance nad stejnými daty by si s první přepisovaly nastavení a výlety. */
	static boolean uzBezi(final FileLock zamek, final File souborZamku) {
		return zamek == null && Start.jeZamceno(souborZamku);
	}

	/** Zprávy z java.util.logging do logu programu. */
	static void presmerujJulDoSlf4j() {
		if (!SLF4JBridgeHandler.isInstalled()) {
			SLF4JBridgeHandler.removeHandlersForRootLogger();
			SLF4JBridgeHandler.install();
		}
	}

	private void nastavSkin() {
		try {
			LafSupport.updateLookAndFeel();
		} catch (final Throwable t) {
			FExceptionDumper.dump(t, EExceptionSeverity.WORKARROUND, "Nastavení vzhledu programu");
		}
	}

	private void promazPreferencePokudJeToPrikazano(final String[] args) {
		for (final String s : args) {
			if (s.trim().equalsIgnoreCase("--reset")) {
				try {
					MyPreferences.root().removeNode();
				} catch (final BackingStoreException e) {
					FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Mazání nastavení (--reset)");
				}
			}
		}
	}

}
