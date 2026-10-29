package cz.geokuk.util.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SledovaniSlozkyTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private final AtomicLong cas = new AtomicLong();

	private SledovaniSlozky sledovani(final Path slozka, final int hloubka) {
		return new SledovaniSlozky(slozka, hloubka, cas::get, TimeUnit.SECONDS.toNanos(60));
	}

	/** Počká až 10 s na změnu; události systému chodí s malým zpožděním. */
	private static boolean pockejNaZmenu(final SledovaniSlozky s) throws InterruptedException {
		final long konec = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < konec) {
			if (s.zmenaOdPosledniho()) {
				return true;
			}
			Thread.sleep(10);
		}
		return false;
	}

	@Test
	public void prvniDotazJeVzdyZmena_dalsiBezUdalostiNe() throws Exception {
		try (SledovaniSlozky s = sledovani(tmp.getRoot().toPath(), 1)) {
			Assert.assertTrue(s.zmenaOdPosledniho());
			for (int i = 0; i < 5; i++) {
				Assert.assertFalse("bez události se složka neprochází", s.zmenaOdPosledniho());
			}
		}
	}

	@Test
	public void novyASmazanySouborJeZmena() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		try (SledovaniSlozky s = sledovani(slozka, 1)) {
			s.zmenaOdPosledniho();
			final Path soubor = Files.write(slozka.resolve("a.map"), new byte[] { 1 });
			Assert.assertTrue(pockejNaZmenu(s));
			Assert.assertFalse("změna se hlásí jednou", s.zmenaOdPosledniho());
			Files.delete(soubor);
			Assert.assertTrue(pockejNaZmenu(s));
		}
	}

	@Test
	public void prepsanySouborJeZmena() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		final Path soubor = Files.write(slozka.resolve("a.map"), new byte[] { 1 });
		try (SledovaniSlozky s = sledovani(slozka, 1)) {
			s.zmenaOdPosledniho();
			Files.write(soubor, new byte[] { 1, 2, 3 });
			Assert.assertTrue(pockejNaZmenu(s));
		}
	}

	@Test
	public void podslozkySeSledujiDoHloubky() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		final Path vrstvy = Files.createDirectory(slozka.resolve("layers"));
		try (SledovaniSlozky s = sledovani(slozka, 3)) {
			s.zmenaOdPosledniho();
			Files.write(vrstvy.resolve("a.map"), new byte[] { 1 });
			Assert.assertTrue("soubor v existující podsložce", pockejNaZmenu(s));
			final Path nova = Files.createDirectories(vrstvy.resolve("cz"));
			Assert.assertTrue("nová podsložka", pockejNaZmenu(s));
			Files.write(nova.resolve("b.map"), new byte[] { 1 });
			Assert.assertTrue("soubor v nově vzniklé podsložce", pockejNaZmenu(s));
		}
	}

	@Test
	public void hloubkaJedenPodslozkyNesleduje() throws Exception {
		final Path slozka = tmp.getRoot().toPath();
		final Path vrstvy = Files.createDirectory(slozka.resolve("layers"));
		try (SledovaniSlozky s = sledovani(slozka, 1)) {
			s.zmenaOdPosledniho();
			Files.write(vrstvy.resolve("a.map"), new byte[] { 1 });
			Thread.sleep(300);
			Assert.assertFalse(s.zmenaOdPosledniho());
		}
	}

	@Test
	public void nelzeSledovatPakSeProchaziVzdy() throws Exception {
		try (SledovaniSlozky s = sledovani(tmp.getRoot().toPath().resolve("neexistuje"), 1)) {
			for (int i = 0; i < 3; i++) {
				Assert.assertTrue(s.zmenaOdPosledniho());
			}
		}
	}

	@Test
	public void pojistkaProjdeSlozkuIBezUdalosti() throws Exception {
		try (SledovaniSlozky s = sledovani(tmp.getRoot().toPath(), 1)) {
			Assert.assertTrue(s.zmenaOdPosledniho());
			cas.addAndGet(TimeUnit.SECONDS.toNanos(59));
			Assert.assertFalse(s.zmenaOdPosledniho());
			cas.addAndGet(TimeUnit.SECONDS.toNanos(1));
			Assert.assertTrue("po minutě bez události se složka projde pro jistotu", s.zmenaOdPosledniho());
			Assert.assertFalse(s.zmenaOdPosledniho());
		}
	}

	@Test
	public void poZavreniSeProchaziVzdy() throws IOException {
		final SledovaniSlozky s = sledovani(tmp.getRoot().toPath(), 1);
		s.close();
		Assert.assertTrue(s.zmenaOdPosledniho());
		Assert.assertTrue(s.zmenaOdPosledniho());
	}
}
