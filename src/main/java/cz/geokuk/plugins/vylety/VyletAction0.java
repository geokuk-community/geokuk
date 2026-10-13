package cz.geokuk.plugins.vylety;

import javax.swing.Icon;

import cz.geokuk.framework.Action0;

public abstract class VyletAction0 extends Action0 {

	private static final long serialVersionUID = -2637836928166450446L;

	protected VyletModel vyletModel;
	/** Povolení podle stavu výletu a keše; během přepínání výletu jsou akce, které výlet upravují, přesto zašedlé. */
	private boolean povoleno = true;

	public VyletAction0(final String string) {
		super(string);
		setEnabled(false);
	}

	/**
	 * @param aString
	 * @param aIcon
	 */
	public VyletAction0(final String aString, final Icon aIcon) {
		super(aString, aIcon);
		setEnabled(false);
	}

	public void inject(final VyletModel vyletModel) {
		this.vyletModel = vyletModel;

	}

	public final void onEvent(final VyletChangeEvent aEvent) {
		vyletChanged();
	}

	public final void onEvent(final VyletyZmenenyEvent aEvent) {
		setEnabled(povoleno);
	}

	@Override
	public void setEnabled(final boolean povoleno) {
		this.povoleno = povoleno;
		super.setEnabled(povoleno && !(upravujeVylet() && vyletModel != null && vyletModel.isPrepina()));
	}

	/** Akce mění obsah výletu, během přepínání výletu se nedá použít. */
	protected boolean upravujeVylet() {
		return false;
	}

	protected void vyletChanged() {}
}
