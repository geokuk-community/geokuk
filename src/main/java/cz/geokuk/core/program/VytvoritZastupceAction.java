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

	/** Výstup PowerShellu jinak jde v kódování konzole (ve Windows česky CP852). */
	private static final String UTF8_VYSTUP = "[Console]::OutputEncoding=New-Object Text.UTF8Encoding $false;";

	/** Zapíše System.AppUserModel.ID do zástupce; Add-Type z proměnné prostředí, ať se nemusí uvozovky v příkazové řádce. */
	static final String CSHARP = "using System;using System.Runtime.InteropServices;"
			+ "public static class GkAumid{"
			+ "[StructLayout(LayoutKind.Sequential)]public struct K{public uint a;public ushort b;public ushort c;public byte d0,d1,d2,d3,d4,d5,d6,d7;public uint pid;}"
			+ "[StructLayout(LayoutKind.Explicit)]public struct V{[FieldOffset(0)]public ushort vt;[FieldOffset(8)]public IntPtr p;[FieldOffset(16)]public long pad;}"
			+ "[ComImport,Guid(\"886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99\"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]public interface IPS{"
			+ "[PreserveSig]int GetCount(out uint c);[PreserveSig]int GetAt(uint i,out K k);[PreserveSig]int GetValue(ref K k,out V v);[PreserveSig]int SetValue(ref K k,ref V v);[PreserveSig]int Commit();}"
			+ "[ComImport,Guid(\"00021401-0000-0000-C000-000000000046\")]public class SL{}"
			+ "[ComImport,Guid(\"0000010b-0000-0000-C000-000000000046\"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]public interface IPF{"
			+ "void GetClassID(out Guid g);[PreserveSig]int IsDirty();void Load([MarshalAs(UnmanagedType.LPWStr)]string f,uint m);void Save([MarshalAs(UnmanagedType.LPWStr)]string f,bool r);void SaveCompleted(string f);void GetCurFile(out string f);}"
			+ "[DllImport(\"propsys.dll\",CharSet=CharSet.Unicode)]static extern int PSGetPropertyKeyFromName(string n,out K k);"
			+ "public static void Set(string lnk,string id){object o=new SL();((IPF)o).Load(lnk,2);var ps=(IPS)o;K k;int h=PSGetPropertyKeyFromName(\"System.AppUserModel.ID\",out k);if(h!=0)throw new Exception(\"PSGetPropertyKeyFromName \"+h);"
			+ "V v=new V();v.vt=31;v.p=Marshal.StringToCoTaskMemUni(id);h=ps.SetValue(ref k,ref v);if(h!=0)throw new Exception(\"SetValue \"+h);h=ps.Commit();if(h!=0)throw new Exception(\"Commit \"+h);((IPF)o).Save(lnk,true);}}";

	/** Zástupce přes WScript.Shell, cesty v proměnných prostředí, aby nevadily mezery ani uvozovky. */
	static final String SKRIPT = UTF8_VYSTUP + "$ErrorActionPreference='Stop';"
			// ID zástupce je navíc: když ho PowerShell nepovolí (zásady firemního počítače, antivir), zástupce vznikne bez něj a skript to ohlásí.
			+ "$script:idOk=$false;if($env:GK_CS){try{Add-Type -TypeDefinition $env:GK_CS;$script:idOk=$true}catch{$script:idChyba=$_.Exception.Message}}"
			+ "$sh=New-Object -ComObject WScript.Shell;"
			+ "function Nastav($s){$s.TargetPath=$env:GK_JAVAW;$s.Arguments=$env:GK_ARGUMENTY;$s.WorkingDirectory=$env:GK_SLOZKA;"
			+ "$s.IconLocation=$env:GK_IKONA+',0';$s.Description='GeoKuk';$s.Save();"
			+ "if($env:GK_AUMID -and $script:idOk){try{[GkAumid]::Set($s.FullName,$env:GK_AUMID)}catch{$script:idOk=$false;$script:idChyba=$_.Exception.Message}}}"
			+ "if($env:GK_KAM){foreach($kam in $env:GK_KAM.Split(';')){Nastav ($sh.CreateShortcut((Join-Path ([Environment]::GetFolderPath($kam)) 'GeoKuk.lnk')))}}"
			+ "if($env:GK_SOUBOR){Nastav ($sh.CreateShortcut($env:GK_SOUBOR));Unblock-File -LiteralPath $env:GK_JAVAW -ErrorAction SilentlyContinue}"
			// Zástupci na ploše, v nabídce Start a na hlavním panelu, kteří vedou do složky, odkud se GeoKuk přesunul.
			+ "if($env:GK_STARY_JAVAW -and -not (Test-Path -LiteralPath $env:GK_STARY_JAVAW)){"
			+ "foreach($d in @([Environment]::GetFolderPath('Desktop'),[Environment]::GetFolderPath('Programs'),"
			+ "(Join-Path $env:APPDATA 'Microsoft\\Internet Explorer\\Quick Launch\\User Pinned\\TaskBar'))){"
			+ "if($d -and (Test-Path -LiteralPath $d)){Get-ChildItem -LiteralPath $d -Filter *.lnk -File|ForEach-Object{"
			+ "$s=$sh.CreateShortcut($_.FullName);if($s.TargetPath -ieq $env:GK_STARY_JAVAW){Nastav $s;'Opraven '+$_.FullName}}}}}"
			+ "if($env:GK_AUMID){if($script:idOk){'AUMID-OK'}else{'AUMID-CHYBA: '+$script:idChyba}}";

	/** Program, pro který je zástupce ve složce s programem. */
	private static final String ZASTUPCE_PRO_value = "zastupcePro";

	/** Zástupce ve složce s programem se zapsaným AppUserModelID; starší se jednou přepíše. */
	private static final String ZASTUPCE_ID_value = "zastupceId";

	private static final String ZASTUPCE_ID_NELZE = "nelze";

	private static final String ID_OK = "AUMID-OK";
	private static final String ID_CHYBA = "AUMID-CHYBA";

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
		if (program.equals(puvodni) && zastupce.isFile() && pref.get(ZASTUPCE_ID_value, null) != null) {
			return;
		}
		final Map<String, String> env = new HashMap<>();
		env.put("GK_SOUBOR", zastupce.getAbsolutePath());
		if (puvodni != null && !program.equals(puvodni)) {
			env.put("GK_STARY_JAVAW", javaw(new File(puvodni)).getAbsolutePath());
		}
		final Thread vlakno = new Thread(() -> {
			try {
				final Vysledek v = spust(env);
				if (v.kod == 0) {
					pref.put(ZASTUPCE_PRO_value, program);
					// Když ID nešlo zapsat (zásady počítače), nezkouší se při každém startu znovu.
					pref.put(ZASTUPCE_ID_value, v.text.contains(ID_OK) ? AppUserModelId.ID : ZASTUPCE_ID_NELZE);
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
		new SwingWorker<Vysledek, Void>() {
			@Override
			protected Vysledek doInBackground() throws Exception {
				return spust(Collections.singletonMap("GK_KAM", String.join(";", kam)));
			}

			@Override
			protected void done() {
				try {
					final Vysledek v = get();
					if (v.kod != 0) {
						Dlg.error("Zástupce se nepodařilo vytvořit:\n" + (v.text.isEmpty() ? "PowerShell skončil s kódem " + v.kod : v.text));
					} else if (!v.text.contains(ID_OK)) {
						Dlg.upozorneni("Zástupce je vytvořený, ale PowerShell na tomto počítači nepovolil označit ho pro hlavní panel.\n"
								+ "Po připnutí na hlavní panel se může ukázat druhá ikona.");
					} else {
						Dlg.info("Zástupce je vytvořený.", "Vytvořit zástupce");
					}
				} catch (final Exception ex) {
					Dlg.error("Zástupce se nepodařilo vytvořit:\n" + ex);
				}
			}
		}.execute();
	}

	/** Výsledek skriptu; kód 0 je zástupce vytvořený, a když text obsahuje {@value #ID_OK}, i s AppUserModelID. */
	private static Vysledek spust(final Map<String, String> promenne) throws IOException, InterruptedException {
		final Map<String, String> env = new HashMap<>(promenne);
		env.put("GK_CS", CSHARP);
		env.put("GK_AUMID", AppUserModelId.ID);
		env.put("GK_JAVAW", javaw().getAbsolutePath());
		env.put("GK_ARGUMENTY", "-XX:-UsePerfData -jar \"" + new File(FConst.JAR_DIR, "start.jar").getAbsolutePath() + "\"");
		env.put("GK_SLOZKA", FConst.JAR_DIR.getAbsolutePath());
		env.put("GK_IKONA", new File(FConst.JAR_DIR, "geokuk.ico").getAbsolutePath());
		final Vysledek v = powershell(SKRIPT, env);
		if (v.kod != 0) {
			log.warn("Zástupce nelze vytvořit ({}): {}", v.kod, v.text);
		} else if (v.text.contains(ID_CHYBA)) {
			log.warn("Zástupce vytvořen bez AppUserModelID, na hlavním panelu se může ukázat druhá ikona: {} {}", promenne, v.text);
		} else {
			log.info("Vytvořen zástupce: {} {}", promenne, v.text);
		}
		return v;
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
