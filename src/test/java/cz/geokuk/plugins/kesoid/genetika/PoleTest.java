package cz.geokuk.plugins.kesoid.genetika;

import org.junit.Assert;
import org.junit.Test;

import lombok.Data;

public class PoleTest {

	@Test
	public void test1() {
		final IndexMap<Cislo, Long> pole = new IndexMap<>();
		Assert.assertNull(pole.get(c(0)));
		Assert.assertNull(pole.get(c(5)));
		Assert.assertNull(pole.get(c(176)));
	}

	@Test
	public void nullNaIndexRovnemDelcePole() {
		final IndexMap<Cislo, Long> pole = new IndexMap<>();
		pole.put(c(2), 7l); // pole má délku 3
		pole.put(c(3), null);
		Assert.assertNull(pole.get(c(3)));
		Assert.assertEquals(Long.valueOf(7l), pole.get(c(2)));
		final IndexMap<Cislo, Long> prazdne = new IndexMap<>();
		prazdne.put(c(0), null);
		Assert.assertNull(prazdne.get(c(0)));
	}

	@Test
	public void test2() {
		final IndexMap<Cislo, Long> pole = new IndexMap<>();
		pole.put(c(0), 42l);
		pole.put(c(16), 17l);
		Assert.assertEquals(new Long(42l), pole.get(c(0)));
		Assert.assertNull(pole.get(c(5)));
		Assert.assertEquals(new Long(17l), pole.get(c(16)));
		Assert.assertNull(pole.get(c(176)));
		pole.put(c(16), 317l);
		Assert.assertEquals(new Long(317l), pole.get(c(16)));
		pole.put(c(16), null);
		Assert.assertNull(pole.get(c(16)));

	}

	private Cislo c(final int i) {
		return new Cislo(i);
	}

	@Data
	private static class Cislo implements Indexable {
		private final int index;
	}
}
