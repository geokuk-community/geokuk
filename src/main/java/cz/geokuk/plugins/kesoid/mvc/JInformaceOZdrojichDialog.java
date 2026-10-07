package cz.geokuk.plugins.kesoid.mvc;

import cz.geokuk.framework.AfterEventReceiverRegistrationInit;
import cz.geokuk.framework.JMyDialog0;

/** Okno Přehled zdrojů: táž tabulka zdrojů jako ve stavovém řádku, dostupná i během načítání. */
public class JInformaceOZdrojichDialog extends JMyDialog0 implements AfterEventReceiverRegistrationInit {

	private static final long serialVersionUID = 5215923043342722378L;

	private final JTabulkaZdroju tabulka = new JTabulkaZdroju(null);

	public JInformaceOZdrojichDialog() {
		setTitle("Přehled zdrojů kešoidů");
	}

	@Override
	public void initAfterEventReceiverRegistration() {
		init();
	}

	public void inject(final KesoidModel kesoidModel) {
		tabulka.setOvladani(kesoidModel);
		tabulka.obnov(kesoidModel.getStavZdroju());
		tabulka.setPovoleneTypy(JPrepinaceZdroju.povoleneTypy(kesoidModel.getUmisteniSouboru()));
	}

	public void onEvent(final KesoidUmisteniSouboruChangedEvent event) {
		tabulka.setPovoleneTypy(JPrepinaceZdroju.povoleneTypy(event.getUmisteniSouboru()));
	}

	public void onEvent(final StavZdrojuEvent event) {
		tabulka.obnov(event.getStav());
	}

	public void onEvent(final KeskyNactenyEvent event) {
		tabulka.setCasDat(JTabulkaZdroju.casDat(event.getVsechny().getInformaceOZdrojich().getYungest()));
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return "ZdrojeKesoidu";
	}

	@Override
	protected void initComponents() {
		add(tabulka);
	}

	JTabulkaZdroju getTabulka() {
		return tabulka;
	}
}
