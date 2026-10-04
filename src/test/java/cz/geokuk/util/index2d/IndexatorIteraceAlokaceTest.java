package cz.geokuk.util.index2d;

import java.lang.management.ManagementFactory;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.*;

/** Kreslení teček prochází statisíce bodů v každém snímku, iterace indexu proto nesmí alokovat na každý bod. */
public class IndexatorIteraceAlokaceTest {

	@Test
	public void iteraceVyrezuNealokujeNaBod() {
		Assume.assumeTrue(ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean);
		final com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		Assume.assumeTrue(mx.isThreadAllocatedMemorySupported() && mx.isThreadAllocatedMemoryEnabled());

		Indexator<Integer> ix = new Indexator<>(BoundingRect.ALL);
		final Random r = new Random(1);
		for (int i = 0; i < 100_000; i++) {
			ix = ix.add(400_000_000 + r.nextInt(20_000_000), 300_000_000 + r.nextInt(10_000_000), i % 1000);
		}
		final Indexator<Integer> vyrez = ix.bound(new BoundingRect(405_000_000, 302_000_000, 415_000_000, 308_000_000));
		final AtomicLong soucet = new AtomicLong();
		vyrez.stream().forEach(soucet::addAndGet);

		final long id = Thread.currentThread().getId();
		final long pred = mx.getThreadAllocatedBytes(id);
		final long[] pocet = new long[1];
		vyrez.stream().forEach(x -> pocet[0]++);
		final long naBod = (mx.getThreadAllocatedBytes(id) - pred) / vyrez.getCount();

		Assert.assertEquals(vyrez.getCount(), pocet[0]);
		Assert.assertTrue("alokováno " + naBod + " B na bod", naBod < 8);
	}
}
