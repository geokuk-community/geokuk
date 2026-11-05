package cz.geokuk.plugins.challenge;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import cz.geokuk.framework.Action0;

/** Další chybějící (F4). */
public class ChallengeDalsiAction extends Action0 {

	private static final long serialVersionUID = 1L;
	private transient ChallengeModel model;
	private transient PlanovacChallengeSluzba sluzba;

	public ChallengeDalsiAction() {
		super("Další chybějící");
		putValue(SHORT_DESCRIPTION, "Vystředí mapu na nejbližší keš, která zaplní chybějící sektor.");
		putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
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
		sluzba.planovac().dalsiChybejici(c);
	}
}
