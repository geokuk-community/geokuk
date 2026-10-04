package cz.geokuk.util.index2d;


import java.util.*;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;



public class IndexatorTest {


	private TestBod b(final int x, final int y) {
		return new TestBod(x, y);
	}

	private void pro(final BoundingRect br, final Collection<TestBod> col) {
		final List<TestBod> zdroj = col.parallelStream()
				.filter(b -> b.getX() >= br.xx1 && b.getX() < br.xx2 && b.getY() >= br.yy1 && b.getY() < br.yy2
						).collect(Collectors.toList());
		Collections.sort(zdroj);

		final Indexator<TestBod> indexator = col.parallelStream().reduce(new Indexator<TestBod>(BoundingRect.ALL),
				(ind, b) -> ind.add(b.getX(), b.getY(), b),
				Indexator::merge).bound(br);
		//indexator.vypis();
		final List<TestBod> result = indexator.parallelStream().collect(Collectors.toList());
		Collections.sort(result);

		//System.out.println(zdroj);
		//System.out.println(result);
		Assert.assertEquals(zdroj, result);

	}

	private List<TestBod> matice(final int n, final int factor) {
		final List<TestBod> list = new ArrayList<>(n);
		for (int x = 0; x < n; x++) {
			for (int y = 0; y < n; y++) {
				list.add(b(x * factor ,y * factor));
			}
		}
		return list;
	}

	@Test
	public void test0() {
		pro(BoundingRect.ALL, Collections.emptySet());
	}

	@Test
	public void test1() {
		pro(BoundingRect.ALL, Collections.singleton(b(13, 27)));
	}

	@Test
	public void test2a() {
		pro(BoundingRect.ALL, Lists.newArrayList(b(13, 27), b(500,800)));
	}

	@Test
	public void test2b() {
		pro(BoundingRect.ALL, Lists.newArrayList(b(13, 27), b(13,27)));
	}

	@Test
	public void testMatice2() {
		pro(BoundingRect.ALL, matice(2, 100));
	}

	@Test
	public void testMatice10() {
		pro(BoundingRect.ALL, matice(10, 100));
	}

	@Test
	public void testMatice10a() {
		pro(BoundingRect.ALL, matice(10, 100_000_000));
	}

	@Test
	public void testMatice10b() {
		pro(BoundingRect.ALL, matice(10, 1000_000_000));
	}

	@Test
	public void testMatice100() {
		pro(BoundingRect.ALL, matice(100, 45));
	}

	@Test
	public void testMatice100a() {
		pro(BoundingRect.ALL, matice(100, 1));
	}

	@Test
	public void testMatice100b() {
		pro(BoundingRect.ALL, matice(100, 0));
	}

	/** Statisíce bodů na stejném místě (třeba waypointy bez souřadnic) nesmí přetéct zásobník. */
	@Test
	public void mnohoBoduNaJednomMiste() {
		Indexator<TestBod> a = new Indexator<>(BoundingRect.ALL);
		Indexator<TestBod> c = new Indexator<>(BoundingRect.ALL);
		for (int i = 0; i < 100_000; i++) {
			a = a.add(0, 0, b(0, 0));
			c = c.add(0, 0, b(0, 0));
		}
		Assert.assertEquals(200_000, a.merge(c).getCount());
	}

	@Test
	public void testMatice100c() {
		pro(BoundingRect.ALL, matice(100, -1));
	}

	@Test
	public void testMatice2e() {
		pro(BoundingRect.ALL, matice(2, -1));
	}

	@Test
	public void testMatice2d() {
		pro(BoundingRect.ALL, matice(2, 1));
	}

	@Test
	public void testMatice1000() {
		pro(BoundingRect.ALL, matice(1000, 45));
	}


	@Test
	public void testBr10() {
		pro(new BoundingRect(23, 35, 68, 77), matice(10, 10));
	}

	@Test
	public void testMaticeBr100a() {
		pro(new BoundingRect(40, 10, 70, 20), matice(100, 1));
	}

	@Test
	public void testMaticeBr100b() {
		pro(new BoundingRect(40, 10, 70, 20), matice(100, 0));
	}

	@Test
	public void testMaticeBr100c() {
		pro(new BoundingRect(40, 10, 70, 20), matice(100, -1));
	}

