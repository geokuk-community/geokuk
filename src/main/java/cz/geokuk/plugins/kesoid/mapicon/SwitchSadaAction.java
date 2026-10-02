/**
 *
 */
package cz.geokuk.plugins.kesoid.mapicon;

import javax.swing.Action;
import javax.swing.Icon;

import cz.geokuk.framework.ToggleAction0;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;

/**
 * @author Martin Veverka
 *
 */
public class SwitchSadaAction extends ToggleAction0 {

	private static final long serialVersionUID = -8054017274338240706L;
	private KesoidModel kesoidModel;
	private final ASada sada;

	/**
	 *
	 */
	public SwitchSadaAction(final ASada sada, final Icon ikonaSady) {
		super(sada.name());
		this.sada = sada;
		super.putValue(SMALL_ICON, ikonaSady);
		putValue(Action.SHORT_DESCRIPTION, "Výběr sady ikon: " + sada);

	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	@Override
	protected void onSlectedChange(final boolean nastaveno) {
		if (nastaveno) {
			kesoidModel.setJmenoAktualniSadyIkon(sada);
		}
	}

}
