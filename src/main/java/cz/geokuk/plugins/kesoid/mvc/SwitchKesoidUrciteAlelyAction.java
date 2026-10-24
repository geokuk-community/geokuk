/**
 *
 */
package cz.geokuk.plugins.kesoid.mvc;

import javax.swing.Icon;

import cz.geokuk.framework.AfterEventReceiverRegistrationInit;
import cz.geokuk.framework.ToggleAction0;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.genetika.Alela;
import cz.geokuk.plugins.kesoid.mapicon.IkonBag;
import cz.geokuk.plugins.kesoid.mapicon.StavIkona;
import cz.geokuk.util.lang.FString;

/**
 * @author Martin Veverka
 *
 */
public class SwitchKesoidUrciteAlelyAction extends ToggleAction0 implements AfterEventReceiverRegistrationInit {

	private static final long serialVersionUID = -8054017274338240706L;
	private volatile IkonBag ikonBag;
	private final Alela alela;
	private KesoidModel kesoidModel;
	private KesBag vsechny;

	/**
	 *
	 */
	public SwitchKesoidUrciteAlelyAction(final Alela alela) {
		super("Zapnutí a vypnutí alely");
		this.alela = alela;
		// putValue(MNEMONIC_KEY, InputEvent.)
		// putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));

	}
	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */

	/*
	 * (non-Javadoc)
	 *
	 * @see cz.geokuk.program.AfterEventReceiverRegistrationInit#initAfterEventReceiverRegistration()
	 */
	@Override
	public void initAfterEventReceiverRegistration() {
		super.putValue(NAME, sestavJmeno());
		super.putValue(SHORT_DESCRIPTION, sestavJmeno());
	}

	/** Vykreslí ikonu alely; skládá obrázky ze sady, proto mimo EDT. */
	public Icon pripravIkonu() {
		return StavIkona.ikonaToolbaru(ikonBag, alela);
	}

	public void setIkona(final Icon ikona) {
		super.putValue(SMALL_ICON, ikona);
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	public void onEvent(final IkonyNactenyEvent event) {
		ikonBag = event.getBag();
	}

	public void onEvent(final KeskyNactenyEvent event) {
		vsechny = event.getVsechny();
	}

	public void onEvent(final KeskyVyfiltrovanyEvent event) {
		vsechny = event.getVsechny();
		// TODO [veverka] Tydy původne bylo tot, ale to není dobře užít toString, zkontrolovat, že to finguje. -- 11. 12. 2019 9:35:23 veverka
		// final boolean nechtena = event.getModel().getFilter().getJmenaNechtenychAlel().contains(alela.toString());
		final boolean nechtena = event.getModel().getFilter().getJmenaNechtenychAlel().getQualNames().contains(alela.qualName());
		setSelected(!nechtena);
		super.putValue(SHORT_DESCRIPTION, sestavJmeno());
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		kesoidModel.filtrujDleAlely(alela.qualName(), nastaveno);
	}

	/** Název alely (u symbolů text z GPX) se zobrazí jako text. */
	static String jmeno(final Alela alela, final int pocet) {
		return String.format("<html>%s: <b>%s</b> <i>(%d)</i>", FString.html(alela.getGen().getDisplayName()), FString.html(alela.getDisplayName()), pocet);
	}

	private String sestavJmeno() {
		return jmeno(alela, vsechny.getPoctyAlel().count(alela));
	}

}
