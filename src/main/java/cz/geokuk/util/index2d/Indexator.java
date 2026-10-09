package cz.geokuk.util.index2d;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


import cz.geokuk.util.index2d.Sheet.Lst;

/**
 * Drží celý index všech objektů na mapě, tedy se dají přes něj dostat i ty objekty.
 *
 * @author Martin Veverka
 *
 */
public class Indexator<T> {

	private final Node<T> root;
	private final BoundingRect br;

	public Indexator(final BoundingRect br) {
		this.br = br;
		root = Empty.get();
	}

	private Indexator(final BoundingRect br, final Node<T> root) {
		this.br = br;
		this.root = root;
	}

	/**
	 * Postaví index najednou, výsledek je stejný jako po postupném {@link #add(int, int, Object)} v pořadí seznamu.
	 */
	public static <T> Indexator<T> postav(final BoundingRect br, final List<T> objekty, final ToIntFunction<? super T> xx, final ToIntFunction<? super T> yy) {
		final int n = objekty.size();
		final int[] xs = new int[n];
		final int[] ys = new int[n];
		final int[] poradi = new int[n];
		for (int i = 0; i < n; i++) {
			final T o = objekty.get(i);
			xs[i] = xx.applyAsInt(o);
			ys[i] = yy.applyAsInt(o);
			poradi[i] = i;
		}
		return new Indexator<>(br, postav(objekty, new Pole(xs, ys, poradi), 0, n, br.xx1, br.yy1, br.xx2, br.yy2));
	}

	/** Souřadnice se přeskládávají spolu s pořadím, aby dělení četlo paměť popořadě, ne na přeskáčku podle pořadí. */
	private static final class Pole {
		final int[] xs, ys, poradi;
		final int[] pomX, pomY, pomPoradi;

		Pole(final int[] xs, final int[] ys, final int[] poradi) {
			this.xs = xs;
			this.ys = ys;
			this.poradi = poradi;
			pomX = new int[xs.length];
			pomY = new int[xs.length];
			pomPoradi = new int[xs.length];
		}
	}

	private static <T> Node<T> postav(final List<T> objekty, final Pole p, final int od, final int doo, final int xx1, final int yy1, final int xx2, final int yy2) {
		if (od == doo) {
			return Empty.get();
		}
		final int[] xs = p.xs;
		final int[] ys = p.ys;
		final int x0 = xs[od];
		final int y0 = ys[od];
		boolean stejne = true;
		for (int i = od + 1; i < doo && stejne; i++) {
			stejne = xs[i] == x0 && ys[i] == y0;
		}
		if (stejne) {
			// Postupné přidávání dává naposledy přidaný objekt na začátek.
			Lst<T> lst = null;
			for (int i = od; i < doo; i++) {
				lst = new Lst<>(objekty.get(p.poradi[i]), lst);
			}
			return new Sheet<>(x0, y0, lst);
		}
		final int xMid = mid(xx1, xx2);
		final int yMid = mid(yy1, yy2);
		// Stabilní rozdělení do čtvrtí jz, jv, sz, sv, pořadí uvnitř čtvrti zůstává.
		final int[] zacatky = new int[5];
		for (int i = od; i < doo; i++) {
			zacatky[ctvrt(xs[i], ys[i], xMid, yMid) + 1]++;
		}
		zacatky[0] = od;
		for (int q = 1; q < 5; q++) {
			zacatky[q] += zacatky[q - 1];
		}
		final int[] dalsi = Arrays.copyOf(zacatky, 4);
		for (int i = od; i < doo; i++) {
			final int k = dalsi[ctvrt(xs[i], ys[i], xMid, yMid)]++;
			p.pomX[k] = xs[i];
			p.pomY[k] = ys[i];
			p.pomPoradi[k] = p.poradi[i];
		}
		final int delka = doo - od;
		System.arraycopy(p.pomX, od, xs, od, delka);
		System.arraycopy(p.pomY, od, ys, od, delka);
		System.arraycopy(p.pomPoradi, od, p.poradi, od, delka);
		return new Ctverecnik<>(xx1, yy1, xx2, yy2,
				postav(objekty, p, zacatky[0], zacatky[1], xx1, yy1, xMid, yMid),
				postav(objekty, p, zacatky[1], zacatky[2], xMid, yy1, xx2, yMid),
				postav(objekty, p, zacatky[2], zacatky[3], xx1, yMid, xMid, yy2),
				postav(objekty, p, zacatky[3], zacatky[4], xMid, yMid, xx2, yy2));
	}

