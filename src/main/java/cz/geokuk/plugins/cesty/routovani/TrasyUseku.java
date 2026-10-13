package cz.geokuk.plugins.cesty.routovani;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.cesty.data.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Hledá na pozadí trasy úseků vedených po cestách. Volá se z EDT, výsledky zapisuje do úseků na EDT. Hledání začne až chvíli po poslední změně cest, takže
 * během tažení bodu se nehledá.
 */
@Slf4j
public class TrasyUseku implements AutoCloseable {

	/** Za jak dlouho po poslední změně cest se začne hledat. */
	static final int ODKLAD_MS = 300;

	private final Routovac routovac;
	private final Consumer<Usek> poVypoctu;
	private final Updator updator = new Updator();
	private final ExecutorService vlakno = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "Trasy úseků");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private final Timer odklad;

	/** Úseky, pro které se hledá; úloha si pamatuje krajní body, pro které hledá. */
	private final Map<Usek, Uloha> bezici = new IdentityHashMap<>();
	private Doc cekajici;

	private final class Uloha extends FutureTask<Trasa> {
		final Usek usek;
		final Mou od;
		final Mou kam;

		Uloha(final Usek usek, final Mou od, final Mou kam) {
			super(() -> routovac.najdi(od.toWgs(), kam.toWgs(), () -> Thread.currentThread().isInterrupted()));
			this.usek = usek;
			this.od = od;
			this.kam = kam;
		}

		@Override
		protected void done() {
			SwingUtilities.invokeLater(() -> dokonceno(this));
		}
	}

	/**
	 * @param slozkaOfflineMap
	 *            složka se soubory .map; čte se při každém hledání
	 * @param poVypoctu
	 *            zavolá se na EDT, když se do úseku uložil výsledek
	 */
	public TrasyUseku(final Supplier<File> slozkaOfflineMap, final Consumer<Usek> poVypoctu) {
		this(Routovac.zeSlozky(slozkaOfflineMap), poVypoctu, ODKLAD_MS);
	}

	TrasyUseku(final Routovac routovac, final Consumer<Usek> poVypoctu, final int odkladMs) {
		this.routovac = routovac;
		this.poVypoctu = poVypoctu;
		odklad = new Timer(odkladMs, e -> spust());
		odklad.setRepeats(false);
	}

	/** Po chvíli spustí hledání pro úseky po cestě, které nemají výsledek pro své současné body. Volat po každé změně cest. */
	public void dopocitej(final Doc doc) {
		cekajici = doc;
		odklad.restart();
	}

	/** Zapomene výsledky „bez mapy“ a „nenalezeno“ a hledá znovu, třeba po přidání offline mapy. */
	public void prepocitej(final Doc doc) {
		for (final Usek usek : doc.getUseky()) {
			if (usek.getStavTrasy() != null && usek.getStavTrasy() != Trasa.Stav.NALEZENO) {
				updator.zapomenTrasu(usek);
			}
		}
		dopocitej(doc);
	}

	/** Pro úsek se trasa hledá nebo bude hledat. */
	public boolean isPocita(final Usek usek) {
		return bezici.containsKey(usek) || odklad.isRunning() && potrebuje(usek);
	}

	private static boolean potrebuje(final Usek usek) {
		return usek.isPoCeste() && usek.getStavTrasy() == null && usek.getBvzad() != null;
	}

	private void spust() {
		final Doc doc = cekajici;
		cekajici = null;
		if (doc == null) {
			return;
		}
		final Set<Usek> potrebne = Collections.newSetFromMap(new IdentityHashMap<>());
		for (final Usek usek : doc.getUseky()) {
			if (!potrebuje(usek)) {
				continue;
			}
			potrebne.add(usek);
			final Mou od = usek.getBvzad().getMou();
			final Mou kam = usek.getBvpred().getMou();
			final Uloha stara = bezici.get(usek);
			if (stara != null && stara.od.equals(od) && stara.kam.equals(kam)) {
				continue;
			}
			if (stara != null) {
				stara.cancel(true);
			}
			final Uloha uloha = new Uloha(usek, od, kam);
			bezici.put(usek, uloha);
			vlakno.execute(uloha);
		}
		for (final Iterator<Map.Entry<Usek, Uloha>> it = bezici.entrySet().iterator(); it.hasNext();) {
			final Map.Entry<Usek, Uloha> e = it.next();
			if (!potrebne.contains(e.getKey())) {
				e.getValue().cancel(true);
				it.remove();
			}
		}
	}

	private void dokonceno(final Uloha uloha) {
		if (bezici.get(uloha.usek) != uloha) {
			return;
		}
		bezici.remove(uloha.usek);
		if (uloha.isCancelled()) {
			return;
		}
		final Trasa trasa;
		try {
			trasa = uloha.get();
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			return;
		} catch (final ExecutionException e) {
			log.warn("Hledání trasy úseku selhalo", e.getCause());
			return;
		}
		if (updator.setTrasa(uloha.usek, uloha.od, uloha.kam, trasa)) {
			poVypoctu.accept(uloha.usek);
		}
	}

	/** Zruší všechna hledání. */
	public void zrusVse() {
		odklad.stop();
		cekajici = null;
		for (final Uloha u : bezici.values()) {
			u.cancel(true);
		}
		bezici.clear();
	}

	@Override
	public void close() {
		zrusVse();
		vlakno.shutdownNow();
		routovac.close();
	}
}
