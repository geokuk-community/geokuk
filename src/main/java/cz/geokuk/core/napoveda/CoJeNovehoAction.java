package cz.geokuk.core.napoveda;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import cz.geokuk.framework.Action0;

/** Nápověda > Co je nového: popisy verzí přibalené v programu. */
public class CoJeNovehoAction extends Action0 {

	private static final long serialVersionUID = 1L;

	public CoJeNovehoAction() {
		super("Co je nového");
		putValue(SHORT_DESCRIPTION, "Zobrazí, co přinesly jednotlivé verze programu.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_C);
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		JCoJeNoveho.ukazZProgramu();
	}
}