	/** Čtvrť jako v {@link Sheet#rozčtvrť()}: 0 jz, 1 jv, 2 sz, 3 sv. */
	private static int ctvrt(final int xx, final int yy, final int xMid, final int yMid) {
		return (xx < xMid ? 0 : 1) + (yy < yMid ? 0 : 2);
	}

	private Indexator<T> with(final Node<T> root) {
		return new Indexator<T>(br, root);
	}

	public Optional<T> locateAnyOne() {
		return stream().findFirst();
	}

	public Stream<T> stream() {
		return streamSheet(false).map(Sheet::get);
	}

	public Stream<T> parallelStream() {
		return streamSheet(true).map(Sheet::get);
	}

	public Optional<T> locateNearestOne(final int xx, final int yy) {
		return streamSheet(false)
				.min(Comparator.comparingLong(s -> {
					final long dx = (long) s.xx - xx;
					final long dy = (long) s.yy - yy;
					return dx * dx + dy * dy;
				}))
				.map(Sheet::get);

	}

	Node<T> root() {
		return root;
	}

	/** Celkový počet objektů uvnitř */
	public int getCount() {
		return root.count;
	}

	public Indexator<T> add(final int xx, final int yy, final T mapobj) {
		checkArgument(xx >= br.xx1 && xx < br.xx2 && yy >= br.yy1 && yy < br.yy2, "Hodnoty %s %s jsou mimo rozsah %s", xx, yy, br);
		return with(merge(root, new Sheet<>(xx, yy, mapobj), br.xx1, br.yy1, br.xx2, br.yy2));
	}

	public Indexator<T> merge(final Indexator<T> indexator) {
		checkArgument(checkSameBounding(indexator.br, br), "Bounding %s %s není stejný.", indexator.br, br);
		return with(merge(indexator.root, root, br.xx1, br.yy1, br.xx2, br.yy2));
	}

	/**
	 * Boundne do daného obdélníku a vrátí indexator s objekty omezenými jen na ten obdélník.
	 * @param rect
	 * @return
	 */
	public Indexator<T> bound(final BoundingRect rect) {
		return with(root.bound(rect));
	}


	private Stream<Sheet<T>> streamSheet(final boolean paralel) {
		return StreamSupport.stream(spliterator(), paralel);
	}

	private Spliterator<Sheet<T>> spliterator() {
		return new MySplitIterator<T>(root);
	}

	public void vypis() {
		root.vypis("root", 1);
	}


	private static <T> Node<T> merge(final Node<T> node1, final Node<T> node2, final int xx1, final int yy1, final int xx2, final int yy2) {
		if (node1.isEmpty()) {
			return node2;
		}
		if (node2.isEmpty()) {
			return node1;
		}
		if (node1.hasSameCoordinates(node2)) {
			return node1.joinWithSameCoordinates(node2);
		} else {
			final Ctverecnik<T> ctv1 = node1.rozčtvrť(xx1, yy1, xx2, yy2);
			final Ctverecnik<T> ctv2 = node2.rozčtvrť(xx1, yy1, xx2, yy2);
			final int xMid = mid(xx1, xx2);
			final int yMid = mid(yy1, yy2);
			return new Ctverecnik<>(xx1, yy1, xx2, yy2,
					merge(ctv1.jz, ctv2.jz, xx1, yy1, xMid, yMid),
					merge(ctv1.jv, ctv2.jv, xMid, yy1, xx2, yMid),
					merge(ctv1.sz, ctv2.sz, xx1, yMid, xMid, yy2),
					merge(ctv1.sv, ctv2.sv, xMid, yMid, xx2, yy2));
		}
	}

	/** Jediné místo, kde se dělí čtverec. */
	static int mid(final int a, final int b) {
		return (int) (((long) a + b) / 2);
	}

	private static boolean checkSameBounding(final BoundingRect a, final BoundingRect b) {
		return a.xx1 == b.xx1 && a.xx2 == b.xx2 && a.yy1 == b.yy1 && a.yy2 == b.yy2;
	}

	public int count(final BoundingRect rect) {
		return bound(rect).getCount();
	}


}
