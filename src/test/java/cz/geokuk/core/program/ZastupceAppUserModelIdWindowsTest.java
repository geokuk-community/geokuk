package cz.geokuk.core.program;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

/** AppUserModelID procesu a zástupce se ověřuje na skutečném Windows, jinde se test přeskočí. */
public class ZastupceAppUserModelIdWindowsTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Before
	public void jenWindows() {
		Assume.assumeTrue("jen ve Windows", System.getProperty("os.name", "").startsWith("Windows"));
	}

	interface Shell32 extends StdCallLibrary {
		int GetCurrentProcessExplicitAppUserModelID(PointerByReference id);
	}

	@Test
	public void procesMaPoNastaveniId() {
		AppUserModelId.nastav();
		final PointerByReference id = new PointerByReference();
		Assert.assertEquals(0, Native.load("shell32", Shell32.class).GetCurrentProcessExplicitAppUserModelID(id));
		final Pointer p = id.getValue();
		Assert.assertEquals(AppUserModelId.ID, p.getWideString(0));
	}

	@Test
	public void zastupceMaId() throws Exception {
		final File lnk = new File(tmp.getRoot(), "GeoKuk.lnk");
		final VytvoritZastupceAction.Vysledek v = VytvoritZastupceAction.powershell(VytvoritZastupceAction.SKRIPT, promenne(lnk, VytvoritZastupceAction.CSHARP));
		Assert.assertEquals(v.text, 0, v.kod);
		Assert.assertTrue(v.text, v.text.contains("AUMID-OK"));
		Assert.assertEquals(AppUserModelId.ID, idZastupce(lnk));
	}

	/** Znovu vytvořený zástupce se stejným ID: SetValue vrací S_FALSE (1), to je úspěch, ne chyba. */
	@Test
	public void opakovaneVytvoreniSeStejnymIdNeniChyba() throws Exception {
		final File lnk = new File(tmp.getRoot(), "GeoKuk.lnk");
		for (int i = 0; i < 2; i++) {
			final VytvoritZastupceAction.Vysledek v = VytvoritZastupceAction.powershell(VytvoritZastupceAction.SKRIPT, promenne(lnk, VytvoritZastupceAction.CSHARP));
			Assert.assertEquals(v.text, 0, v.kod);
			Assert.assertTrue(i + ". " + v.text, v.text.contains("AUMID-OK"));
		}
		Assert.assertEquals(AppUserModelId.ID, idZastupce(lnk));
	}

	/** Když Add-Type nejde, zástupce se vytvoří bez ID a skript to ohlásí. */
	@Test
	public void zastupceVzniknePokudIdNejde() throws Exception {
		final File lnk = new File(tmp.getRoot(), "GeoKuk.lnk");
		final VytvoritZastupceAction.Vysledek v = VytvoritZastupceAction.powershell(VytvoritZastupceAction.SKRIPT, promenne(lnk, "tohle není C#"));
		Assert.assertEquals(v.text, 0, v.kod);
		Assert.assertTrue(v.text, v.text.contains("AUMID-CHYBA"));
		Assert.assertTrue(lnk.isFile());
	}

	private Map<String, String> promenne(final File lnk, final String csharp) {
		final Map<String, String> env = new HashMap<>();
		env.put("GK_CS", csharp);
		env.put("GK_AUMID", AppUserModelId.ID);
		env.put("GK_JAVAW", new File(System.getenv("SystemRoot"), "System32\\cmd.exe").getAbsolutePath());
		env.put("GK_ARGUMENTY", "/c exit");
		env.put("GK_SLOZKA", tmp.getRoot().getAbsolutePath());
		env.put("GK_IKONA", new File(System.getenv("SystemRoot"), "System32\\cmd.exe").getAbsolutePath());
		env.put("GK_SOUBOR", lnk.getAbsolutePath());
		return env;
	}

	private static String idZastupce(final File lnk) throws Exception {
		final Map<String, String> env = new HashMap<>();
		env.put("GK_LNK", lnk.getAbsolutePath());
		return VytvoritZastupceAction.powershell("$f=Get-Item -LiteralPath $env:GK_LNK;"
				+ "(New-Object -ComObject Shell.Application).NameSpace($f.DirectoryName).ParseName($f.Name).ExtendedProperty('System.AppUserModel.ID')", env).text;
	}
}
