package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.imageio.ImageIO;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.mapy.kachle.data.*;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleManager.ItemToSave;
import cz.geokuk.util.file.Filex;

/** Čtení z více vláken souběžně se zápisem musí vracet právě uložené dlaždice a soubor nesmí poškodit. */
public class KachleDBManagerSoubehTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final int VZORU = 20;
	private static final int ULOZENYCH = 100;

	private static Ka ka(final int i) {
		return new Ka(KaLoc.ofJZ(new Mou(0x40000000 + i * (1 << 16), 0x20000000), 16), EKaType.TURIST_M);
	}

	@Test(timeout = 120000)
	public void cteniSouberneSeZapisemVraciSpravneDlazdice() throws Exception {
		final BufferedImage[] obrazy = new BufferedImage[VZORU];
		final byte[][] png = new byte[VZORU][];
		for (int v = 0; v < VZORU; v++) {
			obrazy[v] = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
			final Random r = new Random(v);
			for (int x = 0; x < 256; x++) {
				for (int y = 0; y < 256; y++) {
					obrazy[v].setRGB(x, y, r.nextInt(0x1000000) & 0x0f0f0f);
				}
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(obrazy[v], "png", out);
			png[v] = out.toByteArray();
		}
		final File slozka = tmp.newFolder("cache");
		final KachleCacheFolderHolder holder = new KachleCacheFolderHolder();
		holder.setKachleCacheDir(new Filex(slozka, false, true));
		final KachleDBManager manager = new KachleDBManager(holder);
		final List<ItemToSave> pocatek = new ArrayList<>();
		for (int i = 0; i < ULOZENYCH; i++) {
			pocatek.add(new ItemToSave(ka(i), png[i % VZORU]));
		}
		Assert.assertTrue(manager.save(pocatek));

		final AtomicBoolean konec = new AtomicBoolean();
		final AtomicInteger precteno = new AtomicInteger();
		final List<String> chyby = Collections.synchronizedList(new ArrayList<>());
		final ExecutorService vlakna = Executors.newFixedThreadPool(5);
		final List<Future<?>> ulohy = new ArrayList<>();
		for (int c = 0; c < 4; c++) {
			final Random r = new Random(c);
			ulohy.add(vlakna.submit(() -> {
				while (!konec.get()) {
					final int i = r.nextInt(ULOZENYCH);
					try {
						final Image img = manager.load(ka(i));
						if (!stejny((BufferedImage) img, obrazy[i % VZORU])) {
							chyby.add("dlaždice " + i + " jiná, než se uložila");
						}
						precteno.incrementAndGet();
					} catch (final RuntimeException e) {
						chyby.add("dlaždice " + i + ": " + e);
					}
				}
			}));
		}
		ulohy.add(vlakna.submit(() -> {
			final Random r = new Random(99);
			int dalsi = ULOZENYCH;
			while (!konec.get()) {
				final List<ItemToSave> davka = new ArrayList<>();
				for (int j = 0; j < 20; j++) {
					final int i = r.nextBoolean() ? r.nextInt(ULOZENYCH) : dalsi++;
					davka.add(new ItemToSave(ka(i), png[i % VZORU]));
				}
				if (!manager.save(davka)) {
					chyby.add("zápis selhal");
				}
				Thread.sleep(20);
			}
			return null;
		}));
		Thread.sleep(5000);
		konec.set(true);
		vlakna.shutdown();
		Assert.assertTrue(vlakna.awaitTermination(60, TimeUnit.SECONDS));
		for (final Future<?> f : ulohy) {
			f.get();
		}
		Assert.assertTrue("čtenáři četli", precteno.get() > 0);
		Assert.assertEquals(Collections.emptyList(), chyby.subList(0, Math.min(5, chyby.size())));
		for (int i = 0; i < ULOZENYCH; i++) {
			Assert.assertTrue("po zápisech dlaždice " + i, stejny((BufferedImage) manager.load(ka(i)), obrazy[i % VZORU]));
		}
	}

	private static boolean stejny(final BufferedImage a, final BufferedImage b) {
		if (a == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
			return false;
		}
		for (int k = 0; k < 256; k++) {
			final int x = k * 37 % 256;
			final int y = k * 101 % 256;
			if ((a.getRGB(x, y) & 0xffffff) != (b.getRGB(x, y) & 0xffffff)) {
				return false;
			}
		}
		return true;
	}
}
