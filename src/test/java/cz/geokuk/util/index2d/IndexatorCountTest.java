package cz.geokuk.util.index2d;

import java.lang.management.ManagementFactory;
import java.util.Random;

import org.junit.*;

/** Počet v obdélníku je stejný jako u oříznutého indexu, ale nic se přitom nealokuje. */
public class IndexatorCountTest {

	private static Indexator<Integer> data() {
		Indexator<Integer> ix = new Indexator<>(BoundingRect.ALL);
		final Random r = new Random(1);
		for (int i = 0; i < 20_000; i++) {
			ix = ix.add(400_000_000 + r.nextInt(20_000_000), 300_000_000 + r.nextInt(10_000_000), i);
		}
		return ix;
	}

	@Test
	public void shodaSOriznutymIndexem() {
		final Indexator<Integer> ix = data();
		final Random r = new Random(2);
		for (int i = 0; i < 300; i++) {
			final int x = 395_000_000 + r.nextInt(30_000_000);
			final int y = 295_000_000 + r.nextInt(20_000_000);
			final BoundingRect br = new BoundingRect(x, y, x + 1 + r.nextInt(8_000_000), y + 1 + r.nextInt(4_000_000));
			Assert.assertEquals(ix.bound(br).getCount(), ix.count(br));
		}
	}

	/** Obdélníky s body přesně na hranách: horní a pravá hrana nepatří dovnitř, stejně jako u oříznutého indexu. */
	@Test
	public void shodaNaHranach() {
		Indexator<Integer> ix = new Indexator<>(BoundingRect.ALL);
		for (int i = 0; i < 50; i++) {
			ix = ix.add(400_000_000 + i * 1000, 300_000_000 + i * 1000, i);
		}
		for (int i = 0; i < 50; i++) {
			final int a = 400_000_000 + i * 1000;
			final int b = 300_000_000 + i * 1000;
			for (final BoundingRect br : new BoundingRect[] { new BoundingRect(a - 5000, b - 5000, a, b), new BoundingRect(a, b, a + 5000, b + 5000), new BoundingRect(a, b, a + 1, b + 1) }) {
				Assert.assertEquals(br.toString(), ix.bound(br).getCount(), ix.count(br));
			}
		}
	}

	@Test
	public void countNealokuje() {
		Assume.assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
		final com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		Assume.assumeTrue(mx.isThreadAllocatedMemorySupported() && mx.isThreadAllocatedMemoryEnabled());
		final Indexator<Integer> ix = data();
		final BoundingRect br = new BoundingRect(405_000_000, 302_000_000, 415_000_000, 308_000_000);
		ix.count(br);
		final long id = Thread.currentThread().getId();
		final long pred = mx.getThreadAllocatedBytes(id);
		int soucet = 0;
		for (int i = 0; i < 100; i++) {
			soucet += ix.count(br);
		}
		final long bytes = mx.getThreadAllocatedBytes(id) - pred;
		Assert.assertTrue(soucet > 0);
		Assert.assertTrue("alokováno " + bytes + " B", bytes < 1000);
	}
}
