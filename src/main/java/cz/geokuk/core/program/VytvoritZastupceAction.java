package cz.geokuk.core.program;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.swing.*;

import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MyPreferences;
import lombok.extern.slf4j.Slf4j;

/** Vytvoří zástupce přenosného GeoKuku v nabídce Start nebo na ploše Windows. */
@Slf4j
public class VytvoritZastupceAction extends Action0 {

	private static final long serialVersionUID = 1L;

	/** Zástupce přes WScript.Shell, cesty v proměnných prostředí, aby nevadily mezery ani uvozovky. */
	/** Výstup PowerShellu jinak jde v kódování konzole (ve Windows česky CP852). */
	static final String UTF8_VYSTUP = "[Console]::OutputEncoding=New-Object Text.UTF8Encoding $false;";

	private static final String SKRIPT = UTF8_VYSTUP + "$ErrorActionPreference='Stop';"
			+ "$sh=New-Object -ComObject WScript.Shell;"
			+ "function Nastav($s){$s.TargetPath=$env:GK_JAVAW;$s.Arguments=$env:GK_ARGUMENTY;$s.WorkingDirectory=$env:GK_SLOZKA;"
			+ "$s.IconLocation=$env:GK_IKONA+',0';$s.Description='GeoKuk';$s.Save()}"
			+ "if($env:GK_KAM){foreach($kam in $env:GK_KAM.Split(';')){Nastav ($sh.CreateShortcut((Join-Path ([Environment]::GetFolderPath($kam)) 'GeoKuk.lnk')))}}"
			+ "if($env:GK_SOUBOR){Nastav ($sh.CreateShortcut($env:GK_SOUBOR));Unblock-File -LiteralPath $env:GK_JAVAW -ErrorAction SilentlyContinue}"
			// Zástupci na ploše, v nabídce Start a na hlavním panelu, kteří vedou do složky, odkud se GeoKuk přesunul.
			+ "if($env:GK_STARY_JAVAW -and -not (Test-Path -LiteralPath $env:GK_STARY_JAVAW)){"
			+ "foreach($d in @([Environment]::GetFolderPath('Desktop'),[Environment]::GetFolderPath('Programs'),"
			+ "(Join-Path $env:APPDATA 'Microsoft\\Internet Explorer\\Quick Launch\\User Pinned\\TaskBar'))){"
			+ "if($d -and (Test-Path -LiteralPath $d)){Get-ChildItem -LiteralPath $d -Filter *.lnk -File|ForEach-Object{"
			+ "$s=$sh.CreateShortcut($_.FullName);if($s.TargetPath -ieq $env:GK_STARY_JAVAW){Nastav $s;'Opraven '+$_.FullName}}}}}";

	/** Program, pro který je zástupce ve složce s programem. */
	private static final String ZASTUPCE_PRO_value = "zastupcePro";

	public VytvoritZastupceAction() {
		super("Vytvořit zástupce...");
		putValue(SHORT_DESCRIPTION, "Vytvoří zástupce GeoKuku v nabídce Start nebo na ploše.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_V);
		setEnabled(lzeVytvorit());
	}

	static File javaw() {
		return javaw(FConst.JAR_DIR);
	}

	private static File javaw(final File adresarProgramu) {
		return new File(adresarProgramu, "runtime\\bin\\javaw.exe");
	}

	/**
	 * Zástupce GeoKuk.lnk ve složce s programem, ať ho uživatel může zkopírovat, kam chce. Po přesunu složky opraví i zástupce, kteří vedou na
	 * původní místo.
	 */
	public static void aktualizujZastupceVeSlozce() {
		if (!lzeVytvorit() || FConst.KOREN.equals(FConst.JAR_DIR)) {
			return;
		}
		final MyPreferences pref = MyPreferences.current().node(FPref.VSEOBECNE_node);
		final String program = FConst.JAR_DIR.getAbsolutePath();
		final String puvodni = pref.get(ZASTUPCE_PRO_value, null);
		final File zastupce = new File(FConst.KOREN, "GeoKuk.lnk");
		if (program.equals(puvodni) && zastupce.isFile()) {
			return;
		}
		final Map<String, String> env = new HashMap<>();
		env.put("GK_SOUBOR", zastupce.getAbsolutePath());
		if (puvodni != null && !program.equals(puvodni)) {
			env.put("GK_STARY_JAVAW", javaw(new File(puvodni)).getAbsolutePath());
		}
		final Thread vlakno = new Thread(() -> {
			try {
				if (spust(env) == null) {
					pref.put(ZASTUPCE_PRO_value, program);
				}
			} catch (final IOException e) {
				log.warn("Zástupce ve složce s programem nelze vytvořit", e);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "Zástupce");
		vlakno.setDaemon(true);
		vlakno.start();
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
				return spust(Collections.singletonMap("GK_KAM", String.join(";", kam)));
			}

			@Override
			protected void done() {
				try {
					final String chyba = get();
					if (chyba == null) {
						Dlg.info("Zástupce je vytvořený.", "Vytvořit zástupce");
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
	private static String spust(final Map<String, String> promenne) throws IOException, InterruptedException {
		final Map<String, String> env = new HashMap<>(promenne);
		env.put("GK_JAVAW", javaw().getAbsolutePath());
		env.put("GK_ARGUMENTY", "-XX:-UsePerfData -jar \"" + new File(FConst.JAR_DIR, "start.jar").getAbsolutePath() + "\"");
		env.put("GK_SLOZKA", FConst.JAR_DIR.getAbsolutePath());
		env.put("GK_IKONA", new File(FConst.JAR_DIR, "geokuk.ico").getAbsolutePath());
		final Vysledek v = powershell(SKRIPT, env);
		if (v.kod == 0) {
			log.info("Vytvořen zástupce: {} {}", promenne, v.text);
			return null;
		}
		log.warn("Zástupce nelze vytvořit ({}): {}", v.kod, v.text);
		return v.text.isEmpty() ? "PowerShell skončil s kódem " + v.kod : v.text;
	}

	static final class Vysledek {
		final int kod;
		final String text;

		Vysledek(final int kod, final String text) {
			this.kod = kod;
			this.text = text;
		}
	}

	static Vysledek powershell(final String skript, final Map<String, String> promenne) throws IOException, InterruptedException {
		final ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", skript);
		pb.environment().putAll(promenne);
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
		return new Vysledek(kod, new String(vystup.toByteArray(), StandardCharsets.UTF_8).trim());
	}
}
