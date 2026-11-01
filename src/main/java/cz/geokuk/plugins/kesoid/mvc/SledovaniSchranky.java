package cz.geokuk.plugins.kesoid.mvc;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Sleduje systémovou schránku a předá text, který se v ní objeví, když okno programu není aktivní. Zapnuté jen na požádání: bez zapnutí nic nesleduje a schránku
 * ani nevytvoří. Obsah schránky se nezapisuje do logu.
 *
 * <p>
 * Událost {@link FlavorListener} hlásí změnu sady formátů, ne obsahu: další text po textu ji nevyvolá. Proto se po dobu, kdy je okno neaktivní, schránka ještě
 * pravidelně čte ({@link #PERIODA_MS}); časovač běží jen při zapnuté volbě a neaktivním okně. Čtení schránky běží na jednom vlákně na pozadí, na vlákno událostí se
 * předává jen krátký text. Znovu zkopírovaný týž text se nepozná, dokud se mezitím obsah schránky nezměnil.
 */
final class SledovaniSchranky implements FlavorListener {

	/** Delší text není kód ani odkaz; nepředává se dál. */
	static final int NEJVIC_ZNAKU = 2000;
	/** Po velkém textu se schránka čte jen každé tolikáté tiknutí časovače. */
	static final int TIKU_PO_VELKEM_TEXTU = 10;

	/** Jak často se schránka čte, dokud je okno neaktivní. */
	static final long PERIODA_MS = 1000;

	/** Pravidelné opakování úlohy; vrací zrušení. */
	interface Casovac {
		Runnable opakuj(long periodaMs, Runnable uloha);
	}

	private final Supplier<Clipboard> dodavatelSchranky;
	private final BooleanSupplier oknoJeAktivni;
	private final Predicate<String> prejdiNa;
	private final Consumer<Runnable> naEdt;
	private final Executor pozadi;
	private final Casovac casovac;

	private Clipboard schranka;
	private boolean zapnuto;
	private Runnable zrusCasovac;
	/** Obsah schránky při odchodu z programu: co v ní bylo dřív, se neřeší. */
	private String zakladni;
	/** Poslední text, který se předal; opakování téhož textu se ignoruje. */
	private String posledni;
	/** Ve schránce je příliš velký text; periodická kontrola schránku čte jen zřídka (viz {@link #TIKU_PO_VELKEM_TEXTU}), aby velký text nezatěžoval pozadí. */
	private boolean prilisVelky;
	private int tikuOdVelkeho;

	/**
	 * @param dodavatelSchranky
	 *            schránku vytvoří až při zapnutí volby
	 * @param oknoJeAktivni
	 *            některé okno programu je aktivní; pak se schránka ignoruje (třeba kopírování v programu)
	 * @param prejdiNa
	 *            přejde na kešoid podle textu ze schránky na vlákně událostí; vrací, zda se kešoid našel
	 * @param naEdt
	 *            předá práci vláknu událostí
	 * @param pozadi
	 *            jedno vlákno pro čtení schránky
	 */
	SledovaniSchranky(final Supplier<Clipboard> dodavatelSchranky, final BooleanSupplier oknoJeAktivni, final Predicate<String> prejdiNa, final Consumer<Runnable> naEdt,
			final Executor pozadi, final Casovac casovac) {
		this.dodavatelSchranky = dodavatelSchranky;
		this.oknoJeAktivni = oknoJeAktivni;
		this.prejdiNa = prejdiNa;
		this.naEdt = naEdt;
		this.pozadi = pozadi;
		this.casovac = casovac;
	}

	synchronized void nastav(final boolean sledovat) {
		if (sledovat == zapnuto) {
			return;
		}
		zapnuto = sledovat;
		zakladni = null;
		posledni = null;
		prilisVelky = false;
			tikuOdVelkeho = 0;
		if (sledovat) {
			schranka = dodavatelSchranky.get();
			schranka.addFlavorListener(this);
			if (!oknoJeAktivni.getAsBoolean()) {
				// Volba se zapnula, když už je program na pozadí: časovač a výchozí obsah schránky jako při odchodu z programu.
				oknoSeZmenilo(false);
			}
		} else {
			zastavCasovac();
			if (schranka != null) {
				schranka.removeFlavorListener(this);
			}
		}
	}

	synchronized boolean jeZapnuto() {
		return zapnuto;
	}

	/** Okno programu se stalo aktivním nebo neaktivním (hlavní okno nebo dialog; přechod mezi nimi prochází neaktivním stavem). */
	void oknoSeZmenilo(final boolean aktivni) {
		synchronized (this) {
			if (!zapnuto) {
				return;
			}
			posledni = null;
			zakladni = null;
			prilisVelky = false;
			tikuOdVelkeho = 0;
			if (aktivni) {
				zastavCasovac();
				return;
			}
			if (zrusCasovac == null) {
				zrusCasovac = casovac.opakuj(PERIODA_MS, this::periodickaKontrola);
			}
		}
		// Pořadí zajistí jediné vlákno: základ se přečte dřív než jakákoli další kontrola.
		pozadi.execute(() -> {
			final String text = precti();
			synchronized (this) {
				zakladni = text;
			}
		});
	}

	@Override
	public void flavorsChanged(final FlavorEvent e) {
		synchronized (this) {
			prilisVelky = false;
			tikuOdVelkeho = 0;
		}
		zkontrolujNaPozadi();
	}

	private void periodickaKontrola() {
		synchronized (this) {
			if (prilisVelky && ++tikuOdVelkeho < TIKU_PO_VELKEM_TEXTU) {
				return;
			}
			tikuOdVelkeho = 0;
		}
		zkontrolujNaPozadi();
	}

	private void zkontrolujNaPozadi() {
		if (!jeZapnuto() || oknoJeAktivni.getAsBoolean()) {
			return;
		}
		pozadi.execute(this::zkontroluj);
	}

	/** Na vlákně na pozadí. */
	private void zkontroluj() {
		if (!jeZapnuto() || oknoJeAktivni.getAsBoolean()) {
			return;
		}
		final String text = precti();
		synchronized (this) {
			if (text != null && !text.equals(zakladni)) {
				// Schránka se od odchodu z programu změnila, výchozí obsah už nic neznamená.
				zakladni = null;
			}
		}
		if (text != null && text.length() > NEJVIC_ZNAKU) {
			synchronized (this) {
				prilisVelky = true;
			}
			return;
		}
		if (text == null || text.trim().isEmpty()) {
			return;
		}
		synchronized (this) {
			prilisVelky = false;
			if (text.equals(zakladni) || text.equals(posledni)) {
				return;
			}
			posledni = text;
		}
		naEdt.accept(() -> {
			if (jeZapnuto() && !oknoJeAktivni.getAsBoolean()) {
				prejdiNa.test(text);
			}
		});
	}

	private void zastavCasovac() {
		if (zrusCasovac != null) {
			zrusCasovac.run();
			zrusCasovac = null;
		}
	}

	/** Text schránky; null, když ho nejde přečíst (schránku drží jiná aplikace, není v ní text, text je příliš velký). */
	private String precti() {
		final Clipboard s;
		synchronized (this) {
			s = schranka;
		}
		if (s == null) {
			return null;
		}
		try {
			if (!s.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
				return null;
			}
			final Object data = s.getData(DataFlavor.stringFlavor);
			return data instanceof String ? (String) data : null;
		} catch (final UnsupportedFlavorException | IOException | RuntimeException | OutOfMemoryError ex) {
			// IllegalStateException: schránku právě drží jiná aplikace
			return null;
		}
	}
}
