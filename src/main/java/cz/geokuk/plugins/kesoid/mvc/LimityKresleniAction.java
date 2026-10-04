package cz.geokuk.plugins.kesoid.mvc;

import java.awt.event.ActionEvent;

import cz.geokuk.framework.Action0;

public class LimityKresleniAction extends Action0 {

	private static final long serialVersionUID = 1L;

	public LimityKresleniAction() {
		super("Limity kreslení...");
		putValue(SHORT_DESCRIPTION, "Kolik keší ve výřezu se ještě kreslí jako ikony a kolik jako tečky.");
	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		factory.init(new JLimityKresleniDialog()).setVisible(true);
	}
}
