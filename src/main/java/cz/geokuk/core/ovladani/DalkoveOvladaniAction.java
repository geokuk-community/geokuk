package cz.geokuk.core.ovladani;

import java.io.IOException;

import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.ToggleAction0;

public class DalkoveOvladaniAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;

	private DalkoveOvladani dalkoveOvladani;

	public DalkoveOvladaniAction() {
		super("Dálkové ovládání");
		putValue(SHORT_DESCRIPTION, "Povolí ovládání Geokuku jinými programy na tomto počítači.");
		setSelected(DalkoveOvladani.jeZapnuteVNastaveni());
	}

	public void inject(final DalkoveOvladani dalkoveOvladani) {
		this.dalkoveOvladani = dalkoveOvladani;
	}

	/** Zaškrtne položku podle toho, zda ovládání běží, i když ho zapnul parametr {@code --ovladani}. */
	public void ukazStav() {
		setSelected(dalkoveOvladani.bezi());
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		DalkoveOvladani.setZapnuteVNastaveni(nastaveno);
		if (!nastaveno) {
			dalkoveOvladani.zastav();
			return;
		}
		try {
			dalkoveOvladani.spust(DalkoveOvladani.VYCHOZI_PORT);
		} catch (final IOException e) {
			Dlg.error(DalkoveOvladani.popisChyby(DalkoveOvladani.VYCHOZI_PORT, e));
		}
	}
}
