package cz.geokuk.core.program;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
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
		presmerujJulDoSlf4j();
		final File souborZamku = new File(FConst.DATA_DIR, Start.ZAMEK);
		zamek = Start.zamkni(souborZamku);
		if (uzBezi(zamek, souborZamku)) {
			log.info("GeoKuk nad složkou {} už běží, druhá instance končí.", FConst.DATA_DIR);
			if (!GraphicsEnvironment.isHeadless()) {
				JOptionPane.showMessageDialog(null, "GeoKuk už běží. Přepněte se do jeho okna.", "GeoKuk", JOptionPane.INFORMATION_MESSAGE);
			}
			System.exit(0);
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
				Dlg.upozorneni(textZalohy(zaloha));
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
	static String textZalohy(final String duvod) {
		final String proc = Start.ZALOHA_CHYBI.equals(duvod)
				? "Novou verzi GeoKuku se nepodařilo nainstalovat, proto běží předchozí verze."
				: "Nová verze GeoKuku nejde spustit (soubor geokuk.jar je poškozený), proto běží předchozí verze.";
		return proc + "\nNainstalujte novou verzi znovu (Nápověda → Zkontrolovat aktualizace), nebo rozbalte znovu celý zip s programem.";
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
