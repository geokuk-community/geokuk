package cz.geokuk.util.exception;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.util.lang.VKodovaniWindows;

/** Výpis chyby se ukládá v UTF-8 a říká to i prohlížeči, ve kterém se otevírá. */
public class VypisVyjimkyKodovaniTest {

	@Test
	public void vKodovaniWindows() throws Exception {
		VKodovaniWindows.spust(Kontroly.class);
	}

	public static class Kontroly {

		@Rule
		public TemporaryFolder tmp = new TemporaryFolder();

		@Test
		public void vypisVUtf8() throws Exception {
			Assert.assertEquals(Charset.forName(VKodovaniWindows.KODOVANI), Charset.defaultCharset());
			final FileBasedExceptionDumperRepository repo = new FileBasedExceptionDumperRepository(tmp.newFolder("excrep"));
			final AExcId id = new ExceptionDumper().dump(new IllegalStateException("Soubor Výlet ťuk.gpx"), EExceptionSeverity.DISPLAY, "Načítání keší", repo);
			final byte[] bajty = Files.readAllBytes(Paths.get(repo.getUrl(id).toURI()));
			final String text = new String(bajty, StandardCharsets.UTF_8);
			Assert.assertTrue(text, text.contains("Soubor Výlet ťuk.gpx"));
			Assert.assertTrue(text, text.contains("Načítání keší"));
			Assert.assertTrue("značka kódování musí být na začátku, prohlížeč ji hledá v prvních 1024 bajtech", text.startsWith("<meta charset='UTF-8'>"));
		}
	}
}
