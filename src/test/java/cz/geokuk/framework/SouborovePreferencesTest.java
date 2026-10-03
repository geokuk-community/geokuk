package cz.geokuk.framework;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

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
		static final AtomicInteger POKUSU = new AtomicInteger();

		@Override
		public DocumentBuilder newDocumentBuilder() {
			POKUSU.incrementAndGet();
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

	/** Počká na podmínku nejvýš 30 s, pomalý stroj nevadí. */
	private static void cekej(final BooleanSupplier podminka) throws InterruptedException {
		final long konec = System.currentTimeMillis() + 30_000;
		while (!podminka.getAsBoolean()) {
			if (System.currentTimeMillis() > konec) {
				throw new AssertionError("Podmínka nenastala do 30 s");
			}
			Thread.sleep(50);
		}
	}

	@Test
	public void odlozenyZapisPrezijeVyjimkuZaBehu() throws Exception {
		final File soubor = new File(tmp.getRoot(), "nastaveni.xml");
		final SouborovePreferences pref = SouborovePreferences.prazdne(soubor);
		final String puvodni = System.getProperty(VLASTNOST_TOVARNY);
		System.setProperty(VLASTNOST_TOVARNY, PadajiciTovarna.class.getName());
		try {
			final int pred = PadajiciTovarna.POKUSU.get();
			pref.put("a", "1");
			cekej(() -> PadajiciTovarna.POKUSU.get() > pred);
		} finally {
			if (puvodni == null) {
				System.clearProperty(VLASTNOST_TOVARNY);
			} else {
				System.setProperty(VLASTNOST_TOVARNY, puvodni);
			}
		}
		assertThat(soubor.exists()).isFalse();

		pref.put("b", "2");
		cekej(soubor::exists);
		final String obsah = new String(Files.readAllBytes(soubor.toPath()), StandardCharsets.UTF_8);
		assertThat(obsah).contains("key=\"a\"");
		assertThat(obsah).contains("key=\"b\"");
	}
}
