package cz.geokuk.plugins.challenge;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.swing.SwingUtilities;

/**
 * Poslední podklad kreslení sdílený oběma vrstvami plánovače. S vláknem pro stavbu se podklad (neměnný) staví mimo EDT a vrstva se po dokončení překreslí; bez něj (testy) se staví hned.
 */
final class TvarCache {

	private static final class Polozka {
		final PrehledChallenge prehled;
		final VybranyKandidat vybrany;
		final CompletableFuture<TvarPlanovace> budouci;

		Polozka(final PrehledChallenge prehled, final VybranyKandidat vybrany, final CompletableFuture<TvarPlanovace> budouci) {
			this.prehled = prehled;
			this.vybrany = vybrany;
			this.budouci = budouci;
		}
	}

	private static volatile Polozka posledni;

	private TvarCache() {
	}

	/** Hotový podklad pro přehled a výběr, nebo null, dokud se staví; po dokončení se zavolá {@code poHotovo} na EDT. */
	static TvarPlanovace dej(final PrehledChallenge prehled, final VybranyKandidat vybrany, final Executor stavba, final Runnable poHotovo) {
		Polozka p = posledni;
		if (p == null || p.prehled != prehled || p.vybrany != vybrany) {
			final CompletableFuture<TvarPlanovace> f = stavba == null ? CompletableFuture.completedFuture(TvarPlanovace.z(prehled, vybrany))
					: CompletableFuture.supplyAsync(() -> TvarPlanovace.z(prehled, vybrany), stavba);
			p = new Polozka(prehled, vybrany, f);
			posledni = p;
		}
		if (p.budouci.isDone()) {
			return p.budouci.getNow(null);
		}
		p.budouci.thenRun(() -> SwingUtilities.invokeLater(poHotovo));
		return null;
	}
}
