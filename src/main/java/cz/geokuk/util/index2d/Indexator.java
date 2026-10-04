package cz.geokuk.util.index2d;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Comparator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


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
