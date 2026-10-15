package cz.geokuk.plugins.mapy.sprava;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class HlavickaMapyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	static File kukov(final File slozka) throws IOException {
		final File f = new File(slozka, "kukov.map");
		try (InputStream in = HlavickaMapyTest.class.getResourceAsStream("/offline-mapy/kukov.map")) {
			Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return f;
	}

	@Test
	public void platnaMapa() throws IOException {
		final File f = kukov(tmp.getRoot());
		final HlavickaMapy h = HlavickaMapy.precti(f);
		assertEquals(3, h.getVerze());
		assertEquals(f.length(), h.getVelikostSouboru());
		assertTrue(h.getDatumDat().isAfter(Instant.parse("2020-01-01T00:00:00Z")));
		assertTrue(h.getJih() < 50.003 && 50.003 < h.getSever());
		assertTrue(h.getZapad() < 14.405 && 14.405 < h.getVychod());
		assertTrue(h.obsahuje(50.003, 14.405));
		assertFalse(h.obsahuje(48, 17));
	}

	@Test
	public void neuplnaMapa() throws IOException {
		final File f = kukov(tmp.getRoot());
		final byte[] b = Files.readAllBytes(f.toPath());
		Files.write(f.toPath(), java.util.Arrays.copyOf(b, b.length - 1));
		chyba(f, "neúplná");
		Files.write(f.toPath(), java.util.Arrays.copyOf(b, 30));
		chyba(f, "kratší");
		final byte[] delsi = java.util.Arrays.copyOf(b, b.length + 10);
		Files.write(f.toPath(), delsi);
		chyba(f, "neúplná");
	}

	@Test
	public void poskozenaHlavicka() throws IOException {
		final File f = kukov(tmp.getRoot());
		final byte[] b = Files.readAllBytes(f.toPath());
		zapis(f, b, 0, (byte) 'M');
		chyba(f, "není mapa");
		zapis(f, b, 27, (byte) 9);
		chyba(f, "verze");
		zapis(f, b, 20, (byte) 0x7f);
		chyba(f, "délku hlavičky");
		zapis(f, b, 44, (byte) 0x7f);
		chyba(f, "neplatnou hlavičku");
		Files.write(f.toPath(), new byte[0]);
		chyba(f, "kratší");
	}

	private static void zapis(final File f, final byte[] puvodni, final int pozice, final byte hodnota) throws IOException {
		final byte[] b = puvodni.clone();
		b[pozice] = hodnota;
		Files.write(f.toPath(), b);
	}

	private static void chyba(final File f, final String cast) {
		try {
			HlavickaMapy.precti(f);
			fail("Čekám chybu: " + cast);
		} catch (final IOException e) {
			assertTrue(e.getMessage(), e.getMessage().contains(cast));
		}
	}
}
