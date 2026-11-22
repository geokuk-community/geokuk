package cz.geokuk.plugins.kesoid.detail;

import cz.geokuk.framework.Model0;

/** Drží doplněk detailu kešoidu, který zaregistroval jiný modul; změnu oznámí událostí. Jen ve vlákně Swingu. */
public class DoplnekDetailuModel extends Model0 {

	private DoplnekDetailuKese doplnek;

	/** Zaregistruje doplněk, null ho odregistruje. Detail se obnoví i při stejném doplňku, protože se mohla změnit jeho data. */
	public void nastav(final DoplnekDetailuKese doplnek) {
		this.doplnek = doplnek;
		fire(new DoplnekDetailuEvent(doplnek));
	}

	public DoplnekDetailuKese getDoplnek() {
		return doplnek;
	}

	@Override
	protected void initAndFire() {
		fire(new DoplnekDetailuEvent(doplnek));
	}
}
