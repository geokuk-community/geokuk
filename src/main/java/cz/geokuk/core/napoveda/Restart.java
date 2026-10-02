package cz.geokuk.core.napoveda;

import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.start.Start;
import lombok.extern.slf4j.Slf4j;

/** Restart po stažení nové verze: spouštěč počká, až GeoKuk skončí, vymění jar a spustí novou verzi. */
@Slf4j
public final class Restart {

	private Restart() {}

	/** Jen přenosná verze má spouštěč, který umí jar vyměnit. */
	public static boolean lze() {
		return FConst.JAR_DIR_EXISTUJE && new File(FConst.JAR_DIR, "start.jar").isFile();
	}

	/** Ukončí GeoKuk jako Soubor > Konec a po skončení ho spustí znovu. Když uživatel ukončení zruší, nestane se nic. */
	public static void restartuj() {
		final Thread spoustec = new Thread(Restart::spustSpoustec, "Restart");
		Runtime.getRuntime().addShutdownHook(spoustec);
		final JFrame okno = Dlg.parentFrame();
		okno.dispatchEvent(new WindowEvent(okno, WindowEvent.WINDOW_CLOSING));
		// Sem se dojde, jen když uživatel ukončení zrušil.
		Runtime.getRuntime().removeShutdownHook(spoustec);
	}

	private static void spustSpoustec() {
		final File bin = new File(System.getProperty("java.home"), "bin");
		File java = new File(bin, "javaw.exe");
		if (!java.isFile()) {
			java = new File(bin, "java.exe");
		}
		if (!java.isFile()) {
			java = new File(bin, "java");
		}
		final File nic = new File(System.getProperty("os.name", "").startsWith("Windows") ? "NUL" : "/dev/null");
		try {
			new ProcessBuilder(java.getPath(), "-XX:-UsePerfData", "-jar", new File(FConst.JAR_DIR, "start.jar").getPath(), Start.PO_UKONCENI)
					.directory(FConst.JAR_DIR).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(nic)).start();
		} catch (final IOException e) {
			log.error("Spouštěč pro restart nejde spustit", e);
		}
	}
}
