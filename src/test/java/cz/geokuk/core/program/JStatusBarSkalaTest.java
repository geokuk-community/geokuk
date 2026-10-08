package cz.geokuk.core.program;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.swing.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Na obrazovce se zvětšením se písmo měří s měřítkem obrazovky, takže texty stavového řádku jsou po zobrazení jinak široké než před ním. Měřítko se nastavuje
 * jen při startu JVM, proto test běží v samostatném procesu.
 */
public class JStatusBarSkalaTest {

	@Test
	public void pravyBlokCelyNaObrazovceSeZvetsenim() throws Exception {
		Assume.assumeFalse("bez displeje", GraphicsEnvironment.isHeadless());
		final List<String> prikaz = new ArrayList<>();
		prikaz.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
		prikaz.add("-Dsun.java2d.uiScale=2");
		prikaz.add("-Dfile.encoding=UTF-8");
		prikaz.add("-Dstdout.encoding=UTF-8");
		prikaz.add("-cp");
		prikaz.add(System.getProperty("java.class.path"));
		prikaz.add(JStatusBarSkalaTest.class.getName());
		final Process proces = new ProcessBuilder(prikaz).redirectErrorStream(true).start();
		final boolean skoncil = proces.waitFor(60, TimeUnit.SECONDS);
		final ByteArrayOutputStream bajty = new ByteArrayOutputStream();
		final byte[] buf = new byte[4096];
		for (int n; skoncil && (n = proces.getInputStream().read(buf)) > 0;) {
			bajty.write(buf, 0, n);
		}
		final String vystup = new String(bajty.toByteArray(), StandardCharsets.UTF_8);
		Assert.assertTrue("proces nedoběhl: " + vystup, skoncil);
		Assert.assertEquals(vystup, 0, proces.exitValue());
	}

	/** Zobrazí stavový řádek a skončí chybou, když některá položka pravého bloku nemá svou preferovanou šířku. */
	public static void main(final String[] args) throws Exception {
		final List<String> chyby = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			final JStatusBar radek = new JStatusBar();
			final JFrame okno = new JFrame();
			okno.add(radek, BorderLayout.SOUTH);
			for (final int sirka : new int[] { 1280, 900 }) {
				okno.setSize(sirka, 400);
				okno.setVisible(true);
				okno.validate();
				try {
					final java.lang.reflect.Field f = JStatusBar.class.getDeclaredField("pravyBlok");
					f.setAccessible(true);
					zkontroluj((Container) f.get(radek), sirka, chyby);
				} catch (final ReflectiveOperationException e) {
					chyby.add(e.toString());
				}
			}
			okno.dispose();
		});
		if (!chyby.isEmpty()) {
			System.out.println(String.join("\n", chyby));
			System.exit(1);
		}
		System.exit(0);
	}

	private static void zkontroluj(final Container c, final int sirka, final List<String> chyby) {
		for (final Component d : c.getComponents()) {
			if (d.isVisible()) {
				if (d.getWidth() < d.getPreferredSize().width) {
					chyby.add("šířka okna " + sirka + ": " + d.getWidth() + " < " + d.getPreferredSize().width + " " + d);
				}
				if (d instanceof Container) {
					zkontroluj((Container) d, sirka, chyby);
				}
			}
		}
	}
}
