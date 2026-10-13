package cz.geokuk.util.lang;

import org.junit.Assert;
import org.junit.Test;

/** Celkový počet odpovídá součtu čítačů i po odečtení pod nulu a nastavení. */
public class CCounterMapTest {

	@Test
	public void celkovyPocet() {
		final CCounterMap<String> cm = new CCounterMap<>();
		cm.add("a", 3);
		cm.add("b", 7);
		Assert.assertEquals(10, cm.count());
		cm.add("b", -10); // pod nulu nejde
		Assert.assertEquals(0, cm.count("b"));
		Assert.assertEquals(3, cm.count());
		cm.set("a", 5);
		cm.set("c", 2);
		Assert.assertEquals(7, cm.count());
		cm.set("a", 0);
		Assert.assertEquals(2, cm.count());
	}
}
