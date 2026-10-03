package cz.geokuk.util.index2d;

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

	private static void stejne(final String cesta, final Node<TestBod> a, final Node<TestBod> b) {
		Assert.assertEquals(cesta, a.getClass(), b.getClass());
		Assert.assertEquals(cesta, a.count, b.count);
		if (a instanceof NodeB) {
			final NodeB<TestBod> na = (NodeB<TestBod>) a;
			final NodeB<TestBod> nb = (NodeB<TestBod>) b;
			Assert.assertEquals(cesta, Arrays.asList(na.xx1, na.yy1, na.xx2, na.yy2), Arrays.asList(nb.xx1, nb.yy1, nb.xx2, nb.yy2));
		}
		if (a instanceof Sheet) {
			final Sheet<TestBod> sa = (Sheet<TestBod>) a;
			final Sheet<TestBod> sb = (Sheet<TestBod>) b;
			Assert.assertEquals(cesta, sa.xx, sb.xx);
			Assert.assertEquals(cesta, sa.yy, sb.yy);
			Lst<TestBod> la = sa.mapobj;
			Lst<TestBod> lb = sb.mapobj;
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
			stejne(cesta + "/jz", ca.jz, cb.jz);
			stejne(cesta + "/jv", ca.jv, cb.jv);
			stejne(cesta + "/sz", ca.sz, cb.sz);
			stejne(cesta + "/sv", ca.sv, cb.sv);
		}
	}

	private static void over(final BoundingRect br, final List<TestBod> body) {
		final Indexator<TestBod> ocekavany = postupne(br, body);
		final Indexator<TestBod> postaveny = Indexator.postav(br, body, TestBod::getX, TestBod::getY);
		Assert.assertEquals(body.size(), postaveny.getCount());
		stejne("root", ocekavany.root(), postaveny.root());
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
