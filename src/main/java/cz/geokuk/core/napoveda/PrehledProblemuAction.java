package cz.geokuk.core.napoveda;

import java.awt.event.ActionEvent;

import cz.geokuk.framework.Action0;
import cz.geokuk.util.exception.FError;

public class PrehledProblemuAction extends Action0 {

	private static final long serialVersionUID = 1L;

	public PrehledProblemuAction() {
		super("Přehled problémů...");
		putValue(SHORT_DESCRIPTION, "Otevře okno s problémy, které se vyskytly od spuštění programu.");
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		FError.zobrazPrehled();
	}
}
