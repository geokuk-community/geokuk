package cz.geokuk.util.index2d;

import java.util.Arrays;
import java.util.Spliterator;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MySplitIterator<T> implements Spliterator<Sheet<T>> {

	private Node<T> node;
	/** Zásobník v poli: iterace přes velký výřez nesmí alokovat na každý uzel. */
	private Node<T>[] zasobnik = noveNody(16);
	private int vyska;
	private boolean advancnuto;

	public MySplitIterator(final Node<T> node) {
		this.node = node;
		if (node != null) {
			push(node);
		}
	}

	@Override
	public boolean tryAdvance(final Consumer<? super Sheet<T>> action) {
		if (vyska == 0) {
			return false; // doiterováno jest
		}
		advancnuto = true;
		return pop().tryAdvance(this, action);
	}

	@Override
	public Spliterator<Sheet<T>> trySplit() {
		if (advancnuto) {
			log.warn("Nepodporujeme trySplit, když se advanclo");
			return null;
		}
		final Splitenec<T> splitenec = node.trySplit(0);
		if (splitenec.nahrazenec.count == 0 || splitenec.odriznuto.count == 0) {
			log.debug("Nedokazal splitnout: " + node.count);
			return null; // vlastně jsme nesplitli
		}
		if (log.isDebugEnabled()) {
			log.debug("SPLITNUTO: {}|{}", splitenec.nahrazenec.count, splitenec.odriznuto.count);
			if (log.isTraceEnabled()) {
				node.vypis("node", 1);
				splitenec.odriznuto.vypis("odriznuto", 1);
				splitenec.nahrazenec.vypis("nahrazenec", 1);
			}
		}

		node = splitenec.nahrazenec;
		Arrays.fill(zasobnik, 0, vyska, null);
		vyska = 0;
		push(node);
		return new MySplitIterator<T>(splitenec.odriznuto);
	}

	@Override
	public long estimateSize() {
		return node.count;
	}

	@Override
	public int characteristics() {
		return IMMUTABLE | SIZED | SUBSIZED | NONNULL;
	}


	/** Vytáhn z vrcholu stacku, spadne, pokdu tam nic není */
	private Node<T> pop() {
		final Node<T> node = zasobnik[--vyska];
		zasobnik[vyska] = null;
		return node;
	}

	/** Vloží na vrchol stacku, prázdné uzly vynechá. */
	void push(final Node<T> node) {
		if (node.isEmpty()) {
			return;
		}
		if (vyska == zasobnik.length) {
			zasobnik = Arrays.copyOf(zasobnik, vyska * 2);
		}
		zasobnik[vyska++] = node;
	}

	@SuppressWarnings("unchecked")
	private static <T> Node<T>[] noveNody(final int velikost) {
		return new Node[velikost];
	}
}
