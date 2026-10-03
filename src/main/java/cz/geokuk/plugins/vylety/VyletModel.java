/**
 *
 */
package cz.geokuk.plugins.vylety;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import cz.geokuk.framework.Model0;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.util.exception.EExceptionSeverity;
import cz.geokuk.util.exception.FExceptionDumper;

/**
 * @author Martin Veverka
 *
 */
public class VyletModel extends Model0 {

	private class Worker extends SwingWorker<Void, Void> {

		private final List<Kesoid> kese;

		public Worker(final List<Kesoid> kese) {
			this.kese = kese;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see javax.swing.SwingWorker#doInBackground()
		 */
		@Override
		protected Void doInBackground() throws Exception {
			final Clipboard scl = getSystemClipboard();
			for (final Kesoid kes : kese) {
				if (isCancelled()) {
					break;
				}
				scl.setContents(new StringSelection(kes.getUrlShow().toExternalForm()), null);
				Thread.sleep(100);
			}
			return null;
		}

		@Override
		protected void done() {
			if (isCancelled()) {
				return;
			}
			try {
				get();
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (final ExecutionException e) {
				FExceptionDumper.dump(e.getCause(), EExceptionSeverity.DISPLAY, "Kopírování odkazů do schránky");
			}
		}

	}

	private Vylet vylet;

	private VyletovyZperzistentnovac vyletovyZperzistentnovac;

	private Worker worker;

	/** Zápisy i čtení výletu jdou jedno po druhém, aby starší stav nepřepsal novější. */
	private final ExecutorService zapisovac = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "Zápis výletu");
		t.setDaemon(true);
		return t;
	});
	private final AtomicReference<List<List<String>>> snimekKZapisu = new AtomicReference<>();

	private volatile boolean zapisSelhal;

	/** Do prvního načtení je model prázdný, jeho zápis by soubor vymazal; změny se přehrají po načtení. */
	private List<Consumer<Vylet>> zmenyPredNactenim = new ArrayList<>();
	private KesBag posledniVsechny;
	private int pocetZmen;
	private int generaceNacitani;

	public VyletModel() {
		// Konec programu volá System.exit, rozepsaný výlet by se jinak ztratil.
		Runtime.getRuntime().addShutdownHook(new Thread(this::dopisNaDisk, "Dopsání výletu"));
	}

	public void add(final EVylet evyl, final Kesoid kes) {
		final EVylet evylPuvodni = vylet.add(evyl, kes);
		if (evyl != evylPuvodni) {
			ulozit(v -> v.add(evyl, kes));
			onChange(kes, evylPuvodni, evyl);
		}

	}

	public Set<Kesoid> get(final EVylet evyl) {
		return vylet.get(evyl);
	}

	public EVylet get(final Kesoid kes) {
		return vylet.get(kes);
	}

	List<String> vyletKody(final EVylet evyl) {
		return vylet.kody(evyl);
	}

	public void inject(final VyletovyZperzistentnovac vyletovyZperzistentnovac) {
		this.vyletovyZperzistentnovac = vyletovyZperzistentnovac;
	}

	public void nasypVypetDoGeogetu() {
		if (worker != null) {
			worker.cancel(true);
		}
		worker = new Worker(new ArrayList<>(get(EVylet.ANO)));
		worker.execute();
	}

	public void removeAll(final EVylet evyl) {
		vylet.removeAll(evyl);
		ulozit(v -> v.removeAll(evyl));
		onChange(null, null, null);
	}

	public void setNewVylet(final Vylet newvylet) {
		vylet = newvylet;
		fire(new VyletChangeEvent(this, null, null, null));
	}

	/**
	 * @param vsechny
	 */
	public void startLoadingVylet(final KesBag vsechny) {
		posledniVsechny = vsechny;
		final VyletLoadSwingWorker worker = new VyletLoadSwingWorker(this, vsechny, ++generaceNacitani, pocetZmen);
		worker.execute();
	}

	/** Přečte výlet ze souboru až po dokončení všech zápisů, které před ním čekají. */
	Vylet nactiPoZapisech(final KesBag vsechny) throws InterruptedException, ExecutionException {
		return zapisovac.submit(() -> vyletovyZperzistentnovac.immediatlyNactiVylet(vsechny)).get();
	}

	void prevezmiNactenyVylet(final Vylet nacteny, final int generace, final int pocetZmenPriStartu) {
		if (generace != generaceNacitani) {
			return; // mezitím začalo novější načítání
		}
		if (pocetZmenPriStartu != pocetZmen) {
			// Změny během načítání jsou už zapsané v souboru, načte se znovu i s nimi.
			startLoadingVylet(posledniVsechny);
			return;
		}
		final List<Consumer<Vylet>> odlozene = zmenyPredNactenim;
		zmenyPredNactenim = null;
		if (odlozene != null) {
			odlozene.forEach(zmena -> zmena.accept(nacteny));
		}
		setNewVylet(nacteny);
		if (odlozene != null && !odlozene.isEmpty()) {
			ulozit(null);
		}
	}

	private void ulozit(final Consumer<Vylet> zmena) {
		if (zmenyPredNactenim != null) {
			zmenyPredNactenim.add(zmena);
			return;
		}
		pocetZmen++;
		if (snimekKZapisu.getAndSet(Arrays.asList(vylet.kody(EVylet.ANO), vylet.kody(EVylet.NE))) == null) {
			zapisovac.execute(() -> {
				final List<List<String>> snimek = snimekKZapisu.getAndSet(null);
				if (snimek != null) {
					try {
						vyletovyZperzistentnovac.immediatlyZapisVylet(snimek.get(0), snimek.get(1));
						zapisSelhal = false;
					} catch (final RuntimeException e) {
						// Celý výlet se zapíše znovu při další změně, hlásit stačí jednou.
						if (!zapisSelhal) {
							zapisSelhal = true;
							FExceptionDumper.dump(new IOException(e.getMessage() + ": " + e.getCause()
									+ ". Výlet zůstává v programu a zkusí se uložit znovu při další změně; zkontrolujte, jestli jde do složky zapisovat.", e), EExceptionSeverity.DISPLAY,
									"Zápis výletu");
						}
					}
				}
			});
		}
	}

	private void dopisNaDisk() {
		zapisovac.shutdown();
		try {
			zapisovac.awaitTermination(10, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.program.Model0#initAndFire()
	 */
	@Override
	protected void initAndFire() {
		setNewVylet(new Vylet());
	}

	private void onChange(final Kesoid kes, final EVylet evylPuvodni, final EVylet evyl) {
		if (!SwingUtilities.isEventDispatchThread()) {
			return;
		}
		fire(new VyletChangeEvent(this, kes, evyl, evylPuvodni));
	}

}
