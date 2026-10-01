package cz.geokuk.plugins.mapy.stahovac;

import java.awt.Dimension;

import javax.swing.*;

import cz.geokuk.framework.AfterEventReceiverRegistrationInit;
import cz.geokuk.framework.JMyDialog0;

public class JKachleOflinerPocetStazenychDialog extends JMyDialog0 implements AfterEventReceiverRegistrationInit {

	private static final long serialVersionUID = 7180968190465321695L;

	private JLabel pocetStazenychKachli;

	private JButton zastavit;

	private DavkaStahovani davka;

	public JKachleOflinerPocetStazenychDialog() {
		setTitle("Průběh hromadného dotažení mapových dlaždic");
		init();
	}

	@Override
	public void initAfterEventReceiverRegistration() {

	}

	void setDavka(final DavkaStahovani davka) {
		this.davka = davka;
		obnov();
	}

	void obnov() {
		pocetStazenychKachli.setText("<html>" + davka.popis());
		zastavit.setEnabled(!davka.jeHotova());
	}

	@Override
	protected String getTemaNapovedyDialogu() {
		return "StahovaniMapovychDlazdic";
	}

	@Override
	protected void initComponents() {
		// Napřed registrovat, aby při inicializaci už byl výsledek tady
		final Box box = Box.createVerticalBox();
		pocetStazenychKachli = new JLabel();
		box.add(Box.createVerticalStrut(20));
		pocetStazenychKachli.setAlignmentX(CENTER_ALIGNMENT);
		pocetStazenychKachli.setPreferredSize(new Dimension(400, 60));
		box.add(pocetStazenychKachli);
		box.add(Box.createVerticalStrut(10));
		zastavit = new JButton("Zastavit stahování");
		zastavit.setAlignmentX(CENTER_ALIGNMENT);
		zastavit.addActionListener(e -> davka.zastav("na přání uživatele."));
		box.add(zastavit);
		box.add(Box.createVerticalStrut(20));
		pack();
		add(box);
	}

}
