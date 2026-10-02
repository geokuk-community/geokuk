package cz.geokuk.core.program;

import java.io.File;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.prefs.BackingStoreException;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

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
		zamek = Start.zamkni(new File(FConst.DATA_DIR, Start.ZAMEK));
		// Obrázky číst v paměti: s cache v TEMP by při plném disku nešly načíst ikony ani dlaždice.
		ImageIO.setUseCache(false);
		Diagnostika.sledujKliknuti();
		log.info("Default character encoding: {}", Charset.defaultCharset());
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

	private void nastavSkin() {
		try {
			LafSupport.updateLookAndFeel();
		} catch (final Throwable t) {
			FExceptionDumper.dump(t, EExceptionSeverity.WORKARROUND, "Nastavení skinu");
		}
	}

	private void promazPreferencePokudJeToPrikazano(final String[] args) {
		for (final String s : args) {
			if (s.trim().equalsIgnoreCase("--reset")) {
				try {
					MyPreferences.root().removeNode();
				} catch (final BackingStoreException e) {
					FExceptionDumper.dump(e, EExceptionSeverity.DISPLAY, "Problém s promazáváním preferencí");
				}
			}
		}
	}

}
