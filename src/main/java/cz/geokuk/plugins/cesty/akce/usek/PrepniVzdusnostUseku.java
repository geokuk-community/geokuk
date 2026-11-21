package cz.geokuk.plugins.cesty.akce.usek;

import cz.geokuk.core.coordinates.Mou;
import cz.geokuk.plugins.cesty.data.TypUseku;
import cz.geokuk.plugins.cesty.data.Usek;

public class PrepniVzdusnostUseku extends UsekAction0 {

	private static final long serialVersionUID = 1L;

	public PrepniVzdusnostUseku(final Usek usek, final Mou mouMysi) {
		super(usek, mouMysi);
	}

	@Override
	protected boolean mamPovolitProUsek(final Usek usek, final Mou mou) {
		return true;
	}

	@Override
	protected void nastavJmenoAkce(final Usek usek, final Mou mou) {
		final String dalkas = usek.dalkaHtml();
		if (usek.isVzdusny()) {
			final boolean poCeste = cestyModel.getTypNovychUseku() == TypUseku.PO_CESTE;
			putValue(NAME, "<html>Přepni úsek " + dalkas + (poCeste ? " na úsek po cestě" : " na rovný"));
			putValue(SHORT_DESCRIPTION,
			        "Vybraný vzdušný úsek přepne podle volby Nové úseky na úsek po cestě (plná čára), nebo rovný (čárkovaná čára). Sousední segmenty cesty se spojí a úsek se započítá do délky cesty.");
		} else {
			putValue(NAME, "<html>Přepni úsek " + dalkas + " na vzdušný");
			putValue(SHORT_DESCRIPTION,
			        "Vybraný úsek přepne na vzdušný (tečkovaná čára). Segment cesty se v něm rozdělí na dva a úsek se nezapočítá do délky cesty.");
		}
	}

	@Override
	protected void provedProUsek(final Usek usek, final Mou mou) {
		cestyModel.prepniVzdusnostUseku(usek, !usek.isVzdusny());
	}

}
