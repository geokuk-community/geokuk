/**
 *
 */
package cz.geokuk.core.program;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JOptionPane;

import cz.geokuk.core.profile.ProfileModel;
import cz.geokuk.framework.Action0;
import cz.geokuk.framework.Dlg;
import cz.geokuk.plugins.cesty.akce.soubor.UlozAction;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Martin Veverka
 *
 */
@Slf4j
public class CloseAction extends Action0 {

	private static final long serialVersionUID = -8054017274338240706L;

	private UlozAction ulozAction;

	private ProfileModel profileModel;

	/**
	 *
	 */
	public CloseAction() {
		super("Konec");
		putValue(SHORT_DESCRIPTION, "Zavřít okno a ukončit process");
		putValue(MNEMONIC_KEY, KeyEvent.VK_K);
	}
	/*
	 * (non-Javadoc)
	 *
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */

	@Override
	public void actionPerformed(final ActionEvent e) {
		if (ulozAction.ulozitSDotazem()) {
			if (!ulozNastaveniNeboPresto()) {
				return;
			}
			getMainFrame().dispose();
			System.exit(0);
		}
	}

	/** Když nastavení nejde uložit (plný disk, složka jen pro čtení), musí jít program přesto ukončit. */
	private boolean ulozNastaveniNeboPresto() {
		try {
			profileModel.ulozNastaveni();
			return true;
		} catch (final RuntimeException | Error e) {
			log.error("Nastavení nelze uložit", e);
			Throwable pricina = e;
			while (pricina.getCause() != null) {
				pricina = pricina.getCause();
			}
			final Object[] volby = { "Ukončit bez uložení", "Neukončovat" };
			final int n = JOptionPane.showOptionDialog(Dlg.parentFrame(), "Nastavení se nepodařilo uložit:\n" + pricina.getMessage() + "\n\nUkončit program bez uložení nastavení?",
					"Geokuk: Chyba", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, volby, volby[0]);
			return n == 0;
		}
	}

	public void inject(final ProfileModel profileModel) {
		this.profileModel = profileModel;
	}

	public void inject(final UlozAction ulozAction) {
		this.ulozAction = ulozAction;
	}

}