	@Test
	public void locateNearest0() {
		Assert.assertFalse(new Indexator<TestBod>(BoundingRect.ALL).locateNearestOne(789, 887).isPresent());
	}

	@Test
	public void locateNearest1() {
		Assert.assertEquals(b(60,80), mat10().locateNearestOne(61, 83).get());
	}

	@Test
	public void locateNearest2() {
		Assert.assertEquals(b(60,90), mat10().locateNearestOne(61, 88).get());
	}

	@Test
	public void locateNearest3() {
		Assert.assertEquals(b(0,0), mat10().locateNearestOne(-61, -88).get());
	}

	@Test
	public void locateNearest4() {
		Assert.assertEquals(b(60,0), mat10().locateNearestOne(56, -88).get());
	}

	@Test
	public void locateNearest5() {
		Assert.assertEquals(b(40,20), mat10().locateNearestOne(40, 20).get());
	}


	private Indexator<TestBod> mat(final int n) {
		final int factor = 10;
		Indexator<TestBod> indr = new Indexator<TestBod>(BoundingRect.ALL);
		for (int x = 0; x < n; x++) {
			for (int y = 0; y < n; y++) {
				final TestBod bod = new TestBod(x * factor, y * factor);
				indr = indr.add(bod.getX(), bod.getY(), bod);
			}
		}
		return indr;
	}

	private Indexator<TestBod> mat10() {
		return mat(10);
	}
	@Test
	public void nejblizsiPriVelkychVzdalenostech() {
		final TestBod daleko = b(60000, 0);
		final TestBod blizko = b(0, 10);
		final Indexator<TestBod> indexator = new Indexator<TestBod>(BoundingRect.ALL)
				.add(daleko.getX(), daleko.getY(), daleko)
				.add(blizko.getX(), blizko.getY(), blizko);
		Assert.assertSame(blizko, indexator.locateNearestOne(0, 0).get());
	}


	/** Index drží na bod nejvýš jeden objekt listu, a čtverečníky; u milionů keší jde o stovky MB. */
	@Test
	public void malObjektuNaBod() throws Exception {
		final Random r = new Random(3);
		Indexator<TestBod> indexator = new Indexator<>(BoundingRect.ALL);
		final int n = 10_000;
		for (int i = 0; i < n; i++) {
			final TestBod bod = b(r.nextInt(1_000_000), r.nextInt(1_000_000));
			indexator = indexator.add(bod.getX(), bod.getY(), bod);
		}
		final java.lang.reflect.Field koren = Indexator.class.getDeclaredField("root");
		koren.setAccessible(true);
		final int objektu = pocetObjektu(koren.get(indexator), Collections.newSetFromMap(new IdentityHashMap<>()));
		Assert.assertTrue("objektů indexu " + objektu + " na " + n + " bodů", objektu < 1.8 * n);
	}

	private static int pocetObjektu(final Object o, final Set<Object> videne) throws IllegalAccessException {
		if (o == null || o instanceof TestBod || !videne.add(o)) {
			return 0;
		}
		int pocet = 1;
		for (Class<?> c = o.getClass(); c != Object.class; c = c.getSuperclass()) {
			for (final java.lang.reflect.Field f : c.getDeclaredFields()) {
				if (!f.getType().isPrimitive() && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					pocet += pocetObjektu(f.get(o), videne);
				}
			}
		}
		return pocet;
	}

	@Test
	public void bodMimoRozsahSeOdmitne() {
		final Indexator<String> ix = new Indexator<>(new BoundingRect(0, 0, 100, 100));
		ix.add(0, 99, "okraj");
		for (final int[] mimo : new int[][] { { 100, 5 }, { 5, 100 }, { -1, 5 }, { 5, -1 } }) {
			try {
				ix.add(mimo[0], mimo[1], "mimo");
				Assert.fail("bod " + mimo[0] + " " + mimo[1] + " je mimo rozsah");
			} catch (final IllegalArgumentException e) {
				// očekáváno
			}
		}
	}

	@Test(expected = NullPointerException.class)
	public void nullSeNepridava() {
		new Indexator<String>(BoundingRect.ALL).add(1, 1, null);
	}
}
