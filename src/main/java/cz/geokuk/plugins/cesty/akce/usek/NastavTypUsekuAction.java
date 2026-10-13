package cz.geokuk.plugins.cesty.akce.usek;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.cesty.data.TypUseku;
import cz.geokuk.plugins.cesty.data.Usek;

/** Vede úsek po cestách z offline mapy, nebo rovně. */
public class NastavTypUsekuAction extends UsekAction0 {

	private static final long serialVersionUID = 1L;

	private final TypUseku typ;

	public NastavTypUsekuAction(final Usek usek, final Mou mouMysi, final TypUseku typ) {
		super(usek, mouMysi);
		this.typ = typ;
	}

	@Override
	protected boolean mamPovolitProUsek(final Usek usek, final Mou mou) {
		return usek.getTyp() != typ;
	}

	@Override
	protected void nastavJmenoAkce(final Usek usek, final Mou mou) {
		if (typ == TypUseku.PO_CESTE) {
			putValue(NAME, "<html>Vést úsek " + usek.dalkaHtml() + " po cestě");
			putValue(SHORT_DESCRIPTION, "Úsek povede po cestách a pěšinách z offline mapy.");
		} else {
			putValue(NAME, "<html>Vést úsek " + usek.dalkaHtml() + " rovně");
			putValue(SHORT_DESCRIPTION, "Úsek povede rovnou čarou mezi body.");
		}
	}

	@Override
	protected void provedProUsek(final Usek usek, final Mou mou) {
		cestyModel.nastavTypUseku(usek, typ);
	}
}
