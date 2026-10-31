package cz.geokuk.core.napoveda;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Počet dlaždic, které se od spuštění programu zobrazily na obrazovce, celkem a podle zdroje. Počítá se každé dosazení hotové dlaždice do zobrazené mapy (zdroj a
 * umístění dlaždice), ať přišla z paměti, z disku nebo ze sítě; dlaždice zobrazená znovu po zoomu nebo posunu se počítá znovu. Překreslení téže dlaždice se
 * nepočítá. Při načítání z API by každá takto zobrazená dlaždice byla jedním kreditem.
 */
public final class ZobrazeneDlazdice {

	private static final LongAdder CELKEM = new LongAdder();
	private static final ConcurrentMap<String, Zdroj> ZDROJE = new ConcurrentHashMap<>();

	private static final class Zdroj {
		final String popis;
		final LongAdder pocet = new LongAdder();

		Zdroj(final String popis) {
			this.popis = popis;
		}
	}

	private ZobrazeneDlazdice() {}

	/** Zobrazila se dlaždice daného zdroje. */
	public static void zapocti(final String klicZdroje, final String popisZdroje) {
		ZDROJE.computeIfAbsent(klicZdroje, k -> new Zdroj(popisZdroje)).pocet.increment();
		CELKEM.increment();
	}

	public static long celkem() {
		return CELKEM.sum();
	}

	/** Počet zobrazených dlaždic daného zdroje. */
	public static long pocet(final String klicZdroje) {
		final Zdroj z = ZDROJE.get(klicZdroje);
		return z == null ? 0 : z.pocet.sum();
	}

	/** Jen pro testy. */
	public static void vynuluj() {
		ZDROJE.clear();
		CELKEM.reset();
	}

	/** Oddíl pro Informace pro hlášení chyby. */
	public static String text() {
		final long celkem = celkem();
		final StringBuilder sb = new StringBuilder("\n");
		sb.append("Zobrazených dlaždic od spuštění: ").append(celkem).append(" (odhad kreditů při načítání z API: ").append(celkem).append(")\n");
		sb.append("  Počítá se každé zobrazení hotové dlaždice z libovolného zdroje; dlaždice zobrazená znovu po zoomu nebo posunu se počítá znovu.\n");
		final List<Zdroj> razene = new ArrayList<>(ZDROJE.values());
		razene.sort((a, b) -> Long.compare(b.pocet.sum(), a.pocet.sum()));
		for (final Zdroj z : razene) {
			sb.append("  ").append(z.popis).append(": ").append(z.pocet.sum()).append('\n');
		}
		return sb.toString();
	}
}
