package cz.geokuk.util.index2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/** List stromu: jeden bod. Hranice nedrží, ty zná čtverečník nad ním; jediná hodnota je přímo, víc hodnot ve stejném bodě v {@link Lst}. */
@Slf4j
class Sheet<T> extends Node<T> {

	final int xx;
	final int yy;

	/** T, nebo {@link Lst} s víc hodnotami. */
	private final Object hodnoty;

	Sheet(final int xx, final int yy, final T mapobj) {
		super(1);
		this.xx = xx;
		this.yy = yy;
		hodnoty = Objects.requireNonNull(mapobj, "Nesmi byt null v hodnotách ctvrecnickych");
	}

	private Sheet(final int xx, final int yy, final Lst<T> lst) {
		super(lst.count);
		this.xx = xx;
		this.yy = yy;
		hodnoty = lst.next == null ? lst.value : lst;
	}

	@Override
	boolean isSheet() {
		return true;
	}

	@SuppressWarnings("unchecked")
	T get() {
		return hodnoty instanceof Lst ? ((Lst<T>) hodnoty).value : (T) hodnoty;
	}

	@SuppressWarnings("unchecked")
	Lst<T> lst() {
		return hodnoty instanceof Lst ? (Lst<T>) hodnoty : new Lst<>((T) hodnoty);
	}

	@Override
	void vypis(final String aPrefix, final int aLevel) {
		final String mezery = String.format("%" + aLevel * 2 + "s", " ");
		log.debug("{}{}: [{},{}] {}", mezery, aPrefix, xx, yy, hodnoty);
	}

	@Override
	public String toString() {
		return "{" + xx + " " + yy + " " + hodnoty + "}";
	}

	@Override
	Ctverecnik<T> rozčtvrť(final int xx1, final int yy1, final int xx2, final int yy2) {
		final Empty<T> e = Empty.get();
		final int xMid = Indexator.mid(xx1, xx2);
		final int yMid = Indexator.mid(yy1, yy2);
		if (xx < xMid && yy < yMid) {
			return new Ctverecnik<>(xx1, yy1, xx2, yy2, this, e, e, e);
		} else if (xx >= xMid && yy < yMid) {
			return new Ctverecnik<>(xx1, yy1, xx2, yy2, e, this, e, e);
		} else if (xx < xMid && yy >= yMid) {
			return new Ctverecnik<>(xx1, yy1, xx2, yy2, e, e, this, e);
		} else {
			return new Ctverecnik<>(xx1, yy1, xx2, yy2, e, e, e, this);
		}
	}

	static class Lst<T> {
		final T value;
		final Lst<T> next;
		final int count;

		public Lst(final T value) {
			this(value, null);
		}

		public Lst(final T value, final Lst<T> next) {
			if (value == null) {
				throw new NullPointerException("Nesmi byt null v hodnotách ctvrecnickych");
			}
			this.value = value;
			this.next = next;
			count = next == null ? 1 : 1 + next.count;
		}


		static <T> Lst<T> join(final Lst<T> left, final Lst<T> right) {
			if (left == null) {
				return right;
			}
			if (right == null) {
				return left;
			}
			if (left.count > right.count) {
				return join(right, left); // vždy kvůli rychlosti menší velvo
			}
			// Cyklem, ne rekurzí: tisíce bodů na stejném místě by přetekly zásobník.
			final List<T> hodnoty = new ArrayList<>(left.count);
			for (Lst<T> l = left; l != null; l = l.next) {
				hodnoty.add(l.value);
			}
			Lst<T> vysledek = right;
			for (int i = hodnoty.size() - 1; i >= 0; i--) {
				vysledek = new Lst<>(hodnoty.get(i), vysledek);
			}
			return vysledek;

		}

		@Override
		public String toString() {
			if (count == 1) {
				return value.toString();
			} else {
				return value + " ... more " + count;
			}
		}


	}


	@Override
	boolean hasSameCoordinates(final Node<T> node) {
		if (node instanceof Sheet<?>) {
			final Sheet<T> sheet = (Sheet<T>) node;
			return xx == sheet.xx && yy == sheet.yy;
		} else {
			return false;
		}
	}

	@Override
	Node<T> joinWithSameCoordinates(final Node<T> node) {
		return new Sheet<>(xx, yy, Lst.join(((Sheet<T>) node).lst(), lst()));
	}

	/**
	 * Když je tam, redukuje se na to, když je venku tak prázdný.
	 */
	@Override
	Node<T> bound(final BoundingRect rect) {
		final boolean tenObjektJeUvnitr = xx >= rect.xx1 && xx < rect.xx2 && yy >= rect.yy1 && yy < rect.yy2;
		return tenObjektJeUvnitr ? this : Empty.get();
	}

	@Override
	boolean tryAdvance(final MySplitIterator<T> splititerator, final Consumer<? super Sheet<T>> action) {
		if (!(hodnoty instanceof Lst)) {
			action.accept(this);
			return true;
		}
		final Lst<T> lst = lst();
		action.accept(new Sheet<>(xx, yy, lst.value));
		splititerator.push(new Sheet<>(xx, yy, lst.next)); // a pushneme s jedním zlikvidovaným objektem
		return true; // a něco se zpracovalo
	}

	@Override
	Splitenec<T> trySplit(final int pocetVedle) {
		log.debug("splituje sheet, nesplituje, dlouho by trvalo");
		return new Splitenec<T>(this, Empty.get());
	}
}
