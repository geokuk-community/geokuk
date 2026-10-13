package cz.geokuk.plugins.kesoid.mapicon;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.Atom;
import cz.geokuk.util.lang.VKodovaniWindows;

/** Vlastní sada ikon se sklem s diakritikou ve jméně se načte bez ohledu na kódování skla.txt i výchozí kódování Javy. */
public class SklaTxtKodovaniTest {

	private static final String SKLO = "skloČervené";
	private static final String RADEK = SKLO + ".VSE\n";

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void utf8VKodovaniWindows() throws Exception {
		VKodovaniWindows.spust(Kontroly.class);
	}

	@Test
	public void utf8SBom() throws Exception {
		final byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
		final byte[] text = RADEK.getBytes(StandardCharsets.UTF_8);
		final byte[] bajty = new byte[bom.length + text.length];
		System.arraycopy(bom, 0, bajty, 0, bom.length);
		System.arraycopy(text, 0, bajty, bom.length, text.length);
		overSklo(tmp, bajty);
	}

	@Test
	public void kodovaniWindows() throws Exception {
		overSklo(tmp, RADEK.getBytes("windows-1250"));
	}

	static void overSklo(final TemporaryFolder tmp, final byte[] sklaTxt) throws Exception {
		final Path sada;
		try {
			sada = tmp.getRoot().toPath().resolve("map/Vlastní");
		} catch (final InvalidPathException e) {
			Assume.assumeNoException("jména souborů s diakritikou systém neumí (locale C/POSIX)", e);
			return;
		}
		final Path vrstva = Files.createDirectories(sada.resolve(SKLO + "/1vrstva"));
		try (InputStream in = SklaTxtKodovaniTest.class.getResourceAsStream("/img/map/Standard/skloNormal/5typy/_p3.png")) {
			Files.copy(in, vrstva.resolve("_p3.png"));
		}
		Files.write(sada.resolve("skla.txt"), sklaTxt);

		final IkonNacitacLoader loader = new IkonNacitacLoader();
		loader.setImageMyDir(tmp.getRoot());
		final IkonBag bag = loader.nacti(null, true, Atom.valueOf(ASada.class, "Vlastní"));
		Assert.assertEquals("Vlastní", bag.getSada().getName());
		Assert.assertEquals(1, bag.getSada().getSkloAplikanti().size());
		Assert.assertEquals(SKLO, bag.getSada().getSkloAplikanti().get(0).sklo.getName());
	}

	public static class Kontroly {

		@Rule
		public TemporaryFolder tmp = new TemporaryFolder();

		@Test
		public void utf8() throws Exception {
			Assert.assertEquals(Charset.forName(VKodovaniWindows.KODOVANI), Charset.defaultCharset());
			overSklo(tmp, RADEK.getBytes(StandardCharsets.UTF_8));
		}
	}
}
