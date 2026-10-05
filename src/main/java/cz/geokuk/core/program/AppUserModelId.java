package cz.geokuk.core.program;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

import lombok.extern.slf4j.Slf4j;

/** Windows spojí okno s připnutým zástupcem na hlavním panelu podle AppUserModelID procesu. */
@Slf4j
final class AppUserModelId {

	static final String ID = "GeoKuk";

	private AppUserModelId() {}

	interface Shell32 extends StdCallLibrary {
		int SetCurrentProcessExplicitAppUserModelID(WString id);
	}

	/** Zavolat před prvním oknem. Chyba jen do logu, program běží i bez ID. */
	static void nastav() {
		if (!System.getProperty("os.name", "").startsWith("Windows")) {
			return;
		}
		try {
			final int hr = Native.load("shell32", Shell32.class).SetCurrentProcessExplicitAppUserModelID(new WString(ID));
			log.info("AppUserModelID {}: {}", ID, hr == 0 ? "nastaveno" : "chyba " + hr);
		} catch (final Throwable e) {
			log.warn("AppUserModelID se nepodařilo nastavit", e);
		}
	}
}
