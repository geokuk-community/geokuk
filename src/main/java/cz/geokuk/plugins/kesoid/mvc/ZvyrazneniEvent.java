package cz.geokuk.plugins.kesoid.mvc;

import java.util.List;

import cz.geokuk.framework.Event0;
import cz.geokuk.plugins.kesoid.mapicon.Zvyrazneni;

public class ZvyrazneniEvent extends Event0<KesoidModel> {
	private final Zvyrazneni zvyrazneni;
	private final List<Zvyrazneni> predvolby;

	ZvyrazneniEvent(final Zvyrazneni zvyrazneni, final List<Zvyrazneni> predvolby) {
		this.zvyrazneni = zvyrazneni;
		this.predvolby = predvolby;
	}

	public Zvyrazneni getZvyrazneni() {
		return zvyrazneni;
	}

	/** Vlastní předvolby. */
	public List<Zvyrazneni> getPredvolby() {
		return predvolby;
	}
}
