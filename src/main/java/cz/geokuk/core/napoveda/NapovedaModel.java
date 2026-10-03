package cz.geokuk.core.napoveda;

import java.net.MalformedURLException;
import java.net.URL;

import cz.geokuk.core.onoffline.OnofflineModelChangeEvent;
import cz.geokuk.core.program.FConst;
import cz.geokuk.core.program.FPref;
import cz.geokuk.framework.Model0;
import cz.geokuk.util.process.BrowserOpener;

public class NapovedaModel extends Model0 {

	static final long DNU_ODKLADU = 7L;
	private static final String ODLOZENA_VERZE_value = "odlozenaVerze";

	private boolean onlineMode;
	private ZkontrolovatAktualizaceSwingWorker probihajiciKontrola;

	public void onEvent(final OnofflineModelChangeEvent event) {
		onlineMode = event.isOnlineMOde();
	}

	public void zkontrolujNoveAktualizace(final boolean zobrazovatInfoPriSpravneVerzi) {
		if (!onlineMode) {
			return;
		}
		// Odklad bez uložené verze platí na všechny verze; s verzí rozhodne kontrola až podle nalezené verze.
		if (!zobrazovatInfoPriSpravneVerzi && System.currentTimeMillis() < konecOdkladu() && odlozenaVerze() == null) {
			return;
		}
		// Běžící kontrola ukáže výsledek, druhý dialog by byl stejný.
		if (probihajiciKontrola != null && probihajiciKontrola.isBetaKanal() == Diagnostika.betaKanal()) {
			if (zobrazovatInfoPriSpravneVerzi) {
				probihajiciKontrola.zobrazitDialogPriPosledniVerzi();
			}
			return;
		}
		probihajiciKontrola = new ZkontrolovatAktualizaceSwingWorker(zobrazovatInfoPriSpravneVerzi, this);
		spust(probihajiciKontrola);
	}

	void spust(final ZkontrolovatAktualizaceSwingWorker kontrola) {
		kontrola.execute();
	}

	void kontrolaSkoncila(final ZkontrolovatAktualizaceSwingWorker kontrola) {
		if (probihajiciKontrola == kontrola) {
			probihajiciKontrola = null;
		}
	}

	/** Odloží automatickou nabídku verze {@code verze}, novější verze se nabídne hned. */
	public void odlozKontroluAktualizaci(final String verze) {
		final long ms = System.currentTimeMillis() + DNU_ODKLADU * 24L * 60L * 60L * 1000L;
		currPrefe().node(FPref.VSEOBECNE_node).putLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, ms);
		currPrefe().node(FPref.VSEOBECNE_node).put(ODLOZENA_VERZE_value, verze);
	}

	boolean jeOdlozena(final String verze) {
		return odlozeno(System.currentTimeMillis(), konecOdkladu(), odlozenaVerze(), verze);
	}

	static boolean odlozeno(final long ted, final long konecOdkladu, final String odlozena, final String nabizena) {
		return ted < konecOdkladu && (odlozena == null || !ZkontrolovatAktualizaceSwingWorker.jeNovejsi(nabizena, odlozena));
	}

	private long konecOdkladu() {
		return currPrefe().node(FPref.VSEOBECNE_node).getLong(FPref.NEXT_UPDATE_CHECK_TIMESTAMP_value, 0L);
	}

	private String odlozenaVerze() {
		return currPrefe().node(FPref.VSEOBECNE_node).get(ODLOZENA_VERZE_value, null);
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
