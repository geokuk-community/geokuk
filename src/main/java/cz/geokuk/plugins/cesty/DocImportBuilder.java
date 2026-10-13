package cz.geokuk.plugins.cesty;

import java.util.ArrayList;
import java.util.List;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.cesty.data.*;
import cz.geokuk.plugins.kesoid.importek.GpxWpt;
import cz.geokuk.plugins.kesoid.importek.IImportBuilder;

public class DocImportBuilder implements IImportBuilder {

	private Cesta cesta;
	private List<Cesta> cesty = new ArrayList<>();

	private boolean zacatekSegmentu;
	/** Trasa má označené řídicí body ({@code gk:bod}); ostatní body jsou trasa úseku po cestě. */
	private boolean oznaceneBody;
	/** Body trasy po cestě od posledního řídicího bodu. */
	private final List<Mou> bodyTrasy = new ArrayList<>();

	private final Updator updator = new Updator();

	public DocImportBuilder() {}

	@Override
	public void addGpxWpt(final GpxWpt gpxwpt) {}

	@Override
	public void addTrackWpt(final GpxWpt wpt) {
		if (wpt.wgs == null) {
			return; // bod s nečitelnými souřadnicemi
		}
		final Mou mou = wpt.wgs.toMou();
		if (cesta.isEmpty() && wpt.gkBod) {
			oznaceneBody = true;
		}
		if (oznaceneBody && !wpt.gkBod) {
			bodyTrasy.add(mou);
			return;
		}
		final Bod predchozi = cesta.getCil();
		final Bod bod = updator.pridejNaKonec(cesta, mou);
		final Usek usek = bod == predchozi ? null : bod.getUvzad(); // právě přidaný úsek, pokud nějaký
		if (usek != null && zacatekSegmentu) {
			updator.setVzdusny(usek, true);
		} else if (usek != null && !bodyTrasy.isEmpty()) {
			final List<Mou> trasa = new ArrayList<>(bodyTrasy.size() + 2);
			trasa.add(predchozi.getMou());
			trasa.addAll(bodyTrasy);
			trasa.add(bod.getMou());
			updator.setNactenaTrasa(usek, trasa);
		}
		bodyTrasy.clear();
		zacatekSegmentu = false;
	}

	/** Body trasy, za kterými už řídicí bod nepřišel, se načtou jako obyčejné body. */
	private void pridejZbyleBodyTrasy() {
		for (final Mou mou : bodyTrasy) {
			updator.pridejNaKonec(cesta, mou);
		}
		bodyTrasy.clear();
	}

	@Override
	public void begTrack() {
		cesta = Cesta.create();
		oznaceneBody = false;
		bodyTrasy.clear();
	}

	@Override
	public void begTrackSegment() {
		pridejZbyleBodyTrasy();
		zacatekSegmentu = true;
	}

	@Override
	public void endTrack() {
		pridejZbyleBodyTrasy();
		cesty.add(cesta);
		cesta = null;
	}

	@Override
	public void endTrackSegment() {}

	@Override
	public void setTrackName(final String aTrackName) {
		updator.setNazev(cesta, aTrackName);
	}

	List<Cesta> getCesty() {
		return cesty;
	}

	void setCesty(final List<Cesta> cesty) {
		this.cesty = cesty;
	}

	@Override
	public void init() {
	}

	@Override
	public void done() {
	}

}
