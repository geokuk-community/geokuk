package cz.geokuk.util.index2d;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import cz.geokuk.util.index2d.Sheet.Lst;

/** Index postavený najednou musí být stejný strom jako po postupném přidávání. */
public class IndexatorPostavTest {

	private static Indexator<TestBod> postupne(final BoundingRect br, final List<TestBod> body) {
		Indexator<TestBod> ind = new Indexator<>(br);
		for (final TestBod b : body) {
			ind = ind.add(b.getX(), b.getY(), b);
		}
		return ind;
	}

	private static void stejne(final String cesta, final Node<TestBod> a, final Node<TestBod> b, final int xx1, final int yy1, final int xx2, final int yy2) {
		Assert.assertEquals(cesta, a.getClass(), b.getClass());
		Assert.assertEquals(cesta, a.count, b.count);
		if (a instanceof Sheet) {
			final Sheet<TestBod> sa = (Sheet<TestBod>) a;
			final Sheet<TestBod> sb = (Sheet<TestBod>) b;
			Assert.assertEquals(cesta, sa.xx, sb.xx);
			Assert.assertEquals(cesta, sa.yy, sb.yy);
			Assert.assertTrue(cesta, sa.xx >= xx1 && sa.xx < xx2 && sa.yy >= yy1 && sa.yy < yy2);
			Lst<TestBod> la = sa.lst();
			Lst<TestBod> lb = sb.lst();
			while (la != null || lb != null) {
				Assert.assertNotNull(cesta, la);
				Assert.assertNotNull(cesta, lb);
				Assert.assertSame(cesta, la.value, lb.value);
				la = la.next;
				lb = lb.next;
			}
		} else if (a instanceof Ctverecnik) {
			final Ctverecnik<TestBod> ca = (Ctverecnik<TestBod>) a;
			final Ctverecnik<TestBod> cb = (Ctverecnik<TestBod>) b;
			final int xMid = Indexator.mid(xx1, xx2);
			final int yMid = Indexator.mid(yy1, yy2);
			stejne(cesta + "/jz", ca.jz, cb.jz, xx1, yy1, xMid, yMid);
			stejne(cesta + "/jv", ca.jv, cb.jv, xMid, yy1, xx2, yMid);
			stejne(cesta + "/sz", ca.sz, cb.sz, xx1, yMid, xMid, yy2);
			stejne(cesta + "/sv", ca.sv, cb.sv, xMid, yMid, xx2, yy2);
		}
	}

	private static void over(final BoundingRect br, final List<TestBod> body) {
		final Indexator<TestBod> ocekavany = postupne(br, body);
		final Indexator<TestBod> postaveny = Indexator.postav(br, body, TestBod::getX, TestBod::getY);
		Assert.assertEquals(body.size(), postaveny.getCount());
		stejne("root", ocekavany.root(), postaveny.root(), br.xx1, br.yy1, br.xx2, br.yy2);
	}

	/** Vnitřních uzlů je v indexu řádově tolik jako bodů, proto drží jen počet a čtvrti. */
	@Test
	public void ctverecnikDrziJenPocetACtvrti() {
		final Set<String> pole = new TreeSet<>();
		for (Class<?> c = Ctverecnik.class; c != Object.class; c = c.getSuperclass()) {
			for (final Field f : c.getDeclaredFields()) {
				if (!Modifier.isStatic(f.getModifiers())) {
					pole.add(f.getName());
				}
			}
		}
		Assert.assertEquals(new TreeSet<>(Arrays.asList("count", "jz", "jv", "sz", "sv")), pole);
	}

	@Test
	public void prazdny() {
		over(BoundingRect.ALL, Collections.emptyList());
	}

	@Test
	public void jedenBod() {
		over(BoundingRect.ALL, Collections.singletonList(new TestBod(13, 27)));
	}

	@Test
	public void bodyNaStejnemMiste() {
		over(BoundingRect.ALL, Arrays.asList(new TestBod(5, 5), new TestBod(5, 5), new TestBod(5, 5), new TestBod(-7, 3)));
	}

	@Test
	public void nahodneBodyVcetneZapornychADuplicit() {
		final Random r = new Random(20261003);
		final List<TestBod> body = new ArrayList<>();
		for (int i = 0; i < 20_000; i++) {
			if (i > 0 && r.nextInt(10) == 0) {
				final TestBod b = body.get(r.nextInt(body.size()));
				body.add(new TestBod(b.getX(), b.getY()));
			} else if (r.nextBoolean()) {
				body.add(new TestBod(r.nextInt(Integer.MAX_VALUE) * (r.nextBoolean() ? 1 : -1), r.nextInt(Integer.MAX_VALUE) * (r.nextBoolean() ? 1 : -1))); // celý rozsah
			} else {
				body.add(new TestBod(r.nextInt(2000) - 1000, r.nextInt(2000) - 1000)); // shluk
			}
		}
		over(BoundingRect.ALL, body);
	}

	@Test
	public void omezenyRozsah() {
		final Random r = new Random(7);
		final List<TestBod> body = new ArrayList<>();
		for (int i = 0; i < 5000; i++) {
			body.add(new TestBod(100 + r.nextInt(901), -50 + r.nextInt(301)));
		}
		over(new BoundingRect(100, -50, 1001, 251), body);
	}
}
