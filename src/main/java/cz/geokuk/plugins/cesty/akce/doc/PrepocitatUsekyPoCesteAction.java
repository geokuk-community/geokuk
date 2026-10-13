package cz.geokuk.plugins.cesty.akce.doc;

import cz.geokuk.plugins.cesty.data.Doc;

/** Znovu hledá trasy úseků po cestě, které je nenašly, třeba po přidání offline mapy. */
public class PrepocitatUsekyPoCesteAction extends DocAction0 {

	private static final long serialVersionUID = 1L;

	public PrepocitatUsekyPoCesteAction(final Doc doc) {
		super(doc);
		putValue(NAME, "Přepočítat úseky po cestě");
		putValue(SHORT_DESCRIPTION, "Znovu hledá trasu po cestách u úseků, které ji nenašly, třeba po přidání offline mapy.");
	}

	@Override
	protected boolean mamPovolitProDoc(final Doc doc) {
		return doc.getPocetUsekuPoCesteBezTrasy() > 0;
	}

	@Override
	protected void nastavJmenoAkce(final Doc doc, final boolean aZKontextovehoMenu) {
		putValue(NAME, "Přepočítat úseky po cestě (" + doc.getPocetUsekuPoCesteBezTrasy() + ")");
	}

	@Override
	protected void provedProDoc(final Doc doc) {
		cestyModel.prepocitejUsekyPoCeste();
	}
}
