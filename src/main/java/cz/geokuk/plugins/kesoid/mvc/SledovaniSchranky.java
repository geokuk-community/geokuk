package cz.geokuk.plugins.kesoid.mvc;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sleduje systémovou schránku (událostmi, ne dotazováním) a předá text, který se v ní objeví, když okno programu není aktivní. Zapnuté jen na požádání: bez
 * zapnutí nic nesleduje. Obsah schránky se nezapisuje do logu.
 */
final class SledovaniSchranky implements FlavorListener {

	/** Delší text není kód ani odkaz; nečte se dál. */
	static final int NEJVIC_ZNAKU = 2000;

	private final Clipboard schranka;
	private final BooleanSupplier oknoJeAktivni;
	private final Predicate<String> prejdiNa;
	private final Consumer<Runnable> naEdt;

	private boolean zapnuto;
	private String posledni;

	/**
	 * @param oknoJeAktivni
	 *            některé okno programu je aktivní; pak se schránka ignoruje (třeba kopírování v programu)
	 * @param prejdiNa
	 *            přejde na kešoid podle textu ze schránky na vlákně událostí; vrací, zda se kešoid našel
	 * @param naEdt
	 *            předá práci vláknu událostí
	 */
	SledovaniSchranky(final Clipboard schranka, final BooleanSupplier oknoJeAktivni, final Predicate<String> prejdiNa, final Consumer<Runnable> naEdt) {
		this.schranka = schranka;
		this.oknoJeAktivni = oknoJeAktivni;
		this.prejdiNa = prejdiNa;
		this.naEdt = naEdt;
	}

	synchronized void nastav(final boolean sledovat) {
		if (sledovat == zapnuto) {
			return;
		}
		zapnuto = sledovat;
		posledni = null;
		if (sledovat) {
			schranka.addFlavorListener(this);
		} else {
			schranka.removeFlavorListener(this);
		}
	}

	synchronized boolean jeZapnuto() {
		return zapnuto;
	}

	@Override
	public void flavorsChanged(final FlavorEvent e) {
		if (!jeZapnuto() || oknoJeAktivni.getAsBoolean()) {
			return;
		}
		final String text = precti();
		if (text == null || text.trim().isEmpty() || text.length() > NEJVIC_ZNAKU) {
			return;
		}
		synchronized (this) {
			if (text.equals(posledni)) {
				return;
			}
			posledni = text;
		}
		naEdt.accept(() -> {
			if (jeZapnuto()) {
				prejdiNa.test(text);
			}
		});
	}

	/** Text schránky; null, když ho nejde přečíst (schránku drží jiná aplikace, není v ní text). */
	private String precti() {
		try {
			if (!schranka.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
				return null;
			}
			final Object data = schranka.getData(DataFlavor.stringFlavor);
			return data instanceof String ? (String) data : null;
		} catch (final UnsupportedFlavorException | IOException | RuntimeException ex) {
			// IllegalStateException: schránku právě drží jiná aplikace
			return null;
		}
	}
}
