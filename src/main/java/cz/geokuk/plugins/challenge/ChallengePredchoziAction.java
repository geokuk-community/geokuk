package cz.geokuk.plugins.challenge;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import cz.geokuk.framework.Action0;

/** Předchozí chybějící (Shift+F4). */
public class ChallengePredchoziAction extends Action0 {

	private static final long serialVersionUID = 1L;
	private transient ChallengeModel model;
	private transient PlanovacChallengeSluzba sluzba;

	public ChallengePredchoziAction() {
		super("Předchozí chybějící");
		putValue(SHORT_DESCRIPTION, "Vrátí se k předchozí vybrané keši.");
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F4, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
		setEnabled(false);
	}

	public void inject(final ChallengeModel model) {
		this.model = model;
	}

	public void inject(final PlanovacChallengeSluzba sluzba) {
		this.sluzba = sluzba;
	}

	public void onEvent(final ChallengeZmenenyEvent event) {
		setEnabled(ChallengeAkce.dostupne(event.getModel()));
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final Challenge c = model.hlavni();
		if (c == null || sluzba.planovac() == null) {
			return;
		}
		sluzba.planovac().predchoziChybejici(c);
	}
}
