package cz.geokuk.util.file;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.util.lang.VKodovaniWindows;

/** Text se zapisuje a čte v UTF-8 bez ohledu na výchozí kódování Javy. */
public class FileManagerKodovaniTest {

	@Test
	public void vKodovaniWindows() throws Exception {
		VKodovaniWindows.spust(Kontroly.class);
	}

	public static class Kontroly {

		private static final String TEXT = "Příliš žluťoučký kůň";

		@Rule
		public TemporaryFolder tmp = new TemporaryFolder();

		@Test
		public void zapisVUtf8() throws Exception {
			Assert.assertEquals(Charset.forName(VKodovaniWindows.KODOVANI), Charset.defaultCharset());
			final File f = tmp.newFile();
			FileManager.getInstance(4096).writeStringToFile(f, TEXT);
			Assert.assertArrayEquals(TEXT.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(f.toPath()));
		}

		@Test
		public void cteniVUtf8() throws Exception {
			final File f = tmp.newFile();
			Files.write(f.toPath(), TEXT.getBytes(StandardCharsets.UTF_8));
			Assert.assertEquals(TEXT, FileManager.getInstance(4096).readWholeFileAsString(f));
		}
	}
}
