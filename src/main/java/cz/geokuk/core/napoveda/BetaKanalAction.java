package cz.geokuk.core.napoveda;

import java.awt.event.KeyEvent;

import cz.geokuk.framework.ToggleAction0;

/** Zapíná nabízení testovacích verzí při kontrole aktualizací. */
public class BetaKanalAction extends ToggleAction0 {

	private static final long serialVersionUID = 1L;
	private NapovedaModel napovedaModel;

	public BetaKanalAction() {
		super("Nabízet testovací verze (beta)");
		putValue(SHORT_DESCRIPTION, "Kontrola aktualizací nabídne i testovací verze. Po vypnutí nabídne poslední ostrou verzi.");
		putValue(MNEMONIC_KEY, KeyEvent.VK_B);
		setSelected(Diagnostika.betaKanal());
	}

	public void inject(final NapovedaModel napovedaModel) {
		this.napovedaModel = napovedaModel;
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		if (nastaveno == Diagnostika.betaKanal()) {
			return;
		}
		Diagnostika.setBetaKanal(nastaveno);
		Diagnostika.zaznamenej("Beta kanál " + (nastaveno ? "zapnut" : "vypnut"));
		napovedaModel.zkontrolujNoveAktualizace(true);
	}
}
