package cz.geokuk.core.napoveda;

import java.net.MalformedURLException;
import java.net.URL;

import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.core.program.FConst;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.Model0;
import cz.geokuk.util.process.BrowserOpener;

public class NapovedaModel extends Model0 {

	private boolean onlineMode;

	public void onEvent(final OnofflineModelChangeEvent event) {
		onlineMode = event.isOnlineMOde();
	}

	public void zkontrolujNoveAktualizace(final boolean zobrazovatInfoPriSpravneVerzi) {
		if (!onlineMode) {
			return;
		}
		// ruční kontrola z menu odklad ignoruje
		if (!zobrazovatInfoPriSpravneVerzi) {
			final long nextCheck = currPrefe().node(FPref.VSEOBECNE_node)
					.getLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, 0L);
			if (System.currentTimeMillis() < nextCheck) {
				return;
			}
		}
		new ZkontrolovatAktualizaceSwingWorker(zobrazovatInfoPriSpravneVerzi, this).execute();
	}

	public void odlozKontroluAktualizaci(final long dnu) {
		final long ms = System.currentTimeMillis() + dnu * 24L * 60L * 60L * 1000L;
		currPrefe().node(FPref.VSEOBECNE_node).putLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, ms);
	}

	public void zobrazNapovedu(final String tema) {
		try {
			BrowserOpener.displayURL(new URL(tema == null ? FConst.WEB_PAGE_WIKI : FConst.WEB_PAGE_WIKI + "/" + tema));
		} catch (final MalformedURLException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	protected void initAndFire() {
		fire(new NapovedaModelChangedEvent());
	}

}
