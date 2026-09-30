package cz.geokuk.plugins.kesoid;

import cz.geokuk.framework.DialogOpeningAction0;
import cz.geokuk.framework.JMyDialog0;

public class LimityKresleniAction extends DialogOpeningAction0 {

	private static final long serialVersionUID = 1L;

	public LimityKresleniAction() {
		super("Limity kreslení...");
		putValue(SHORT_DESCRIPTION, "Do kolika waypointů ve výřezu se kreslí keše, popisky, kruhy a obsazenost.");
	}

	@Override
	public JMyDialog0 createDialog() {
		return new JLimityKresleniDialog();
	}
}
