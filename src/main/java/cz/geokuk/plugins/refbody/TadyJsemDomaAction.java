/**
 *
 */
package cz.geokuk.plugins.refbody;

import java.awt.event.ActionEvent;

import cz.geokuk.core.coord.PoziceChangedEvent;
import cz.geokuk.core.coord.VyrezModel;
import cz.geokuk.core.coordinates.Wgs;
import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;

/**
 * @author Martin Veverka
 *
 */
public class TadyJsemDomaAction extends Action0 {

	private static final long serialVersionUID = -2882817111560336824L;

	private Wgs hc;

	private RefbodyModel refbodyModel;

	public TadyJsemDomaAction() {
		super("Jsem doma…");
		putValue(SHORT_DESCRIPTION,
		        "Nastaví aktuální pozici jako domácí souřadnice." + " Na takto uložená nastavení se dostanete volbou \"Přesunout domů\", od těchto souřadnic se počítá vzdálenost a pozice keší.");
		// putValue(MNEMONIC_KEY, KeyEvent.VK_D);

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	@Override
	public void actionPerformed(final ActionEvent aE) {
		final Wgs stareGc = refbodyModel.getHc();
		boolean zapsat = true;
		if (!VyrezModel.DEFAULTNI_DOMACI_SOURADNICE.equals(stareGc)) {
			// ještě neukládal, tak se nemusíme ptát
			zapsat = Dlg.anone(textDotazu(hc, stareGc));
		}
		if (zapsat) {
			refbodyModel.setHc(hc);
		}
	}

	/** Dotaz před přepsáním domova: „Nastavit domov na N50°… E14°…? Dosavadní domov: N49°… E15°…“. */
	static String textDotazu(final Wgs nove, final Wgs dosavadni) {
		return "Nastavit domov na " + nove + "? Dosavadní domov: " + dosavadni;
	}

	public void inject(final RefbodyModel refbodyModel) {
		this.refbodyModel = refbodyModel;
	}

	public void onEvent(final PoziceChangedEvent aEvent) {
		if (aEvent.poziceq.isNoPosition()) {
			setEnabled(false);
		} else {
			setEnabled(true);
			hc = aEvent.poziceq.getWgs();
		}
	}

}
