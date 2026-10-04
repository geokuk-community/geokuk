/**
 *
 */
package cz.geokuk.plugins.cesty.akce.cesta;

import java.awt.event.KeyEvent;

import cz.geokuk.plugins.cesty.data.Cesta;

/**
 * Jde na vybranou pozici
 *
 * @author Martin Veverka
 *
 */
public class UzavritCestuAction extends CestaAction0 {

	private static final long serialVersionUID = -2882817111560336824L;

	// private Pozice pozice;
	/**
	 * @param aBoard
	 */
	public UzavritCestuAction(final Cesta cesta) {
		super(cesta);
		putValue(NAME, "Uzavřít cestu");
		putValue(SHORT_DESCRIPTION, "Spojí konec cesty s jejím začátkem.");
		// putValue(MNEMONIC_KEY, KeyEvent.VK_P);
		putValue(MNEMONIC_KEY, KeyEvent.VK_C);
		// putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke("RIGHT"));
	}

	@Override
	protected boolean mamPovolitProCestu(final Cesta cesta) {
		return !cesta.isKruh();
	}

	@Override
	protected void nastavJmenoAkce(final Cesta cesta, final boolean aZKontextovehoMenu) {
		putValue(NAME, "<html>Uzavřít cestu" + cesta.getNazevHtml() + " " + cesta.dalkaHtml() + " +" + cesta.dalkaStartuACileHtml());
	}

	@Override
	protected void provedProCestu(final Cesta cesta) {
		cestyModel.uzavriCestu(cesta);
	}

}
