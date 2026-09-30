package cz.geokuk.util.process;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URL;

import javax.swing.*;

import cz.geokuk.framework.Dlg;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * Komplet zkopírované z webu
 *
 * A simple, static class to display a URL in the system browser.
 *
 * Under Unix, the system browser is hard-coded to be 'netscape'. Netscape must be in your PATH for this to work. This has been tested with the following platforms: AIX, HP-UX and Solaris.
 *
 * Under Windows, this will bring up the default browser under windows, usually either Netscape or Microsoft IE. The default browser is determined by the OS. This has been tested under Windows 95/98/NT.
 *
 * Examples: * BrowserControl.displayURL("http://www.javaworld.com")
 *
 * BrowserControl.displayURL("file://c:\\docs\\index.html")
 *
 * BrowserContorl.displayURL("file:///user/joe/index.html");
 *
 * Note - you must include the url type -- either "http://" or "file://".
 */
@Slf4j
public class BrowserOpener {
	/**
	 * Display a file in the system browser. If you want to display a file, you must include the absolute path name.
	 *
	 * @param url
	 *            the file's url (the url must start with either "http://" or "file://").
	 */
	public static void displayURL(final URL url) {
		try {
			Desktop.getDesktop().browse(url.toURI());
		} catch (final Exception e) {
			try {
				final Runtime runtime = Runtime.getRuntime();
				runtime.exec("xdg-open " + url);
			} catch (final Exception e1) {
				log.warn("Prohlížeč nejde otevřít pro {}", url, e);
				if (SwingUtilities.isEventDispatchThread()) {
					nelzeOtevrit(url);
				} else {
					SwingUtilities.invokeLater(() -> nelzeOtevrit(url));
				}
			}
		}
	}

	private static void nelzeOtevrit(final URL url) {
		final JTextField adresa = new JTextField(url.toString());
		adresa.setEditable(false);
		final String zkopirovat = "Zkopírovat adresu";
		final int volba = JOptionPane.showOptionDialog(Dlg.parentFrame(), new Object[] { "Prohlížeč se nepodařilo otevřít. Otevřete si adresu ručně:", adresa }, "Geokuk",
				JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new Object[] { zkopirovat, "Zavřít" }, zkopirovat);
		if (volba == 0) {
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url.toString()), null);
		}
	}
}
