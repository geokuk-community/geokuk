package cz.geokuk.plugins.mapy.kachle.podklady;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.gui.DialogOptimalizace;

/** Mapy > Optimalizace databáze…: zhutní soubor s cache dlaždic. */
public class OptimalizaceDatabazeAction extends Action0 {

	private static final long serialVersionUID = 1L;

	private KachleModel kachleModel;

	public OptimalizaceDatabazeAction() {
		super("Optimalizace databáze…");
		putValue(SHORT_DESCRIPTION, "Zmenší a zrychlí soubor s uloženými dlaždicemi map.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_D);
	}

	public void inject(final KachleModel kachleModel) {
		this.kachleModel = kachleModel;
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		DialogOptimalizace.zobraz(Dlg.parentFrame(), kachleModel.kachleManager.optimalizace(), false);
	}
}
