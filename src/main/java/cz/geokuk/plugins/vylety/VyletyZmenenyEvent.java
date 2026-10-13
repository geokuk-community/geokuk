package cz.geokuk.plugins.vylety;

import cz.geokuk.framework.Event0;

/** Změnil se seznam výletů, jejich jména nebo aktivní výlet. */
public class VyletyZmenenyEvent extends Event0<VyletModel> {

	private final VyletModel vyletModel;

	VyletyZmenenyEvent(final VyletModel vyletModel) {
		this.vyletModel = vyletModel;
	}

	public VyletModel getVyletModel() {
		return vyletModel;
	}
}
