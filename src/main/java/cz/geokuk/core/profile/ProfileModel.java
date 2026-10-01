package cz.geokuk.core.profile;

import java.io.IOException;

import cz.geokuk.framework.Model0;

public class ProfileModel extends Model0 {

	/** Zapíše nastavení do souboru, chybu ohlásí výjimkou. */
	public void ulozNastaveni() {
		try {
			Nastaveni.koren().ulozHned();
		} catch (final IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	@Override
	protected void initAndFire() {
		// nastavení se načítá už při startu programu
	}
}
