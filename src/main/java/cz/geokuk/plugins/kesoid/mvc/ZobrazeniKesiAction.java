package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.ToggleAction0;
import cz.geokuk.plugins.kesoid.EZobrazeniKesi;

/** Jedna volba z přepínače Zobrazení keší. */
public class ZobrazeniKesiAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;
	private final EZobrazeniKesi zobrazeni;
	private KesoidModel model;

	public ZobrazeniKesiAction(final EZobrazeniKesi zobrazeni, final String nazev, final String popis) {
		super(nazev);
		this.zobrazeni = zobrazeni;
		putValue(SHORT_DESCRIPTION, popis);
	}

	public void inject(final KesoidModel model) {
		this.model = model;
	}

	public void onEvent(final ZobrazeniKesiEvent event) {
		setSelected(event.getZobrazeni() == zobrazeni);
	}

	@Override
	protected void onSlectedChange(final boolean onoff) {
		if (onoff) {
			model.setZobrazeniKesi(zobrazeni);
		}
	}
}
