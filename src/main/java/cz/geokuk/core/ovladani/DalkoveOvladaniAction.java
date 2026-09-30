package cz.geokuk.core.ovladani;

import java.io.IOException;

import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.ToggleAction0;

public class DalkoveOvladaniAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;

	private DalkoveOvladani dalkoveOvladani;

	public DalkoveOvladaniAction() {
		super("Dálkové ovládání");
		putValue(SHORT_DESCRIPTION, "Povolí ovládání Geokuku jinými programy na tomto počítači, třeba doplňkem Geogetu.");
		setSelected(DalkoveOvladani.jeZapnuteVNastaveni());
	}

	public void inject(final DalkoveOvladani dalkoveOvladani) {
		this.dalkoveOvladani = dalkoveOvladani;
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
			Dlg.error("Dálkové ovládání nejde spustit na portu " + DalkoveOvladani.VYCHOZI_PORT + ": " + e.getMessage());
		}
	}
}
