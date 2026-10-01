package cz.geokuk.core.program;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.swing.*;

import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import lombok.extern.slf4j.Slf4j;

/** Vytvoří zástupce přenosného GeoKuku v nabídce Start nebo na ploše Windows. */
@Slf4j
public class VytvoritZastupceAction extends Action0 {

	private static final long serialVersionUID = 1L;

	/** Zástupce přes WScript.Shell, cesty v proměnných prostředí, aby nevadily mezery ani uvozovky. */
	private static final String SKRIPT = "$ErrorActionPreference='Stop';"
			+ "$sh=New-Object -ComObject WScript.Shell;"
			+ "foreach($kam in $env:GK_KAM.Split(';')){"
			+ "$s=$sh.CreateShortcut((Join-Path ([Environment]::GetFolderPath($kam)) 'GeoKuk.lnk'));"
			+ "$s.TargetPath=$env:GK_JAVAW;$s.Arguments=$env:GK_ARGUMENTY;$s.WorkingDirectory=$env:GK_SLOZKA;"
			+ "$s.IconLocation=$env:GK_IKONA+',0';$s.Description='GeoKuk';$s.Save()}";

	public VytvoritZastupceAction() {
		super("Vytvořit zástupce...");
		putValue(SHORT_DESCRIPTION, "Vytvoří zástupce GeoKuku v nabídce Start nebo na ploše.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_V);
		setEnabled(lzeVytvorit());
	}

	static File javaw() {
		return new File(FConst.JAR_DIR, "runtime\\bin\\javaw.exe");
	}

	static boolean lzeVytvorit() {
		return System.getProperty("os.name", "").startsWith("Windows") && javaw().isFile() && new File(FConst.JAR_DIR, "start.jar").isFile();
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final JCheckBox start = new JCheckBox("V nabídce Start", true);
		final JCheckBox plocha = new JCheckBox("Na ploše", false);
		final int n = JOptionPane.showConfirmDialog(Dlg.parentFrame(), new Object[] { "Kde vytvořit zástupce GeoKuku?", start, plocha }, "Vytvořit zástupce",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (n != JOptionPane.OK_OPTION || !start.isSelected() && !plocha.isSelected()) {
			return;
		}
		final List<String> kam = new ArrayList<>();
		if (start.isSelected()) {
			kam.add("Programs");
		}
		if (plocha.isSelected()) {
			kam.add("Desktop");
		}
		new SwingWorker<String, Void>() {
			@Override
			protected String doInBackground() throws Exception {
				return vytvor(String.join(";", kam));
			}

			@Override
			protected void done() {
				try {
					final String chyba = get();
					if (chyba == null) {
						Dlg.info("Zástupce je vytvořený. Když GeoKuk přesunete, vytvořte ho znovu.", "Vytvořit zástupce");
					} else {
						Dlg.error("Zástupce se nepodařilo vytvořit:\n" + chyba);
					}
				} catch (final Exception ex) {
					Dlg.error("Zástupce se nepodařilo vytvořit:\n" + ex);
				}
			}
		}.execute();
	}

	/** Vrátí popis chyby, nebo null. */
	private static String vytvor(final String kam) throws IOException, InterruptedException {
		final ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", SKRIPT);
		final Map<String, String> env = pb.environment();
		env.put("GK_KAM", kam);
		env.put("GK_JAVAW", javaw().getAbsolutePath());
		env.put("GK_ARGUMENTY", "-jar \"" + new File(FConst.JAR_DIR, "start.jar").getAbsolutePath() + "\"");
		env.put("GK_SLOZKA", FConst.JAR_DIR.getAbsolutePath());
		env.put("GK_IKONA", new File(FConst.JAR_DIR, "geokuk.ico").getAbsolutePath());
		pb.redirectErrorStream(true);
		final Process p = pb.start();
		p.getOutputStream().close();
		final ByteArrayOutputStream vystup = new ByteArrayOutputStream();
		try (InputStream in = p.getInputStream()) {
			final byte[] buf = new byte[4096];
			int k;
			while ((k = in.read(buf)) > 0) {
				vystup.write(buf, 0, k);
			}
		}
		final int kod = p.waitFor();
		if (kod == 0) {
			log.info("Vytvořen zástupce: {}", kam);
			return null;
		}
		final String text = new String(vystup.toByteArray(), StandardCharsets.UTF_8).trim();
		log.warn("Zástupce nelze vytvořit ({}): {}", kod, text);
		return text.isEmpty() ? "PowerShell skončil s kódem " + kod : text;
	}
}
