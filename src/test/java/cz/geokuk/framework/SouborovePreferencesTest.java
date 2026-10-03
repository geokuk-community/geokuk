package cz.geokuk.framework;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SouborovePreferencesTest {

	private static final String VLASTNOST_TOVARNY = DocumentBuilderFactory.class.getName();

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	/** Tovární třída, která selže výjimkou za běhu, jako by selhalo sestavení dokumentu. */
	public static final class PadajiciTovarna extends DocumentBuilderFactory {
		@Override
		public DocumentBuilder newDocumentBuilder() {
			throw new IllegalStateException("test");
		}

		@Override
		public void setAttribute(final String name, final Object value) {
		}

		@Override
		public Object getAttribute(final String name) {
			return null;
		}

		@Override
		public void setFeature(final String name, final boolean value) {
		}

		@Override
		public boolean getFeature(final String name) {
			return false;
		}
	}

	@Test
	public void odlozenyZapisPrezijeVyjimkuZaBehu() throws Exception {
		final File soubor = new File(tmp.getRoot(), "nastaveni.xml");
		final SouborovePreferences pref = SouborovePreferences.prazdne(soubor);
		final String puvodni = System.getProperty(VLASTNOST_TOVARNY);
		System.setProperty(VLASTNOST_TOVARNY, PadajiciTovarna.class.getName());
		try {
			pref.put("a", "1");
			Thread.sleep(3000);
		} finally {
			if (puvodni == null) {
				System.clearProperty(VLASTNOST_TOVARNY);
			} else {
				System.setProperty(VLASTNOST_TOVARNY, puvodni);
			}
		}
		assertThat(soubor.exists()).isFalse();

		pref.put("b", "2");
		final long konec = System.currentTimeMillis() + 10_000;
		while (!soubor.exists() && System.currentTimeMillis() < konec) {
			Thread.sleep(100);
		}
		final String obsah = new String(Files.readAllBytes(soubor.toPath()), StandardCharsets.UTF_8);
		assertThat(obsah).contains("key=\"a\"");
		assertThat(obsah).contains("key=\"b\"");
	}
}
