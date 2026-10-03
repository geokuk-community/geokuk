package cz.geokuk.plugins.cesty.akce;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import cz.geokuk.core.coord.PoziceChangedEvent;
import cz.geokuk.core.coord.Poziceq;
import cz.geokuk.core.coordinates.Mouable;
import cz.geokuk.plugins.cesty.data.Bod;
import cz.geokuk.plugins.kesoid.Wpt;

public class PridatDoCestyAction extends CestyAction0 {

	private static final long serialVersionUID = 1L;

	private final Mouable kontextoveMouable;
	private Poziceq poziceq;

	public PridatDoCestyAction(final Mouable kontextoveMouable) {
		this.kontextoveMouable = kontextoveMouable;

		putValue(NAME, "Přidat do cesty");
		putValue(SHORT_DESCRIPTION, "Zařadí waypoint nebo pozici do vybrané cesty. Když žádná cesta není vybraná, vybere se nejbližší cesta, a když žádná cesta neexistuje, založí se.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_P);
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("INSERT"));
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		cestyModel.addToVylet(effectiveMouable());
	}

	public void onEvent(final PoziceChangedEvent aEvent) {
		poziceq = aEvent.poziceq;
		enablujPokudMaSmysl();
	}

	protected void enablujPokudMaSmysl() {
		setEnabled(effectiveMouable() != null && !cestyModel.isOnVylet(effectiveMouable()));
	}

	@Override
	protected final void vyletChanged() {
		super.vyletChanged();
		enablujPokudMaSmysl();
	}

	private Mouable effectiveMouable() {
		if (kontextoveMouable != null) {
			return proPridaniDoVyletu(kontextoveMouable);
		}
		// jinak se musíme spolehnout na pozici
		if (!poziceq.isNoPosition()) {
			return proPridaniDoVyletu(poziceq.getPoziceMouable());
		}
		return null;
	}

	/**
	 * Do vyýletu se dá přidat jen WPT, ale ne Bod, ale také volná pozice.
	 *
	 * @param mouable
	 * @return
	 */
	private Mouable proPridaniDoVyletu(final Mouable mouable) {
		if (mouable instanceof Wpt) {
			return mouable;
		}
		if (mouable instanceof Bod) {
			return null; // připojovat na bod se nebudeme
		}
		return mouable.getMou(); // a ke všemu ostatnímu se připojíme, což je zřejmě notmální bod
	}

}
