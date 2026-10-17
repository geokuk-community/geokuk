package cz.geokuk.util.process;

import java.io.File;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.util.process.OtevreniSlozky.Zpusob;

/** Volba způsobu otevření složky: správce souborů, ne prohlížeč. */
public class OtevreniSlozkyTest {

	@Test
	public void desktopKdyzUmiOtevrit() {
		Assert.assertEquals(Zpusob.DESKTOP, OtevreniSlozky.zvolZpusob("Windows 11", true));
		Assert.assertEquals(Zpusob.DESKTOP, OtevreniSlozky.zvolZpusob("Linux", true));
	}

	@Test
	public void bezDesktopuPodleSystemu() {
		Assert.assertEquals(Zpusob.EXPLORER, OtevreniSlozky.zvolZpusob("Windows 11", false));
		Assert.assertEquals(Zpusob.OPEN, OtevreniSlozky.zvolZpusob("Mac OS X", false));
		Assert.assertEquals(Zpusob.XDG_OPEN, OtevreniSlozky.zvolZpusob("Linux", false));
	}

	@Test
	public void cestaSDiakritikouJeJedenArgument() {
		final File f = new File("E:\\Tomáš\\Offline mapy");
		Assert.assertEquals(Arrays.asList("explorer.exe", f.getAbsolutePath()), OtevreniSlozky.prikaz(Zpusob.EXPLORER, f));
		Assert.assertEquals(Arrays.asList("open", f.getAbsolutePath()), OtevreniSlozky.prikaz(Zpusob.OPEN, f));
		Assert.assertEquals(Arrays.asList("xdg-open", f.getAbsolutePath()), OtevreniSlozky.prikaz(Zpusob.XDG_OPEN, f));
	}
}
